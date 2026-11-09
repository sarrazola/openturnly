package org.openturnly.mobile.rules

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Dates and times as the business lives them.
 *
 * Every time on screen is in the business's time zone, never the phone's: a
 * barber checking the agenda from abroad still sees the shop's 9:00. Instants
 * travel as ISO-8601 UTC strings; calendar days and wall-clock times travel as
 * "YYYY-MM-DD" and "HH:MM".
 */
object BusinessTime {
    fun zone(id: String?): ZoneId = runCatching { ZoneId.of(id ?: "UTC") }.getOrDefault(ZoneOffset.UTC)

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun parseDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun today(zone: ZoneId, now: Instant = Instant.now()): LocalDate = now.atZone(zone).toLocalDate()

    /** `[date 00:00, next day 00:00)` in the zone, as the web asks for a day. */
    fun dayRange(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

    fun range(from: LocalDate, toInclusive: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        from.atStartOfDay(zone).toInstant() to toInclusive.plusDays(1).atStartOfDay(zone).toInstant()

    /** Weeks start on Monday, as in the web's calendar and most of the Spanish-speaking world. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weeksBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.WEEKS.between(weekStart(a), weekStart(b))

    /** "2026-11-10T14:00:00.000Z": how the server writes instants, and how it wants them back. */
    fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.MILLIS)).let {
        if (it.contains('.')) it else it.removeSuffix("Z") + ".000Z"
    }

    fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
    fun localTime(instant: Instant, zone: ZoneId): LocalTime = instant.atZone(zone).toLocalTime()

    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

    /** "09:30", in 24 hours like the web and the server's slots. */
    fun time(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(hourMinute)
    fun time(value: String?, zone: ZoneId): String = parse(value)?.let { time(it, zone) } ?: ""

    fun dateKey(date: LocalDate): String = date.toString()

    /** "lunes, 9 de noviembre" / "Monday, November 9". */
    fun longDay(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "es") "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }

    /** "9 nov 2026" / "Nov 9, 2026": dates in lists. */
    fun mediumDate(date: LocalDate, locale: Locale, withYear: Boolean = true): String {
        val pattern = when {
            locale.language == "es" && withYear -> "d MMM yyyy"
            locale.language == "es" -> "d MMM"
            withYear -> "MMM d, yyyy"
            else -> "MMM d"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(".", "")
    }

    /** "lun 9 nov" / "Mon, Nov 9": compact headers and summaries. */
    fun shortDay(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "es") "EEE d MMM" else "EEE, MMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(".", "").replaceFirstChar { it.titlecase(locale) }
    }

    /** "noviembre 2026" for the month above the week strip. */
    fun monthYear(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

    /** One-letter or three-letter weekday labels for the week strip. */
    fun weekdayShort(date: LocalDate, locale: Locale): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replace(".", "").replaceFirstChar { it.titlecase(locale) }

    /** The server counts weekdays like JavaScript: 0 = Sunday … 6 = Saturday. */
    fun serverWeekday(day: DayOfWeek): Int = day.value % 7
    fun dayOfWeek(serverWeekday: Int): DayOfWeek = DayOfWeek.of(if (serverWeekday == 0) 7 else serverWeekday)

    /** Monday first, as the week is shown. */
    val weekOrder: List<Int> = listOf(1, 2, 3, 4, 5, 6, 0)

    fun weekdayName(serverWeekday: Int, locale: Locale): String =
        dayOfWeek(serverWeekday).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    /** Minutes between two instants, for durations on cards. */
    fun minutes(from: Instant, to: Instant): Long = ChronoUnit.MINUTES.between(from, to)

    /** "45 min", "1 h", "1 h 30 min". */
    fun duration(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** Date pickers speak UTC midnight milliseconds; the business speaks calendar days. */
    fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
