package org.openturnly.mobile

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openturnly.mobile.api.CustomerVisit
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Inbox
import org.openturnly.mobile.rules.Search
import org.openturnly.mobile.ui.customers.CustomerFormRules
import org.openturnly.mobile.ui.customers.CustomerVisits

class CustomersRulesTest {
    private lateinit var previous: Locale

    @Before fun spanish() { previous = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("es-CO")) }
    @After fun restore() { Locale.setDefault(previous) }

    private fun laura(query: String) = Search.customer(query, "Laura Gómez", "laura.gomez@correo.com", "+573001234567", "1.020.304.050")

    // Search

    @Test fun everyWordMatchesInAnyOrderIgnoringAccentsAndCase() {
        assertTrue(laura("gomez laura"))
        assertTrue(laura("Laura Gómez"))
        assertTrue(laura("GÓMEZ"))
        assertTrue(laura("  laura   gom  "))
        assertTrue(laura("lau gó"))
        assertFalse(laura("laura perez"))
        assertFalse(laura("ana"))
    }

    @Test fun anEmptyQueryMatchesEveryone() {
        assertTrue(laura(""))
        assertTrue(laura("   "))
    }

    @Test fun aQueryCanMixNameEmailAndPhone() {
        assertTrue(laura("gomez@"))
        assertTrue(laura("correo.com laura"))
        assertFalse(laura("laura@"))
        assertTrue(laura("laura 300"))
    }

    @Test fun threeOrMoreDigitsMatchAPhoneWrittenWithSpaces() {
        val formatted = listOf("Laura Gómez", null, "+57 300 123 4567", null)
        assertTrue(Search.matches("3001234567", formatted))
        assertTrue(Search.matches("0012", formatted))
        assertTrue(Search.matches("300 123", formatted))
        assertTrue(Search.matches("+57 300", formatted))
        // Two digits only match as written; a word with letters never reads as digits.
        assertFalse(Search.matches("01", formatted))
        assertFalse(Search.matches("x0012", formatted))
        assertTrue(laura("300 123 4567"))
        assertFalse(laura("3009999"))
    }

    @Test fun theDocumentIsSearchedToo() {
        assertTrue(laura("1.020.304.050"))
        assertTrue(laura("1020304050"))
        assertTrue(laura("304050"))
        assertFalse(laura("1020304059"))
    }

    @Test fun foldDropsAccentsAndCase() {
        assertEquals("andres nunez", Search.fold("Andrés Núñez"))
        assertEquals("alvaro", Search.fold("ÁLVARO"))
    }

    // A–Z sections

    @Test fun aNameGoesUnderItsFirstLetterWithoutAccents() {
        assertEquals("A", Search.section("Álvaro Ruiz"))
        assertEquals("A", Search.section("andrés"))
        assertEquals("E", Search.section("Émile"))
        assertEquals("N", Search.section("Ñandú Pérez"))
        assertEquals("Z", Search.section("  zoe"))
    }

    @Test fun anythingButALetterGoesUnderHash() {
        assertEquals("#", Search.section("3 Hermanos"))
        assertEquals("#", Search.section("+57 300"))
        assertEquals("#", Search.section(""))
        assertEquals("#", Search.section("   "))
        assertEquals("#", Search.section("Ωmega"))
    }

    @Test fun sectionsReadAToZWithHashLastAndNamesInOrder() {
        val names = listOf("Bruno", "3 Hermanos", "andrés", "Álvaro", "Camila", "Ana", "#hash")
        val sections = Search.sections(names) { it }
        assertEquals(listOf("A", "B", "C", "#"), sections.map { it.first })
        assertEquals(listOf("Álvaro", "Ana", "andrés"), sections.first().second)
        assertEquals(listOf("#hash", "3 Hermanos"), sections.last().second)
    }

    // Visits

    private val now: Instant = Instant.parse("2026-11-09T15:00:00Z")

    private fun visit(id: String, startsAt: String, status: String = "confirmed", minutes: Long = 45) = CustomerVisit(
        id = id, startsAt = startsAt, endsAt = Instant.parse(startsAt).plusSeconds(minutes * 60).toString(), status = status,
        serviceName = "Corte", resourceName = "Ana",
    )

    @Test fun visitsCountOnlyConfirmedAppointmentsThatStarted() {
        val all = listOf(
            visit("past", "2026-10-01T15:00:00Z"),
            visit("older", "2026-03-01T15:00:00Z"),
            visit("running", "2026-11-09T14:45:00Z"),
            visit("future", "2026-11-12T15:00:00Z"),
            visit("cancelled", "2026-10-05T15:00:00Z", "cancelled"),
            visit("noShow", "2026-10-06T15:00:00Z", "no_show"),
            visit("pending", "2026-10-07T15:00:00Z", "pending_payment"),
        )
        val summary = CustomerVisits.of(all, now)
        assertEquals(3, summary.visits)
        assertEquals("running", summary.last?.id)
        assertEquals("future", summary.next?.id)
    }

    @Test fun upcomingIsSoonestFirstAndHistoryNewestFirst() {
        val all = listOf(
            visit("past", "2026-10-01T15:00:00Z"),
            visit("later", "2026-12-01T15:00:00Z"),
            visit("soon", "2026-11-10T15:00:00Z", "pending_payment"),
            visit("cancelledAhead", "2026-11-20T15:00:00Z", "cancelled"),
            visit("yesterday", "2026-11-08T15:00:00Z", "no_show"),
        )
        val summary = CustomerVisits.of(all, now)
        assertEquals(listOf("soon", "later"), summary.upcoming.map { it.id })
        assertEquals(listOf("cancelledAhead", "yesterday", "past"), summary.history.map { it.id })
        // Pending payment is coming but is not «Próxima cita»: only confirmed ones are.
        assertEquals("later", summary.next?.id)
        assertEquals(1, summary.visits)
    }

    @Test fun nothingYetReadsAsZeroAndDashes() {
        val summary = CustomerVisits.of(emptyList(), now)
        assertEquals(0, summary.visits)
        assertNull(summary.last)
        assertNull(summary.next)
        assertTrue(summary.upcoming.isEmpty() && summary.history.isEmpty())
    }

    // The form

    @Test fun theFormAsksForNamesAndAMobileOrAnEmail() {
        val errors = CustomerFormRules.errors(" ", "", null, "")
        assertEquals("Escribe el nombre.", errors[CustomerFormRules.FIRST_NAME])
        assertEquals("Escribe el apellido.", errors[CustomerFormRules.LAST_NAME])
        assertEquals("Deja al menos un celular o un email.", errors[CustomerFormRules.CONTACT])
        assertNull(errors[CustomerFormRules.EMAIL])
    }

    @Test fun anEmailMustLookLikeOne() {
        val bad = CustomerFormRules.errors("Laura", "Gómez", "+57 3001234567", "laura@correo")
        assertEquals(mapOf(CustomerFormRules.EMAIL to "Escribe un email válido."), bad)
        // Without a mobile, a broken email doesn't count as contact either.
        val alone = CustomerFormRules.errors("Laura", "Gómez", null, "laura")
        assertEquals(setOf(CustomerFormRules.CONTACT, CustomerFormRules.EMAIL), alone.keys)
        assertTrue(CustomerFormRules.errors("Laura", "Gómez", null, " laura@correo.com ").isEmpty())
        assertTrue(CustomerFormRules.errors("Laura", "Gómez", "+57 3001234567", "").isEmpty())
    }

    // Copy

    @Test fun rowsAndTilesReadTheSameOnBothApps() {
        assertEquals("1 visita", Copy.customers.visits(1))
        assertEquals("4 visitas", Copy.customers.visits(4))
        val nov12 = BusinessTime.mediumDate(LocalDate.of(2026, 11, 12), Locale.forLanguageTag("es-CO"), withYear = false)
        assertEquals("Próxima 12 nov", Copy.customers.next(nov12))
        assertEquals("Cliente desde 9 nov 2026", Copy.customers.since(BusinessTime.mediumDate(LocalDate.of(2026, 11, 9), Locale.forLanguageTag("es-CO"))))
        assertEquals("Sin definir", Copy.customers.genderLabel(""))
        assertEquals("Sin definir", Copy.customers.genderLabel(null))
        assertEquals(listOf("Femenino", "Masculino", "Otro", "Sin definir"), Copy.customers.genders.map { Copy.customers.genderLabel(it) })

        Locale.setDefault(Locale.US)
        assertEquals("1 visit", Copy.customers.visits(1))
        assertEquals("4 visits", Copy.customers.visits(4))
        assertEquals("Next Nov 12", Copy.customers.next(BusinessTime.mediumDate(LocalDate.of(2026, 11, 12), Locale.US, withYear = false)))
        assertEquals("Customer since Nov 9, 2026", Copy.customers.since(BusinessTime.mediumDate(LocalDate.of(2026, 11, 9), Locale.US)))
        assertEquals("Not set", Copy.customers.genderLabel(""))
    }

    // Notifications

    @Test fun aServerWithoutAnInboxHidesTheBell() {
        assertTrue(Inbox.unsupported(404))
        assertFalse(Inbox.unsupported(0))
        assertFalse(Inbox.unsupported(500))
        assertFalse(Inbox.unsupported(401))
    }

    @Test fun onlyRowsAboutAnAppointmentOpenSomething() {
        assertTrue(Inbox.opens("apt_1"))
        assertFalse(Inbox.opens(null))
        assertFalse(Inbox.opens(""))
    }

    @Test fun theBellSaysHowManyAreUnread() {
        assertEquals("Avisos", Copy.notifications.bell(0))
        assertEquals("Avisos, 2 sin leer", Copy.notifications.bell(2))
        Locale.setDefault(Locale.US)
        assertEquals("Notifications, 2 unread", Copy.notifications.bell(2))
    }
}
