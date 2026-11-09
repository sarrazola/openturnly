import XCTest
@testable import OpenTurnly

final class TextAndPhoneTests: XCTestCase {
    func testSearchIgnoresAccentsAndCase() {
        XCTAssertTrue(TextRules.matches("andres", ["Andrés Ruiz"]))
        XCTAssertTrue(TextRules.matches("RUIZ and", ["Andrés Ruiz", nil]))
        XCTAssertTrue(TextRules.matches("300 123", ["+573001234567"]), "digits match inside a stored phone")
        XCTAssertFalse(TextRules.matches("laura", ["Andrés Ruiz", "andres@example.com"]))
        XCTAssertTrue(TextRules.matches("  ", ["anything"]))
    }

    func testInitials() {
        XCTAssertEqual(TextRules.initials("Ana María Pérez"), "AP")
        XCTAssertEqual(TextRules.initials("camila"), "C")
        XCTAssertEqual(TextRules.initials(""), "?")
    }

    func testProfessionalColoursFollowTheTeamOrder() {
        let ids = ["a", "b", "c"]
        XCTAssertEqual(TextRules.professionalColor("a", in: ids), "#0f766e")
        XCTAssertEqual(TextRules.professionalColor("b", in: ids), "#0284c7")
        XCTAssertEqual(TextRules.professionalColor(nil, in: ids), "#0f766e")
        XCTAssertEqual(TextRules.avatarColor("Ana"), TextRules.avatarColor("Ana"))
    }

    func testEmail() {
        XCTAssertTrue(TextRules.isEmail("ana@example.com"))
        XCTAssertFalse(TextRules.isEmail("ana@"))
        XCTAssertFalse(TextRules.isEmail("ana example.com"))
    }

    func testPhoneSplitComposeAndFormat() {
        XCTAssertEqual(Phone.split("+573001234567")?.iso, "CO")
        XCTAssertEqual(Phone.split("+573001234567")?.national, "3001234567")
        XCTAssertEqual(Phone.split("+12025550123")?.iso, "US", "shared code 1 prefers the US")
        XCTAssertEqual(Phone.compose(iso: "CO", national: "300 123 4567"), "+57 3001234567")
        XCTAssertNil(Phone.compose(iso: "CO", national: ""))
        XCTAssertEqual(Phone.format("+573001234567"), "+57 300 123 4567")
        XCTAssertEqual(Phone.format(nil), "")
    }

    func testContactLinks() {
        XCTAssertEqual(Phone.whatsAppURL("+57 300 123 4567")?.absoluteString, "https://wa.me/573001234567")
        XCTAssertNil(Phone.whatsAppURL("3001234567"), "WhatsApp needs the country code")
        XCTAssertEqual(Phone.callURL("+573001234567")?.absoluteString, "tel:+573001234567")
        XCTAssertEqual(Phone.mailURL("ana@example.com")?.absoluteString, "mailto:ana@example.com")
        XCTAssertNil(Phone.mailURL("nope"))
    }

    func testDefaultCountryComesFromTheBusiness() {
        XCTAssertEqual(Phone.defaultCountry(business: "MX"), "MX")
        XCTAssertEqual(Phone.defaultCountry(business: "co"), "CO")
    }
}
