import XCTest
@testable import OpenTurnly

final class MoneyTests: XCTestCase {
    /// Formatters put a no-break space between symbol and amount in some locales.
    private func plain(_ value: String) -> String {
        value.replacingOccurrences(of: "\u{00a0}", with: " ").replacingOccurrences(of: "\u{202f}", with: " ")
    }

    func testDigitsFollowTheSharedTable() {
        XCTAssertEqual(Money.digits("COP"), 0, "COP has no decimals here, whatever ISO says")
        XCTAssertEqual(Money.digits("clp"), 0)
        XCTAssertEqual(Money.digits("JPY"), 0)
        XCTAssertEqual(Money.digits("KWD"), 3)
        XCTAssertEqual(Money.digits("USD"), 2)
        XCTAssertEqual(Money.digits("EUR"), 2)
        XCTAssertEqual(Money.digits("XYZ"), 2)
    }

    func testFormatsInTheCurrencyOwnLocale() {
        XCTAssertEqual(plain(Money.format(35000, currency: "COP")), "$ 35.000")
        XCTAssertEqual(plain(Money.format(2550, currency: "USD")), "$25.50")
        XCTAssertEqual(plain(Money.format(2500, currency: "USD")), "$25", "whole amounts drop the decimals")
        XCTAssertEqual(plain(Money.format(0, currency: "COP")), "$ 0")
    }

    func testMinorUnitConversion() {
        XCTAssertEqual(Money.toMinor(25.5, currency: "USD"), 2550)
        XCTAssertEqual(Money.toMinor(35000, currency: "COP"), 35000)
        XCTAssertEqual(Money.toMinor(1.2345, currency: "KWD"), 1235)
        XCTAssertEqual(Money.fromMinor(2550, currency: "USD"), Decimal(string: "25.5"))
    }

    func testParsesWhatPeopleType() {
        XCTAssertEqual(Money.parse("35.000", currency: "COP"), 35000)
        XCTAssertEqual(Money.parse("35000", currency: "COP"), 35000)
        XCTAssertEqual(Money.parse("$ 35.000", currency: "COP"), 35000)
        XCTAssertEqual(Money.parse("25.50", currency: "USD"), 2550)
        XCTAssertEqual(Money.parse("25,5", currency: "EUR"), 2550)
        XCTAssertEqual(Money.parse("1,250", currency: "USD"), 125000, "three digits after a mark are grouping")
        XCTAssertEqual(Money.parse("1.250,75", currency: "EUR"), 125075)
        XCTAssertEqual(Money.parse("12.", currency: "USD"), 1200)
        XCTAssertNil(Money.parse("", currency: "USD"))
        XCTAssertNil(Money.parse("abc", currency: "COP"))
    }

    func testEditableRoundTrips() {
        for (amount, currency) in [(35000, "COP"), (2550, "USD"), (2500, "USD"), (125075, "EUR")] {
            XCTAssertEqual(Money.parse(Money.editable(amount, currency: currency), currency: currency), amount, "\(amount) \(currency)")
        }
    }
}
