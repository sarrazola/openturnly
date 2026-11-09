import Foundation

/// English and Spanish, chosen by the phone.
///
/// There is no language picker on purpose: the phone already knows. Anything the
/// device is not set to falls back to English.
enum AppLocale {
    /// Tests pin a language here; the app never sets it.
    nonisolated(unsafe) static var override: String?

    static var languageCode: String {
        if let override { return override }
        let preferred = Locale.preferredLanguages.first ?? "en"
        let code = Locale(identifier: preferred).language.languageCode?.identifier ?? "en"
        return code.lowercased() == "es" ? "es" : "en"
    }

    static var isSpanish: Bool { languageCode == "es" }

    static func pick<T>(_ en: T, _ es: T) -> T { isSpanish ? es : en }

    /// The language the app speaks with the phone's region, for dates and names.
    static var locale: Locale {
        let region = Locale.current.region?.identifier
        return Locale(identifier: region.map { "\(languageCode)_\($0)" } ?? languageCode)
    }
}

/// Copy in the phone's language: English first, Spanish second.
func tr(_ en: String, _ es: String) -> String { AppLocale.pick(en, es) }

/// Dates as people read them, always in the business's time zone.
///
/// Fixed patterns rather than the locale's templates, so both apps write the same
/// day the same way: «Lunes, 9 de noviembre», «Lun 9 nov», «9 nov 2026».
enum Fmt {
    private static func formatter(_ pattern: String, timeZone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.locale
        formatter.timeZone = timeZone
        formatter.dateFormat = pattern
        return formatter
    }

    private static let utc = TimeZone(identifier: "UTC")!

    private static func capitalized(_ value: String) -> String {
        guard let first = value.first else { return value }
        return first.uppercased() + value.dropFirst()
    }

    private static func string(_ date: LocalDate, _ pattern: String) -> String {
        formatter(pattern, timeZone: utc).string(from: date.noonUTC).replacingOccurrences(of: ".", with: "")
    }

    /// The year today, to leave it out of dates in the current year.
    static var currentYear: Int { Calendar(identifier: .gregorian).component(.year, from: Date()) }

    /// "Lunes, 9 de noviembre" / "Monday, November 9", with the year when it is not this one
    /// ("… de 2027" / "…, 2027").
    static func dayLong(_ date: LocalDate, thisYear: Int = currentYear) -> String {
        let day = capitalized(string(date, AppLocale.isSpanish ? "EEEE, d 'de' MMMM" : "EEEE, MMMM d"))
        guard date.year != thisYear else { return day }
        return AppLocale.isSpanish ? "\(day) de \(date.year)" : "\(day), \(date.year)"
    }

    /// "Lun 9 nov" / "Mon, Nov 9".
    static func dayMedium(_ date: LocalDate) -> String {
        capitalized(string(date, AppLocale.isSpanish ? "EEE d MMM" : "EEE, MMM d"))
    }

    /// "9 nov 2026" / "Nov 9, 2026".
    static func dayWithYear(_ date: LocalDate) -> String {
        string(date, AppLocale.isSpanish ? "d MMM yyyy" : "MMM d, yyyy")
    }

    /// "9 nov" / "Nov 9".
    static func dayShort(_ date: LocalDate) -> String {
        string(date, AppLocale.isSpanish ? "d MMM" : "MMM d")
    }

    /// "nov" / "Nov".
    static func monthShort(_ date: LocalDate) -> String {
        string(date, "MMM")
    }

    /// "Lun" / "Mon".
    static func weekdayShort(_ date: LocalDate) -> String {
        capitalized(String(string(date, "EEE").prefix(3)))
    }

    /// "Noviembre 2026" / "November 2026".
    static func monthYear(_ date: LocalDate) -> String {
        capitalized(string(date, "LLLL yyyy"))
    }

    /// The weekday's name for an opening-hours row (0 = Sunday).
    static func weekdayName(_ weekday: Int) -> String {
        // 2026-11-08 was a Sunday.
        let date = LocalDate(year: 2026, month: 11, day: 8).adding(days: weekday)
        return capitalized(string(date, "EEEE"))
    }

    /// "Hoy", "Mañana", "Ayer", or nil for any other day.
    static func relativeName(_ date: LocalDate, today: LocalDate) -> String? {
        switch today.days(to: date) {
        case 0: return L.Common.today
        case 1: return L.Common.tomorrow
        case -1: return L.Common.yesterday
        default: return nil
        }
    }

    /// "Hoy", "Mañana", "Ayer" or the long day.
    static func relativeDay(_ date: LocalDate, today: LocalDate, short: Bool = false) -> String {
        relativeName(date, today: today) ?? (short ? dayMedium(date) : dayLong(date, thisYear: today.year))
    }

    /// An instant as a date and time there: "9 nov 2026 · 14:30".
    static func dateTime(_ instant: Date, clock: BusinessClock) -> String {
        let date = clock.localDate(of: instant)
        return "\(dayWithYear(date)) · \(clock.time(of: instant))"
    }

    /// How long ago, for a notification: «Ahora», «Hace 5 min», «Hace 2 h», «Ayer»,
    /// «Hace 3 días», then the day («9 nov»). Minutes within the hour, hours within
    /// the same day, calendar days for a week.
    static func ago(_ instant: Date, now: Date = Date(), clock: BusinessClock) -> String {
        let minutes = Int(now.timeIntervalSince(instant) / 60)
        if minutes < 1 { return L.Notifications.justNow }
        if minutes < 60 { return L.Notifications.minutesAgo(minutes) }
        let day = clock.localDate(of: instant)
        let days = day.days(to: clock.localDate(of: now))
        if days <= 0 { return L.Notifications.hoursAgo(minutes / 60) }
        if days == 1 { return L.Common.yesterday }
        if days < 7 { return L.Notifications.daysAgo(days) }
        return dayShort(day)
    }

    /// "45 %" / "45%": Spanish puts a space before the sign, English doesn't.
    static func percent(_ value: Double, digits: Int = 0) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: AppLocale.isSpanish ? "es_CO" : "en_US")
        formatter.maximumFractionDigits = digits
        let number = formatter.string(from: NSNumber(value: value * 100)) ?? "\(Int((value * 100).rounded()))"
        return AppLocale.isSpanish ? "\(number) %" : "\(number)%"
    }

    /// A change against the period before: "+12 %" / "−5 %" ("+12%" in English).
    static func change(_ fraction: Double) -> String {
        let rounded = Int((fraction * 100).rounded())
        let sign = rounded > 0 ? "+" : rounded < 0 ? "\u{2212}" : ""
        return sign + percent(Double(abs(rounded)) / 100)
    }
}
