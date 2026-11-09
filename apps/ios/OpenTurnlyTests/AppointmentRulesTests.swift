import XCTest
@testable import OpenTurnly

final class AppointmentRulesTests: XCTestCase {
    private let before = Fixtures.date("2026-11-10T13:00:00.000Z")
    private let after = Fixtures.date("2026-11-10T14:10:00.000Z")

    func testDisplayStatusSplitsConfirmedByTime() {
        let start = Fixtures.date("2026-11-10T14:00:00.000Z")
        XCTAssertEqual(AppointmentRules.displayStatus(.confirmed, startsAt: start, now: before), .upcoming)
        XCTAssertEqual(AppointmentRules.displayStatus(.confirmed, startsAt: start, now: after), .confirmed)
        XCTAssertEqual(AppointmentRules.displayStatus(.cancelled, startsAt: start, now: before), .cancelled)
        XCTAssertEqual(AppointmentRules.displayStatus(.noShow, startsAt: start, now: after), .noShow)
        XCTAssertEqual(AppointmentRules.displayStatus(.pendingPayment, startsAt: start, now: before), .pendingPayment)
    }

    func testAdminActionsOnAnUpcomingUnpaidAppointment() {
        let actions = AppointmentRules.actions(for: Fixtures.appointment(), isAdmin: true, now: before)
        XCTAssertEqual(actions, [.charge, .deposit, .reschedule, .cancel, .viewCustomer])
    }

    func testNoShowOnlyOnceItStarted() {
        let actions = AppointmentRules.actions(for: Fixtures.appointment(), isAdmin: true, now: after)
        XCTAssertTrue(actions.contains(.markNoShow))
    }

    func testCoveredByDepositsClosesTheSale() {
        let appointment = Fixtures.appointment(price: 35000, deposit: 35000)
        let actions = AppointmentRules.actions(for: appointment, isAdmin: true, now: before)
        XCTAssertTrue(actions.contains(.closeSale))
        XCTAssertFalse(actions.contains(.charge))
        XCTAssertFalse(actions.contains(.deposit))
        XCTAssertEqual(AppointmentRules.paymentState(appointment), .covered(deposit: 35000))
    }

    func testChargedAppointmentOffersTheSaleAndNoCancel() {
        let appointment = Fixtures.appointment(sale: 128)
        let actions = AppointmentRules.actions(for: appointment, isAdmin: true, now: after)
        XCTAssertEqual(actions, [.viewSale, .reschedule, .viewCustomer])
        XCTAssertEqual(AppointmentRules.paymentState(appointment), .paid(number: 128))
    }

    func testProfessionalsCanOnlyMoveTheirAppointments() {
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(), isAdmin: false, now: after), [.reschedule])
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(status: "no_show"), isAdmin: false, now: after), [])
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(status: "cancelled"), isAdmin: false), [])
    }

    func testClosedStatusesOfferNoChanges() {
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(status: "cancelled"), isAdmin: true), [.viewCustomer])
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(status: "pending_payment"), isAdmin: true), [.viewCustomer])
        XCTAssertEqual(AppointmentRules.actions(for: Fixtures.appointment(status: "no_show"), isAdmin: true), [.undoNoShow, .viewCustomer])
    }

    func testOwedSubtractsDiscountAndDeposits() {
        XCTAssertEqual(AppointmentRules.owed(Fixtures.appointment(price: 35000, discount: 5000, deposit: 10000)), 20000)
        XCTAssertEqual(AppointmentRules.owed(Fixtures.appointment(price: nil, deposit: 10000)), 0)
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(price: 35000, deposit: 10000)), .partly(owed: 25000, deposit: 10000))
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment()), .unpaid)
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(status: "cancelled")), .none)
    }

    func testSource() {
        XCTAssertEqual(AppointmentRules.source(Fixtures.appointment(source: "admin")), .admin)
        XCTAssertEqual(AppointmentRules.source(Fixtures.appointment(source: "online")), .online)
        XCTAssertEqual(AppointmentRules.source(Fixtures.appointment(source: "agent")), .agent(nil))
    }

    func testUnknownStatusesDecodeSafely() {
        XCTAssertEqual(Fixtures.appointment(status: "done").status, .unknown)
    }
}

final class AgendaRulesTests: XCTestCase {
    private let clock = BusinessClock(identifier: "America/Bogota")

    func testSegmentsByBusinessWallClock() {
        let list = [
            Fixtures.appointment(id: "evening", startsAt: "2026-11-10T23:30:00.000Z", endsAt: "2026-11-11T00:00:00.000Z"),
            Fixtures.appointment(id: "morning", startsAt: "2026-11-10T14:00:00.000Z"),
            Fixtures.appointment(id: "noon", startsAt: "2026-11-10T17:00:00.000Z", endsAt: "2026-11-10T17:30:00.000Z"),
            Fixtures.appointment(id: "early", startsAt: "2026-11-10T13:30:00.000Z", endsAt: "2026-11-10T14:00:00.000Z"),
        ]
        let segments = AgendaRules.segments(list, clock: clock)
        XCTAssertEqual(segments.map(\.segment), [.morning, .afternoon, .evening])
        XCTAssertEqual(segments[0].items.map(\.id), ["early", "morning"], "in time order")
        XCTAssertEqual(segments[1].items.map(\.id), ["noon"])
        XCTAssertEqual(segments[2].items.map(\.id), ["evening"])
    }

    func testSegmentBoundaries() {
        XCTAssertEqual(AgendaRules.Segment.of(minuteOfDay: 11 * 60 + 59), .morning)
        XCTAssertEqual(AgendaRules.Segment.of(minuteOfDay: 12 * 60), .afternoon)
        XCTAssertEqual(AgendaRules.Segment.of(minuteOfDay: 18 * 60), .evening)
    }

    func testFilters() {
        let list = [
            Fixtures.appointment(id: "juan", resourceId: "p1"),
            Fixtures.appointment(id: "camila", resourceId: "p2", locationId: "l2"),
            Fixtures.appointment(id: "cancelled", status: "cancelled", resourceId: "p1"),
            Fixtures.appointment(id: "nobody", resourceId: nil),
        ]
        XCTAssertEqual(AgendaRules.apply(.init(), to: list).map(\.id), ["juan", "camila", "nobody"])
        XCTAssertEqual(AgendaRules.apply(.init(showCancelled: true), to: list).count, 4)
        XCTAssertEqual(AgendaRules.apply(.init(professionalIds: ["p2"]), to: list).map(\.id), ["camila"])
        XCTAssertEqual(AgendaRules.apply(.init(locationId: "l1"), to: list).map(\.id), ["juan", "nobody"])
    }

    func testByDayUsesTheBusinessDate() {
        let list = [
            Fixtures.appointment(id: "late", startsAt: "2026-11-11T03:00:00.000Z", endsAt: "2026-11-11T03:30:00.000Z"),
            Fixtures.appointment(id: "next", startsAt: "2026-11-11T14:00:00.000Z", endsAt: "2026-11-11T14:30:00.000Z"),
        ]
        let days = AgendaRules.byDay(list, clock: clock)
        XCTAssertEqual(days[LocalDate(year: 2026, month: 11, day: 10)]?.map(\.id), ["late"])
        XCTAssertEqual(days[LocalDate(year: 2026, month: 11, day: 11)]?.map(\.id), ["next"])
    }

    func testExpectedTakingsLeaveOutClosedAppointments() {
        let list = [
            Fixtures.appointment(id: "a", price: 35000, discount: 5000),
            Fixtures.appointment(id: "b", status: "cancelled", price: 20000),
            Fixtures.appointment(id: "c", price: nil),
        ]
        XCTAssertEqual(AgendaRules.expected(list), 30000)
    }

    func testOngoing() {
        let appointment = Fixtures.appointment()
        XCTAssertTrue(AgendaRules.isOngoing(appointment, now: Fixtures.date("2026-11-10T14:20:00.000Z")))
        XCTAssertFalse(AgendaRules.isOngoing(appointment, now: Fixtures.date("2026-11-10T14:45:00.000Z")))
    }
}

final class BookingRulesTests: XCTestCase {
    private func professional(_ id: String, services: [String], active: Bool = true) -> Professional {
        Professional(id: id, name: id, active: active, position: 0, serviceCommission: 0, productCommission: 0,
                     serviceIds: services, locationIds: nil, hasOwnHours: false, photoUrl: nil)
    }

    func testTeamOffersTheServiceAtTheLocation() {
        let team = [professional("p1", services: ["s1"]), professional("p2", services: ["s2"]), professional("p3", services: ["s1"], active: false),
                    professional("p4", services: ["s1"])]
        let location = Location(id: "l1", name: "Centro", address: nil, phone: nil, active: true, position: 0,
                                serviceIds: ["s1", "s2"], resourceIds: ["p1", "p2", "p3"], appointmentCount: 0)
        XCTAssertEqual(BookingRules.team(team, serviceId: "s1", location: location).map(\.id), ["p1"])
        XCTAssertEqual(BookingRules.team(team, serviceId: "s1", location: nil).map(\.id), ["p1", "p4"])
        XCTAssertEqual(BookingRules.team(team, serviceId: nil, location: nil).map(\.id), [])
    }

    func testTimesForWhoeverIsFreeOrOnePerson() {
        let slots = [Slot(time: "09:00", resourceIds: ["p1", "p2"]), Slot(time: "09:30", resourceIds: ["p2"])]
        XCTAssertEqual(BookingRules.times(slots, resourceId: nil).map(\.time), ["09:00", "09:30"])
        XCTAssertEqual(BookingRules.times(slots, resourceId: "p1").map(\.time), ["09:00"])
    }

    func testLocationIsNeededWithSeveralActive() {
        let a = Location(id: "a", name: "A", address: nil, phone: nil, active: true, position: 0, serviceIds: nil, resourceIds: nil, appointmentCount: nil)
        let b = Location(id: "b", name: "B", address: nil, phone: nil, active: false, position: 1, serviceIds: nil, resourceIds: nil, appointmentCount: nil)
        XCTAssertFalse(BookingRules.needsLocation([a, b]))
        var c = b
        c.active = true
        XCTAssertTrue(BookingRules.needsLocation([a, c]))
        XCTAssertEqual(BookingRules.places([a, b], keeping: "b").map(\.id), ["a", "b"])
        XCTAssertEqual(BookingRules.places([a, b]).map(\.id), ["a"])
    }
}
