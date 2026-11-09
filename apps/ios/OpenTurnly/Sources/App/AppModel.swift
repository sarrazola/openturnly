import Foundation
import Observation
import SwiftUI

/// The root state machine: who is signed in, against which server, and which
/// business is open.
@MainActor
@Observable
final class AppModel {
    enum Screen: Equatable { case loading, signIn, reconnect, noBusinesses, pickBusiness, main }

    var screen: Screen = .loading
    private(set) var session: StoredSession?
    private(set) var businesses: [Business] = []
    /// The open business, with its catalog and API.
    private(set) var workspace: Workspace?
    /// Shown on the sign-in screen after a session ends on its own.
    var signInNotice: String?
    var signingOut = false

    let toasts = ToastCenter()
    let push = PushController()
    private var generation = 0
    /// A tapped notification waiting for the businesses to load.
    private var pendingTap: NotificationData?

    init() {
        APIClient.shared.setUnauthorizedHandler { [weak self] in
            self?.sessionExpired()
        }
        let events = PushEvents.shared
        events.onToken = { [weak self] token in
            guard let self else { return }
            Task { await self.push.received(token) }
        }
        events.onArrival = { [weak self] data in self?.notificationArrived(data) }
        events.onTap = { [weak self] data in self?.notificationTapped(data) }
    }

    var api: OpenTurnlyAPI? { session.map { OpenTurnlyAPI(server: $0.server, token: $0.token) } }

    // MARK: Session lifecycle

    func restore() async {
        generation += 1
        let current = generation
        guard let stored = SessionStore.load() else {
            screen = .signIn
            return
        }
        session = stored
        push.begin(stored)
        screen = .loading
        await loadBusinesses(generation: current)
    }

    /// Sign-in succeeded against `server`.
    func signedIn(server: String, token: String, user: User) async {
        let stored = StoredSession(server: server, token: token, user: user)
        do {
            try SessionStore.store(stored)
        } catch {
            toasts.show(L.Errors.generic, error: true)
            return
        }
        Preferences.lastServer = server
        generation += 1
        let current = generation
        signInNotice = nil
        session = stored
        push.begin(stored)
        screen = .loading
        await loadBusinesses(generation: current)
    }

    /// Fetch the businesses this person can open and open one.
    func loadBusinesses(generation current: Int? = nil) async {
        let current = current ?? generation
        guard let api else { screen = .signIn; return }
        do {
            async let me = api.me()
            async let list = api.businesses()
            let (user, found) = try await (me, list)
            guard current == generation, var stored = session else { return }
            if stored.user != user {
                stored.user = user
                session = stored
                try? SessionStore.store(stored)
            }
            businesses = found
            if found.isEmpty {
                workspace = nil
                screen = .noBusinesses
                return
            }
            let remembered = Preferences.lastBusiness(server: stored.server, user: stored.user.id)
            if let keep = workspace?.business.id, let match = found.first(where: { $0.id == keep }) {
                workspace?.business = match
                screen = .main
            } else if let match = found.first(where: { $0.id == remembered }) ?? (found.count == 1 ? found.first : nil) {
                open(match)
            } else {
                screen = .pickBusiness
            }
            takePendingTap()
        } catch let error as APIError where error.isUnauthorized {
            // The unauthorized handler already signed out.
        } catch {
            guard current == generation else { return }
            screen = .reconnect
        }
    }

    func open(_ business: Business) {
        guard let session else { return }
        Preferences.setLastBusiness(business.id, server: session.server, user: session.user.id)
        if workspace?.business.id != business.id {
            workspace = Workspace(business: business, session: session, toasts: toasts, push: push)
        }
        screen = .main
    }

    func updateUser(_ user: User) {
        guard var stored = session else { return }
        stored.user = user
        session = stored
        try? SessionStore.store(stored)
    }

    func signOut() async {
        guard !signingOut, let api else { return }
        signingOut = true
        // Ending the session on the server is best effort: the token is forgotten either way.
        await push.signOut()
        try? await api.signOut()
        forget()
        signingOut = false
    }

    /// Any 401: the session ended (expired, revoked, password changed elsewhere).
    func sessionExpired() {
        guard session != nil else { return }
        forget()
        signInNotice = L.SignIn.sessionEnded
    }

    private func forget() {
        generation += 1
        try? SessionStore.clear()
        push.end()
        pendingTap = nil
        session = nil
        businesses = []
        workspace = nil
        screen = .signIn
    }

    // MARK: Notifications

    /// A notification arrived while the app is open: the bell and the agenda catch up.
    private func notificationArrived(_ data: NotificationData) {
        guard let workspace else { return }
        Task { await workspace.refreshNotifications() }
        if data.businessId == nil || data.businessId == workspace.id { workspace.changed() }
    }

    /// A notification was tapped: its business, then its appointment. One that
    /// launched the app waits here until the saved session has loaded.
    private func notificationTapped(_ data: NotificationData) {
        guard screen != .signIn else { return }
        pendingTap = data
        takePendingTap()
    }

    private func takePendingTap() {
        guard let tap = pendingTap, let api, !businesses.isEmpty, [.main, .pickBusiness].contains(screen) else { return }
        pendingTap = nil
        if let id = tap.businessId {
            // Not one of this person's businesses (any more): nothing to open.
            guard let business = businesses.first(where: { $0.id == id }) else { return }
            if workspace?.business.id != id { open(business) }
        }
        guard let workspace else { return }
        Task {
            if let id = tap.notificationId, let unreadTotal = try? await api.markNotificationsRead(ids: [id]) {
                push.setBadge(unreadTotal)
            }
            await workspace.refreshNotifications()
        }
        if let appointmentId = tap.appointmentId {
            Task { await workspace.openAppointment(appointmentId, near: tap.startsAt, onTop: false) }
        }
    }
}

/// One open business: its API, its catalog (locations, services, staff) and a
/// revision number screens watch to reload after something changed elsewhere.
@MainActor
@Observable
final class Workspace {
    var business: Business
    let api: OpenTurnlyAPI
    let session: StoredSession
    let toasts: ToastCenter
    let push: PushController

    var locations: [Location] = []
    var services: [Service] = []
    var professionals: [Professional] = []
    var catalogLoaded = false
    var catalogError: Error?
    /// Bumped after a booking, a cancellation, a sale…: lists showing that data reload.
    var revision = 0

    /// The tab showing, and the agenda's screens, so a notification can lead somewhere.
    var tab: MainTabView.Tab = .agenda
    var agendaPath: [Route] = []
    /// A day the agenda should show next (after booking or moving an appointment).
    var agendaDay: LocalDate?
    /// The agenda's location filter, the first choice of a new booking.
    var agendaLocation: String?
    /// Unread notifications in this business, for the bell.
    var unread = 0
    /// False against a server without notifications: the bell hides.
    var hasNotifications = true

    init(business: Business, session: StoredSession, toasts: ToastCenter, push: PushController) {
        self.business = business
        self.session = session
        self.toasts = toasts
        self.push = push
        api = OpenTurnlyAPI(server: session.server, token: session.token)
        ErrorText.timeZone = business.clock.timeZone
    }

    var id: String { business.id }
    var isAdmin: Bool { business.isAdmin }
    var clock: BusinessClock { business.clock }
    var currency: String { business.currency }
    var server: String { session.server }

    func money(_ amount: Int) -> String { business.money(amount) }

    /// The public booking page.
    var bookingURL: URL? { URL(string: "\(session.server)/\(business.slug)") }

    /// The web panel on the same server, for everything the app leaves out.
    var webPanelURL: URL? { URL(string: "\(session.server)/admin") }

    func absoluteURL(_ relative: String?) -> URL? {
        guard let relative, !relative.isEmpty else { return nil }
        if relative.hasPrefix("http") { return URL(string: relative) }
        return URL(string: session.server + relative)
    }

    func loadCatalog(force: Bool = false) async {
        if catalogLoaded && !force { return }
        do {
            async let locations = api.locations(business.id)
            async let services = api.services(business.id)
            async let professionals = api.professionals(business.id)
            let (l, s, p) = try await (locations, services, professionals)
            self.locations = l.sorted { ($0.position ?? 0, $0.name) < ($1.position ?? 0, $1.name) }
            self.services = s
            self.professionals = p
            catalogLoaded = true
            catalogError = nil
        } catch {
            catalogError = error
        }
    }

    func changed() { revision += 1 }

    // MARK: Lookups

    func location(_ id: String?) -> Location? { locations.first { $0.id == id } }
    func service(_ id: String?) -> Service? { services.first { $0.id == id } }
    func professional(_ id: String?) -> Professional? { professionals.first { $0.id == id } }

    /// The web's calendar colour for a professional (by their place in the team).
    func color(for resourceId: String?) -> Color {
        Theme.professional(TextRules.professionalColor(resourceId, in: professionals.map(\.id)))
    }

    func colorHex(for resourceId: String?) -> String {
        TextRules.professionalColor(resourceId, in: professionals.map(\.id))
    }

    var activeLocations: [Location] { locations.filter(\.active) }

    /// The professional this person is, when they are one.
    var me: Professional? { professional(business.professionalId) }

    /// Reload the one appointment a detail screen shows (there is no route for one).
    func reloadAppointment(_ id: String, near date: Date) async throws -> Appointment? {
        let day = clock.localDate(of: date)
        let range = clock.range(day, through: day)
        let list = try await api.appointments(business.id, from: range.from, to: range.to, includeCancelled: true)
        return list.first { $0.id == id }
    }

    // MARK: Notifications

    /// The bell's count and the icon's number, from the server.
    func refreshNotifications() async {
        guard hasNotifications else { return }
        do {
            let page = try await api.notifications(business.id)
            showCounts(unread: page.unread, unreadTotal: page.unreadTotal)
        } catch let error as APIError where error.status == 404 {
            hasNotifications = false
        } catch {
            // The bell keeps its last count until the next refresh.
        }
    }

    func showCounts(unread: Int, unreadTotal: Int) {
        self.unread = unread
        push.setBadge(unreadTotal)
    }

    /// An appointment's detail on the agenda, from the notifications screen
    /// (`onTop`, so Back returns there) or from a tapped notification.
    func openAppointment(_ id: String, near startsAt: Date?, onTop: Bool) async {
        do {
            guard let startsAt, let found = try await reloadAppointment(id, near: startsAt) else {
                toasts.show(L.Appointment.notFound, error: true)
                return
            }
            tab = .agenda
            if onTop { agendaPath.append(.appointment(found)) } else { agendaPath = [.appointment(found)] }
        } catch is CancellationError {
        } catch {
            toasts.error(error)
        }
    }
}
