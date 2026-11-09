import Foundation
import Security

/// The signed-in server, its bearer token and who signed in.
struct StoredSession: Codable, Equatable {
    var server: String
    var token: String
    var user: User
}

/// Keeps the session in the Keychain. Never falls back to plain storage on a
/// device: one that cannot keep a credential safely asks for the password again.
enum SessionStore {
    private static let key = "openturnly.session.v1"

    static func parse(_ raw: String?) -> StoredSession? {
        guard let raw, let data = raw.data(using: .utf8),
              let session = try? JSONDecoder().decode(StoredSession.self, from: data),
              !session.token.isEmpty, let url = URL(string: session.server),
              let scheme = url.scheme, ["http", "https"].contains(scheme), url.user == nil, url.password == nil else { return nil }
        return session
    }

    static func load() -> StoredSession? {
        if let stored = parse(try? Keychain.read(key)) { return stored }
        return parse(SimulatorFallback.read())
    }

    static func store(_ session: StoredSession) throws {
        let raw = String(decoding: try JSONEncoder().encode(session), as: UTF8.self)
        do {
            try Keychain.write(key, raw)
        } catch let failure as Keychain.Failure where SimulatorFallback.applies(failure) {
            try SimulatorFallback.write(raw)
        }
    }

    static func clear() throws {
        SimulatorFallback.clear()
        do {
            try Keychain.delete(key)
        } catch let failure as Keychain.Failure where SimulatorFallback.applies(failure) {}
    }
}

/// A simulator build compiled without code signing has no Keychain entitlement
/// (`errSecMissingEntitlement`), so it could never sign in. Only there, the
/// session goes to a file inside the app's container instead. Device builds are
/// always signed and never take this path; the code is not even compiled in.
enum SimulatorFallback {
    static func applies(_ failure: Keychain.Failure) -> Bool {
        #if targetEnvironment(simulator)
        return failure.status == errSecMissingEntitlement
        #else
        return false
        #endif
    }

    private static var url: URL? {
        #if targetEnvironment(simulator)
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        return base?.appendingPathComponent("simulator-session.json")
        #else
        return nil
        #endif
    }

    static func read() -> String? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ raw: String) throws {
        guard var url else { return }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data(raw.utf8).write(to: url, options: [.atomic, .completeFileProtection])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }

    static func clear() {
        guard let url else { return }
        try? FileManager.default.removeItem(at: url)
    }
}

/// Small, non-secret preferences: the business opened last on this phone.
enum Preferences {
    private static let defaults = UserDefaults.standard

    static func lastBusiness(server: String, user: String) -> String? {
        defaults.string(forKey: "lastBusiness|\(server)|\(user)")
    }

    static func setLastBusiness(_ id: String?, server: String, user: String) {
        let key = "lastBusiness|\(server)|\(user)"
        if let id { defaults.set(id, forKey: key) } else { defaults.removeObject(forKey: key) }
    }

    /// The address typed last, to pre-fill the sign-in screen next time.
    static var lastServer: String? {
        get { defaults.string(forKey: "lastServer") }
        set { defaults.set(newValue, forKey: "lastServer") }
    }
}
