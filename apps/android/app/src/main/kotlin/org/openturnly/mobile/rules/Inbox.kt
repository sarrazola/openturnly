package org.openturnly.mobile.rules

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The notifications inbox: the bell's count, what each notification is about and how old it reads. */
object Inbox {
    /** What happened to an appointment. A kind this app does not know yet still shows, plainly. */
    enum class Kind { BOOKED, RESCHEDULED, CANCELLED, OTHER }

    fun kind(raw: String?): Kind = when (raw) {
        "appointment_booked" -> Kind.BOOKED
        "appointment_rescheduled" -> Kind.RESCHEDULED
        "appointment_cancelled" -> Kind.CANCELLED
        else -> Kind.OTHER
    }

    /**
     * Whether a failed load means the server has no inbox at all: an older one
     * answers 404 for the route, and then the bell stays hidden.
     */
    fun unsupported(status: Int): Boolean = status == 404

    /** A row opens the appointment it is about; one without an appointment is not tappable. */
    fun opens(appointmentId: String?): Boolean = !appointmentId.isNullOrBlank()

    /** Nothing at zero, the number up to nine, then "9+" so the badge stays small. */
    fun badge(count: Int): String? = when {
        count <= 0 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }

    sealed interface Age {
        data object Now : Age
        data class Minutes(val count: Long) : Age
        data class Hours(val count: Long) : Age
        data object Yesterday : Age
        data class Days(val count: Long) : Age
        data class On(val date: LocalDate) : Age
    }

    /** Minutes within the hour, hours within the day, then days for a week, then the date. */
    fun age(at: Instant, now: Instant, zone: ZoneId): Age {
        val minutes = Duration.between(at, now).toMinutes()
        if (minutes < 1) return Age.Now
        if (minutes < 60) return Age.Minutes(minutes)
        val day = at.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, now.atZone(zone).toLocalDate())
        return when {
            days <= 0 -> Age.Hours(minutes / 60)
            days == 1L -> Age.Yesterday
            days < 7 -> Age.Days(days)
            else -> Age.On(day)
        }
    }
}
