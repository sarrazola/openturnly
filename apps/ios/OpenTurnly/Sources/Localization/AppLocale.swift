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
enum Fmt {
    private static func formatter(_ template: String, timeZone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.locale
        formatter.timeZone = timeZone
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter
    }

    private static let utc = TimeZone(identifier: "UTC")!

    private static func capitalized(_ value: String) -> String {
        guard let first = value.first else { return value }
        return first.uppercased() + value.dropFirst()
    }

    /// "Lunes, 9 de noviembre" / "Monday, November 9".
    static func dayLong(_ date: LocalDate) -> String {
        capitalized(formatter("EEEEdMMMM", timeZone: utc).string(from: date.noonUTC))
    }

    /// "Lun, 9 nov" / "Mon, Nov 9".
    static func dayMedium(_ date: LocalDate) -> String {
        capitalized(formatter("EEEdMMM", timeZone: utc).string(from: date.noonUTC))
    }

    /// "9 nov 2026" / "Nov 9, 2026".
    static func dayWithYear(_ date: LocalDate) -> String {
        formatter("dMMMy", timeZone: utc).string(from: date.noonUTC)
    }

    /// "9 nov" / "Nov 9".
    static func dayShort(_ date: LocalDate) -> String {
        formatter("dMMM", timeZone: utc).string(from: date.noonUTC)
    }

    /// "nov" / "Nov".
    static func monthShort(_ date: LocalDate) -> String {
        formatter("MMM", timeZone: utc).string(from: date.noonUTC).replacingOccurrences(of: ".", with: "")
    }

    /// "lun" / "Mon".
    static func weekdayShort(_ date: LocalDate) -> String {
        formatter("EEE", timeZone: utc).string(from: date.noonUTC).replacingOccurrences(of: ".", with: "")
    }

    /// "Noviembre 2026" / "November 2026".
    static func monthYear(_ date: LocalDate) -> String {
        capitalized(formatter("MMMMy", timeZone: utc).string(from: date.noonUTC))
    }

    /// The weekday's name for an opening-hours row (0 = Sunday).
    static func weekdayName(_ weekday: Int) -> String {
        // 2026-11-08 was a Sunday.
        let date = LocalDate(year: 2026, month: 11, day: 8).adding(days: weekday)
        return capitalized(formatter("EEEE", timeZone: utc).string(from: date.noonUTC))
    }

    /// "Hoy", "Mañana", "Ayer" or the long day.
    static func relativeDay(_ date: LocalDate, today: LocalDate, short: Bool = false) -> String {
        switch today.days(to: date) {
        case 0: return L.Common.today
        case 1: return L.Common.tomorrow
        case -1: return L.Common.yesterday
        default: return short ? dayMedium(date) : dayLong(date)
        }
    }

    /// An instant as a date and time there: "9 nov 2026, 14:30".
    static func dateTime(_ instant: Date, clock: BusinessClock) -> String {
        let date = clock.localDate(of: instant)
        return "\(dayWithYear(date)), \(clock.time(of: instant))"
    }

    static func percent(_ value: Double, digits: Int = 0) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .percent
        formatter.locale = AppLocale.locale
        formatter.maximumFractionDigits = digits
        return formatter.string(from: NSNumber(value: value)) ?? "\(Int(value * 100)) %"
    }
}
