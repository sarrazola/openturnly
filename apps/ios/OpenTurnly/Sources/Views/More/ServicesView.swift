import SwiftUI

/// The services customers can book, by category, with a quick visibility switch.
struct ServicesView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var creating = false
    @State private var query = ""

    private var groups: [(category: String, items: [Service])] {
        let list = workspace.services
            .filter { query.isEmpty || TextRules.matches(query, [$0.name, $0.category]) }
            .sorted { ($0.position ?? 0, $0.name) < ($1.position ?? 0, $1.name) }
        let grouped = Dictionary(grouping: list) { $0.category ?? L.Services.uncategorized }
        return grouped.keys.sorted { a, b in
            if a == L.Services.uncategorized { return false }
            if b == L.Services.uncategorized { return true }
            return a.localizedCaseInsensitiveCompare(b) == .orderedAscending
        }.map { ($0, grouped[$0] ?? []) }
    }

    var body: some View {
        List {
            if !workspace.catalogLoaded {
                RowsSkeleton(count: 6).listRowInsets(EdgeInsets())
            } else if workspace.services.isEmpty {
                EmptyState(symbol: "scissors", title: L.Services.emptyTitle, message: L.Services.emptyBody,
                           actionTitle: workspace.isAdmin ? L.Services.new : nil) { creating = true }
            } else {
                ForEach(groups, id: \.category) { group in
                    Section(group.category) {
                        ForEach(group.items) { service in
                            if workspace.isAdmin {
                                NavigationLink(value: Route.service(service)) { ServiceRow(service: service) }
                                    .swipeActions(edge: .trailing) {
                                        Button {
                                            Task { await toggle(service) }
                                        } label: {
                                            Label(service.active ? L.Services.hidden : L.Services.visible,
                                                  systemImage: service.active ? "eye.slash" : "eye")
                                        }
                                        .tint(service.active ? .gray : Theme.brand)
                                    }
                            } else {
                                ServiceRow(service: service)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: L.Common.search)
        .navigationTitle(L.Services.title)
        .toolbar {
            if workspace.isAdmin {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Label(L.Services.new, systemImage: "plus") }
                }
            }
        }
        .refreshable { await workspace.loadCatalog(force: true) }
        .task { await workspace.loadCatalog() }
        .sheet(isPresented: $creating) {
            NavigationStack { ServiceFormView(service: nil) }
        }
    }

    private func toggle(_ service: Service) async {
        do {
            _ = try await workspace.api.updateService(workspace.id, service.id, ["active": .bool(!service.active)])
            Haptics.success()
            await workspace.loadCatalog(force: true)
        } catch {
            toasts.error(error)
        }
    }
}

private struct ServiceRow: View {
    let service: Service
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(service.name).font(.body.weight(.medium)).foregroundStyle(service.active ? Theme.ink : Theme.muted)
                HStack(spacing: 6) {
                    Label(WallTime.duration(service.durationMin), systemImage: "clock").labelStyle(.titleAndIcon)
                    if let mode = service.paymentMode, mode != "none" {
                        Text("· \(L.Services.paymentMode(mode))")
                    }
                }
                .font(.footnote).foregroundStyle(Theme.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(workspace.business.price(service.priceAmount))
                    .font(.body.weight(.semibold)).monospacedDigit().foregroundStyle(Theme.ink)
                if !service.active { Chip(text: L.Services.hidden, tone: .neutral) }
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }
}

/// Creating or editing a service.
struct ServiceFormView: View {
    let service: Service?
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var category = ""
    @State private var description = ""
    @State private var duration = 30
    @State private var price: Int?
    @State private var active = true
    @State private var busy = false
    @State private var failure: Error?
    @State private var attempted = false
    @State private var prepared = false

    var body: some View {
        Form {
            Section {
                TextField(L.Services.namePlaceholder, text: $name)
                TextField(L.Services.categoryPlaceholder, text: $category)
            } header: {
                Text(L.Services.name)
            } footer: {
                if attempted && name.trimmingCharacters(in: .whitespaces).isEmpty { FieldError(text: L.Services.nameRequired) }
            }
            Section {
                Stepper(value: $duration, in: 5...600, step: 5) {
                    HStack {
                        Text(L.Services.duration)
                        Spacer()
                        Text(WallTime.duration(duration)).monospacedDigit().foregroundStyle(Theme.muted)
                    }
                }
                HStack {
                    Text(L.Services.price)
                    Spacer()
                    MoneyField(title: "0", amount: $price, currency: workspace.currency)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 160)
                }
            } footer: {
                if attempted && price == nil { FieldError(text: L.Services.priceRequired) }
            }
            Section(L.Services.description) {
                TextField(L.Services.descriptionPlaceholder, text: $description, axis: .vertical).lineLimit(2...6)
            }
            Section {
                Toggle(L.Services.active, isOn: $active).tint(Theme.brand)
            } footer: {
                Text(L.Services.activeHint)
            }
            if let service, let mode = service.paymentMode {
                Section {
                    LabeledContent(L.Services.onlinePayment, value: L.Services.paymentMode(mode))
                }
            }
            if let failure {
                Section { FieldError(text: ErrorText.message(failure)) }
            }
        }
        .navigationTitle(service == nil ? L.Services.new : L.Services.edit)
        .keyboardDoneButton()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if service == nil {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
            }
            ToolbarItem(placement: .confirmationAction) {
                if busy { ProgressView() } else {
                    Button(L.Common.save) { Task { await save() } }.fontWeight(.semibold)
                }
            }
        }
        .onAppear(perform: prepare)
    }

    private func prepare() {
        guard !prepared else { return }
        prepared = true
        guard let service else { return }
        name = service.name
        category = service.category ?? ""
        description = service.description ?? ""
        duration = service.durationMin
        price = service.priceAmount
        active = service.active
    }

    private func save() async {
        attempted = true
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty, let price else {
            Haptics.warning()
            return
        }
        busy = true
        failure = nil
        defer { busy = false }
        let body: [String: JSON] = [
            "name": .string(name.trimmingCharacters(in: .whitespaces)),
            "category": .text(category),
            "description": .text(description),
            "durationMin": .int(duration),
            "priceAmount": .int(price),
            "active": .bool(active),
        ]
        do {
            if let service {
                _ = try await workspace.api.updateService(workspace.id, service.id, body)
                toasts.show(L.Common.saved)
            } else {
                _ = try await workspace.api.createService(workspace.id, body)
                toasts.show(L.Services.created)
            }
            await workspace.loadCatalog(force: true)
            dismiss()
        } catch {
            failure = error
            Haptics.error()
        }
    }
}
