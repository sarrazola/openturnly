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
    private var generation = 0

    init() {
        APIClient.shared.setUnauthorizedHandler { [weak self] in
            self?.sessionExpired()
        }
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
            workspace = Workspace(business: business, session: session, toasts: toasts)
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
        session = nil
        businesses = []
        workspace = nil
        screen = .signIn
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

    var locations: [Location] = []
    var services: [Service] = []
    var professionals: [Professional] = []
    var catalogLoaded = false
    var catalogError: Error?
    /// Bumped after a booking, a cancellation, a sale…: lists showing that data reload.
    var revision = 0

    init(business: Business, session: StoredSession, toasts: ToastCenter) {
        self.business = business
        self.session = session
        self.toasts = toasts
        api = OpenTurnlyAPI(server: session.server, token: session.token)
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
}
