package org.openturnly.mobile

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openturnly.mobile.api.ApiClient
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.AppointmentCustomer
import org.openturnly.mobile.api.SaleRef
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.Appointments.Action as A
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Checkout
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Periods
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.rules.ServerAddress

class RulesTest {
    private lateinit var previous: Locale

    @Before fun spanish() { previous = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("es-CO")) }
    @After fun restore() { Locale.setDefault(previous) }

    private fun plain(text: String) = text.replace('\u00A0', ' ').replace('\u202F', ' ')

    // Server addresses

    @Test fun serverAddressPrependsHttpsAndKeepsHttpOnlyForLocalAddresses() {
        assertEquals("https://turnos.example.com", ServerAddress.normalize("turnos.example.com/"))
        assertEquals("https://turnos.example.com", ServerAddress.normalize("https://turnos.example.com/admin/k7m2qx9a4p"))
        assertEquals("https://turnos.example.com", ServerAddress.normalize("turnos.example.com/api"))
        assertEquals("http://10.0.2.2:4100", ServerAddress.normalize("10.0.2.2:4100"))
        assertEquals("http://localhost:4100", ServerAddress.normalize("localhost:4100"))
        assertEquals("http://192.168.1.20:4100", ServerAddress.normalize("http://192.168.1.20:4100"))
        assertEquals("http://mac.local", ServerAddress.normalize("mac.local"))
        assertEquals("https://example.com:8443/turnos", ServerAddress.normalize("example.com:8443/turnos"))
        assertNull(ServerAddress.normalize("http://turnos.example.com"))
        assertNull(ServerAddress.normalize("ftp://example.com"))
        assertNull(ServerAddress.normalize("   "))
        assertNull(ServerAddress.normalize("not a server"))
        assertNull(ServerAddress.normalize("https://user:pass@example.com"))
    }

    @Test fun localHostsAreThePrivateRanges() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "10.1.2.3", "192.168.0.10", "172.16.0.1", "172.31.255.1", "shop.local", "app.localhost").forEach {
            assertTrue(it, ServerAddress.isLocalHost(it))
        }
        listOf("172.32.0.1", "8.8.8.8", "example.com", "192.169.0.1", "10.0.0.256").forEach { assertFalse(it, ServerAddress.isLocalHost(it)) }
    }

    @Test fun hostedServerAcceptsAnAccountNameOrAMatchingAddress() {
        val template = "https://{workspace}.example.com"
        assertEquals("https://barberia.example.com", HostedServer.resolve("Barberia", template))
        assertEquals("https://barberia.example.com", HostedServer.resolve("https://barberia.example.com", template))
        assertNull(HostedServer.resolve("https://barberia.other.com", template))
        assertNull(HostedServer.resolve("bad name", template))
    }

    // Money

    @Test fun decimalsComeFromTheServersTable() {
        assertEquals(0, Money.digits("COP"))
        assertEquals(0, Money.digits("clp"))
        assertEquals(2, Money.digits("USD"))
        assertEquals(3, Money.digits("KWD"))
        assertEquals(2, Money.digits("EUR"))
    }

    @Test fun formatsLikeTheWeb() {
        assertEquals("$ 25.000", plain(Money.format(25000, "COP")))
        assertEquals("$25.50", plain(Money.format(2550, "USD")))
        assertEquals("$25", plain(Money.format(2500, "USD")))
        assertEquals("$ 0", plain(Money.format(0, "COP")))
        assertTrue(plain(Money.format(123456, "EUR")).contains("1234,56") || plain(Money.format(123456, "EUR")).contains("1.234,56"))
    }

    @Test fun parsesWhatPeopleType() {
        assertEquals(25000L, Money.parse("25.000", "COP"))
        assertEquals(25000L, Money.parse("25000", "COP"))
        assertEquals(25000L, Money.parse("$ 25,000", "COP"))
        assertEquals(2550L, Money.parse("25.50", "USD"))
        assertEquals(2550L, Money.parse("25,5", "USD"))
        assertEquals(250000L, Money.parse("2,500", "USD"))
        assertEquals(1234500L, Money.parse("12.345", "KWD").let { it?.times(100) })
        assertNull(Money.parse("", "USD"))
        assertNull(Money.parse("abc", "COP"))
        assertEquals("25000", Money.inputText(25000, "COP"))
        assertEquals("25.5", Money.inputText(2550, "USD"))
        assertEquals("25", Money.inputText(2500, "USD"))
    }

    // Time

    @Test fun aBusinessDayIsMidnightToMidnightInItsZone() {
        val bogota = ZoneId.of("America/Bogota")
        val (from, to) = BusinessTime.dayRange(LocalDate.of(2026, 11, 10), bogota)
        assertEquals("2026-11-10T05:00:00.000Z", BusinessTime.iso(from))
        assertEquals("2026-11-11T05:00:00.000Z", BusinessTime.iso(to))
        val madrid = ZoneId.of("Europe/Madrid")
        // The day the clocks go back is 25 hours long.
        val (start, end) = BusinessTime.dayRange(LocalDate.of(2026, 10, 25), madrid)
        assertEquals(25 * 60L, BusinessTime.minutes(start, end))
    }

    @Test fun timesAreShownInTheBusinessZoneNotThePhones() {
        val instant = Instant.parse("2026-11-10T14:00:00.000Z")
        assertEquals("09:00", BusinessTime.time(instant, ZoneId.of("America/Bogota")))
        assertEquals("15:00", BusinessTime.time(instant, ZoneId.of("Europe/Madrid")))
        assertEquals(LocalDate.of(2026, 11, 9), BusinessTime.localDate(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("America/Bogota")))
    }

    @Test fun weekdaysCountLikeJavaScript() {
        assertEquals(0, BusinessTime.serverWeekday(DayOfWeek.SUNDAY))
        assertEquals(1, BusinessTime.serverWeekday(DayOfWeek.MONDAY))
        assertEquals(6, BusinessTime.serverWeekday(DayOfWeek.SATURDAY))
        assertEquals(DayOfWeek.SUNDAY, BusinessTime.dayOfWeek(0))
        assertEquals(LocalDate.of(2026, 11, 9), BusinessTime.weekStart(LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 9), BusinessTime.weekStart(LocalDate.of(2026, 11, 9)))
    }

    @Test fun parsesInstantsWithAndWithoutMilliseconds() {
        assertNotNull(BusinessTime.parse("2026-11-10T14:00:00.000Z"))
        assertNotNull(BusinessTime.parse("2026-11-10T14:00:00Z"))
        assertNotNull(BusinessTime.parse("2026-11-10T09:00:00-05:00"))
        assertNull(BusinessTime.parse(""))
        assertEquals("1 h 30 min", BusinessTime.duration(90))
        assertEquals("45 min", BusinessTime.duration(45))
        assertEquals("2 h", BusinessTime.duration(120))
    }

    @Test fun pickerDatesRoundTrip() {
        val day = LocalDate.of(2026, 2, 28)
        assertEquals(day, BusinessTime.fromPickerMillis(BusinessTime.pickerMillis(day)))
    }

    // Appointments

    private fun appointment(
        status: String = "confirmed", start: String = "2026-11-10T14:00:00.000Z", sale: SaleRef? = null,
        price: Long? = 35000, discount: Long = 0, deposit: Long = 0, resource: String? = "a", location: String? = "l",
    ) = Appointment(
        id = start + status, code = "K7Q4XM", startsAt = start, endsAt = Instant.parse(start).plusSeconds(2700).toString(), status = status,
        resourceId = resource, locationId = location, serviceName = "Corte", priceAmount = price, discountAmount = discount,
        depositAmount = deposit, sale = sale, customer = AppointmentCustomer(id = "c", firstName = "Ana", lastName = "Pérez"),
    )

    private val before = Instant.parse("2026-11-10T13:00:00Z")
    private val after = Instant.parse("2026-11-10T15:00:00Z")

    @Test fun confirmedSplitsByTimeLikeTheWeb() {
        assertEquals(Appointments.Status.UPCOMING, Appointments.status(appointment(), before))
        assertEquals(Appointments.Status.CONFIRMED, Appointments.status(appointment(), after))
        assertEquals(Appointments.Status.CANCELLED, Appointments.status(appointment("cancelled"), after))
        assertEquals(Appointments.Status.NO_SHOW, Appointments.status(appointment("no_show"), after))
        assertEquals(Appointments.Status.PENDING_PAYMENT, Appointments.status(appointment("pending_payment"), before))
    }

    @Test fun owedTakesOffDiscountAndDeposits() {
        assertEquals(35000L, Appointments.owed(appointment()))
        assertEquals(20000L, Appointments.owed(appointment(discount = 5000, deposit = 10000)))
        assertEquals(0L, Appointments.owed(appointment(deposit = 50000)))
        assertEquals(0L, Appointments.owed(appointment(price = null)))
        assertEquals(Appointments.Payment.COVERED, Appointments.payment(appointment(deposit = 35000)))
        assertEquals(Appointments.Payment.PARTIAL, Appointments.payment(appointment(deposit = 10000)))
        assertEquals(Appointments.Payment.PAID, Appointments.payment(appointment(sale = SaleRef("s", 12))))
        assertEquals(Appointments.Payment.UNPAID, Appointments.payment(appointment()))
        assertEquals(Appointments.Payment.NONE, Appointments.payment(appointment("cancelled")))
    }

    @Test fun adminActionsFollowStatusAndPayment() {
        assertEquals(listOf(A.CHARGE, A.DEPOSIT, A.RESCHEDULE, A.CANCEL, A.VIEW_CUSTOMER), Appointments.actions(appointment(), true, before))
        assertEquals(listOf(A.CHARGE, A.DEPOSIT, A.NO_SHOW, A.RESCHEDULE, A.CANCEL, A.VIEW_CUSTOMER), Appointments.actions(appointment(), true, after))
        assertEquals(listOf(A.CLOSE_SALE, A.NO_SHOW, A.RESCHEDULE, A.CANCEL, A.VIEW_CUSTOMER), Appointments.actions(appointment(deposit = 35000), true, after))
        assertEquals(listOf(A.VIEW_SALE, A.RESCHEDULE, A.VIEW_CUSTOMER), Appointments.actions(appointment(sale = SaleRef("s", 3)), true, after))
        assertEquals(listOf(A.UNDO_NO_SHOW, A.VIEW_CUSTOMER), Appointments.actions(appointment("no_show"), true, after))
        assertEquals(listOf(A.VIEW_CUSTOMER), Appointments.actions(appointment("cancelled"), true, after))
        assertEquals(listOf(A.VIEW_CUSTOMER), Appointments.actions(appointment("pending_payment"), true, after))
    }

    @Test fun professionalsOnlyMoveTheirOwnAppointments() {
        assertEquals(listOf(A.RESCHEDULE), Appointments.actions(appointment(), false, after))
        assertEquals(emptyList<Appointments.Action>(), Appointments.actions(appointment("no_show"), false, after))
        assertEquals(emptyList<Appointments.Action>(), Appointments.actions(appointment("cancelled"), false, after))
    }

    @Test fun theDayIsGroupedIntoMorningAfternoonAndEvening() {
        assertEquals(Appointments.Segment.MORNING, Appointments.segment(LocalTime.of(11, 59)))
        assertEquals(Appointments.Segment.AFTERNOON, Appointments.segment(LocalTime.of(12, 0)))
        assertEquals(Appointments.Segment.EVENING, Appointments.segment(LocalTime.of(18, 0)))
        val bogota = ZoneId.of("America/Bogota")
        val day = listOf(
            appointment(start = "2026-11-10T23:30:00.000Z"), // 18:30
            appointment(start = "2026-11-10T14:00:00.000Z"), // 09:00
            appointment(start = "2026-11-10T19:00:00.000Z"), // 14:00
            appointment(start = "2026-11-10T13:00:00.000Z"), // 08:00
        )
        val sections = Appointments.group(day, bogota)
        assertEquals(listOf(Appointments.Segment.MORNING, Appointments.Segment.AFTERNOON, Appointments.Segment.EVENING), sections.map { it.segment })
        assertEquals(listOf("2026-11-10T13:00:00.000Z", "2026-11-10T14:00:00.000Z"), sections[0].appointments.map { it.startsAt })
        // In Madrid the same instants fall in other parts of the day.
        assertEquals(Appointments.Segment.AFTERNOON, Appointments.group(listOf(day[3]), ZoneId.of("Europe/Madrid")).single().segment)
    }

    @Test fun filtersByProfessionalLocationAndCancelled() {
        val list = listOf(appointment(resource = "a"), appointment(resource = "b", location = "m"), appointment("cancelled", resource = "a"))
        assertEquals(2, Appointments.filter(list, null, null, false).size)
        assertEquals(3, Appointments.filter(list, null, null, true).size)
        assertEquals(1, Appointments.filter(list, "a", null, false).size)
        assertEquals(1, Appointments.filter(list, null, "m", true).size)
        val bogota = ZoneId.of("America/Bogota")
        assertEquals(3, Appointments.onDay(list, LocalDate.of(2026, 11, 10), bogota).size)
        assertEquals(0, Appointments.onDay(list, LocalDate.of(2026, 11, 11), bogota).size)
    }

    // Checkout

    @Test fun saleArithmeticMatchesTheServerChecks() {
        val lines = listOf(
            Checkout.Line("1", "service", "Corte", unitPrice = 35000),
            Checkout.Line("2", "product", "Cera", quantity = 2, unitPrice = 12000, discountAmount = 4000),
        )
        assertEquals(55000L, Checkout.subtotal(lines))
        assertEquals(5500L, Checkout.codeDiscount(55000, 10, null))
        assertEquals(55000L, Checkout.codeDiscount(55000, null, 80000))
        assertEquals(3300L, Checkout.codeDiscount(10001, 33, null))
        val total = Checkout.total(55000, 5500)
        assertEquals(49500L, total)
        val due = Checkout.due(total, 5000)
        assertEquals(54500L, due)
        assertEquals(44500L, Checkout.remaining(due, 10000, 0))
        assertEquals(0L, Checkout.remaining(due, 60000, 0))
        assertEquals(0L, Checkout.balance(due, 54500))
        assertEquals(-500L, Checkout.balance(due, 54000))
        assertEquals(20000L, Checkout.giftCardPayment(20000, 54500))
        assertEquals(1000L, Checkout.giftCardPayment(20000, 1000))
    }

    // Phones and names

    @Test fun phonesCarryTheirCountryCode() {
        val colombia = Phones.country("CO")!!
        assertEquals("+57 3001234567", Phones.compose(colombia, "300 123 4567"))
        assertEquals("+57 3001234567", Phones.compose(colombia, "+57 300 123 4567"))
        assertNull(Phones.compose(colombia, "  "))
        assertEquals("CO", Phones.split("+573001234567")?.first?.iso)
        assertEquals("3001234567", Phones.split("+573001234567")?.second)
        assertEquals("US", Phones.split("+15551234567")?.first?.iso)
        assertEquals("+57 300 123 4567", Phones.format("+573001234567"))
        assertEquals("https://wa.me/573001234567", Phones.whatsappUrl("+57 300 123 4567"))
        assertEquals("tel:+573001234567", Phones.dialUri("+573001234567"))
        assertEquals("CO", Phones.defaultCountry("CO").iso)
        assertEquals("MX", Phones.defaultCountry("MX").iso)
    }

    @Test fun initialsForAvatars() {
        assertEquals("AP", Names.initials("Ana Pérez"))
        assertEquals("AG", Names.initials("  ana maría  gómez "))
        assertEquals("C", Names.initials("Camila"))
        assertEquals("?", Names.initials(""))
        assertEquals("Ana Pérez", Names.full("Ana", " Pérez "))
        val index = Names.colorIndex("Camila", 8)
        assertEquals(index, Names.colorIndex("Camila", 8))
        assertTrue(index in 0 until 8)
    }

    // Periods

    @Test fun reportPeriodsMatchTheWebsPresets() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(Periods.Range(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)), Periods.range(Periods.Preset.WEEK, today))
        assertEquals(Periods.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), Periods.range(Periods.Preset.MONTH, today))
        assertEquals(Periods.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), Periods.range(Periods.Preset.LAST_MONTH, today))
        assertEquals(Periods.Range(LocalDate.of(2026, 10, 13), today), Periods.range(Periods.Preset.LAST_30, today))
        assertEquals(0.5, Periods.change(15.0, 10.0)!!, 0.0001)
        assertNull(Periods.change(3.0, 0.0))
    }

    // Errors

    @Test fun serverErrorsAreReadAndWrittenInPlainWords() {
        val failure = ApiClient.parseFailure(400, """{"error":{"code":"validation_failed","fields":{"customer.phone":"invalid_phone"}}}""")
        assertEquals("validation_failed", failure.code)
        assertEquals("invalid_phone", failure.fields["customer.phone"])
        assertEquals("Escribe un número de celular válido.", failure.message)
        assertEquals("Escribe un número de celular válido.", failure.fieldMessage("phone"))
        val booked = ApiClient.parseFailure(409, """{"error":{"code":"already_booked","fields":{"code":"K7Q4XM","startsAt":"2026-11-10T14:00:00.000Z"}}}""")
        assertEquals("K7Q4XM", booked.fields["code"])
        assertEquals("No tienes permiso para hacer esto.", ApiClient.parseFailure(403, "not json").message)
        assertTrue(ApiClient.parseFailure(401, "").isUnauthorized)
        assertEquals("Algo salió mal. Inténtalo de nuevo.", ApiClient.parseFailure(400, """{"error":{"code":"brand_new_code"}}""").message)
    }

    @Test fun copyFollowsThePhonesLanguage() {
        assertEquals("Cancelar cita", Copy.agenda.cancelAppointment)
        assertEquals("Faltan $ 5 por cobrar", plain(Copy.agenda.dueLeft("$ 5")))
        Locale.setDefault(Locale.US)
        assertEquals("Cancel appointment", Copy.agenda.cancelAppointment)
        assertEquals("No-show", Copy.agenda.status(Appointments.Status.NO_SHOW))
    }

    @Test fun nullsAreSentWhenARouteRequiresTheKey() {
        assertEquals("""{"resourceId":null,"serviceId":"s"}""", obj("resourceId" to null, "serviceId" to "s").toString())
    }
}
