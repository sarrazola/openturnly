import XCTest
@testable import OpenTurnly

final class APITests: XCTestCase {
    override func setUp() {
        super.setUp()
        AppLocale.override = "es"
    }

    override func tearDown() {
        AppLocale.override = nil
        super.tearDown()
    }

    func testDecodesTheAgendaListToleratingExtraAndMissingFields() {
        let appointment = Fixtures.appointment(price: nil, sale: 12)
        XCTAssertEqual(appointment.code, "K7Q4XM")
        XCTAssertNil(appointment.priceAmount)
        XCTAssertEqual(appointment.sale?.number, 12)
        XCTAssertEqual(appointment.customer.fullName, "Ana Pérez")
        XCTAssertEqual(appointment.answers?.first?.value, "Sí")
    }

    func testDecodesABusinessWithAccess() {
        let business: Business = Fixtures.decode([
            "id": "b1", "shortId": "k7m2qx9a4p", "slug": "barberia", "name": "Barbería", "timezone": "America/Bogota",
            "currency": "COP", "locale": "es", "access": ["role": "professional", "resourceId": "p1"], "bookingForm": ["phone": "required"],
        ])
        XCTAssertFalse(business.isAdmin)
        XCTAssertEqual(business.professionalId, "p1")
        XCTAssertNil(business.phone)
        let owner: Business = Fixtures.decode([
            "id": "b1", "slug": "barberia", "name": "Barbería", "timezone": "America/Bogota", "currency": "COP",
            "access": ["role": "owner", "resourceId": NSNull()],
        ])
        XCTAssertTrue(owner.isAdmin)
        XCTAssertNil(owner.professionalId)
    }

    func testParsesServerErrors() {
        let data = Data(#"{"error":{"code":"validation_failed","fields":{"customer.phone":"invalid_phone"}}}"#.utf8)
        let error = APIClient.parseError(data, status: 400)
        XCTAssertEqual(error.code, "validation_failed")
        XCTAssertEqual(error.fields["customer.phone"], "invalid_phone")
        XCTAssertEqual(APIClient.parseError(Data("<html>".utf8), status: 502).code, "internal_error")
        XCTAssertTrue(APIClient.parseError(Data(), status: 401).isUnauthorized)
    }

    func testErrorMessagesPreferTheField() {
        let phone = APIError(status: 400, code: "validation_failed", fields: ["customer.phone": "invalid_phone"])
        XCTAssertEqual(ErrorText.message(phone), "Escribe un número de celular válido.")
        XCTAssertEqual(ErrorText.field(phone, "phone"), "Escribe un número de celular válido.")
        XCTAssertEqual(ErrorText.message(APIError(status: 409, code: "slot_taken")),
                       "Alguien acaba de tomar ese horario. Elige otro.")
        XCTAssertEqual(ErrorText.message(APIError(status: 400, code: "payments_mismatch")), "Lo pagado no cuadra con el total.")
        XCTAssertEqual(ErrorText.message(APIError(status: 418, code: "brand_new_code")), "Algo salió mal. Inténtalo de nuevo.")
        XCTAssertEqual(ErrorText.message(APIError.network), "No hay conexión con el servidor. Revisa tu internet.")
    }

    func testAlreadyBookedNamesTheOtherAppointment() {
        let error = APIError(status: 409, code: "already_booked", fields: ["code": "K7Q4XM", "startsAt": "2026-11-10T14:00:00.000Z"])
        ErrorText.timeZone = TimeZone(identifier: "America/Bogota")!
        XCTAssertEqual(ErrorText.message(error), "Ese cliente ya tiene una cita el Mar 10 nov a las 09:00 (código K7Q4XM).")
    }

    func testEnglishCopy() {
        AppLocale.override = "en"
        XCTAssertEqual(L.Status.label(.noShow), "No-show")
        XCTAssertEqual(ErrorText.message(APIError(status: 400, code: "payments_mismatch")), "What was paid doesn't match the total.")
        AppLocale.override = "es"
        XCTAssertEqual(L.Status.label(.noShow), "No asistió")
        XCTAssertEqual(L.Agenda.count(1), "1 cita")
        XCTAssertEqual(L.Agenda.count(3), "3 citas")
    }

    func testURLsEncodePlusInQueries() {
        let url = APIClient.url("http://localhost:4100", "/api/businesses/b1/customers", query: [("q", "+57 300"), ("skip", nil)])
        XCTAssertEqual(url?.absoluteString, "http://localhost:4100/api/businesses/b1/customers?q=%2B57%20300")
        XCTAssertEqual(APIClient.segment("a/b"), "a%2Fb")
    }

    func testBodiesCarryExplicitNulls() throws {
        let body: JSON = ["resourceId": .opt(nil as String?), "date": "2026-11-10", "notes": .text("  ")]
        let text = String(decoding: try APIClient.encoder.encode(body), as: UTF8.self)
        XCTAssertTrue(text.contains(#""resourceId":null"#))
        XCTAssertTrue(text.contains(#""notes":null"#))
        XCTAssertTrue(text.contains(#""date":"2026-11-10""#))
    }

    func testStoredSessionsMustBeWellFormed() {
        XCTAssertNil(SessionStore.parse(nil))
        XCTAssertNil(SessionStore.parse(#"{"server":"ftp://x","token":"t","user":{"id":"u","name":"A","email":"a@b.c","role":"owner"}}"#))
        XCTAssertNil(SessionStore.parse(#"{"server":"https://x.com","token":"","user":{"id":"u","name":"A","email":"a@b.c","role":"owner"}}"#))
        XCTAssertEqual(SessionStore.parse(#"{"server":"http://localhost:4100","token":"t","user":{"id":"u","name":"A","email":"a@b.c","role":"owner"}}"#)?.token, "t")
    }
}
