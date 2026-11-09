package org.openturnly.mobile.rules

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.openturnly.mobile.api.Appointment

/**
 * What an appointment's status means on screen, and what can be done with it.
 *
 * The server has four statuses: confirmed, cancelled, no_show and
 * pending_payment. There is no "completed": an appointment is done when it is
 * charged (it has a sale) or simply past. The web splits confirmed in two by
 * time, "Próxima" before it starts and "Confirmada" once it has.
 */
object Appointments {
    enum class Status { UPCOMING, CONFIRMED, CANCELLED, NO_SHOW, PENDING_PAYMENT }

    fun status(appointment: Appointment, now: Instant = Instant.now()): Status = when (appointment.status) {
        "cancelled" -> Status.CANCELLED
        "no_show" -> Status.NO_SHOW
        "pending_payment" -> Status.PENDING_PAYMENT
        else -> if (started(appointment, now)) Status.CONFIRMED else Status.UPCOMING
    }

    fun started(appointment: Appointment, now: Instant = Instant.now()): Boolean =
        BusinessTime.parse(appointment.startsAt)?.let { !it.isAfter(now) } ?: false

    /** Still taking up its time: confirmed, or held while the customer pays online. */
    fun isActive(appointment: Appointment): Boolean = appointment.status == "confirmed" || appointment.status == "pending_payment"

    /** What is still owed, as the web works it out. */
    fun owed(appointment: Appointment): Long =
        maxOf(0L, (appointment.priceAmount ?: 0L) - appointment.discountAmount - appointment.depositAmount)

    enum class Payment { PAID, COVERED, PARTIAL, UNPAID, NONE }

    /** The payment line of an appointment: charged, covered by deposits, partly paid, or not yet. */
    fun payment(appointment: Appointment): Payment = when {
        appointment.status != "confirmed" -> Payment.NONE
        appointment.sale != null -> Payment.PAID
        appointment.depositAmount > 0 && appointment.priceAmount != null && owed(appointment) == 0L -> Payment.COVERED
        appointment.depositAmount > 0 -> Payment.PARTIAL
        else -> Payment.UNPAID
    }

    enum class Action { CHARGE, CLOSE_SALE, DEPOSIT, VIEW_SALE, RESCHEDULE, NO_SHOW, UNDO_NO_SHOW, CANCEL, VIEW_CUSTOMER }

    /**
     * The actions the web offers for an appointment, narrowed to what the server
     * lets this person do. Professionals can only move their own appointments:
     * cancelling, no-shows and charging are for admins (the server answers 403).
     * Cancelling is hidden once an appointment is charged; void the sale first.
     */
    fun actions(appointment: Appointment, isAdmin: Boolean, now: Instant = Instant.now()): List<Action> {
        val result = ArrayList<Action>()
        when (appointment.status) {
            "no_show" -> if (isAdmin) result += Action.UNDO_NO_SHOW
            "confirmed" -> {
                if (isAdmin) {
                    when (payment(appointment)) {
                        Payment.PAID -> result += Action.VIEW_SALE
                        Payment.COVERED -> result += Action.CLOSE_SALE
                        else -> { result += Action.CHARGE; result += Action.DEPOSIT }
                    }
                    if (appointment.sale == null && started(appointment, now)) result += Action.NO_SHOW
                }
                result += Action.RESCHEDULE
                if (isAdmin && appointment.sale == null) result += Action.CANCEL
            }
        }
        if (isAdmin && appointment.customer.id.isNotEmpty()) result += Action.VIEW_CUSTOMER
        return result
    }

    // The agenda's day, grouped.

    enum class Segment { MORNING, AFTERNOON, EVENING }

    /** Morning until noon, afternoon until six, evening after. */
    fun segment(time: LocalTime): Segment = when {
        time.hour < 12 -> Segment.MORNING
        time.hour < 18 -> Segment.AFTERNOON
        else -> Segment.EVENING
    }

    data class Section(val segment: Segment, val appointments: List<Appointment>)

    /** A day's appointments in start order, in their part of the day (in the business's zone). */
    fun group(appointments: List<Appointment>, zone: ZoneId): List<Section> {
        val sorted = appointments.sortedWith(compareBy({ it.startsAt }, { it.resourceName ?: "" }))
        return sorted.groupBy { appointment ->
            val start = BusinessTime.parse(appointment.startsAt) ?: Instant.EPOCH
            segment(BusinessTime.localTime(start, zone))
        }.toSortedMap().map { (segment, items) -> Section(segment, items) }
    }

    /** The filters on the agenda: one professional, one location, and whether cancelled ones show. */
    fun filter(
        appointments: List<Appointment>,
        professionalId: String?,
        locationId: String?,
        showCancelled: Boolean,
    ): List<Appointment> = appointments.filter { appointment ->
        (professionalId == null || appointment.resourceId == professionalId) &&
            (locationId == null || appointment.locationId == locationId) &&
            (showCancelled || appointment.status != "cancelled")
    }

    /** Appointments that start on a business-local day. */
    fun onDay(appointments: List<Appointment>, day: java.time.LocalDate, zone: ZoneId): List<Appointment> = appointments.filter {
        BusinessTime.parse(it.startsAt)?.let { start -> BusinessTime.localDate(start, zone) == day } ?: false
    }
}
