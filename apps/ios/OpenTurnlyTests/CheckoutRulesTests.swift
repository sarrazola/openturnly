import XCTest
@testable import OpenTurnly

final class CheckoutRulesTests: XCTestCase {
    func testLineTotalsAndSubtotal() {
        let lines = [
            CheckoutRules.Line(kind: .service, serviceId: "s1", appointmentId: "a1", name: "Corte", unitPrice: 35000, discountAmount: 5000),
            CheckoutRules.Line(kind: .product, productId: "p1", name: "Cera", quantity: 2, unitPrice: 12000),
        ]
        XCTAssertEqual(lines[0].total, 30000)
        XCTAssertEqual(lines[1].total, 24000)
        XCTAssertEqual(CheckoutRules.subtotal(lines), 54000)
    }

    func testDiscountCodesAreComputedHere() {
        XCTAssertEqual(CheckoutRules.codeDiscount(percent: 10, amount: nil, subtotal: 35000), 3500)
        XCTAssertEqual(CheckoutRules.codeDiscount(percent: 15, amount: nil, subtotal: 2550), 383, "rounded like the web")
        XCTAssertEqual(CheckoutRules.codeDiscount(percent: nil, amount: 50000, subtotal: 35000), 35000, "capped at the subtotal")
        XCTAssertEqual(CheckoutRules.codeDiscount(percent: nil, amount: 5000, subtotal: 35000), 5000)
    }

    func testPaymentsMustMatchTotalPlusTips() {
        let total = CheckoutRules.total(subtotal: 47000, discount: 0)
        let due = CheckoutRules.due(total: total, tips: 5000)
        XCTAssertEqual(due, 52000)
        let remaining = CheckoutRules.remaining(due: due, deposits: 10000)
        XCTAssertEqual(remaining, 42000)
        let cash = CheckoutRules.Payment(method: "cash", amount: 42000)
        XCTAssertEqual(CheckoutRules.difference(due: due, deposits: 10000, payments: [cash]), 0)
        XCTAssertEqual(CheckoutRules.difference(due: due, deposits: 10000, payments: [CheckoutRules.Payment(method: "cash", amount: 40000)]), -2000)
        XCTAssertEqual(CheckoutRules.difference(due: due, deposits: 0, payments: [cash, CheckoutRules.Payment(method: "card", amount: 15000)]), 5000)
    }

    func testDepositsCoveringEverythingLeaveNothingToPay() {
        XCTAssertEqual(CheckoutRules.remaining(due: 35000, deposits: 35000), 0)
        XCTAssertEqual(CheckoutRules.remaining(due: 35000, deposits: 40000), 0)
        XCTAssertEqual(CheckoutRules.total(subtotal: 1000, discount: 5000), 0)
    }

    func testZeroPaymentsAreDropped() {
        let payments = [CheckoutRules.Payment(method: "cash", amount: 0), CheckoutRules.Payment(method: "card", amount: 100)]
        XCTAssertEqual(CheckoutRules.payable(payments).map(\.method), ["card"])
    }
}
