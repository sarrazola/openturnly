package org.openturnly.mobile.rules

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The report and cash periods the web offers, as business-local calendar days (inclusive). */
object Periods {
    enum class Preset { TODAY, WEEK, MONTH, LAST_MONTH, LAST_30, YEAR }

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
}
