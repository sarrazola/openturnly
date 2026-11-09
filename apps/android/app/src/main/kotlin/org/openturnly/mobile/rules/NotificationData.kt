package org.openturnly.mobile.rules

/**
 * What one push message says, read from the message itself.
 *
 * This file is the only place in the app that knows the message's envelope:
 * the keys the server puts in its data (`title`, `body`, `badge`, `kind`,
 * `notificationId`, `businessId`, `appointmentId`, `startsAt`) and the
 * platform's own `notification` block, whose title and body win when present.
 * A publisher whose delivery wraps the payload differently replaces this one
 * file, keeping the class and [parse]; nothing else reads a key.
 */
data class NotificationData(
    val title: String = "",
    val body: String = "",
    /** Unread notifications across every business, for the launcher's count. */
    val badge: Int? = null,
    /** What happened: `appointment_booked`, `appointment_rescheduled`, `appointment_cancelled`… */
    val kind: String? = null,
    val notificationId: String? = null,
    val businessId: String? = null,
    val appointmentId: String? = null,
    /** ISO 8601. Where to look for the appointment: the app finds it in the week it starts. */
    val startsAt: String? = null,
) {
    /** Something a person can read: a message without words is never shown. */
    val showable: Boolean get() = title.isNotBlank() || body.isNotBlank()

    companion object {
        fun parse(data: Map<String, String>, notificationTitle: String? = null, notificationBody: String? = null): NotificationData {
            fun present(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
            fun text(key: String) = present(data[key])
            return NotificationData(
                title = present(notificationTitle) ?: text("title") ?: "",
                body = present(notificationBody) ?: text("body") ?: "",
                badge = text("badge")?.toIntOrNull()?.takeIf { it >= 0 },
                kind = text("kind"),
                notificationId = text("notificationId"),
                businessId = text("businessId"),
                appointmentId = text("appointmentId"),
                startsAt = text("startsAt"),
            )
        }
    }
}
