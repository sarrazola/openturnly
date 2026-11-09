package org.openturnly.mobile.push

import android.content.Context
import android.content.Intent
import org.openturnly.mobile.MainActivity
import org.openturnly.mobile.rules.NotificationData

/** Where a tapped notification leads: its business, the appointment and the entry to mark read. */
data class NotificationTap(
    val businessId: String?,
    val appointmentId: String?,
    val startsAt: String?,
    val notificationId: String?,
) {
    /** Opens (or brings back) the app with this tap in its extras. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(BUSINESS, businessId)
        .putExtra(APPOINTMENT, appointmentId)
        .putExtra(STARTS_AT, startsAt)
        .putExtra(NOTIFICATION, notificationId)

    companion object {
        const val ACTION = "org.openturnly.mobile.OPEN_NOTIFICATION"
        private const val BUSINESS = "notification.businessId"
        private const val APPOINTMENT = "notification.appointmentId"
        private const val STARTS_AT = "notification.startsAt"
        private const val NOTIFICATION = "notification.id"

        /** The platform's marker on a tap of a notification it drew itself from a message. */
        private const val PLATFORM_MESSAGE = "google.message_id"

        fun of(message: NotificationData) = NotificationTap(message.businessId, message.appointmentId, message.startsAt, message.notificationId)

        /**
         * The tap this intent carries, if it came from a notification: one the app
         * posted, or one the system drew while the app was closed, whose extras are
         * the message's data and are read like any message.
         */
        fun from(intent: Intent?): NotificationTap? {
            val extras = intent?.extras ?: return null
            val tap = when {
                intent.action == ACTION -> NotificationTap(
                    extras.getString(BUSINESS), extras.getString(APPOINTMENT), extras.getString(STARTS_AT), extras.getString(NOTIFICATION),
                )
                extras.containsKey(PLATFORM_MESSAGE) -> {
                    // The platform's own keys (some of them numbers) are not the message's data.
                    val data = extras.keySet().filterNot { it.startsWith("google.") || it == "from" || it == "collapse_key" }
                        .mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
                    of(NotificationData.parse(data))
                }
                else -> return null
            }
            return tap.takeIf { it.businessId != null || it.appointmentId != null || it.notificationId != null }
        }

        /** So a tap is handled once, not again when the activity is recreated. */
        fun consume(intent: Intent?) {
            intent ?: return
            listOf(BUSINESS, APPOINTMENT, STARTS_AT, NOTIFICATION, PLATFORM_MESSAGE).forEach { intent.removeExtra(it) }
            if (intent.action == ACTION) intent.action = Intent.ACTION_MAIN
        }
    }
}
