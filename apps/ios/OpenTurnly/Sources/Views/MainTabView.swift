import SwiftUI

/// Where a navigation stack can go.
enum Route: Hashable {
    case appointment(Appointment)
    case customer(id: String, name: String)
    case sale(Sale)
    case saleId(String)
    case bookingLink
    case reports
    case account
}

/// The bottom navigation: Agenda, Clientes, Ventas (admins) and Más.
///
/// The app is for the day: appointments, customers and the day's sales. Setting
/// the business up (hours, services, staff, stock) stays in the web panel.
/// A professional gets their own agenda and "More".
struct MainTabView: View {
    @Environment(Workspace.self) private var workspace
    @State private var tab: Tab = .agenda

    enum Tab: Hashable { case agenda, customers, sales, more }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack {
                AgendaView()
                    .withRoutes()
            }
            .tabItem { Label(L.Tabs.agenda, systemImage: "calendar") }
            .tag(Tab.agenda)

            if workspace.isAdmin {
                NavigationStack {
                    CustomersView()
                        .withRoutes()
                }
                .tabItem { Label(L.Tabs.customers, systemImage: "person.2") }
                .tag(Tab.customers)

                NavigationStack {
                    SalesView()
                        .withRoutes()
                }
                .tabItem { Label(L.Tabs.sales, systemImage: "banknote") }
                .tag(Tab.sales)
            }

            NavigationStack {
                MoreView()
                    .withRoutes()
            }
            .tabItem { Label(L.Tabs.more, systemImage: "square.grid.2x2") }
            .tag(Tab.more)
        }
        .onChange(of: tab) { _, _ in Haptics.selection() }
        .task { await workspace.loadCatalog() }
    }
}

private struct RouteDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content.navigationDestination(for: Route.self) { route in
            switch route {
            case .appointment(let appointment): AppointmentDetailView(initial: appointment)
            case .customer(let id, let name): CustomerDetailView(customerId: id, name: name)
            case .sale(let sale): SaleDetailView(initial: sale)
            case .saleId(let id): SaleDetailView(saleId: id)
            case .bookingLink: BookingLinkView()
            case .reports: ReportsView()
            case .account: AccountView()
            }
        }
    }
}

extension View {
    func withRoutes() -> some View { modifier(RouteDestinations()) }
}

/// The open business at the top of a tab, tap to switch.
struct BusinessSwitcherButton: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @State private var showing = false

    var body: some View {
        Button {
            showing = true
        } label: {
            HStack(spacing: 8) {
                BusinessLogo(business: workspace.business, server: workspace.server, size: 28)
                Text(workspace.business.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                if model.businesses.count > 1 {
                    Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundStyle(Theme.subtle)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(workspace.business.name), \(L.Business.switchBusiness)")
        .sheet(isPresented: $showing) {
            NavigationStack {
                BusinessPickerList(businesses: model.businesses, current: workspace.business.id) { business in
                    showing = false
                    if business.id != workspace.business.id { model.open(business) }
                }
                .navigationTitle(L.Business.switchBusiness)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.close) { showing = false } } }
                .refreshable { await model.loadBusinesses() }
            }
            .presentationDetents([.medium, .large])
        }
    }
}
