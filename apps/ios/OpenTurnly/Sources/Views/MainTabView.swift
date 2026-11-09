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
    case notifications
}

/// The bottom navigation: Agenda, Clientes, Ventas (admins) and Más.
///
/// The app is for the day: appointments, customers and the day's sales. Setting
/// the business up (hours, services, staff, stock) stays in the web panel.
/// A professional gets their own agenda and "More".
struct MainTabView: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @Environment(\.scenePhase) private var scenePhase

    enum Tab: Hashable { case agenda, customers, sales, more }

    var body: some View {
        @Bindable var workspace = workspace
        TabView(selection: $workspace.tab) {
            NavigationStack(path: $workspace.agendaPath) {
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
        .onChange(of: workspace.tab) { _, _ in Haptics.selection() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task {
                await workspace.refreshNotifications()
                await model.push.foregrounded()
            }
        }
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
            case .notifications: NotificationsView()
            }
        }
    }
}

extension View {
    func withRoutes() -> some View { modifier(RouteDestinations()) }
}

/// The open business's logo and name at the top of the agenda; tap to switch
/// when the person has more than one.
struct BusinessSwitcherButton: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @State private var showing = false

    var body: some View {
        // Only the business's logo (or initials) sits in the bar: the day title
        // below says where you are, and "More" carries the name.
        Group {
            if model.businesses.count > 1 {
                Button { showing = true } label: { mark(chevron: true) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(workspace.business.name), \(L.Business.switchBusiness)")
            } else {
                mark(chevron: false)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(workspace.business.name)
            }
        }
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

    private func mark(chevron: Bool) -> some View {
        HStack(spacing: 4) {
            BusinessLogo(business: workspace.business, server: workspace.server, size: 32)
            if chevron {
                Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundStyle(Theme.subtle)
            }
        }
    }
}
