import Foundation

/// What the agenda shows for an appointment and what can be done with it.
///
/// The server is the authority; these rules mirror the web admin so the app only
/// offers actions the server accepts for that role and state. There is no
/// "completed" status: an appointment is done when a sale charged it.
enum AppointmentRules {
    /// How an appointment's state reads in a chip.
    enum DisplayStatus: Equatable {
        /// Confirmed and still ahead ("Próxima").
        case upcoming
        /// Confirmed and already started ("Confirmada").
        case confirmed
        case cancelled
        case noShow
        case pendingPayment
    }

    static func displayStatus(_ status: AppointmentStatus, startsAt: Date, now: Date = Date()) -> DisplayStatus {
        switch status {
        case .cancelled: return .cancelled
        case .noShow: return .noShow
        case .pendingPayment: return .pendingPayment
        case .confirmed, .unknown: return startsAt > now ? .upcoming : .confirmed
        }
    }

    enum Action: Equatable, Hashable {
        case reschedule
        case cancel
        case markNoShow
        case undoNoShow
        /// "Cobrar": the price is not covered yet.
        case charge
        /// "Cerrar venta": deposits already cover the price, only the sale is left.
        case closeSale
        /// "Abono": leave money in advance.
        case deposit
        case viewSale
        case viewCustomer
    }

    /// What's still owed: price minus the booking discount minus unused deposits.
    static func owed(_ appointment: Appointment) -> Int {
        max(0, (appointment.priceAmount ?? 0) - appointment.discountAmount - appointment.depositAmount)
    }

    /// Deposits that already cover the price.
    static func covered(_ appointment: Appointment) -> Bool {
        appointment.depositAmount > 0 && appointment.priceAmount != nil && owed(appointment) == 0
    }

    static func started(_ appointment: Appointment, now: Date = Date()) -> Bool {
        appointment.startsAt <= now
    }

    /// The actions offered, in the order the detail screen lists them.
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
                if let _ = appointment.sale {
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

    /// The money line under an appointment.
    enum PaymentState: Equatable {
        /// Charged by sale #number.
        case paid(number: Int)
        /// Deposits cover the price; the sale is still to be closed.
        case covered(deposit: Int)
        /// Part was left as a deposit; this much is still owed.
        case partly(owed: Int, deposit: Int)
        case unpaid
        /// Nothing to collect (cancelled, no-show, pending online payment).
        case none
    }

    static func paymentState(_ appointment: Appointment) -> PaymentState {
        if let sale = appointment.sale { return .paid(number: sale.number) }
        guard appointment.status == .confirmed else { return .none }
        if covered(appointment) { return .covered(deposit: appointment.depositAmount) }
        if appointment.depositAmount > 0 && appointment.priceAmount != nil {
            return .partly(owed: owed(appointment), deposit: appointment.depositAmount)
        }
        return .unpaid
    }

    /// The source line: booking page, panel, or an AI agent with the key's name.
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

    /// Happening right now.
    static func isOngoing(_ appointment: Appointment, now: Date = Date()) -> Bool {
        appointment.status == .confirmed && appointment.startsAt <= now && now < appointment.endsAt
    }

    /// The expected takings of a day: confirmed appointments' prices after discounts.
    static func expected(_ appointments: [Appointment]) -> Int {
        appointments
            .filter { $0.status == .confirmed }
            .reduce(0) { $0 + max(0, ($1.priceAmount ?? 0) - $1.discountAmount) }
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

    /// The free times for the chosen professional, or for anyone when nil.
    static func times(_ slots: [Slot], resourceId: String?) -> [Slot] {
        slots.filter { resourceId == nil || $0.resourceIds.contains(resourceId!) }
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
}
