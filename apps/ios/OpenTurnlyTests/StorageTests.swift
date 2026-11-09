import Security
import XCTest
@testable import OpenTurnly

final class StorageTests: XCTestCase {
    /// A signed build keeps the session in the Keychain. An unsigned simulator
    /// build has no Keychain entitlement; there the test is skipped.
    func testKeychainRoundTrip() throws {
        let key = "openturnly.tests.\(UUID().uuidString)"
        do {
            try Keychain.write(key, "first")
        } catch let failure as Keychain.Failure where failure.status == errSecMissingEntitlement {
            throw XCTSkip("Unsigned build: no Keychain entitlement")
        }
        XCTAssertEqual(try Keychain.read(key), "first")
        try Keychain.write(key, "second")
        XCTAssertEqual(try Keychain.read(key), "second")
        try Keychain.delete(key)
        XCTAssertNil(try Keychain.read(key))
    }

    func testSimulatorFallbackOnlyForMissingEntitlement() {
        #if targetEnvironment(simulator)
        XCTAssertTrue(SimulatorFallback.applies(Keychain.Failure(status: errSecMissingEntitlement)))
        #endif
        XCTAssertFalse(SimulatorFallback.applies(Keychain.Failure(status: errSecDuplicateItem)))
    }

    func testSessionRoundTripsThroughStorage() throws {
        let session = StoredSession(server: "http://localhost:4100", token: "token-for-tests",
                                    user: User(id: "u1", name: "Ana", email: "ana@example.com", role: "member"))
        let previous = SessionStore.load()
        try SessionStore.store(session)
        XCTAssertEqual(SessionStore.load(), session)
        try SessionStore.clear()
        XCTAssertNil(SessionStore.load())
        if let previous { try SessionStore.store(previous) }
    }
}
