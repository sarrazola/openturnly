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
    /**
     * The chip an appointment shows. A confirmed appointment reads "Próxima"
     * before it starts, "Ahora" while it runs and "Confirmada" once it ended.
     */
    enum class Status { UPCOMING, NOW, CONFIRMED, CANCELLED, NO_SHOW, PENDING_PAYMENT }

    fun status(appointment: Appointment, now: Instant = Instant.now()): Status =
        status(appointment.status, appointment.startsAt, appointment.endsAt, now)

    /** The same chip for any stored appointment (a customer's history, a notification). */
    fun status(raw: String, startsAt: String?, endsAt: String?, now: Instant = Instant.now()): Status = when (raw) {
        "cancelled" -> Status.CANCELLED
        "no_show" -> Status.NO_SHOW
        "pending_payment" -> Status.PENDING_PAYMENT
        else -> {
            val start = BusinessTime.parse(startsAt)
            val end = BusinessTime.parse(endsAt)
            when {
                start == null || now.isBefore(start) -> Status.UPCOMING
                end != null && now.isBefore(end) -> Status.NOW
                else -> Status.CONFIRMED
            }
        }
    }

    fun started(appointment: Appointment, now: Instant = Instant.now()): Boolean =
        BusinessTime.parse(appointment.startsAt)?.let { !it.isAfter(now) } ?: false

    /** Still taking up its time: confirmed, or held while the customer pays online. */
    fun isActive(appointment: Appointment): Boolean = appointment.status == "confirmed" || appointment.status == "pending_payment"

    /** What is still owed, as the web works it out. */
    fun owed(appointment: Appointment): Long =
        maxOf(0L, (appointment.priceAmount ?: 0L) - appointment.discountAmount - appointment.depositAmount)

    /** The price after the booking's discount, or null when the service has no price. */
    fun priceAfterDiscount(appointment: Appointment): Long? =
        appointment.priceAmount?.let { maxOf(0L, it - appointment.discountAmount) }

    enum class Payment { PAID, COVERED, PARTIAL, UNPAID, NONE }

    /** The payment line of an appointment: charged, covered by deposits, partly paid, or not yet. */
    fun payment(appointment: Appointment): Payment = when {
        appointment.status != "confirmed" -> Payment.NONE
        appointment.sale != null -> Payment.PAID
        appointment.depositAmount > 0 && appointment.priceAmount != null && owed(appointment) == 0L -> Payment.COVERED
        appointment.depositAmount > 0 && owed(appointment) > 0 -> Payment.PARTIAL
        else -> Payment.UNPAID
    }

    /** What an agenda card shows on the money side, trailing its last line. */
    sealed interface Badge {
        /** Muted text: the price after the booking's discount. */
        data class Price(val amount: Long) : Badge
        /** «Sin cobrar», warning: started and nothing paid. */
        data object NotCharged : Badge
        /** «Abonó $ X», neutral: deposits before it starts. */
        data class Deposited(val amount: Long) : Badge
        /** «Faltan $ X», warning: started, partly paid by deposits. */
        data class Left(val amount: Long) : Badge
        /** «Por cerrar», warning: started, deposits cover the price, the sale is still open. */
        data object ToClose : Badge
        /** «Pagada», info with a check: it has a sale. */
        data object Paid : Badge
        data object None : Badge
    }

    /**
     * The card's money badge. Admins see the payment state; professionals only
     * see the price after the booking's discount.
     */
    fun badge(appointment: Appointment, isAdmin: Boolean, now: Instant = Instant.now()): Badge {
        val price = priceAfterDiscount(appointment)
        if (!isAdmin) return price?.let { Badge.Price(it) } ?: Badge.None
        val started = started(appointment, now)
        return when (payment(appointment)) {
            Payment.NONE -> Badge.None
            Payment.PAID -> Badge.Paid
            Payment.UNPAID -> if (started) Badge.NotCharged else price?.let { Badge.Price(it) } ?: Badge.None
            Payment.PARTIAL -> if (started) Badge.Left(owed(appointment)) else Badge.Deposited(appointment.depositAmount)
            Payment.COVERED -> if (started) Badge.ToClose else Badge.Deposited(appointment.depositAmount)
        }
    }

    /** The count, the money expected and how many are left to charge, for a day's header. */
    data class DaySummary(val count: Int, val expected: Long, val toCharge: Int)

    /**
     * Count: confirmed and awaiting payment (no-shows and cancelled don't take
     * the time). Expected: what the confirmed ones cost after their discounts.
     * To charge: confirmed, started and without a sale.
     */
    fun daySummary(appointments: List<Appointment>, now: Instant = Instant.now()): DaySummary = DaySummary(
        count = appointments.count { isActive(it) },
        expected = appointments.filter { it.status == "confirmed" }.sumOf { priceAfterDiscount(it) ?: 0L },
        toCharge = appointments.count { it.status == "confirmed" && it.sale == null && started(it, now) },
    )

    /**
     * Where the "now" line goes in a day's list: after this many cards (in start
     * order). An appointment that already started, ongoing included, stays above
     * the line, so the line never lands under a later part of the day's header.
     */
    fun nowLineAfter(appointments: List<Appointment>, now: Instant = Instant.now()): Int =
        appointments.count { started(it, now) }

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
        val sorted = appointments.sortedWith(compareBy({ BusinessTime.parse(it.startsAt) }, { BusinessTime.parse(it.endsAt) }, { it.id }))
        return sorted.groupBy { appointment ->
            val start = BusinessTime.parse(appointment.startsAt) ?: Instant.EPOCH
            segment(BusinessTime.localTime(start, zone))
        }.toSortedMap().map { (segment, items) -> Section(segment, items) }
    }

    /** «Cobrar $ X»: what is still owed, or null when the service has no price («Cobrar» alone). */
    fun chargeAmount(appointment: Appointment): Long? = appointment.priceAmount?.let { owed(appointment) }

    /** A segment's count, like the day's: no-shows and cancelled ones are listed, not counted. */
    fun count(appointments: List<Appointment>): Int = appointments.count { isActive(it) }

    /**
     * The day has appointments, but the professional or location filter hides
     * them all. Cancelled ones hidden because «Mostrar canceladas» is off don't count.
     */
    fun hiddenByFilters(day: List<Appointment>, professionalId: String?, locationId: String?, showCancelled: Boolean): Boolean {
        if (professionalId == null && locationId == null) return false
        if (filter(day, professionalId, locationId, showCancelled).isNotEmpty()) return false
        return filter(day, null, null, showCancelled).isNotEmpty()
    }

    /** Whether a day gets the week strip's dot: one confirmed or awaiting-payment appointment after the filters. */
    fun busy(day: List<Appointment>, professionalId: String?, locationId: String?): Boolean =
        filter(day, professionalId, locationId, showCancelled = false).any { isActive(it) }

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
