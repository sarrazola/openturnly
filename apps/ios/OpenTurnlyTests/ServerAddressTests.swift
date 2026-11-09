import XCTest
@testable import OpenTurnly

final class ServerAddressTests: XCTestCase {
    func testPrependsHTTPSForPublicHosts() {
        XCTAssertEqual(ServerAddress.normalize("turnos.example.com"), "https://turnos.example.com")
        XCTAssertEqual(ServerAddress.normalize("  https://Turnos.Example.com/  "), "https://turnos.example.com")
        XCTAssertEqual(ServerAddress.normalize("turnos.example.com/api"), "https://turnos.example.com")
        XCTAssertEqual(ServerAddress.normalize("example.com/agenda/"), "https://example.com/agenda")
    }

    func testPlainHTTPOnlyOnTheLocalNetwork() {
        XCTAssertEqual(ServerAddress.normalize("localhost:4100"), "http://localhost:4100")
        XCTAssertEqual(ServerAddress.normalize("192.168.1.20:4100"), "http://192.168.1.20:4100")
        XCTAssertEqual(ServerAddress.normalize("10.0.0.4"), "http://10.0.0.4")
        XCTAssertEqual(ServerAddress.normalize("172.20.1.1"), "http://172.20.1.1")
        XCTAssertEqual(ServerAddress.normalize("mac-mini.local:4100"), "http://mac-mini.local:4100")
        XCTAssertEqual(ServerAddress.normalize("http://127.0.0.1:4100"), "http://127.0.0.1:4100")
        XCTAssertEqual(ServerAddress.normalize("172.32.1.1"), "https://172.32.1.1", "outside 172.16–31 is public")
        XCTAssertNil(ServerAddress.normalize("http://turnos.example.com"), "plain http to a public host is refused")
    }

    func testRejectsWhatIsNotAnAddress() {
        XCTAssertNil(ServerAddress.normalize(""))
        XCTAssertNil(ServerAddress.normalize("   "))
        XCTAssertNil(ServerAddress.normalize("not an address"))
        XCTAssertNil(ServerAddress.normalize("ftp://example.com"))
        XCTAssertNil(ServerAddress.normalize("https://user:pass@example.com"))
    }

    func testLocalHosts() {
        XCTAssertTrue(ServerAddress.isLocal("localhost"))
        XCTAssertTrue(ServerAddress.isLocal("127.0.0.1"))
        XCTAssertTrue(ServerAddress.isLocal("10.0.2.2"))
        XCTAssertTrue(ServerAddress.isLocal("172.16.0.1"))
        XCTAssertFalse(ServerAddress.isLocal("8.8.8.8"))
        XCTAssertFalse(ServerAddress.isLocal("example.com"))
    }

    func testForgotPasswordPageLivesOnTheServer() {
        XCTAssertEqual(ServerAddress.forgotPasswordURL("http://localhost:4100")?.absoluteString, "http://localhost:4100/forgot-password")
        XCTAssertEqual(ServerAddress.forgotPasswordURL("https://turnos.example.com/")?.absoluteString, "https://turnos.example.com/forgot-password")
        XCTAssertNil(ServerAddress.forgotPasswordURL("ftp://example.com"))
    }

    func testDisplayHostKeepsThePort() {
        XCTAssertEqual(ServerAddress.displayHost("http://localhost:4100"), "localhost:4100")
        XCTAssertEqual(ServerAddress.displayHost("https://turnos.example.com"), "turnos.example.com")
    }

    func testHostedTemplateAcceptsNameOrMatchingAddress() {
        let template = "https://{workspace}.example.com"
        XCTAssertEqual(HostedServer.resolve("Barberia", template: template), "https://barberia.example.com")
        XCTAssertEqual(HostedServer.resolve("https://barberia.example.com", template: template), "https://barberia.example.com")
        XCTAssertNil(HostedServer.resolve("https://barberia.other.com", template: template))
        XCTAssertNil(HostedServer.resolve("bad name", template: template))
    }
}
