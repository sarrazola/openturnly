import SwiftUI

/// Everyone who books with the business, searchable as you type.
///
/// The server sends the whole list at once (no paging), so it is filtered on
/// the phone and drawn a page at a time as the person scrolls.
struct CustomersView: View {
    @Environment(Workspace.self) private var workspace
    @State private var customers: [CustomerSummary] = []
    @State private var loaded = false
    @State private var error: Error?
    @State private var query = ""
    @State private var shown = 60
    @State private var creating = false
    @State private var booking: PickedCustomer?

    private var filtered: [CustomerSummary] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return customers }
        return customers.filter { TextRules.matches(needle, [$0.fullName, $0.email, $0.phone, $0.document]) }
    }

    var body: some View {
        let list = filtered
        List {
            if !loaded {
                RowsSkeleton(count: 9).listRowInsets(EdgeInsets()).listRowSeparator(.hidden)
            } else if let error, customers.isEmpty {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }.listRowSeparator(.hidden)
            } else if customers.isEmpty {
                EmptyState(symbol: "person.2", title: L.Customers.emptyTitle, message: L.Customers.emptyBody,
                           actionTitle: L.Customers.new) { creating = true }
                    .listRowSeparator(.hidden)
            } else if list.isEmpty {
                EmptyState(symbol: "magnifyingglass", title: L.Customers.noResults).listRowSeparator(.hidden)
            } else {
                Section {
                    ForEach(list.prefix(shown)) { customer in
                        NavigationLink(value: Route.customer(id: customer.id, name: customer.fullName)) {
                            CustomerRow(customer: customer)
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                booking = PickedCustomer(id: customer.id, name: customer.fullName, phone: customer.phone, email: customer.email)
                            } label: {
                                Label(L.Customers.bookFor, systemImage: "calendar.badge.plus")
                            }
                            .tint(Theme.brand)
                        }
                        .swipeActions(edge: .trailing) {
                            if let url = Phone.whatsAppURL(customer.phone) {
                                Link(destination: url) { Label(L.Common.whatsapp, systemImage: "message.fill") }
                                    .tint(Color(hex: "#16a34a"))
                            }
                            if let url = Phone.callURL(customer.phone) {
                                Link(destination: url) { Label(L.Common.call, systemImage: "phone.fill") }
                                    .tint(Color(hex: "#0284c7"))
                            }
                        }
                        .onAppear {
                            if customer.id == list.prefix(shown).last?.id, shown < list.count { shown += 60 }
                        }
                    }
                } header: {
                    Text(L.Customers.count(list.count))
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L.Customers.search)
        .onChange(of: query) { _, _ in shown = 60 }
        .navigationTitle(L.Customers.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    creating = true
                } label: {
                    Label(L.Customers.new, systemImage: "person.badge.plus")
                }
            }
        }
        .refreshable { await load() }
        .task { if !loaded { await load() } }
        .task(id: workspace.revision) { if workspace.revision > 0 { await load() } }
        .sheet(isPresented: $creating) {
            CustomerFormView(customer: nil) { _ in Task { await load() } }
        }
        .sheet(item: Binding(get: { booking.map(IdentifiedCustomer.init) }, set: { booking = $0?.customer })) { item in
            BookingView(date: workspace.clock.today(), preset: item.customer)
        }
    }

    private func load() async {
        do {
            customers = try await workspace.api.customers(workspace.id)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
        loaded = true
    }
}

/// Lets a picked customer drive `.sheet(item:)`.
struct IdentifiedCustomer: Identifiable {
    let customer: PickedCustomer
    var id: String { customer.id }
}

struct CustomerRow: View {
    let customer: CustomerSummary
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(spacing: 12) {
            Avatar(name: customer.fullName, size: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text(customer.fullName).font(.body.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(1)
                Text(contactLine).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 3) {
                if let next = customer.nextVisit {
                    Chip(text: Fmt.dayShort(workspace.clock.localDate(of: next)), tone: .brand, symbol: "calendar")
                }
                if let visits = customer.visits, visits > 0 {
                    Text(L.Agenda.count(visits)).font(.caption).foregroundStyle(Theme.subtle)
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private var contactLine: String {
        let phone = Phone.format(customer.phone)
        if !phone.isEmpty { return phone }
        return customer.email ?? "—"
    }
}
