import Foundation
@testable import OpenTurnly

/// Builds models from JSON shaped like the server's, extra fields included.
enum Fixtures {
    private static func value(_ any: Any?) -> Any { any ?? NSNull() }

    static func appointment(
        id: String = "a1", status: String = "confirmed", startsAt: String = "2026-11-10T14:00:00.000Z",
        endsAt: String = "2026-11-10T14:45:00.000Z", resourceId: String? = "p1", locationId: String? = "l1",
        price: Int? = 35000, discount: Int = 0, deposit: Int = 0, sale: Int? = nil, source: String = "online"
    ) -> Appointment {
        let object: [String: Any] = [
            "id": id, "code": "K7Q4XM", "startsAt": startsAt, "endsAt": endsAt, "status": status,
            "locationId": value(locationId), "locationName": "Sede Centro",
            "serviceId": "s1", "serviceName": "Corte clásico",
            "resourceId": value(resourceId), "resourceName": value(resourceId == nil ? nil : "Juan"),
            "priceAmount": value(price), "discountAmount": discount, "giftCode": NSNull(),
            "answers": [["id": "q-1", "label": "¿Primera vez?", "value": "Sí"]],
            "source": source, "sourceLabel": NSNull(), "createdAt": "2026-11-08T22:10:00.000Z", "cancelledAt": NSNull(),
            "customer": ["id": "c1", "shortId": "x4k9pm2qa7", "firstName": "Ana", "lastName": "Pérez", "email": NSNull(), "phone": "+573001234567"],
            "sale": value(sale.map { ["id": "sale1", "number": $0] as [String: Any] }),
            "depositAmount": deposit,
            "somethingNew": "ignored",
        ]
        return decode(object)
    }

    static func decode<T: Decodable>(_ object: Any, as type: T.Type = T.self) -> T {
        let data = try! JSONSerialization.data(withJSONObject: object)
        return try! APIClient.decoder.decode(T.self, from: data)
    }

    static func date(_ iso: String) -> Date { APIClient.parseInstant(iso)! }
}
