import SwiftUI

/// Everyone who books with the business, A to Z, searchable as you type.
///
/// The server sends the whole list at once (no paging), so it is filtered on
/// the phone; the list draws rows lazily as the person scrolls.
struct CustomersView: View {
    @Environment(Workspace.self) private var workspace
    @State private var customers: [CustomerSummary] = []
    @State private var loaded = false
    @State private var error: Error?
    @State private var query = ""
    @State private var creating = false
    @State private var booking: PickedCustomer?
    @State private var opened: Customer?

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
                Text(L.Customers.count(list.count))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Theme.muted)
                    .listRowSeparator(.hidden)
                ForEach(CustomerRules.sections(list, name: \.fullName), id: \.key) { section in
                    Section {
                        ForEach(section.items) { customer in
                            row(customer)
                        }
                    } header: {
                        Text(section.key)
                    }
                    .modifier(SectionIndex(label: section.key))
                }
            }
        }
        .listStyle(.plain)
        .modifier(SectionIndexVisible())
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L.Customers.search)
        .navigationTitle(L.Customers.title)
        .navigationDestination(item: $opened) { customer in
            CustomerDetailView(customerId: customer.id, name: customer.fullName)
        }
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
            CustomerFormView(customer: nil) { saved in
                Task { await load() }
                opened = saved
            }
        }
        .sheet(item: Binding(get: { booking.map(IdentifiedCustomer.init) }, set: { booking = $0?.customer })) { item in
            BookingView(date: workspace.clock.today(), preset: item.customer)
        }
    }

    private func row(_ customer: CustomerSummary) -> some View {
        let picked = PickedCustomer(id: customer.id, name: customer.fullName, phone: customer.phone, email: customer.email)
        return NavigationLink(value: Route.customer(id: customer.id, name: customer.fullName)) {
            CustomerRow(customer: customer)
        }
        .contextMenu {
            Button { booking = picked } label: { Label(L.Agenda.newAppointment, systemImage: "calendar.badge.plus") }
            if let url = Phone.callURL(customer.phone) {
                Link(destination: url) { Label(L.Common.call, systemImage: "phone") }
            }
            if let url = Phone.whatsAppURL(customer.phone) {
                Link(destination: url) { Label(L.Common.whatsapp, systemImage: "message") }
            }
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                booking = picked
            } label: {
                Label(L.Agenda.newAppointment, systemImage: "calendar.badge.plus")
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

/// The letter a section shows in the list's side index, where the system has one.
private struct SectionIndex: ViewModifier {
    let label: String

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.sectionIndexLabel(Text(label))
        } else {
            content
        }
    }
}

private struct SectionIndexVisible: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.listSectionIndexVisibility(.visible)
        } else {
            content
        }
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
                    Text(L.Customers.next(Fmt.dayShort(workspace.clock.localDate(of: next))))
                        .font(.caption.weight(.semibold)).foregroundStyle(Theme.brand)
                }
                if let visits = customer.visits, visits > 0 {
                    Text(L.Customers.visitCount(visits)).font(.caption).foregroundStyle(Theme.subtle)
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private var contactLine: String {
        let phone = Phone.format(customer.phone)
        if !phone.isEmpty { return phone }
        return customer.email ?? L.Customers.noContact
    }
}
