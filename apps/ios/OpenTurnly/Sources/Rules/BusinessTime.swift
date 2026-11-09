import Foundation

/// A calendar date with no time zone: "2026-11-10".
///
/// Booking dates, slot lookups, opening hours and report ranges are wall-clock
/// values in the business's time zone, never the phone's, so they are kept as
/// plain dates and only turned into instants through a `BusinessClock`.
struct LocalDate: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init?(_ string: String) {
        let parts = string.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, (1...12).contains(parts[1]), (1...31).contains(parts[2]) else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let value = LocalDate(raw) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Not a date: \(raw)"))
        }
        self = value
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(string)
    }

    var string: String { String(format: "%04d-%02d-%02d", year, month, day) }
    var description: String { string }

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    /// Calendar arithmetic happens in UTC, where every day is 24 hours long.
    private static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    /// Noon UTC on this date: a stable instant to format the date itself.
    var noonUTC: Date {
        LocalDate.utc.date(from: DateComponents(year: year, month: month, day: day, hour: 12)) ?? Date(timeIntervalSince1970: 0)
    }

    init(noonUTC date: Date) {
        let parts = LocalDate.utc.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    func adding(days: Int) -> LocalDate {
        LocalDate(noonUTC: LocalDate.utc.date(byAdding: .day, value: days, to: noonUTC) ?? noonUTC)
    }

    func adding(months: Int) -> LocalDate {
        LocalDate(noonUTC: LocalDate.utc.date(byAdding: .month, value: months, to: noonUTC) ?? noonUTC)
    }

    /// 0 = Sunday … 6 = Saturday, like the server's opening hours.
    var weekday: Int { (LocalDate.utc.component(.weekday, from: noonUTC) + 6) % 7 }

    /// Days from this date to `other` (negative when `other` is earlier).
    func days(to other: LocalDate) -> Int {
        LocalDate.utc.dateComponents([.day], from: noonUTC, to: other.noonUTC).day ?? 0
    }

    var firstOfMonth: LocalDate { LocalDate(year: year, month: month, day: 1) }

    var lastOfMonth: LocalDate { firstOfMonth.adding(months: 1).adding(days: -1) }

    /// The first day of the week this date falls in. `firstWeekday` is 0 for
    /// Sunday or 1 for Monday.
    func startOfWeek(firstWeekday: Int = 1) -> LocalDate {
        let back = (weekday - firstWeekday + 7) % 7
        return adding(days: -back)
    }
}

/// Converts between instants and the business's wall clock.
struct BusinessClock: Sendable {
    let timeZone: TimeZone

    init(timeZone: TimeZone) {
        self.timeZone = timeZone
    }

    init(identifier: String) {
        timeZone = TimeZone(identifier: identifier) ?? .current
    }

    var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    /// The instant a business day starts (00:00 there), DST included.
    func startOfDay(_ date: LocalDate) -> Date {
        let components = DateComponents(year: date.year, month: date.month, day: date.day, hour: 0, minute: 0)
        return calendar.date(from: components).map { calendar.startOfDay(for: $0) } ?? date.noonUTC
    }

    /// `[start of from, start of the day after to)`: the instants a day range covers.
    func range(_ from: LocalDate, through to: LocalDate) -> (from: Date, to: Date) {
        (startOfDay(from), startOfDay(to.adding(days: 1)))
    }

    func localDate(of instant: Date) -> LocalDate {
        let parts = calendar.dateComponents([.year, .month, .day], from: instant)
        return LocalDate(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    func today(now: Date = Date()) -> LocalDate { localDate(of: now) }

    /// Wall-clock "HH:mm" of an instant, as the server writes slot times.
    func time(of instant: Date) -> String {
        let parts = calendar.dateComponents([.hour, .minute], from: instant)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    /// Minutes since midnight there.
    func minuteOfDay(_ instant: Date) -> Int {
        let parts = calendar.dateComponents([.hour, .minute], from: instant)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    /// The instant a wall-clock time on a business date stands for.
    func instant(_ date: LocalDate, time: String) -> Date? {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        return calendar.date(from: DateComponents(year: date.year, month: date.month, day: date.day, hour: parts[0], minute: parts[1]))
    }
}

/// "HH:MM" helpers for opening hours.
enum WallTime {
    static func minutes(_ value: String) -> Int? {
        let parts = value.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2, (0...24).contains(parts[0]), (0...59).contains(parts[1]) else { return nil }
        return parts[0] * 60 + parts[1]
    }

    static func string(_ minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }

    /// Minutes as people say them: "45 min", "1 h", "1 h 30 min".
    static func duration(_ minutes: Int) -> String {
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60, rest = minutes % 60
        return rest == 0 ? "\(hours) h" : "\(hours) h \(rest) min"
    }
}
