package org.openturnly.mobile

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openturnly.mobile.api.ApiClient
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.AppointmentCustomer
import org.openturnly.mobile.api.Location
import org.openturnly.mobile.api.SaleRef
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.Appointments.Badge
import org.openturnly.mobile.rules.Appointments.Status
import org.openturnly.mobile.rules.Booking
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.ServerAddress
import org.openturnly.mobile.ui.ClockMark

/**
 * The shared rules both phone apps implement (status chip, money badge, day
 * summary, now line, colours, formats): the same inputs give the same answers
 * as the iPhone app's parity tests, so the two can't drift apart.
 */
class ParityRulesTest {
    private lateinit var previous: Locale

    @Before fun spanish() { previous = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("es-CO")) }
    @After fun restore() { Locale.setDefault(previous) }

    // 14:00–14:45 UTC.
    private fun appointment(
        status: String = "confirmed", start: String = "2026-11-10T14:00:00.000Z", sale: SaleRef? = null,
        price: Long? = 22000, discount: Long = 0, deposit: Long = 0, resource: String? = "a", location: String? = "l", id: String? = null,
    ) = Appointment(
        id = id ?: (start + status), code = "NLGN62", startsAt = start, endsAt = Instant.parse(start).plusSeconds(2700).toString(), status = status,
        resourceId = resource, locationId = location, serviceName = "Corte", priceAmount = price, discountAmount = discount,
        depositAmount = deposit, sale = sale, customer = AppointmentCustomer(id = "c", firstName = "Laura", lastName = "Gómez"),
    )

    private val before = Instant.parse("2026-11-10T13:00:00Z")
    private val during = Instant.parse("2026-11-10T14:10:00Z")
    private val after = Instant.parse("2026-11-10T15:00:00Z")

    @Test fun statusChipFollowsTheClock() {
        assertEquals(Status.UPCOMING, Appointments.status(appointment(), before))
        assertEquals(Status.NOW, Appointments.status(appointment(), during))
        assertEquals(Status.NOW, Appointments.status(appointment(), Instant.parse("2026-11-10T14:00:00Z")))
        assertEquals(Status.CONFIRMED, Appointments.status(appointment(), Instant.parse("2026-11-10T14:45:00Z")))
        assertEquals(Status.CONFIRMED, Appointments.status(appointment(), after))
        assertEquals(Status.NO_SHOW, Appointments.status(appointment("no_show"), during))
        assertEquals(Status.CANCELLED, Appointments.status("cancelled", "2026-11-10T14:00:00Z", "2026-11-10T14:45:00Z", during))
        assertEquals(Status.PENDING_PAYMENT, Appointments.status("pending_payment", null, null, during))
        assertEquals("Ahora", Copy.agenda.status(Status.NOW))
        assertEquals("Próxima", Copy.agenda.status(Status.UPCOMING))
    }

    @Test fun moneyBadgeForAdmins() {
        assertEquals(Badge.Price(22000), Appointments.badge(appointment(), true, before))
        assertEquals(Badge.Price(17000), Appointments.badge(appointment(discount = 5000), true, before))
        assertEquals(Badge.NotCharged, Appointments.badge(appointment(), true, during))
        assertEquals(Badge.Deposited(10000), Appointments.badge(appointment(deposit = 10000), true, before))
        assertEquals(Badge.Left(12000), Appointments.badge(appointment(deposit = 10000), true, after))
        assertEquals(Badge.Deposited(22000), Appointments.badge(appointment(deposit = 22000), true, before))
        assertEquals(Badge.ToClose, Appointments.badge(appointment(deposit = 22000), true, after))
        assertEquals(Badge.Paid, Appointments.badge(appointment(sale = SaleRef("s", 12)), true, before))
        assertEquals(Badge.None, Appointments.badge(appointment("no_show"), true, after))
        assertEquals(Badge.None, Appointments.badge(appointment("cancelled"), true, after))
        // No price: nothing before it starts, «Sin cobrar» after, deposits or not.
        assertEquals(Badge.None, Appointments.badge(appointment(price = null), true, before))
        assertEquals(Badge.NotCharged, Appointments.badge(appointment(price = null, deposit = 5000), true, after))
    }

    @Test fun professionalsSeeThePriceAfterTheDiscount() {
        assertEquals(Badge.Price(17000), Appointments.badge(appointment(discount = 5000), false, after))
        assertEquals(Badge.Price(22000), Appointments.badge(appointment(sale = SaleRef("s", 1)), false, after))
        assertEquals(Badge.None, Appointments.badge(appointment(price = null), false, after))
    }

    @Test fun chargeAmountIsWhatIsLeft() {
        assertEquals(22000L, Appointments.chargeAmount(appointment()))
        assertEquals(12000L, Appointments.chargeAmount(appointment(deposit = 10000)))
        assertEquals(7000L, Appointments.chargeAmount(appointment(discount = 5000, deposit = 10000)))
        assertEquals(null, Appointments.chargeAmount(appointment(price = null)))
    }

    @Test fun daySummaryCountsExpectsAndChargesLikeTheSpec() {
        val day = listOf(
            appointment(start = "2026-11-10T09:00:00.000Z", discount = 2000),
            appointment(start = "2026-11-10T10:00:00.000Z", sale = SaleRef("s", 1)),
            appointment("pending_payment", start = "2026-11-10T11:00:00.000Z"),
            appointment("no_show", start = "2026-11-10T12:00:00.000Z"),
            appointment("cancelled", start = "2026-11-10T12:30:00.000Z"),
            appointment(start = "2026-11-10T16:00:00.000Z", deposit = 22000),
        )
        val summary = Appointments.daySummary(day, after)
        assertEquals(4, summary.count)
        assertEquals(20000L + 22000L + 22000L, summary.expected)
        assertEquals(1, summary.toCharge)
        assertEquals(3, Appointments.count(day.take(4)))
    }

    @Test fun nowLineStaysInTheSegmentOfWhatStarted() {
        val morning = appointment(start = "2026-11-10T11:00:00.000Z", id = "m")
        val afternoon = appointment(start = "2026-11-10T13:00:00.000Z", id = "a")
        val list = listOf(morning, afternoon)
        assertEquals(0, Appointments.nowLineAfter(list, Instant.parse("2026-11-10T10:00:00Z")))
        // 11:22: after the morning card, so at the end of the morning, not under «Por la tarde».
        assertEquals(1, Appointments.nowLineAfter(list, Instant.parse("2026-11-10T11:22:00Z")))
        assertEquals(2, Appointments.nowLineAfter(list, Instant.parse("2026-11-10T13:10:00Z")))
    }

    @Test fun filtersAndDots() {
        val day = listOf(appointment(resource = "a"), appointment("cancelled", start = "2026-11-10T16:00:00.000Z", resource = "b"))
        assertFalse(Appointments.hiddenByFilters(day, null, null, false))
        assertFalse(Appointments.hiddenByFilters(listOf(appointment("cancelled")), null, null, false))
        assertTrue(Appointments.hiddenByFilters(day, "z", null, false))
        assertFalse(Appointments.hiddenByFilters(listOf(appointment("cancelled", resource = "b")), "z", null, false))
        assertTrue(Appointments.busy(day, null, null))
        assertFalse(Appointments.busy(day, "b", null))
        assertFalse(Appointments.busy(listOf(appointment("no_show")), null, null))
        assertTrue(Appointments.busy(listOf(appointment("pending_payment")), null, "l"))
    }

    @Test fun avatarColoursMatchTheIphonesHash() {
        assertEquals(2, Names.colorIndex("Ana Pérez", 8))
        assertEquals(7, Names.colorIndex("Camila", 8))
        assertEquals(5, Names.colorIndex("Laura Gómez", 8))
    }

    @Test fun formatsMatchTheSpec() {
        val es = Locale.forLanguageTag("es-CO")
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Lunes, 9 de noviembre", BusinessTime.longDay(today, es, today))
        assertEquals("Martes, 9 de noviembre de 2027", BusinessTime.longDay(LocalDate.of(2027, 11, 9), es, today))
        assertEquals("Tuesday, November 9, 2027", BusinessTime.longDay(LocalDate.of(2027, 11, 9), Locale.US, today))
        assertEquals("Lun 9 nov", BusinessTime.shortDay(today, es))
        assertEquals("Noviembre 2026", BusinessTime.monthYear(today, es))
        assertEquals(BusinessTime.Relative.TOMORROW, BusinessTime.relative(today.plusDays(1), today))
        assertEquals("45 %", Money.percent("45", es))
        assertEquals("45%", Money.percent("45", Locale.US))
    }

    @Test fun signInRecoversPasswordsOnTheServer() {
        assertEquals("http://10.0.2.2:4100/forgot-password", ServerAddress.forgotPasswordUrl("http://10.0.2.2:4100"))
        assertEquals("https://turnos.example.com/forgot-password", ServerAddress.forgotPasswordUrl("https://turnos.example.com/"))
    }

    @Test fun createThemReadsTheQuery() {
        assertEquals(Booking.Draft(email = "ana@negocio.com"), Booking.draft(" ana@negocio.com "))
        assertEquals(Booking.Draft(phone = "3001234567"), Booking.draft("300 123 4567"))
        assertEquals(Booking.Draft(firstName = "Ana", lastName = "María Pérez"), Booking.draft("Ana María Pérez"))
        val places = listOf(Location("a", active = true, resourceIds = listOf("p")), Location("b", active = false), Location("c", active = true, resourceIds = listOf("q")))
        assertEquals(listOf("a", "c"), Booking.places(places).map { it.id })
        assertEquals(listOf("a", "b", "c"), Booking.places(places, keeping = "b").map { it.id })
        assertEquals(listOf("a"), Booking.places(places, professionalId = "p").map { it.id })
    }

    @Test fun theClockTurnsLikeTheIphones() {
        assertEquals(-Math.PI / 2, ClockMark.minuteAngle(0), 1e-9)
        assertEquals(0.0, ClockMark.hourAngle(0), 1e-9)
        // A quarter of 1.6 s: the long hand points right; the short one has turned 1/48 of a turn.
        assertEquals(0.0, ClockMark.minuteAngle(400_000_000), 1e-9)
        assertEquals(2 * Math.PI / 48, ClockMark.hourAngle(400_000_000), 1e-9)
        assertEquals(1f, ClockMark.dotAlpha(0), 1e-6f)
        assertEquals(0.55f, ClockMark.dotAlpha(800_000_000), 1e-6f)
        assertEquals(1.04f, ClockMark.pulse(600_000_000), 1e-6f)
    }

    @Test fun oneErrorTableForBothApps() {
        assertEquals("Esto solo se puede hacer desde el panel web.", ApiClient.parseFailure(403, """{"error":{"code":"forbidden_origin"}}""").message)
        assertEquals("No encontramos ese abono.", ApiClient.parseFailure(404, """{"error":{"code":"deposit_not_found"}}""").message)
        assertEquals(
            "Ya tienes un cliente con ese email.",
            ApiClient.parseFailure(409, """{"error":{"code":"customer_exists","fields":{"email":"customer_exists"}}}""").message,
        )
        assertEquals("Tu sesión terminó. Ingresa de nuevo.", Copy.signIn.sessionEnded)
    }
}
