import SwiftUI

/// The team that serves customers.
struct ProfessionalsView: View {
    @Environment(Workspace.self) private var workspace

    var body: some View {
        List {
            if !workspace.catalogLoaded {
                RowsSkeleton(count: 4).listRowInsets(EdgeInsets())
            } else if workspace.professionals.isEmpty {
                EmptyState(symbol: "person.2", title: L.Professionals.emptyTitle, message: L.Professionals.emptyBody)
            } else {
                ForEach(workspace.professionals) { professional in
                    NavigationLink(value: Route.professional(professional)) {
                        HStack(spacing: 12) {
                            Avatar(name: professional.name, colorHex: workspace.colorHex(for: professional.id), size: 46,
                                   photoURL: workspace.absoluteURL(professional.photoUrl))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(professional.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                                Text("\(L.Professionals.servicesCount(professional.serviceIds?.count ?? 0)) · \(professional.hasOwnHours == true ? L.Professionals.ownHours : L.Professionals.businessHours)")
                                    .font(.footnote).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            Chip(text: professional.active ? L.Professionals.available : L.Professionals.unavailable,
                                 tone: professional.active ? .success : .neutral)
                        }
                        .padding(.vertical, 3)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.Professionals.title)
        .refreshable { await workspace.loadCatalog(force: true) }
        .task { await workspace.loadCatalog() }
    }
}

/// A professional: availability, services, commissions and weekly hours.
struct ProfessionalDetailView: View {
    /// What the server has: the form is dirty when it differs from this.
    @State private var professional: Professional
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var name = ""
    @State private var active = true
    @State private var serviceIds: Set<String> = []
    @State private var serviceCommission = 0.0
    @State private var productCommission = 0.0
    @State private var hours: [String: [HoursRange]] = [:]
    @State private var hoursLoaded = false
    @State private var busy = false
    @State private var prepared = false
    @State private var failure: Error?

    init(professional: Professional) {
        _professional = State(initialValue: professional)
    }

    private var dirty: Bool {
        name != professional.name || active != professional.active || serviceIds != Set(professional.serviceIds ?? [])
            || serviceCommission != (professional.serviceCommission ?? 0) || productCommission != (professional.productCommission ?? 0)
    }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 14) {
                    Avatar(name: name.isEmpty ? professional.name : name, colorHex: workspace.colorHex(for: professional.id), size: 60,
                           photoURL: workspace.absoluteURL(professional.photoUrl))
                    TextField(L.Common.name, text: $name).font(.title3.weight(.semibold))
                }
                .padding(.vertical, 4)
                Toggle(L.Professionals.active, isOn: $active).tint(Theme.brand)
            } footer: {
                Text(L.Professionals.activeHint)
            }
            Section(L.Professionals.services) {
                ForEach(workspace.services) { service in
                    Button {
                        Haptics.selection()
                        if serviceIds.contains(service.id) { serviceIds.remove(service.id) } else { serviceIds.insert(service.id) }
                    } label: {
                        HStack {
                            Text(service.name).foregroundStyle(Theme.ink)
                            Spacer()
                            Image(systemName: serviceIds.contains(service.id) ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(serviceIds.contains(service.id) ? Theme.brand : Theme.subtle)
                                .font(.title3)
                        }
                    }
                }
            }
            Section {
                commissionRow(L.Professionals.serviceCommission, $serviceCommission)
                commissionRow(L.Professionals.productCommission, $productCommission)
            }
            ForEach(workspace.locations.filter { (professional.locationIds ?? [$0.id]).contains($0.id) }) { location in
                Section {
                    if !hoursLoaded {
                        SkeletonBlock(width: 180, height: 12).skeleton()
                    } else {
                        let own = hours[location.id] ?? []
                        WeeklyHoursList(ranges: own)
                        if own.isEmpty || professional.hasOwnHours != true {
                            Text(L.Professionals.usesBusinessHours).font(.footnote).foregroundStyle(Theme.muted)
                        }
                    }
                } header: {
                    Text(workspace.locations.count > 1 ? "\(L.Professionals.weeklyHours) · \(location.name)" : L.Professionals.weeklyHours)
                }
            }
            if let failure {
                Section { FieldError(text: ErrorText.message(failure)) }
            }
        }
        .navigationTitle(professional.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if busy { ProgressView() } else {
                    Button(L.Common.save) { Task { await save() } }
                        .fontWeight(.semibold)
                        .disabled(!dirty || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .onAppear(perform: prepare)
        .task { await loadHours() }
    }

    private func commissionRow(_ title: String, _ value: Binding<Double>) -> some View {
        Stepper(value: value, in: 0...100, step: 5) {
            HStack {
                Text(title)
                Spacer()
                Text("\(Int(value.wrappedValue)) %").monospacedDigit().foregroundStyle(Theme.muted)
            }
        }
    }

    private func prepare() {
        guard !prepared else { return }
        prepared = true
        name = professional.name
        active = professional.active
        serviceIds = Set(professional.serviceIds ?? [])
        serviceCommission = professional.serviceCommission ?? 0
        productCommission = professional.productCommission ?? 0
    }

    /// Their own hours at each location (an empty list means the location's).
    private func loadHours() async {
        var found: [String: [HoursRange]] = [:]
        for location in workspace.locations {
            if let response = try? await workspace.api.hours(workspace.id, locationId: location.id) {
                let own = response.professionals?[professional.id] ?? []
                found[location.id] = own.isEmpty ? response.business : own
            }
        }
        hours = found
        hoursLoaded = true
    }

    private func save() async {
        busy = true
        failure = nil
        defer { busy = false }
        do {
            try await workspace.api.updateProfessional(workspace.id, professional.id, [
                "name": .string(name.trimmingCharacters(in: .whitespaces)),
                "active": .bool(active),
                "serviceIds": .array(serviceIds.sorted().map(JSON.string)),
                "serviceCommission": .double(serviceCommission),
                "productCommission": .double(productCommission),
            ])
            toasts.show(L.Common.saved)
            await workspace.loadCatalog(force: true)
            if let fresh = workspace.professional(professional.id) { professional = fresh }
        } catch {
            failure = error
            Haptics.error()
        }
    }
}

/// Monday to Sunday with their opening ranges, read-only.
struct WeeklyHoursList: View {
    let ranges: [HoursRange]

    var body: some View {
        ForEach([1, 2, 3, 4, 5, 6, 0], id: \.self) { weekday in
            let day = ranges.filter { $0.weekday == weekday }.sorted { $0.start < $1.start }
            HStack {
                Text(Fmt.weekdayName(weekday)).foregroundStyle(day.isEmpty ? Theme.muted : Theme.ink)
                Spacer()
                Text(day.isEmpty ? L.Hours.closed : day.map { "\($0.start) – \($0.end)" }.joined(separator: ", "))
                    .monospacedDigit()
                    .foregroundStyle(day.isEmpty ? Theme.subtle : Theme.ink)
            }
            .font(.subheadline)
        }
    }
}
