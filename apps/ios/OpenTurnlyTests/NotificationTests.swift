import XCTest
@testable import OpenTurnly

final class NotificationTests: XCTestCase {
    override func setUp() {
        super.setUp()
        AppLocale.override = "es"
    }

    override func tearDown() {
        AppLocale.override = nil
        super.tearDown()
    }

    // MARK: Push payload

    func testReadsTheServersPayload() {
        let userInfo: [AnyHashable: Any] = [
            "aps": ["alert": ["title": "Nueva reserva", "body": "Ana Pérez · Corte"], "badge": 3, "sound": "default"],
            "kind": "appointment_booked",
            "notificationId": "n1",
            "businessId": "b1",
            "appointmentId": "a1",
            "startsAt": "2026-11-14T14:00:00.000Z",
        ]
        let data = NotificationData(userInfo)
        XCTAssertEqual(data.kind, "appointment_booked")
        XCTAssertEqual(data.notificationId, "n1")
        XCTAssertEqual(data.businessId, "b1")
        XCTAssertEqual(data.appointmentId, "a1")
        XCTAssertEqual(data.startsAt, Fixtures.date("2026-11-14T14:00:00.000Z"))
    }

    func testReadsEveryKindTheSameWay() {
        for kind in ["appointment_booked", "appointment_rescheduled", "appointment_cancelled", "something_new"] {
            let data = NotificationData([
                "aps": ["alert": ["title": "Aviso", "body": "Texto"]],
                "kind": kind, "notificationId": "n1", "businessId": "b1", "appointmentId": "a1",
                "startsAt": "2026-11-14T14:00:00.000Z",
            ])
            XCTAssertEqual(data.kind, kind)
            XCTAssertEqual(data.appointmentId, "a1", "\(kind) still leads to its appointment")
            XCTAssertNotNil(data.startsAt)
        }
    }

    func testKindsPickTheirSymbol() {
        XCTAssertEqual(NotificationKind("appointment_booked"), .booked)
        XCTAssertEqual(NotificationKind("appointment_rescheduled"), .rescheduled)
        XCTAssertEqual(NotificationKind("appointment_cancelled"), .cancelled)
        XCTAssertEqual(NotificationKind("something_new"), .other)
        XCTAssertEqual(NotificationKind(nil), .other)
        XCTAssertEqual(NotificationKind.booked.symbol, "calendar.badge.plus")
        XCTAssertEqual(NotificationKind.rescheduled.symbol, "arrow.triangle.2.circlepath")
        XCTAssertEqual(NotificationKind.cancelled.symbol, "xmark.circle")
        XCTAssertEqual(NotificationKind.other.symbol, "bell")
    }

    func testToleratesMissingAndOddValues() {
        let data = NotificationData([
            "aps": ["alert": "Hola"],
            "businessId": "  b1 ",
            "appointmentId": "",
            "notificationId": 42,
            "startsAt": "2026-11-14T14:00:00Z",
            7: "not a string key",
        ])
        XCTAssertEqual(data.businessId, "b1", "whitespace is trimmed")
        XCTAssertNil(data.appointmentId, "an empty value is no value")
        XCTAssertEqual(data.notificationId, "42", "a number is read as text")
        XCTAssertEqual(data.startsAt, Fixtures.date("2026-11-14T14:00:00.000Z"), "milliseconds are optional")
        XCTAssertNil(data.kind)

        XCTAssertEqual(NotificationData([:]), NotificationData())
        XCTAssertNil(NotificationData(["startsAt": "mañana"]).startsAt)
        XCTAssertNil(NotificationData(["businessId": ["nested": "b1"]]).businessId)
    }

    // MARK: Bell

    func testBadgeText() {
        XCTAssertNil(NotificationRules.badgeText(0))
        XCTAssertNil(NotificationRules.badgeText(-1))
        XCTAssertEqual(NotificationRules.badgeText(1), "1")
        XCTAssertEqual(NotificationRules.badgeText(9), "9")
        XCTAssertEqual(NotificationRules.badgeText(10), "9+")
        XCTAssertEqual(NotificationRules.badgeText(250), "9+")
    }

    func testBellLabel() {
        XCTAssertEqual(L.Notifications.bell(0), "Avisos")
        XCTAssertEqual(L.Notifications.bell(1), "Avisos, 1 sin leer")
        XCTAssertEqual(L.Notifications.bell(12), "Avisos, 12 sin leer")
        AppLocale.override = "en"
        XCTAssertEqual(L.Notifications.bell(2), "Notifications, 2 unread")
    }

    func testRelativeTime() {
        let clock = BusinessClock(timeZone: TimeZone(identifier: "America/Bogota")!)
        let now = Fixtures.date("2026-11-14T15:00:00.000Z")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-20), now: now, clock: clock), "Ahora")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-300), now: now, clock: clock), "Hace 5 min")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-7200), now: now, clock: clock), "Hace 2 h")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-86_400), now: now, clock: clock), "Ayer")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-3 * 86_400), now: now, clock: clock), "Hace 3 días")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-10 * 86_400), now: now, clock: clock), "4 nov")
        AppLocale.override = "en"
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-20), now: now, clock: clock), "Now")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-300), now: now, clock: clock), "5 min ago")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-7200), now: now, clock: clock), "2 h ago")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-86_400), now: now, clock: clock), "Yesterday")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-3 * 86_400), now: now, clock: clock), "3 days ago")
        XCTAssertEqual(Fmt.ago(now.addingTimeInterval(-10 * 86_400), now: now, clock: clock), "Nov 4")
        AppLocale.override = "es"
    }

    // MARK: Registration

    func testTokensTravelAsHex() {
        XCTAssertEqual(NotificationRules.hex(Data([0x00, 0x0f, 0xab, 0xff])), "000fabff")
    }

    func testGatewayFollowsTheSignature() {
        // No push in the build: nothing to report, whatever else is true.
        XCTAssertNil(NotificationRules.serverEnvironment(brand: "", signed: "development", simulator: false))
        XCTAssertNil(NotificationRules.serverEnvironment(brand: "  ", signed: nil, simulator: true))
        // A simulator carries no profile and goes by the brand.
        XCTAssertEqual(NotificationRules.serverEnvironment(brand: "development", signed: nil, simulator: true), "sandbox")
        XCTAssertEqual(NotificationRules.serverEnvironment(brand: "production", signed: nil, simulator: true), "production")
        // The profile a phone build was signed with wins.
        XCTAssertEqual(NotificationRules.serverEnvironment(brand: "development", signed: "development", simulator: false), "sandbox")
        XCTAssertEqual(NotificationRules.serverEnvironment(brand: "development", signed: "production", simulator: false), "production")
        // An App Store or TestFlight install has no profile: always production.
        XCTAssertEqual(NotificationRules.serverEnvironment(brand: "development", signed: nil, simulator: false), "production")
    }

    func testReadsTheProfilesEntitlement() {
        let plist = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0"><dict><key>Entitlements</key><dict>
        <key>aps-environment</key><string>development</string>
        </dict></dict></plist>
        """
        // A profile is a signed envelope around that plist.
        var profile = Data([0x30, 0x82, 0x01, 0x02, 0xff])
        profile.append(Data(plist.utf8))
        profile.append(Data([0x00, 0xa0, 0x82]))
        XCTAssertEqual(NotificationRules.signedEnvironment(profile: profile), "development")
        XCTAssertNil(NotificationRules.signedEnvironment(profile: nil))
        XCTAssertNil(NotificationRules.signedEnvironment(profile: Data("no plist here".utf8)))
    }

    // MARK: API shapes

    func testDecodesTheNotificationsList() {
        let page: NotificationsPage = Fixtures.decode([
            "notifications": [
                [
                    "id": "n2", "kind": "appointment_booked", "title": "Nueva reserva",
                    "body": "Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan",
                    "businessId": "b1", "appointmentId": "a1", "appointmentStartsAt": "2026-11-14T14:00:00.000Z",
                    "createdAt": "2026-11-09T15:00:00.000Z", "readAt": NSNull(),
                ],
                [
                    "id": "n1", "kind": "something_new", "title": "Aviso", "body": "Texto",
                    "businessId": "b1", "appointmentId": NSNull(), "appointmentStartsAt": NSNull(),
                    "createdAt": "2026-11-08T15:00:00.000Z", "readAt": "2026-11-08T16:00:00.000Z",
                ],
            ],
            "unread": 1, "unreadTotal": 4,
        ])
        XCTAssertEqual(page.notifications.map(\.id), ["n2", "n1"])
        XCTAssertEqual(page.notifications[0].appointmentStartsAt, Fixtures.date("2026-11-14T14:00:00.000Z"))
        XCTAssertNil(page.notifications[0].readAt)
        XCTAssertNil(page.notifications[1].appointmentId)
        XCTAssertNotNil(page.notifications[1].readAt)
        XCTAssertEqual(page.unread, 1)
        XCTAssertEqual(page.unreadTotal, 4)
    }

    func testSettingsWithoutPushMeanOff() {
        XCTAssertTrue((Fixtures.decode(["push": ["enabled": true]]) as MobileSettings).pushEnabled)
        XCTAssertFalse((Fixtures.decode(["push": ["enabled": false]]) as MobileSettings).pushEnabled)
        XCTAssertFalse((Fixtures.decode([String: Any]()) as MobileSettings).pushEnabled)
    }
}
