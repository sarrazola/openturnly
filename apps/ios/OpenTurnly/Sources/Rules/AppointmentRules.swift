import Foundation

/// What the agenda shows for an appointment and what can be done with it.
///
/// The server is the authority; these rules mirror the web admin so the app only
/// offers actions the server accepts for that role and state. There is no
/// "completed" status: an appointment is done when a sale charged it.
///
/// The Android app implements the same tables (status chip, payment state, card
/// badge, actions); the unit tests pin them so the two can't drift.
enum AppointmentRules {
    /// How an appointment's state reads in a chip.
    enum DisplayStatus: Equatable {
        /// Confirmed and still ahead ("Próxima").
        case upcoming
        /// Confirmed and happening right now ("Ahora").
        case ongoing
        /// Confirmed and already over ("Confirmada").
        case confirmed
        case cancelled
        case noShow
        case pendingPayment
    }

    static func displayStatus(_ status: AppointmentStatus, startsAt: Date, endsAt: Date, now: Date = Date()) -> DisplayStatus {
        switch status {
        case .cancelled: return .cancelled
        case .noShow: return .noShow
        case .pendingPayment: return .pendingPayment
        case .confirmed, .unknown:
            if now < startsAt { return .upcoming }
            if now < endsAt { return .ongoing }
            return .confirmed
        }
    }

    static func displayStatus(_ appointment: Appointment, now: Date = Date()) -> DisplayStatus {
        displayStatus(appointment.status, startsAt: appointment.startsAt, endsAt: appointment.endsAt, now: now)
    }

    enum Action: Equatable, Hashable {
        case reschedule
        case cancel
        case markNoShow
        case undoNoShow
        /// "Cobrar $ X": the price is not covered yet.
        case charge
        /// "Cerrar venta": deposits already cover the price, only the sale is left.
        case closeSale
        /// "Registrar abono": leave money in advance.
        case deposit
        case viewSale
        case viewCustomer
    }

    /// What's still owed: price minus the booking discount minus unused deposits.
    static func owed(_ appointment: Appointment) -> Int {
        max(0, (appointment.priceAmount ?? 0) - appointment.discountAmount - appointment.depositAmount)
    }

    /// The price after the booking discount, when there is a price.
    static func price(_ appointment: Appointment) -> Int? {
        appointment.priceAmount.map { max(0, $0 - appointment.discountAmount) }
    }

    /// Deposits that already cover the price.
    static func covered(_ appointment: Appointment) -> Bool {
        appointment.depositAmount > 0 && appointment.priceAmount != nil && owed(appointment) == 0
    }

    static func started(_ appointment: Appointment, now: Date = Date()) -> Bool {
        appointment.startsAt <= now
    }

    /// The actions allowed, in a stable order.
    ///
    /// - Professionals can only move their own confirmed appointments: the server
    ///   refuses cancelling, no-shows and anything to do with money for them.
    /// - Cancelled and pending-payment appointments can't change.
    /// - A no-show can be undone by an admin ("Sí vino").
    static func actions(for appointment: Appointment, isAdmin: Bool, now: Date = Date()) -> [Action] {
        var result: [Action] = []
        switch appointment.status {
        case .noShow:
            if isAdmin { result.append(.undoNoShow) }
        case .cancelled, .pendingPayment, .unknown:
            break
        case .confirmed:
            if isAdmin {
                if appointment.sale != nil {
                    result.append(.viewSale)
                } else {
                    result.append(covered(appointment) ? .closeSale : .charge)
                    if !covered(appointment) { result.append(.deposit) }
                    if started(appointment, now: now) { result.append(.markNoShow) }
                }
            }
            result.append(.reschedule)
            // The web hides cancelling once a sale charged the appointment.
            if isAdmin && appointment.sale == nil { result.append(.cancel) }
        }
        if isAdmin { result.append(.viewCustomer) }
        return result
    }

    /// The sticky bar under the detail: an optional secondary button on the left
    /// and an optional primary one on the right. One button fills the width.
    struct Bar: Equatable {
        var secondary: Action?
        var primary: Action?
        var isEmpty: Bool { secondary == nil && primary == nil }
    }

    static func bar(_ actions: [Action]) -> Bar {
        if actions.contains(.undoNoShow) { return Bar(secondary: nil, primary: .undoNoShow) }
        let primary = [Action.charge, .closeSale, .viewSale].first(where: actions.contains)
        return Bar(secondary: actions.contains(.reschedule) ? .reschedule : nil, primary: primary)
    }

    /// The rows of the detail's "Acciones" list, in order.
    static func listed(_ actions: [Action]) -> [Action] {
        [Action.deposit, .markNoShow, .cancel].filter(actions.contains)
    }

    /// The long-press menu on an agenda card, in order. "Llamar" and "WhatsApp"
    /// go between the two parts.
    static func menu(_ actions: [Action]) -> (before: [Action], after: [Action]) {
        let money = [Action.charge, .closeSale, .viewSale].filter(actions.contains)
        let edit = actions.contains(.reschedule) ? [Action.reschedule] : []
        let after = [Action.markNoShow, .cancel].filter(actions.contains)
        return (money + edit, after)
    }

    /// The amount on "Cobrar $ X": what is still owed, or nil without a price.
    static func chargeAmount(_ appointment: Appointment) -> Int? {
        appointment.priceAmount == nil ? nil : owed(appointment)
    }

    /// The money state of a confirmed appointment (admins).
    enum PaymentState: Equatable {
        /// Charged by sale #number.
        case paid(number: Int)
        /// Deposits cover the price; the sale is still to be closed.
        case covered(deposit: Int)
        /// Part was left as a deposit; this much is still owed.
        case partly(owed: Int, deposit: Int)
        /// Nothing paid yet.
        case unpaid
        /// Nothing to collect (cancelled, no-show, pending online payment).
        case none
    }

    static func paymentState(_ appointment: Appointment) -> PaymentState {
        guard appointment.status == .confirmed else { return .none }
        if let sale = appointment.sale { return .paid(number: sale.number) }
        if covered(appointment) { return .covered(deposit: appointment.depositAmount) }
        if appointment.depositAmount > 0 && owed(appointment) > 0 {
            return .partly(owed: owed(appointment), deposit: appointment.depositAmount)
        }
        return .unpaid
    }

    /// What the agenda card shows at its bottom right.
    enum Badge: Equatable {
        /// The price after the booking discount, as muted text.
        case price(Int)
        /// «Sin cobrar» (warning).
        case notCharged
        /// «Abonó $ X» (neutral).
        case deposited(Int)
        /// «Faltan $ X» (warning).
        case left(Int)
        /// «Por cerrar» (warning).
        case toClose
        /// «Pagada» (info, check).
        case paid
        case none
    }

    static func badge(_ appointment: Appointment, isAdmin: Bool, now: Date = Date()) -> Badge {
        let plain: Badge = price(appointment).map(Badge.price) ?? .none
        guard isAdmin else { return plain }
        let started = started(appointment, now: now)
        switch paymentState(appointment) {
        case .none: return .none
        case .paid: return .paid
        case .covered(let deposit): return started ? .toClose : .deposited(deposit)
        case .partly(let owed, let deposit): return started ? .left(owed) : .deposited(deposit)
        case .unpaid: return started ? .notCharged : plain
        }
    }

    /// Confirmed, started and not charged: the day summary's «N por cobrar».
    static func toCharge(_ appointment: Appointment, now: Date = Date()) -> Bool {
        appointment.status == .confirmed && started(appointment, now: now) && appointment.sale == nil
    }

    /// The source line: booking page, the team, or an AI assistant with the key's name.
    enum Source: Equatable {
        case online, admin, agent(String?), other
    }

    static func source(_ appointment: Appointment) -> Source {
        switch appointment.source {
        case "online": return .online
        case "admin": return .admin
        case "agent": return .agent(appointment.sourceLabel)
        default: return .other
        }
    }
}

/// How the agenda lays out one day.
enum AgendaRules {
    enum Segment: Int, CaseIterable, Comparable {
        case morning, afternoon, evening
        static func < (lhs: Segment, rhs: Segment) -> Bool { lhs.rawValue < rhs.rawValue }

        /// Before noon, noon to 6 p.m., from 6 p.m. on (business wall clock).
        static func of(minuteOfDay minute: Int) -> Segment {
            if minute < 12 * 60 { return .morning }
            if minute < 18 * 60 { return .afternoon }
            return .evening
        }
    }

    struct Filter: Equatable {
        /// Empty means every professional.
        var professionalIds: Set<String> = []
        var locationId: String?
        var showCancelled = false

        /// A professional or a location narrows the list.
        var narrowed: Bool { !professionalIds.isEmpty || locationId != nil }
        /// Anything on: the filter button fills and the tag shows.
        var active: Bool { narrowed || showCancelled }
    }

    static func apply(_ filter: Filter, to appointments: [Appointment]) -> [Appointment] {
        appointments.filter { appointment in
            if !filter.showCancelled && appointment.status == .cancelled { return false }
            if let location = filter.locationId, appointment.locationId != location { return false }
            if !filter.professionalIds.isEmpty {
                guard let resource = appointment.resourceId, filter.professionalIds.contains(resource) else { return false }
            }
            return true
        }
    }

    /// The day has appointments, but the professional or location filter hides them all.
    /// Cancelled ones hidden because «Mostrar canceladas» is off don't count.
    static func hiddenByFilters(_ filter: Filter, all: [Appointment]) -> Bool {
        guard filter.narrowed, apply(filter, to: all).isEmpty else { return false }
        return !apply(Filter(showCancelled: filter.showCancelled), to: all).isEmpty
    }

    /// One day's appointments, in time order, split into morning / afternoon / evening.
    static func segments(_ appointments: [Appointment], clock: BusinessClock) -> [(segment: Segment, items: [Appointment])] {
        let sorted = appointments.sorted { ($0.startsAt, $0.endsAt, $0.id) < ($1.startsAt, $1.endsAt, $1.id) }
        let grouped = Dictionary(grouping: sorted) { Segment.of(minuteOfDay: clock.minuteOfDay($0.startsAt)) }
        return Segment.allCases.compactMap { segment in
            guard let items = grouped[segment], !items.isEmpty else { return nil }
            return (segment, items)
        }
    }

    /// Appointments that fall on each business day of a range.
    static func byDay(_ appointments: [Appointment], clock: BusinessClock) -> [LocalDate: [Appointment]] {
        Dictionary(grouping: appointments) { clock.localDate(of: $0.startsAt) }
    }

    /// Days with at least one confirmed or pending-payment appointment, after the
    /// professional and location filters: the week strip's dots.
    static func busyDays(_ appointments: [Appointment], filter: Filter, clock: BusinessClock) -> Set<LocalDate> {
        let narrowed = Filter(professionalIds: filter.professionalIds, locationId: filter.locationId, showCancelled: false)
        let counted = apply(narrowed, to: appointments).filter { [.confirmed, .pendingPayment].contains($0.status) }
        return Set(counted.map { clock.localDate(of: $0.startsAt) })
    }

    /// Happening right now.
    static func isOngoing(_ appointment: Appointment, now: Date = Date()) -> Bool {
        appointment.status == .confirmed && appointment.startsAt <= now && now < appointment.endsAt
    }

    /// The day summary's count: confirmed and pending payment (not no-shows, not cancelled).
    static func count(_ appointments: [Appointment]) -> Int {
        appointments.filter { [.confirmed, .pendingPayment].contains($0.status) }.count
    }

    /// The expected takings of a day: confirmed appointments' prices after discounts.
    static func expected(_ appointments: [Appointment]) -> Int {
        appointments
            .filter { $0.status == .confirmed }
            .reduce(0) { $0 + max(0, ($1.priceAmount ?? 0) - $1.discountAmount) }
    }

    /// «N por cobrar»: confirmed, started and without a sale.
    static func toCharge(_ appointments: [Appointment], now: Date = Date()) -> Int {
        appointments.filter { AppointmentRules.toCharge($0, now: now) }.count
    }

    /// Where the red "now" line goes in a day that is today.
    enum NowLine: Equatable {
        /// Above the first segment: nothing has started yet.
        case top
        /// Right after this appointment, inside its segment. An appointment that
        /// started is always above the line, so the line never lands under the
        /// header of a later segment.
        case after(String)
    }

    static func nowLine(_ segments: [(segment: Segment, items: [Appointment])], now: Date) -> NowLine? {
        let items = segments.flatMap(\.items)
        guard !items.isEmpty else { return nil }
        guard let last = items.last(where: { $0.startsAt <= now }) else { return .top }
        return .after(last.id)
    }
}

/// Picking a professional and a time the way the web's booking form does.
enum BookingRules {
    /// Professionals who offer the service and work at the location.
    static func team(_ professionals: [Professional], serviceId: String?, location: Location?) -> [Professional] {
        professionals.filter { professional in
            guard professional.active, let serviceId, (professional.serviceIds ?? []).contains(serviceId) else { return false }
            if let location, let ids = location.resourceIds { return ids.contains(professional.id) }
            return true
        }
    }

    /// Services offered at the location (all of them when it does not say).
    static func offered(_ services: [Service], location: Location?, includeInactive: Bool = false) -> [Service] {
        services.filter { service in
            guard includeInactive || service.active else { return false }
            if let location, let ids = location.serviceIds { return ids.contains(service.id) }
            return true
        }
    }

    /// Services grouped by category in catalog order; services without one go last.
    static func grouped(_ services: [Service]) -> [(category: String?, services: [Service])] {
        var order: [String?] = []
        var groups: [String?: [Service]] = [:]
        for service in services {
            let key = TextRules.clean(service.category)
            if groups[key] == nil { order.append(key) }
            groups[key, default: []].append(service)
        }
        let named = order.filter { $0 != nil }
        let keys = named + (order.contains(where: { $0 == nil }) ? [nil] : [])
        return keys.map { ($0, groups[$0] ?? []) }
    }

    /// The free times for the chosen professional, or for anyone when nil.
    static func times(_ slots: [Slot], resourceId: String?) -> [Slot] {
        slots.filter { resourceId == nil || $0.resourceIds.contains(resourceId!) }
    }

    /// Free times by part of the day ("Por la mañana / Por la tarde / Por la noche").
    static func timesBySegment(_ slots: [Slot]) -> [(segment: AgendaRules.Segment, slots: [Slot])] {
        let grouped = Dictionary(grouping: slots) { AgendaRules.Segment.of(minuteOfDay: WallTime.minutes($0.time) ?? 0) }
        return AgendaRules.Segment.allCases.compactMap { segment in
            guard let items = grouped[segment], !items.isEmpty else { return nil }
            return (segment, items)
        }
    }

    /// Locations a booking can go to: active ones, plus the appointment's own.
    static func places(_ locations: [Location], keeping current: String? = nil, professionalId: String? = nil) -> [Location] {
        locations.filter { location in
            guard location.active || location.id == current else { return false }
            if let professionalId, let ids = location.resourceIds { return ids.contains(professionalId) }
            return true
        }
    }

    /// A location must be named when more than one is active.
    static func needsLocation(_ locations: [Location]) -> Bool {
        locations.filter(\.active).count > 1
    }

    /// «Crearlo» from a search that found nobody: an e-mail, a phone, or a name.
    struct Draft: Equatable {
        var firstName = ""
        var lastName = ""
        var phone = ""
        var email = ""
    }

    static func draft(from query: String) -> Draft {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.contains("@") { return Draft(email: text) }
        if text.filter(\.isNumber).count >= 6 { return Draft(phone: text.filter(\.isNumber)) }
        let words = text.split(whereSeparator: \.isWhitespace).map(String.init)
        return Draft(firstName: words.first ?? "", lastName: words.dropFirst().joined(separator: " "))
    }
}
