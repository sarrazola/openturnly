import Foundation

/// The sales list: which days a period covers and what its summary adds up.
enum SalesRules {
    enum Span: Hashable, CaseIterable {
        case day, week, month
    }

    /// The business days a period covers, both ends included. Weeks start on Monday.
    static func range(_ span: Span, anchor: LocalDate) -> (from: LocalDate, to: LocalDate) {
        switch span {
        case .day: return (anchor, anchor)
        case .week:
            let start = anchor.startOfWeek(firstWeekday: 1)
            return (start, start.adding(days: 6))
        case .month: return (anchor.firstOfMonth, anchor.lastOfMonth)
        }
    }

    /// The anchor one period before or after.
    static func step(_ span: Span, anchor: LocalDate, by count: Int) -> LocalDate {
        switch span {
        case .day: return anchor.adding(days: count)
        case .week: return anchor.adding(days: 7 * count)
        case .month: return anchor.firstOfMonth.adding(months: count)
        }
    }

    static func includes(_ span: Span, anchor: LocalDate, day: LocalDate) -> Bool {
        let range = range(span, anchor: anchor)
        return range.from <= day && day <= range.to
    }

    /// «Hoy» / «Lun 9 nov», «2 nov – 8 nov», «Noviembre 2026».
    static func label(_ span: Span, anchor: LocalDate, today: LocalDate) -> String {
        switch span {
        case .day: return anchor == today ? L.Common.today : Fmt.dayMedium(anchor)
        case .week:
            let range = range(span, anchor: anchor)
            return "\(Fmt.dayShort(range.from)) – \(Fmt.dayShort(range.to))"
        case .month: return Fmt.monthYear(anchor)
        }
    }

    /// What came in: every paid sale (internal ones too) with its tips, so it
    /// equals the per-method breakdown.
    struct Summary: Equatable {
        let collected: Int
        let count: Int
        /// The average sale total (tips apart); 0 without sales.
        let average: Int
        let tips: Int
        /// Method and amount, biggest first.
        let byMethod: [Method]

        struct Method: Equatable {
            let method: String
            let amount: Int
        }
    }

    static func summary(_ sales: [Sale]) -> Summary {
        let paid = sales.filter { !$0.isVoid }
        var totals: [String: Int] = [:]
        for sale in paid { for payment in sale.payments { totals[payment.method, default: 0] += payment.amount } }
        let methods = totals.map { Summary.Method(method: $0.key, amount: $0.value) }
            .sorted { ($0.amount, $1.method) > ($1.amount, $0.method) }
        return Summary(
            collected: paid.reduce(0) { $0 + $1.collected },
            count: paid.count,
            average: paid.isEmpty ? 0 : paid.reduce(0) { $0 + $1.total } / paid.count,
            tips: paid.reduce(0) { $0 + $1.tipAmount },
            byMethod: methods
        )
    }

    /// The payment methods of a sale, each once, in the order they were paid.
    static func methods(_ sale: Sale) -> [String] {
        var seen: [String] = []
        for payment in sale.payments where !seen.contains(payment.method) { seen.append(payment.method) }
        return seen
    }

    /// The first two items, then «+N».
    static func items(_ sale: Sale) -> String {
        let names = sale.items.map(\.name)
        let shown = names.prefix(2).joined(separator: ", ")
        return names.count > 2 ? "\(shown) +\(names.count - 2)" : shown
    }
}

/// The customers list and a customer's numbers.
enum CustomerRules {
    /// The section a customer goes in: the first letter of the first name without
    /// accents, or «#».
    static func sectionKey(_ name: String) -> String {
        guard let first = TextRules.folded(name.trimmingCharacters(in: .whitespaces)).uppercased().first,
              first.isASCII, first.isLetter else { return "#" }
        return String(first)
    }

    /// A–Z sections, «#» last, people sorted by name inside each.
    static func sections<T>(_ people: [T], name: (T) -> String) -> [(key: String, items: [T])] {
        let grouped = Dictionary(grouping: people) { sectionKey(name($0)) }
        return grouped.keys
            .sorted { ($0 == "#" ? 1 : 0, $0) < ($1 == "#" ? 1 : 0, $1) }
            .map { key in
                (key, (grouped[key] ?? []).sorted { TextRules.folded(name($0)) < TextRules.folded(name($1)) })
            }
    }

    /// The three tiles: visits (confirmed and started), the last of them, and the next one.
    struct Stats: Equatable {
        let visits: Int
        let lastVisit: Date?
        let nextVisit: Date?
    }

    static func stats(_ appointments: [CustomerAppointment], now: Date = Date()) -> Stats {
        let visits = appointments.filter { $0.status == .confirmed && $0.startsAt <= now }
        let next = upcoming(appointments, now: now).first { $0.status == .confirmed }
        return Stats(visits: visits.count, lastVisit: visits.map(\.startsAt).max(), nextVisit: next?.startsAt)
    }

    /// Still ahead and not cancelled, soonest first.
    static func upcoming(_ appointments: [CustomerAppointment], now: Date = Date()) -> [CustomerAppointment] {
        appointments.filter { $0.startsAt > now && $0.status != .cancelled }.sorted { $0.startsAt < $1.startsAt }
    }

    /// Everything else, newest first.
    static func history(_ appointments: [CustomerAppointment], now: Date = Date()) -> [CustomerAppointment] {
        let ahead = Set(upcoming(appointments, now: now).map(\.id))
        return appointments.filter { !ahead.contains($0.id) }.sorted { $0.startsAt > $1.startsAt }
    }
}

/// The reports' comparisons with the period before.
enum ReportRules {
    enum Preset: String, CaseIterable, Identifiable {
        case today, week, month, lastMonth, last30, year
        var id: String { rawValue }

        func range(today: LocalDate) -> (LocalDate, LocalDate) {
            switch self {
            case .today: return (today, today)
            case .week: return (today.startOfWeek(firstWeekday: 1), today.startOfWeek(firstWeekday: 1).adding(days: 6))
            case .month: return (today.firstOfMonth, today.lastOfMonth)
            case .lastMonth:
                let previous = today.firstOfMonth.adding(days: -1)
                return (previous.firstOfMonth, previous.lastOfMonth)
            case .last30: return (today.adding(days: -29), today)
            case .year: return (LocalDate(year: today.year, month: 1, day: 1), LocalDate(year: today.year, month: 12, day: 31))
            }
        }
    }

    /// The change against the period before, as a fraction; nil when there is nothing to compare.
    static func change(_ current: Double, _ before: Double) -> Double? {
        before == 0 ? nil : (current - before) / before
    }

    /// Occupancy moves in points, not percent of percent.
    static func points(_ current: Double?, _ before: Double?) -> Int? {
        guard let current, let before else { return nil }
        return Int(((current - before) * 100).rounded())
    }
}
