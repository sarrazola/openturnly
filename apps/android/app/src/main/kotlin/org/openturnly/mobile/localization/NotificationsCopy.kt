package org.openturnly.mobile.localization

import java.util.Locale
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Inbox

object NotificationsCopy {
    val title get() = tr("Notifications", "Avisos")
    fun bell(unread: Int) = when {
        unread <= 0 -> title
        AppLocale.isSpanish -> "Avisos, $unread sin leer"
        else -> "Notifications, $unread unread"
    }
    val unread get() = tr("Unread", "Sin leer")
    val emptyTitle get() = tr("No notifications yet", "Sin avisos todavía")
    val emptyBody get() = tr("New, moved and cancelled appointments will show up here.", "Aquí verás las citas nuevas, movidas y canceladas.")
    val notFound get() = tr(
        "We couldn't find that appointment. It may have been moved or cancelled.",
        "No encontramos esa cita. Puede que la hayan movido o cancelado.",
    )

    /** The channel's name in the phone's notification settings. */
    val channel get() = tr("Appointments", "Citas")

    // "My account"
    val section get() = tr("Notifications", "Avisos")
    val onThisPhone get() = tr("Notifications on this phone", "Avisos en este teléfono")
    val onThisPhoneHint get() = tr(
        "We let you know about new bookings, and appointments moved or cancelled, even when the app is closed.",
        "Te avisamos de las reservas nuevas y de las citas movidas o canceladas, aunque la app esté cerrada.",
    )
    val blocked get() = tr(
        "Notifications are turned off for this app in your phone's settings.",
        "Los avisos de esta app están desactivados en los ajustes del teléfono.",
    )
    val openSettings get() = tr("Open settings", "Abrir ajustes")

    fun age(age: Inbox.Age, locale: Locale): String = when (age) {
        Inbox.Age.Now -> tr("now", "ahora")
        is Inbox.Age.Minutes -> tr("${age.count} min ago", "hace ${age.count} min")
        is Inbox.Age.Hours -> tr("${age.count} h ago", "hace ${age.count} h")
        Inbox.Age.Yesterday -> tr("yesterday", "ayer")
        is Inbox.Age.Days -> tr("${age.count} days ago", "hace ${age.count} días")
        is Inbox.Age.On -> BusinessTime.mediumDate(age.date, locale, withYear = false)
    }
}
