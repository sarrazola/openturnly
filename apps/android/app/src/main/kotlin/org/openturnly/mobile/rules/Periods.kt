package org.openturnly.mobile.rules

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/** The report and cash periods the web offers, as business-local calendar days (inclusive). */
object Periods {
    enum class Preset { TODAY, WEEK, MONTH, LAST_MONTH, LAST_30, YEAR }

    /** The period the reports open on: commissions are paid, and owners count, by the month. */
    val default: Preset = Preset.MONTH

    data class Range(val start: LocalDate, val end: LocalDate)

    fun range(preset: Preset, today: LocalDate): Range = when (preset) {
        Preset.TODAY -> Range(today, today)
        Preset.WEEK -> BusinessTime.weekStart(today).let { Range(it, it.plusDays(6)) }
        Preset.MONTH -> Range(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
        Preset.LAST_MONTH -> today.minusMonths(1).let { Range(it.withDayOfMonth(1), it.with(TemporalAdjusters.lastDayOfMonth())) }
        Preset.LAST_30 -> Range(today.minusDays(29), today)
        Preset.YEAR -> Range(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
    }

    /** The change against the period before, as a fraction; null when there is nothing to compare. */
    fun change(current: Double, before: Double): Double? = if (before == 0.0) null else (current - before) / before

    /** Which way a figure moved, judged on the rounded number that is shown. */
    enum class Direction { UP, DOWN, FLAT }

    /** A change as it is shown: a whole number without its sign, and its direction. */
    data class Trend(val amount: Int, val direction: Direction)

    private fun trendOf(signed: Int): Trend = Trend(
        abs(signed),
        when { signed > 0 -> Direction.UP; signed < 0 -> Direction.DOWN; else -> Direction.FLAT },
    )

    /**
     * The change of a count or an amount in whole percent ("+12 %", "−5 %"); null
     * when the period before had nothing. Halves round away from zero, the same
     * up as down.
     */
    fun percentTrend(current: Double, before: Double): Trend? = change(current, before)?.let { fraction ->
        val rounded = (abs(fraction) * 100).roundToInt()
        trendOf(if (fraction < 0) -rounded else rounded)
    }

    /** Occupancy (0 to 1) as the whole percent shown: 0.454 is 45. */
    fun wholePercent(fraction: Double): Int = (fraction * 100).roundToInt()

    /**
     * The change in occupancy in percentage points, between the two whole percents
     * shown, so "45 %" against "40 %" always reads "+5 pts"; null when either
     * period has no occupancy.
     */
    fun pointsTrend(current: Double?, before: Double?): Trend? {
        if (current == null || before == null) return null
        return trendOf(wholePercent(current) - wholePercent(before))
    }

    /**
     * Each count's whole share of the total, adding up to exactly 100 (largest
     * remainder: the shares that lost the most to rounding down get the points
     * left over, earlier ones first on a tie). All zeros when there is nothing.
     */
    fun shares(counts: List<Int>): List<Int> {
        val total = counts.sumOf { it.coerceAtLeast(0).toLong() }
        if (total == 0L) return counts.map { 0 }
        val scaled = counts.map { it.coerceAtLeast(0).toLong() * 100 }
        val result = scaled.map { (it / total).toInt() }.toMutableList()
        var left = 100 - result.sum()
        val byRemainder = counts.indices.sortedWith(compareByDescending<Int> { scaled[it] % total }.thenBy { it })
        for (index in byRemainder) {
            if (left <= 0) break
            result[index] += 1
            left -= 1
        }
        return result
    }

    /** Where bookings come from, in a fixed order that breaks ties between equal counts. */
    private val originOrder = listOf("online", "admin", "agent")

    /** One way bookings arrive ("online", "admin", "agent"), its count and its whole share. */
    data class OriginRow(val key: String, val count: Int, val share: Int)

    /** The origin rows as shown: most bookings first, ties in the web's order, each with its share. */
    fun origins(origin: Map<String, Int>): List<OriginRow> {
        val rows = origin.entries.filter { it.value > 0 }.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { originOrder.indexOf(it.key).let { index -> if (index < 0) originOrder.size else index } }
                .thenBy { it.key },
        )
        val shares = shares(rows.map { it.value })
        return rows.mapIndexed { index, entry -> OriginRow(entry.key, entry.value, shares[index]) }
    }
}
