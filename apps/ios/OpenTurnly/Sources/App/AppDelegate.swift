import UIKit
import UserNotifications

/// What only an application delegate receives: the push token, and
/// notifications that arrive or are tapped. Each goes on through `PushEvents`.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Set before launching finishes, so a tap that opened the app is not missed.
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = NotificationRules.hex(deviceToken)
        Task { @MainActor in PushEvents.shared.token(token) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // A simulator build without signing has no push entitlement, and a phone
        // can be offline. The app works without notifications, so nobody is told.
    }

    // The completion-handler forms on purpose: UIKit requires a tap's handler to
    // be called on the main thread, which the async forms do not guarantee when
    // the system calls them from elsewhere. Each handler is called from the main actor.

    /// A notification while the app is open: show it all the same, and refresh the counts.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let data = NotificationData(notification.request.content.userInfo)
        Task { @MainActor in
            PushEvents.shared.arrived(data)
            completionHandler([.banner, .list, .sound, .badge])
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let tapped = response.actionIdentifier == UNNotificationDefaultActionIdentifier
        let data = NotificationData(response.notification.request.content.userInfo)
        Task { @MainActor in
            if tapped { PushEvents.shared.tapped(data) }
            completionHandler()
        }
    }
}
