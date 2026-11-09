import Foundation

/// Turns what a person types into the address of an OpenTurnly server.
///
/// `https://` is assumed when no scheme is typed. Plain `http://` is accepted
/// only for addresses on the local network (a development server on the same
/// machine or LAN), which is also all that `NSAllowsLocalNetworking` lets
/// through; anything else must be HTTPS.
enum ServerAddress {
    /// localhost, 127.x, 10.x, 192.168.x, 172.16–31.x, *.local and bare host names.
    static func isLocal(_ host: String) -> Bool {
        let value = host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        if value == "localhost" || value.hasSuffix(".local") || value.hasSuffix(".localhost") || value == "::1" { return true }
        let octets = value.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) }
        if octets.count == 4, octets.allSatisfy({ $0 != nil && (0...255).contains($0!) }) {
            let a = octets[0]!, b = octets[1]!
            return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && (16...31).contains(b))
        }
        return false
    }

    /// The normalized base URL ("https://turnos.example.com"), or nil when it
    /// is not a usable address.
    static func normalize(_ input: String) -> String? {
        var value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        while value.hasSuffix("/") { value.removeLast() }
        guard !value.isEmpty, !value.contains(" ") else { return nil }
        let lower = value.lowercased()
        if !lower.hasPrefix("http://") && !lower.hasPrefix("https://") {
            if lower.contains("://") { return nil }
            let host = String(lower.split(separator: "/").first ?? "").split(separator: ":").first.map(String.init) ?? ""
            value = (isLocal(host) ? "http://" : "https://") + value
        }
        if value.lowercased().hasSuffix("/api") { value.removeLast(4) }
        while value.hasSuffix("/") { value.removeLast() }
        guard let url = URL(string: value), let scheme = url.scheme?.lowercased(), let host = url.host(), !host.isEmpty,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return nil }
        if scheme == "http" && !isLocal(host) { return nil }
        guard scheme == "http" || scheme == "https" else { return nil }
        // Scheme and host in lower case; the path is kept as typed.
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = scheme
        components?.host = host.lowercased()
        return components?.string ?? value
    }

    /// The host a person recognises, for "Connected to …".
    /// The server's own page to recover a password.
    static func forgotPasswordURL(_ server: String) -> URL? {
        let base = server.hasSuffix("/") ? String(server.dropLast()) : server
        guard let url = URL(string: base + "/forgot-password"), let scheme = url.scheme, ["http", "https"].contains(scheme) else { return nil }
        return url
    }

    static func displayHost(_ server: String) -> String {
        guard let url = URL(string: server), let host = url.host() else { return server }
        if let port = url.port { return "\(host):\(port)" }
        return host
    }
}

/// Resolves an account name against a hosted preset ("{workspace}.example.com").
///
/// Only names, or full addresses that belong to the configured service, are
/// accepted, so a typo can never send credentials to an unrelated host.
enum HostedServer {
    private static func validName(_ name: String) -> Bool {
        name.range(of: "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$", options: .regularExpression) != nil
    }

    static func resolve(_ input: String, template: String) -> String? {
        let value = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard template.contains("{workspace}"), !value.isEmpty else { return nil }
        if validName(value) { return template.replacingOccurrences(of: "{workspace}", with: value) }
        guard let address = URL(string: value.contains("://") ? value : "https://\(value)"),
              let sample = URL(string: template.replacingOccurrences(of: "{workspace}", with: "workspace-placeholder")),
              let addressHost = address.host(), let sampleHost = sample.host() else { return nil }
        let parts = sampleHost.components(separatedBy: "workspace-placeholder")
        guard parts.count == 2 else { return nil }
        let prefix = parts[0], suffix = parts[1]
        guard address.scheme == "https", sample.scheme == "https",
              address.user == nil, address.password == nil, address.query == nil, address.fragment == nil,
              address.port == sample.port, normalizedPath(address) == normalizedPath(sample),
              addressHost.hasPrefix(prefix), addressHost.hasSuffix(suffix),
              addressHost.count >= prefix.count + suffix.count else { return nil }
        let start = addressHost.index(addressHost.startIndex, offsetBy: prefix.count)
        let end = addressHost.index(addressHost.endIndex, offsetBy: -suffix.count)
        let name = String(addressHost[start..<end])
        return validName(name) ? template.replacingOccurrences(of: "{workspace}", with: name) : nil
    }

    private static func normalizedPath(_ url: URL) -> String {
        let path = url.path()
        return path.isEmpty ? "/" : path
    }
}
