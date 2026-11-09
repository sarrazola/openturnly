import Foundation

/// What a push notification points at, read from its payload.
///
/// This is the only place that knows how a push is shaped. The server's own
/// payload carries its keys at the top level, next to `aps`:
///
///     { "aps": {...}, "kind": "appointment_booked", "notificationId": "...",
///       "businessId": "...", "appointmentId": "...", "startsAt": "2026-11-14T14:00:00.000Z" }
///
/// A publisher whose delivery service wraps those keys in another envelope
/// replaces this one file; the rest of the app only ever sees these fields.
struct NotificationData: Equatable, Sendable {
    /// `appointment_booked`, `appointment_rescheduled`, `appointment_cancelled`, or
    /// a kind a newer server adds. Every kind with an appointment opens it.
    var kind: String?
    var notificationId: String?
    var businessId: String?
    var appointmentId: String?
    /// When the appointment starts, to find it in the agenda.
    var startsAt: Date?

    init(kind: String? = nil, notificationId: String? = nil, businessId: String? = nil,
         appointmentId: String? = nil, startsAt: Date? = nil) {
        self.kind = kind
        self.notificationId = notificationId
        self.businessId = businessId
        self.appointmentId = appointmentId
        self.startsAt = startsAt
    }

    init(_ userInfo: [AnyHashable: Any]) {
        func text(_ key: String) -> String? {
            let raw: String?
            switch userInfo[key] {
            case let value as String: raw = value
            case let value as NSNumber: raw = value.stringValue
            default: raw = nil
            }
            let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed?.isEmpty == false ? trimmed : nil
        }
        self.init(
            kind: text("kind"),
            notificationId: text("notificationId"),
            businessId: text("businessId"),
            appointmentId: text("appointmentId"),
            startsAt: text("startsAt").flatMap(Self.instant)
        )
    }

    /// ISO 8601, with or without milliseconds.
    private static func instant(_ raw: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: raw) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: raw)
    }
}
