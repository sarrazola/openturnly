package org.openturnly.mobile

import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Periods
import org.openturnly.mobile.rules.Periods.Direction

/** The reports' figures and the More screens' copy, as both apps must show them. */
class MoreRulesTest {
    private lateinit var saved: Locale

    @Before fun remember() { saved = Locale.getDefault() }
    @After fun restore() { Locale.setDefault(saved) }

    private fun spanish() = Locale.setDefault(Locale.forLanguageTag("es-CO"))
    private fun english() = Locale.setDefault(Locale.forLanguageTag("en-US"))

    // Periods

    @Test fun reportsOpenOnThisMonth() {
        assertEquals(Periods.Preset.MONTH, Periods.default)
        val today = LocalDate.of(2026, 11, 9)
        assertEquals(Periods.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), Periods.range(Periods.default, today))
        assertEquals(Periods.Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), Periods.range(Periods.Preset.YEAR, today))
        assertEquals(Periods.Range(today, today), Periods.range(Periods.Preset.TODAY, today))
    }

    @Test fun periodPresetsReadTheSameInBothLanguages() {
        spanish()
        assertEquals(
            listOf("Hoy", "Esta semana", "Este mes", "Mes pasado", "Últimos 30 días", "Este año"),
            Periods.Preset.entries.map { Copy.reports.preset(it) },
        )
        english()
        assertEquals(
            listOf("Today", "This week", "This month", "Last month", "Last 30 days", "This year"),
            Periods.Preset.entries.map { Copy.reports.preset(it) },
        )
    }

    // KPI change

    @Test fun kpiChangeIsAWholePercentWithItsDirection() {
        assertEquals(Periods.Trend(20, Direction.UP), Periods.percentTrend(12.0, 10.0))
        assertEquals(Periods.Trend(5, Direction.DOWN), Periods.percentTrend(95.0, 100.0))
        assertEquals(Periods.Trend(0, Direction.FLAT), Periods.percentTrend(100.0, 100.0))
        // Halves round away from zero, the same up as down.
        assertEquals(Periods.Trend(3, Direction.UP), Periods.percentTrend(205.0, 200.0))
        assertEquals(Periods.Trend(3, Direction.DOWN), Periods.percentTrend(195.0, 200.0))
        // A change that rounds to nothing is flat, whichever way it went.
        assertEquals(Periods.Trend(0, Direction.FLAT), Periods.percentTrend(1001.0, 1000.0))
        assertNull(Periods.percentTrend(4.0, 0.0))
        assertNull(Periods.percentTrend(0.0, 0.0))
    }

    @Test fun occupancyMovesInPointsBetweenTheShownPercents() {
        assertEquals(45, Periods.wholePercent(0.45))
        assertEquals(Periods.Trend(5, Direction.UP), Periods.pointsTrend(0.45, 0.40))
        assertEquals(Periods.Trend(2, Direction.DOWN), Periods.pointsTrend(0.50, 0.52))
        assertEquals(Periods.Trend(0, Direction.FLAT), Periods.pointsTrend(0.301, 0.299))
        assertNull(Periods.pointsTrend(0.3, null))
        assertNull(Periods.pointsTrend(null, 0.3))
    }

    @Test fun kpiChangeIsWrittenWithASignAndTheLanguagesPercent() {
        spanish()
        assertEquals("+12 %", Copy.reports.change(Periods.Trend(12, Direction.UP), inPoints = false))
        assertEquals("−5 %", Copy.reports.change(Periods.Trend(5, Direction.DOWN), inPoints = false))
        assertEquals("0 %", Copy.reports.change(Periods.Trend(0, Direction.FLAT), inPoints = false))
        assertEquals("+3 pts", Copy.reports.change(Periods.Trend(3, Direction.UP), inPoints = true))
        assertEquals("vs. 10 antes", Copy.reports.vs("10"))
        assertEquals("Sin datos para comparar", Copy.reports.noComparison)
        english()
        assertEquals("+12%", Copy.reports.change(Periods.Trend(12, Direction.UP), inPoints = false))
        assertEquals("−5%", Copy.reports.change(Periods.Trend(5, Direction.DOWN), inPoints = false))
        assertEquals("−2 pts", Copy.reports.change(Periods.Trend(2, Direction.DOWN), inPoints = true))
        assertEquals("vs. 10 before", Copy.reports.vs("10"))
    }

    // Where bookings come from

    @Test fun originSharesAlwaysAddUpTo100() {
        assertEquals(listOf(60, 30, 10), Periods.shares(listOf(12, 6, 2)))
        assertEquals(listOf(34, 33, 33), Periods.shares(listOf(1, 1, 1)))
        assertEquals(listOf(67, 33), Periods.shares(listOf(2, 1)))
        assertEquals(listOf(100), Periods.shares(listOf(7)))
        assertEquals(listOf(0, 0), Periods.shares(listOf(0, 0)))
        assertEquals(emptyList<Int>(), Periods.shares(emptyList()))
        listOf(listOf(3, 3, 1), listOf(5, 7, 9), listOf(1, 2, 97), listOf(33, 33, 34), listOf(1, 1, 1, 1, 1, 1, 1)).forEach {
            assertEquals(it.toString(), 100, Periods.shares(it).sum())
        }
    }

    @Test fun originRowsGoMostFirstWithTiesInTheWebsOrder() {
        val rows = Periods.origins(linkedMapOf("agent" to 2, "admin" to 6, "online" to 12))
        assertEquals(listOf("online", "admin", "agent"), rows.map { it.key })
        assertEquals(listOf(60, 30, 10), rows.map { it.share })
        val tied = Periods.origins(linkedMapOf("agent" to 1, "admin" to 1, "online" to 1, "other" to 0))
        assertEquals(listOf("online", "admin", "agent"), tied.map { it.key })
        assertEquals(listOf(34, 33, 33), tied.map { it.share })
        assertEquals(emptyList<Periods.OriginRow>(), Periods.origins(emptyMap()))
    }

    @Test fun originRowsReadCountAndShare() {
        spanish()
        assertEquals("Cómo llegan tus reservas", Copy.reports.origin)
        assertEquals("Página de reservas", Copy.reports.originLabel("online"))
        assertEquals("12 · 60 %", Copy.reports.originFigures(12, 60))
        english()
        assertEquals("Where bookings come from", Copy.reports.origin)
        assertEquals("12 · 60%", Copy.reports.originFigures(12, 60))
        assertEquals("No data for these dates yet.", Copy.reports.nothing)
    }

    // Commissions

    @Test fun commissionBaseShowsTheRateAndTheAmount() {
        spanish()
        assertEquals("10 % de ${Money.format(200_000, "COP")}", Copy.reports.rateOf(10.0, Money.format(200_000, "COP")))
        assertEquals("12,5 % de ${Money.format(80_000, "COP")}", Copy.reports.rateOf(12.5, Money.format(80_000, "COP")))
        assertEquals("Servicios (4)", Copy.reports.servicesCount(4))
        assertEquals("Productos (1)", Copy.reports.productsCount(1))
        english()
        assertEquals("10% of ${Money.format(20_000_000, "USD")}", Copy.reports.rateOf(10.0, Money.format(20_000_000, "USD")))
        assertEquals("12.5% of $800", Copy.reports.rateOf(12.5, "$800"))
        assertEquals("Services (4)", Copy.reports.servicesCount(4))
    }

    // Booking link and account

    @Test fun bookingLinkIsSharedWithTheBusinessName() {
        spanish()
        assertEquals("Reserva tu cita en Barbería Centro: https://turnos.example.com/centro", Copy.business.shareText("Barbería Centro", "https://turnos.example.com/centro"))
        assertEquals("Enlace copiado", Copy.business.linkCopied)
        assertEquals("Imprímelo para el mostrador o el espejo: tus clientes lo escanean y reservan.", Copy.business.qrHint)
        english()
        assertEquals("Book with Barbería Centro: https://turnos.example.com/centro", Copy.business.shareText("Barbería Centro", "https://turnos.example.com/centro"))
        assertEquals("Link copied", Copy.business.linkCopied)
    }

    @Test fun accountCopyMatchesTheOtherApp() {
        spanish()
        assertEquals("Cambios guardados", Copy.account.updated)
        assertEquals("La app sigue el modo claro u oscuro de tu teléfono.", Copy.account.theme)
        english()
        assertEquals("Changes saved", Copy.account.updated)
        assertEquals("The app follows your phone's light or dark mode.", Copy.account.theme)
    }
}
