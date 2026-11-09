import XCTest
@testable import OpenTurnly

final class BusinessTimeTests: XCTestCase {
    private let bogota = BusinessClock(identifier: "America/Bogota")
    private let madrid = BusinessClock(identifier: "Europe/Madrid")

    func testLocalDateParsingAndArithmetic() {
        let date = LocalDate("2026-11-09")
        XCTAssertEqual(date, LocalDate(year: 2026, month: 11, day: 9))
        XCTAssertEqual(date?.string, "2026-11-09")
        XCTAssertEqual(date?.adding(days: 23).string, "2026-12-02")
        XCTAssertEqual(LocalDate("2026-12-31")?.adding(days: 1).string, "2027-01-01")
        XCTAssertEqual(LocalDate("2026-11-09")?.weekday, 1, "a Monday; 0 is Sunday like the server")
        XCTAssertEqual(LocalDate("2026-11-08")?.weekday, 0)
        XCTAssertEqual(LocalDate("2026-11-12")?.startOfWeek(firstWeekday: 1).string, "2026-11-09")
        XCTAssertEqual(LocalDate("2026-11-08")?.startOfWeek(firstWeekday: 1).string, "2026-11-02")
        XCTAssertEqual(LocalDate("2026-02-10")?.lastOfMonth.string, "2026-02-28")
        XCTAssertNil(LocalDate("2026-13-01"))
        XCTAssertNil(LocalDate("hoy"))
    }

    func testDayStartsInTheBusinessZoneNotThePhone() {
        let day = LocalDate(year: 2026, month: 11, day: 10)
        XCTAssertEqual(APIClient.instantString(bogota.startOfDay(day)), "2026-11-10T05:00:00.000Z")
        let range = bogota.range(day, through: day)
        XCTAssertEqual(range.to.timeIntervalSince(range.from), 24 * 3600)
    }

    func testDaylightSavingDays() {
        // Madrid moves its clocks on 2026-03-29: that day lasts 23 hours.
        let day = LocalDate(year: 2026, month: 3, day: 29)
        let range = madrid.range(day, through: day)
        XCTAssertEqual(range.to.timeIntervalSince(range.from), 23 * 3600)
        XCTAssertEqual(APIClient.instantString(range.from), "2026-03-28T23:00:00.000Z")
    }

    func testInstantsShownOnTheBusinessClock() throws {
        let instant = try XCTUnwrap(APIClient.parseInstant("2026-11-10T14:00:00.000Z"))
        XCTAssertEqual(bogota.time(of: instant), "09:00")
        XCTAssertEqual(bogota.localDate(of: instant).string, "2026-11-10")
        XCTAssertEqual(madrid.time(of: instant), "15:00")
        let late = try XCTUnwrap(APIClient.parseInstant("2026-11-11T03:30:00Z"))
        XCTAssertEqual(bogota.localDate(of: late).string, "2026-11-10", "22:30 the evening before in Bogotá")
        XCTAssertEqual(bogota.minuteOfDay(late), 22 * 60 + 30)
        XCTAssertEqual(bogota.instant(LocalDate(year: 2026, month: 11, day: 10), time: "09:00"), instant)
    }

    func testWallTimeHelpers() {
        XCTAssertEqual(WallTime.minutes("09:30"), 570)
        XCTAssertNil(WallTime.minutes("9h"))
        XCTAssertEqual(WallTime.string(570), "09:30")
        XCTAssertEqual(WallTime.duration(45), "45 min")
        XCTAssertEqual(WallTime.duration(60), "1 h")
        XCTAssertEqual(WallTime.duration(90), "1 h 30 min")
    }
}
