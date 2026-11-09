import XCTest
@testable import OpenTurnly

/// The rules both phone apps must apply identically: status chip, payment state,
/// card badge, actions, day summary, now line, checkout lines, sales summary,
/// formats. The Android app pins the same inputs to the same outputs.
final class ParityRulesTests: XCTestCase {
    private let clock = BusinessClock(identifier: "America/Bogota")
    /// The fixture starts at 09:00 and ends at 09:45 on Tue 10 Nov 2026, Bogotá time.
    private let before = Fixtures.date("2026-11-10T13:00:00.000Z")
    private let during = Fixtures.date("2026-11-10T14:10:00.000Z")
    private let after = Fixtures.date("2026-11-10T15:00:00.000Z")

    override func setUp() {
        super.setUp()
        AppLocale.override = "es"
    }

    override func tearDown() {
        AppLocale.override = nil
        super.tearDown()
    }

    // MARK: §2.2 Status chip

    func testStatusTable() {
        let appointment = Fixtures.appointment()
        XCTAssertEqual(AppointmentRules.displayStatus(appointment, now: before), .upcoming)
        XCTAssertEqual(AppointmentRules.displayStatus(appointment, now: during), .ongoing)
        XCTAssertEqual(AppointmentRules.displayStatus(appointment, now: after), .confirmed)
        XCTAssertEqual(AppointmentRules.displayStatus(Fixtures.appointment(status: "pending_payment"), now: during), .pendingPayment)
        XCTAssertEqual(AppointmentRules.displayStatus(Fixtures.appointment(status: "no_show"), now: after), .noShow)
        XCTAssertEqual(AppointmentRules.displayStatus(Fixtures.appointment(status: "cancelled"), now: before), .cancelled)

        XCTAssertEqual([.upcoming, .ongoing, .confirmed, .pendingPayment, .noShow, .cancelled].map(L.Status.label),
                       ["Próxima", "Ahora", "Confirmada", "Pendiente de pago", "No asistió", "Cancelada"])
        XCTAssertEqual([.upcoming, .ongoing, .confirmed, .pendingPayment, .noShow, .cancelled].map(Tone.of),
                       [.brand, .success, .success, .warning, .orange, .danger])
        AppLocale.override = "en"
        XCTAssertEqual([.upcoming, .ongoing, .confirmed, .pendingPayment, .noShow, .cancelled].map(L.Status.label),
                       ["Upcoming", "Now", "Confirmed", "Awaiting payment", "No-show", "Cancelled"])
    }

    // MARK: §2.3 Payment state and card badge

    func testPaymentStates() {
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(price: 22000)), .unpaid)
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(price: 22000, deposit: 10000)), .partly(owed: 12000, deposit: 10000))
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(price: 22000, deposit: 22000)), .covered(deposit: 22000))
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(price: 22000, sale: 12)), .paid(number: 12))
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(status: "no_show", sale: 12)), .none)
        XCTAssertEqual(AppointmentRules.paymentState(Fixtures.appointment(status: "pending_payment")), .none)
    }

    func testCardBadgeBeforeAndAfterTheStart() {
        let unpaid = Fixtures.appointment(price: 25000, discount: 3000)
        XCTAssertEqual(AppointmentRules.badge(unpaid, isAdmin: true, now: before), .price(22000))
        XCTAssertEqual(AppointmentRules.badge(unpaid, isAdmin: true, now: during), .notCharged)

        let partly = Fixtures.appointment(price: 22000, deposit: 10000)
        XCTAssertEqual(AppointmentRules.badge(partly, isAdmin: true, now: before), .deposited(10000))
        XCTAssertEqual(AppointmentRules.badge(partly, isAdmin: true, now: after), .left(12000))

        let covered = Fixtures.appointment(price: 22000, deposit: 22000)
        XCTAssertEqual(AppointmentRules.badge(covered, isAdmin: true, now: before), .deposited(22000))
        XCTAssertEqual(AppointmentRules.badge(covered, isAdmin: true, now: after), .toClose)

        XCTAssertEqual(AppointmentRules.badge(Fixtures.appointment(sale: 12), isAdmin: true, now: before), .paid)
        XCTAssertEqual(AppointmentRules.badge(Fixtures.appointment(sale: 12), isAdmin: true, now: after), .paid)
        XCTAssertEqual(AppointmentRules.badge(Fixtures.appointment(status: "cancelled"), isAdmin: true, now: after), .none)
        XCTAssertEqual(AppointmentRules.badge(Fixtures.appointment(price: nil), isAdmin: true, now: before), .none)

        // A professional sees the price after the booking discount, whatever was paid.
        XCTAssertEqual(AppointmentRules.badge(unpaid, isAdmin: false, now: after), .price(22000))
        XCTAssertEqual(AppointmentRules.badge(Fixtures.appointment(price: 22000, sale: 3), isAdmin: false, now: after), .price(22000))
    }

    func testMoneyCopy() {
        XCTAssertEqual(L.Checkout.deposited("$ 10.000"), "Abonó $ 10.000")
        XCTAssertEqual(L.Checkout.left("$ 12.000"), "Faltan $ 12.000")
        XCTAssertEqual(L.Checkout.dueLeft("$ 12.000"), "Faltan $ 12.000 por cobrar")
        XCTAssertEqual(L.Checkout.toClose, "Por cerrar")
        XCTAssertEqual(L.Checkout.unpaid, "Sin cobrar")
        XCTAssertEqual(L.Checkout.paid, "Pagada")
        AppLocale.override = "en"
        XCTAssertEqual(L.Checkout.deposited("$10,000"), "Deposit $10,000")
        XCTAssertEqual(L.Checkout.left("$12,000"), "$12,000 left")
        XCTAssertEqual(L.Checkout.toClose, "To close")
        XCTAssertEqual(L.Checkout.unpaid, "Not charged")
    }

    // MARK: §6.3 Actions per state

    private func bar(_ appointment: Appointment, admin: Bool = true, now: Date) -> AppointmentRules.Bar {
        AppointmentRules.bar(AppointmentRules.actions(for: appointment, isAdmin: admin, now: now))
    }

    private func listed(_ appointment: Appointment, now: Date) -> [AppointmentRules.Action] {
        AppointmentRules.listed(AppointmentRules.actions(for: appointment, isAdmin: true, now: now))
    }

    func testDetailBarAndActionsPerState() {
        let unpaid = Fixtures.appointment(price: 22000)
        XCTAssertEqual(bar(unpaid, now: before), .init(secondary: .reschedule, primary: .charge))
        XCTAssertEqual(listed(unpaid, now: before), [.deposit, .cancel])
        XCTAssertEqual(listed(unpaid, now: during), [.deposit, .markNoShow, .cancel])
        XCTAssertEqual(listed(unpaid, now: after), [.deposit, .markNoShow, .cancel])

        let partly = Fixtures.appointment(price: 22000, deposit: 10000)
        XCTAssertEqual(bar(partly, now: after), .init(secondary: .reschedule, primary: .charge))
        XCTAssertEqual(AppointmentRules.chargeAmount(partly), 12000)
        XCTAssertEqual(listed(partly, now: before), [.deposit, .cancel])

        let covered = Fixtures.appointment(price: 22000, deposit: 22000)
        XCTAssertEqual(bar(covered, now: after), .init(secondary: .reschedule, primary: .closeSale))
        XCTAssertEqual(listed(covered, now: before), [.cancel])
        XCTAssertEqual(listed(covered, now: after), [.markNoShow, .cancel])

        let paid = Fixtures.appointment(sale: 12)
        XCTAssertEqual(bar(paid, now: after), .init(secondary: .reschedule, primary: .viewSale))
        XCTAssertEqual(listed(paid, now: after), [])

        XCTAssertEqual(bar(Fixtures.appointment(status: "no_show"), now: after), .init(secondary: nil, primary: .undoNoShow))
        XCTAssertTrue(bar(Fixtures.appointment(status: "cancelled"), now: after).isEmpty)
        XCTAssertTrue(bar(Fixtures.appointment(status: "pending_payment"), now: before).isEmpty)

        // Professionals: «Editar cita» full width while confirmed, nothing otherwise.
        XCTAssertEqual(bar(unpaid, admin: false, now: after), .init(secondary: .reschedule, primary: nil))
        XCTAssertTrue(bar(Fixtures.appointment(status: "no_show"), admin: false, now: after).isEmpty)

        XCTAssertNil(AppointmentRules.chargeAmount(Fixtures.appointment(price: nil)))
    }

    func testLongPressMenuOrder() {
        let menu = AppointmentRules.menu(AppointmentRules.actions(for: Fixtures.appointment(), isAdmin: true, now: after))
        XCTAssertEqual(menu.before, [.charge, .reschedule])
        XCTAssertEqual(menu.after, [.markNoShow, .cancel])
        let paid = AppointmentRules.menu(AppointmentRules.actions(for: Fixtures.appointment(sale: 4), isAdmin: true, now: after))
        XCTAssertEqual(paid.before, [.viewSale, .reschedule])
        XCTAssertEqual(paid.after, [])
    }

    func testActionCopy() {
        XCTAssertEqual(L.Checkout.chargeAmount("$ 22.000"), "Cobrar $ 22.000")
        XCTAssertEqual(L.Checkout.deposit, "Registrar abono")
        XCTAssertEqual(L.Appointment.markNoShow, "Marcar «No asistió»")
        XCTAssertEqual(L.Appointment.noShowTitle, "¿Marcar como «No asistió»?")
        XCTAssertEqual(L.Common.goBack, "Volver")
        XCTAssertEqual(L.Appointment.sourceAdmin, "Tu equipo")
        XCTAssertEqual(L.Appointment.discountCode("BIENVENIDA"), "Descuento · BIENVENIDA")
        AppLocale.override = "en"
        XCTAssertEqual(L.Appointment.markNoShow, "Mark as no-show")
        XCTAssertEqual(L.Appointment.noShowMarked, "Marked as no-show")
        XCTAssertEqual(L.Checkout.deposit, "Record deposit")
    }

    func testServerLineCopy() {
        XCTAssertEqual(L.SignIn.connectedTo("localhost:4100"), "Conectado a localhost:4100")
        XCTAssertEqual(L.SignIn.changeServer, "Cambiar")
        AppLocale.override = "en"
        XCTAssertEqual(L.SignIn.connectedTo("localhost:4100"), "Connected to localhost:4100")
        XCTAssertEqual(L.SignIn.changeServer, "Change")
    }

    // MARK: §5 Agenda

    func testDaySummary() {
        let list = [
            Fixtures.appointment(id: "a", price: 25000, discount: 3000),
            Fixtures.appointment(id: "b", status: "pending_payment", price: 30000),
            Fixtures.appointment(id: "c", status: "no_show", price: 20000),
            Fixtures.appointment(id: "d", startsAt: "2026-11-10T20:00:00.000Z", endsAt: "2026-11-10T20:30:00.000Z", price: 10000, sale: 5),
            Fixtures.appointment(id: "e", startsAt: "2026-11-10T13:30:00.000Z", endsAt: "2026-11-10T14:00:00.000Z", price: 15000, deposit: 5000),
        ]
        XCTAssertEqual(AgendaRules.count(list), 4, "confirmed and pending payment")
        XCTAssertEqual(AgendaRules.expected(list), 22000 + 10000 + 15000, "confirmed only, after discounts")
        XCTAssertEqual(AgendaRules.toCharge(list, now: during), 2, "started, confirmed, no sale")
        XCTAssertEqual(L.Agenda.toCharge(2), "2 por cobrar")
        XCTAssertEqual(L.Agenda.expected("$ 87.000"), "$ 87.000 esperado")
    }

    func testWeekDotsFollowTheFilters() {
        let list = [
            Fixtures.appointment(id: "mon", startsAt: "2026-11-09T14:00:00.000Z", endsAt: "2026-11-09T14:30:00.000Z", resourceId: "p1"),
            Fixtures.appointment(id: "tue", resourceId: "p2"),
            Fixtures.appointment(id: "wed", status: "cancelled", startsAt: "2026-11-11T14:00:00.000Z", endsAt: "2026-11-11T14:30:00.000Z"),
            Fixtures.appointment(id: "thu", status: "no_show", startsAt: "2026-11-12T14:00:00.000Z", endsAt: "2026-11-12T14:30:00.000Z"),
        ]
        let days = AgendaRules.busyDays(list, filter: .init(showCancelled: true), clock: clock)
        XCTAssertEqual(days, [LocalDate(year: 2026, month: 11, day: 9), LocalDate(year: 2026, month: 11, day: 10)])
        XCTAssertEqual(AgendaRules.busyDays(list, filter: .init(professionalIds: ["p2"]), clock: clock), [LocalDate(year: 2026, month: 11, day: 10)])
    }

    func testFilteredEmptyOnlyWhenTheFiltersHideSomething() {
        let cancelledOnly = [Fixtures.appointment(status: "cancelled")]
        XCTAssertFalse(AgendaRules.hiddenByFilters(.init(), all: cancelledOnly), "hidden cancelled ones are not 'filtered'")
        XCTAssertFalse(AgendaRules.hiddenByFilters(.init(locationId: "l9"), all: cancelledOnly))
        XCTAssertTrue(AgendaRules.hiddenByFilters(.init(locationId: "l9"), all: [Fixtures.appointment()]))
        XCTAssertFalse(AgendaRules.hiddenByFilters(.init(locationId: "l1"), all: [Fixtures.appointment()]))
        let filter = AgendaRules.Filter(professionalIds: ["p1"], locationId: "l1", showCancelled: true)
        XCTAssertTrue(filter.active)
        XCTAssertTrue(AgendaRules.Filter(showCancelled: true).active)
        XCTAssertFalse(AgendaRules.Filter(showCancelled: true).narrowed)
    }

    func testNowLineStaysInsideTheSegmentOfWhatStarted() {
        let list = [
            Fixtures.appointment(id: "early", startsAt: "2026-11-10T14:00:00.000Z", endsAt: "2026-11-10T15:30:00.000Z"),
            Fixtures.appointment(id: "late-morning", startsAt: "2026-11-10T16:00:00.000Z", endsAt: "2026-11-10T16:30:00.000Z"),
            Fixtures.appointment(id: "noon", startsAt: "2026-11-10T18:00:00.000Z", endsAt: "2026-11-10T18:30:00.000Z"),
        ]
        let segments = AgendaRules.segments(list, clock: clock)
        XCTAssertEqual(AgendaRules.nowLine(segments, now: Fixtures.date("2026-11-10T12:00:00.000Z")), .top)
        XCTAssertEqual(AgendaRules.nowLine(segments, now: Fixtures.date("2026-11-10T14:20:00.000Z")), .after("early"), "an ongoing card stays above")
        // 11:22 there: the next card is the first of the afternoon, so the line closes the morning.
        XCTAssertEqual(AgendaRules.nowLine(segments, now: Fixtures.date("2026-11-10T16:22:00.000Z")), .after("late-morning"))
        XCTAssertEqual(AgendaRules.nowLine(segments, now: Fixtures.date("2026-11-10T23:00:00.000Z")), .after("noon"), "end of the list")
        XCTAssertNil(AgendaRules.nowLine([], now: during))
    }

    func testSegmentNames() {
        XCTAssertEqual(AgendaRules.Segment.allCases.map(L.Agenda.segment), ["Por la mañana", "Por la tarde", "Por la noche"])
        AppLocale.override = "en"
        XCTAssertEqual(AgendaRules.Segment.allCases.map(L.Agenda.segment), ["Morning", "Afternoon", "Evening"])
    }

    // MARK: §7 Booking

    func testCreateFromASearchThatFoundNobody() {
        XCTAssertEqual(BookingRules.draft(from: "laura@correo.com"), .init(email: "laura@correo.com"))
        XCTAssertEqual(BookingRules.draft(from: "300 123 4567"), .init(phone: "3001234567"))
        XCTAssertEqual(BookingRules.draft(from: "Laura Gómez Ruiz"), .init(firstName: "Laura", lastName: "Gómez Ruiz"))
    }

    func testServicesByCategoryAndTimesByPartOfDay() {
        func service(_ id: String, _ category: String?) -> Service {
            Service(id: id, name: id, description: nil, category: category, durationMin: 30, priceAmount: 1000,
                    paymentMode: nil, depositAmount: nil, active: true, position: 0, locationIds: nil)
        }
        let groups = BookingRules.grouped([service("a", "Cortes"), service("b", nil), service("c", "Barba"), service("d", "Cortes")])
        XCTAssertEqual(groups.map(\.category), ["Cortes", "Barba", nil])
        XCTAssertEqual(groups[0].services.map(\.id), ["a", "d"])

        let slots = ["09:00", "11:30", "12:00", "17:30", "18:00"].map { Slot(time: $0, resourceIds: ["p1"]) }
        let parts = BookingRules.timesBySegment(slots)
        XCTAssertEqual(parts.map(\.segment), [.morning, .afternoon, .evening])
        XCTAssertEqual(parts.map { $0.slots.map(\.time) }, [["09:00", "11:30"], ["12:00", "17:30"], ["18:00"]])
    }

    // MARK: §10 Checkout

    func testCheckoutSummaryLines() {
        let lines = [
            CheckoutRules.Line(kind: .service, appointmentId: "a1", name: "Corte", unitPrice: 25000, discountAmount: 3000),
            CheckoutRules.Line(kind: .product, productId: "p1", name: "Cera", quantity: 2, unitPrice: 9000),
        ]
        let summary = CheckoutRules.summary(lines: lines, discount: 5000, tips: 4000, deposits: 10000)
        XCTAssertEqual(summary.subtotal, 40000)
        XCTAssertEqual(summary.discount, 5000)
        XCTAssertEqual(summary.total, 35000, "the sale total: tips are apart")
        XCTAssertEqual(summary.tips, 4000)
        XCTAssertEqual(summary.deposits, 10000)
        XCTAssertEqual(summary.toCollect, 29000, "total + tips − deposits")
        XCTAssertEqual(CheckoutRules.summary(lines: lines, discount: 90000, tips: 0, deposits: 0).discount, 40000, "capped at the subtotal")
    }

    func testChargeButtonShowsWhatIsLeftToCollect() {
        // The bug this fixes: $ 40.000 shown while $ 20.000 was left after deposits.
        let lines = [CheckoutRules.Line(kind: .service, appointmentId: "a1", name: "Corte", unitPrice: 40000)]
        let summary = CheckoutRules.summary(lines: lines, discount: 0, tips: 0, deposits: 20000)
        XCTAssertEqual(CheckoutRules.submit(toCollect: summary.toCollect, forAppointment: true), .charge(20000))
        XCTAssertEqual(CheckoutRules.submit(toCollect: 0, forAppointment: true), .closeSale)
        XCTAssertEqual(CheckoutRules.submit(toCollect: 0, forAppointment: false), .charge(0))
    }

    func testBalanceSplitAndGiftCards() {
        XCTAssertEqual(CheckoutRules.balance(0), .balanced)
        XCTAssertEqual(CheckoutRules.balance(-5000), .missing(5000))
        XCTAssertEqual(CheckoutRules.balance(2000), .over(2000))
        var payments = [CheckoutRules.Payment(method: "cash", amount: 10000)]
        payments = CheckoutRules.split(payments, difference: -5000)
        XCTAssertEqual(payments.map(\.method), ["cash", "card"])
        XCTAssertEqual(payments.last?.amount, 5000)
        payments = CheckoutRules.split(CheckoutRules.split(payments, difference: 0), difference: 0)
        XCTAssertEqual(payments.count, 4)
        XCTAssertEqual(CheckoutRules.split(payments, difference: -1).count, 4, "four ways at most")
        XCTAssertEqual(CheckoutRules.giftCardAmount(balance: 50000, due: 22000), 22000)
        XCTAssertEqual(CheckoutRules.giftCardAmount(balance: 8000, due: 22000), 8000)
        XCTAssertEqual(L.Checkout.split, "Dividir el pago")
        XCTAssertEqual(L.Checkout.toCollect, "Por cobrar ahora")
    }

    // MARK: §11 Sales

    private func sale(_ number: Int, total: Int, tip: Int, status: String = "paid", isInternal: Bool = false,
                      payments: [(String, Int)], items: [String] = ["Corte"]) -> Sale {
        Fixtures.decode([
            "id": "s\(number)", "number": number, "status": status, "internal": isInternal, "locationName": NSNull(),
            "subtotal": total, "discountAmount": 0, "total": total, "tipAmount": tip, "note": NSNull(),
            "createdAt": "2026-11-09T15:20:00.000Z", "voidedAt": NSNull(), "soldByName": NSNull(), "customer": NSNull(),
            "items": items.enumerated().map { ["id": "i\($0.offset)", "kind": "service", "appointmentId": NSNull(), "name": $0.element,
                                               "resourceName": NSNull(), "quantity": 1, "unitPrice": total, "discountAmount": 0, "total": total] },
            "tips": [], "payments": payments.enumerated().map { ["id": "p\($0.offset)", "method": $0.element.0, "amount": $0.element.1] },
        ] as [String: Any])
    }

    func testSalesSummaryAddsUpWhatCameIn() {
        let sales = [
            sale(1, total: 30000, tip: 5000, payments: [("cash", 20000), ("card", 15000)]),
            sale(2, total: 10000, tip: 0, isInternal: true, payments: [("cash", 10000)]),
            sale(3, total: 50000, tip: 0, status: "void", payments: [("card", 50000)]),
        ]
        let summary = SalesRules.summary(sales)
        XCTAssertEqual(summary.collected, 45000, "tips and internal sales included, void left out")
        XCTAssertEqual(summary.count, 2)
        XCTAssertEqual(summary.average, 20000)
        XCTAssertEqual(summary.tips, 5000)
        XCTAssertEqual(summary.byMethod, [.init(method: "cash", amount: 30000), .init(method: "card", amount: 15000)])
        XCTAssertEqual(summary.byMethod.reduce(0) { $0 + $1.amount }, summary.collected)
        XCTAssertEqual(SalesRules.items(sale(4, total: 1, tip: 0, payments: [], items: ["A", "B", "C", "D"])), "A, B +2")
    }

    func testSalesPeriods() {
        let today = LocalDate(year: 2026, month: 11, day: 9)
        XCTAssertEqual(SalesRules.label(.day, anchor: today, today: today), "Hoy")
        XCTAssertEqual(SalesRules.label(.day, anchor: today.adding(days: 1), today: today), "Mar 10 nov")
        XCTAssertEqual(SalesRules.label(.week, anchor: LocalDate(year: 2026, month: 11, day: 4), today: today), "2 nov – 8 nov")
        XCTAssertEqual(SalesRules.label(.month, anchor: today, today: today), "Noviembre 2026")
        XCTAssertEqual(SalesRules.step(.month, anchor: LocalDate(year: 2026, month: 1, day: 31), by: 1), LocalDate(year: 2026, month: 2, day: 1))
        XCTAssertTrue(SalesRules.includes(.week, anchor: LocalDate(year: 2026, month: 11, day: 15), day: today))
        XCTAssertFalse(SalesRules.includes(.day, anchor: today.adding(days: -1), day: today))
    }

    // MARK: §13–14 Customers

    func testCustomerSections() {
        let names = ["Ángela Ruiz", "andrés Gil", "Bruno Paz", "4 Seasons", "Zoe Díaz", "Ñandú Pérez"]
        let sections = CustomerRules.sections(names) { $0 }
        XCTAssertEqual(sections.map(\.key), ["A", "B", "N", "Z", "#"])
        XCTAssertEqual(sections[0].items, ["andrés Gil", "Ángela Ruiz"])
    }

    func testCustomerStats() {
        func visit(_ id: String, _ start: String, _ status: String = "confirmed") -> CustomerAppointment {
            Fixtures.decode(["id": id, "code": "X", "startsAt": start, "endsAt": start, "status": status, "serviceName": "Corte",
                             "resourceName": NSNull(), "priceAmount": 1000, "source": "admin"] as [String: Any])
        }
        let now = Fixtures.date("2026-11-10T12:00:00.000Z")
        let list = [
            visit("old", "2026-10-01T14:00:00.000Z"),
            visit("last", "2026-11-05T14:00:00.000Z"),
            visit("missed", "2026-11-06T14:00:00.000Z", "no_show"),
            visit("next", "2026-11-12T14:00:00.000Z"),
            visit("later", "2026-11-20T14:00:00.000Z"),
            visit("gone", "2026-11-13T14:00:00.000Z", "cancelled"),
        ]
        let stats = CustomerRules.stats(list, now: now)
        XCTAssertEqual(stats.visits, 2, "confirmed and started only")
        XCTAssertEqual(stats.lastVisit, Fixtures.date("2026-11-05T14:00:00.000Z"))
        XCTAssertEqual(stats.nextVisit, Fixtures.date("2026-11-12T14:00:00.000Z"))
        XCTAssertEqual(CustomerRules.upcoming(list, now: now).map(\.id), ["next", "later"])
        XCTAssertEqual(CustomerRules.history(list, now: now).map(\.id), ["gone", "missed", "last", "old"], "newest first")
        XCTAssertEqual(L.Customers.visitCount(3), "3 visitas")
        XCTAssertEqual(L.Customers.next("12 nov"), "Próxima 12 nov")
    }

    // MARK: §19 Reports

    func testReportChanges() {
        XCTAssertNil(ReportRules.change(10, 0))
        XCTAssertEqual(ReportRules.change(112, 100) ?? 0, 0.12, accuracy: 0.0001)
        XCTAssertEqual(Fmt.change(0.12), "+12 %")
        XCTAssertEqual(Fmt.change(-0.05), "\u{2212}5 %")
        XCTAssertEqual(ReportRules.points(0.62, 0.57), 5)
        XCTAssertNil(ReportRules.points(nil, 0.5))
        AppLocale.override = "en"
        XCTAssertEqual(Fmt.change(0.12), "+12%")
        let year = ReportRules.Preset.year.range(today: LocalDate(year: 2026, month: 11, day: 9))
        XCTAssertEqual(year.0, LocalDate(year: 2026, month: 1, day: 1))
        XCTAssertEqual(year.1, LocalDate(year: 2026, month: 12, day: 31))
    }

    // MARK: §2.5 Formats

    func testDateFormats() {
        let monday = LocalDate(year: 2026, month: 11, day: 9)
        XCTAssertEqual(Fmt.dayLong(monday, thisYear: 2026), "Lunes, 9 de noviembre")
        XCTAssertEqual(Fmt.dayLong(monday, thisYear: 2025), "Lunes, 9 de noviembre de 2026")
        XCTAssertEqual(Fmt.dayMedium(monday), "Lun 9 nov")
        XCTAssertEqual(Fmt.dayWithYear(monday), "9 nov 2026")
        XCTAssertEqual(Fmt.dayShort(monday), "9 nov")
        XCTAssertEqual(Fmt.monthYear(monday), "Noviembre 2026")
        XCTAssertEqual(Fmt.weekdayShort(monday.adding(days: 2)), "Mié")
        XCTAssertEqual(Fmt.relativeName(monday, today: monday.adding(days: -1)), "Mañana")
        XCTAssertEqual(Fmt.relativeName(monday, today: monday.adding(days: 1)), "Ayer")
        XCTAssertNil(Fmt.relativeName(monday, today: monday.adding(days: 2)))
        XCTAssertEqual(Fmt.percent(0.45), "45 %")
        AppLocale.override = "en"
        XCTAssertEqual(Fmt.dayLong(monday, thisYear: 2026), "Monday, November 9")
        XCTAssertEqual(Fmt.dayLong(monday, thisYear: 2027), "Monday, November 9, 2026")
        XCTAssertEqual(Fmt.dayMedium(monday), "Mon, Nov 9")
        XCTAssertEqual(Fmt.dayWithYear(monday), "Nov 9, 2026")
        XCTAssertEqual(Fmt.monthYear(monday), "November 2026")
        XCTAssertEqual(Fmt.weekdayShort(monday), "Mon")
        XCTAssertEqual(Fmt.percent(0.45), "45%")
    }

    // MARK: §21.5 Errors

    func testOneErrorTable() {
        XCTAssertEqual(ErrorText.text("forbidden_origin"), "Esto solo se puede hacer desde el panel web.")
        XCTAssertEqual(ErrorText.text("customer_exists"), "Ya tienes un cliente con ese celular o email.")
        XCTAssertEqual(ErrorText.text("not_found"), "No encontramos lo que buscabas.")
        XCTAssertEqual(ErrorText.text("deposit_amount"), "Un abono se usa completo: no se puede partir.")
        XCTAssertEqual(ErrorText.text("stock_unchanged"), "El conteo es igual al stock actual: no hay nada que ajustar.")
        XCTAssertEqual(ErrorText.text("validation_failed"), "Revisa los datos marcados.")
        AppLocale.override = "en"
        XCTAssertEqual(L.Errors.generic, "Something went wrong. Try again.")
        XCTAssertEqual(ErrorText.text("too_many_attempts"), "Too many attempts with a wrong password. Wait 15 minutes.")
    }

    // MARK: Loader

    func testClockLoaderTimings() {
        XCTAssertEqual(ClockMark.minuteTurn, 1.6)
        XCTAssertEqual(ClockMark.hourTurn, 19.2, accuracy: 0.0001, "twelve times slower")
        XCTAssertEqual(ClockMark.dotOpacity(0), 1, accuracy: 0.0001)
        XCTAssertEqual(ClockMark.dotOpacity(0.8), 0.55, accuracy: 0.0001)
        XCTAssertEqual(ClockMark.dotOpacity(1.6), 1, accuracy: 0.0001)
        XCTAssertEqual(Double(ClockMark.pulse(0)), 1, accuracy: 0.0001)
        XCTAssertEqual(Double(ClockMark.pulse(0.6)), 1.04, accuracy: 0.0001)
    }
}
