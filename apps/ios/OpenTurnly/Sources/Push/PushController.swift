import Foundation
import Observation
import UIKit
import UserNotifications

/// Hands the application delegate's callbacks to whoever listens.
///
/// The delegate starts before anything is signed in, and a notification that
/// launched the app is tapped before the app knows its businesses, so a tap
/// nobody takes yet waits here.
@MainActor
final class PushEvents {
    static let shared = PushEvents()

    var onToken: ((String) -> Void)?
    var onArrival: ((NotificationData) -> Void)?
    var onTap: ((NotificationData) -> Void)? {
        didSet {
            guard let onTap, let pendingTap else { return }
            self.pendingTap = nil
            onTap(pendingTap)
        }
    }
    private var pendingTap: NotificationData?

    func token(_ token: String) { onToken?(token) }

    func arrived(_ data: NotificationData) { onArrival?(data) }

    func tapped(_ data: NotificationData) {
        if let onTap { onTap(data) } else { pendingTap = data }
    }
}

/// Push notifications for the signed-in person on this phone.
///
/// The app asks the operating system for the phone's native token and hands it
/// to the server, which delivers through whatever it was set up with. No
/// delivery service's SDK is involved. Three rules this keeps:
///
/// 1. Ask for nothing unless this build can notify (`Brand.hasPush`) and the
///    server says it sends. A permission prompt that leads nowhere only teaches
///    people to say no.
/// 2. Ask once, from the agenda after it loaded, never on the sign-in screens,
///    and never again after the person switched notifications off here.
/// 3. Never get in the way. Notifications sit on top of an app that works
///    without them, so every failure here is swallowed.
@MainActor
@Observable
final class PushController {
    /// Whether the signed-in server sends push at all; nil until it said.
    private(set) var serverEnabled: Bool?
    /// What the phone's Settings allow this app.
    private(set) var permission: UNAuthorizationStatus = .notDetermined
    /// The person switched notifications off on this phone, in "My account".
    private(set) var switchedOff = false

    @ObservationIgnored private var session: StoredSession?
    /// The token registered under this session.
    @ObservationIgnored private var posted: String?
    /// The agenda already asked during this session.
    @ObservationIgnored private var prepared = false

    /// This build can notify and the server sends: "My account" shows the switch.
    var available: Bool { Brand.hasPush && serverEnabled == true }

    /// The phone's Settings let this app show notifications.
    var allowed: Bool { [.authorized, .provisional, .ephemeral].contains(permission) }

    private var api: OpenTurnlyAPI? { session.map { OpenTurnlyAPI(server: $0.server, token: $0.token) } }

    // MARK: Session

    /// A session opened: signed in, or restored at launch.
    func begin(_ session: StoredSession) {
        guard self.session != session else { return }
        self.session = session
        serverEnabled = nil
        posted = nil
        prepared = false
        switchedOff = Preferences.pushSwitchedOff(server: session.server, user: session.user.id)
    }

    /// Before the session ends on the server: this phone stops notifying that person.
    func signOut() async {
        if Brand.hasPush, let api, let token = Preferences.pushToken {
            // Best effort: signing out ends the server's registration anyway.
            try? await api.forgetDevice(token: token)
        }
        end()
    }

    /// The session is gone (signed out or expired).
    func end() {
        session = nil
        serverEnabled = nil
        posted = nil
        prepared = false
        switchedOff = false
        setBadge(0)
    }

    // MARK: Registering

    /// After the agenda's first load: learn whether the server sends, ask once, register.
    func prepare() async {
        guard Brand.hasPush, session != nil, !prepared else { return }
        await loadSettings()
        // Without an answer (offline), the next agenda load tries again.
        guard serverEnabled != nil else { return }
        prepared = true
        guard available, !switchedOff else { return }
        await register()
    }

    /// Whether the server sends push, asked once per session.
    func loadSettings() async {
        guard Brand.hasPush, serverEnabled == nil, let api else { return }
        let asked = session
        if let settings = try? await api.mobileSettings(), session == asked {
            serverEnabled = settings.pushEnabled
        }
    }

    func refreshPermission() async {
        permission = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Back from the background: the person may have allowed notifications in Settings.
    func foregrounded() async {
        guard Brand.hasPush, session != nil else { return }
        let before = allowed
        await refreshPermission()
        if !before, allowed, prepared, available, !switchedOff, posted == nil {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    /// Ask for permission if it was never asked, then fetch the token.
    private func register() async {
        await refreshPermission()
        if permission == .notDetermined {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
            await refreshPermission()
        }
        guard allowed, session != nil, !switchedOff else { return }
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// From the application delegate: a token, maybe a new one.
    func received(_ token: String) async {
        Preferences.pushToken = token
        guard available, !switchedOff, token != posted, let api, let environment = Self.environment else { return }
        let asked = session
        do {
            try await api.registerDevice(token: token, environment: environment)
            if session == asked { posted = token }
        } catch {
            // Registered again on the next launch; not worth an error on screen.
        }
    }

    /// The switch in "My account". It flips at once; the server hears right after.
    func setOn(_ on: Bool) {
        guard let session else { return }
        switchedOff = !on
        Preferences.setPushSwitchedOff(!on, server: session.server, user: session.user.id)
        if on {
            prepared = true
            Task { await register() }
        } else {
            setBadge(0)
            posted = nil
            guard let api, let token = Preferences.pushToken else { return }
            Task { try? await api.forgetDevice(token: token) }
        }
    }

    // MARK: Badge

    /// The number on the app's icon: everything unread, in every business.
    func setBadge(_ unreadTotal: Int) {
        guard Brand.hasPush else { return }
        let count = switchedOff ? 0 : max(0, unreadTotal)
        Task { try? await UNUserNotificationCenter.current().setBadgeCount(count) }
    }

    /// The gateway the server is told this phone's token belongs to.
    static let environment: String? = {
        #if targetEnvironment(simulator)
        let simulator = true
        #else
        let simulator = false
        #endif
        let profile = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision")
            .flatMap { try? Data(contentsOf: $0) }
        return NotificationRules.serverEnvironment(
            brand: Brand.apsEnvironment, signed: NotificationRules.signedEnvironment(profile: profile), simulator: simulator
        )
    }()
}
