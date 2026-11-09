import Foundation

/// What a notification is about. The server sends the kind as text; one this
/// app does not know yet is still listed and still opens its appointment.
enum NotificationKind: Equatable {
    case booked, rescheduled, cancelled, other

    init(_ raw: String?) {
        switch raw {
        case "appointment_booked": self = .booked
        case "appointment_rescheduled": self = .rescheduled
        case "appointment_cancelled": self = .cancelled
        default: self = .other
        }
    }

    /// The symbol in front of it in the notifications screen.
    var symbol: String {
        switch self {
        case .booked: return "calendar.badge.plus"
        case .rescheduled: return "arrow.triangle.2.circlepath"
        case .cancelled: return "xmark.circle"
        case .other: return "bell"
        }
    }
}

/// Small decisions behind notifications, kept apart so they can be tested.
enum NotificationRules {
    /// The bell's count: nothing when all is read, "9+" past nine.
    static func badgeText(_ unread: Int) -> String? {
        guard unread > 0 else { return nil }
        return unread > 9 ? "9+" : String(unread)
    }

    /// A push token the way the server stores it: lowercase hex.
    static func hex(_ token: Data) -> String {
        token.map { String(format: "%02x", $0) }.joined()
    }

    /// Which gateway this install's token belongs to, as the server names it
    /// (`sandbox` or `production`), or nil when the build has no push.
    ///
    /// `brand` is the build's BRAND_APS_ENVIRONMENT. The provisioning profile the
    /// app was signed with, when it carries one, has the last word: exporting an
    /// archive for distribution re-signs it for production whatever the brand
    /// file said. An install from the App Store or TestFlight carries no profile
    /// and is always production; a simulator has none either and goes by the brand.
    static func serverEnvironment(brand: String, signed: String?, simulator: Bool) -> String? {
        let brand = brand.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !brand.isEmpty else { return nil }
        let entitled = signed?.lowercased() ?? (simulator ? brand : "production")
        return entitled == "development" ? "sandbox" : "production"
    }

    /// The `aps-environment` of the provisioning profile inside the app, if any.
    static func signedEnvironment(profile data: Data?) -> String? {
        guard let data,
              let start = data.range(of: Data("<?xml".utf8)),
              let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound..<data.endIndex),
              let plist = try? PropertyListSerialization.propertyList(from: data[start.lowerBound..<end.upperBound], format: nil),
              let entitlements = (plist as? [String: Any])?["Entitlements"] as? [String: Any] else { return nil }
        return entitlements["aps-environment"] as? String
    }
}
