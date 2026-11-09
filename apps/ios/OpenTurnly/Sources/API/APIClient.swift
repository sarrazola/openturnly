import Foundation

/// What the server answered when a call failed.
///
/// The server always replies `{"error": {"code", "fields"?}}`: `code` is a stable
/// lowercase string and `fields` maps an input path ("customer.phone") to an
/// issue code. Network problems are reported with status 0 and code `network`.
struct APIError: Error, Equatable {
    let status: Int
    let code: String
    let fields: [String: String]

    init(status: Int, code: String, fields: [String: String] = [:]) {
        self.status = status
        self.code = code
        self.fields = fields
    }

    var isUnauthorized: Bool { status == 401 }

    static let network = APIError(status: 0, code: "network")
    static let unknown = APIError(status: 0, code: "unknown_error")
}

/// A JSON value for request bodies.
///
/// Several routes need an explicit `null` ("resourceId": null means "whoever is
/// free"), which a synthesized `Encodable` would leave out, so bodies are built
/// from these instead.
enum JSON: Encodable, Equatable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case null
    case array([JSON])
    case object([String: JSON])

    /// A string, or `null` when there is none.
    static func opt(_ value: String?) -> JSON { value.map(JSON.string) ?? .null }
    /// An integer, or `null` when there is none.
    static func opt(_ value: Int?) -> JSON { value.map(JSON.int) ?? .null }
    /// Trimmed text, or `null` when it is empty.
    static func text(_ value: String) -> JSON {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? .null : .string(trimmed)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .int(let value): try container.encode(value)
        case .double(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .null: try container.encodeNil()
        case .array(let value): try container.encode(value)
        case .object(let value): try container.encode(value)
        }
    }
}

extension JSON: ExpressibleByStringLiteral, ExpressibleByIntegerLiteral, ExpressibleByBooleanLiteral,
    ExpressibleByDictionaryLiteral, ExpressibleByArrayLiteral, ExpressibleByNilLiteral {
    init(stringLiteral value: String) { self = .string(value) }
    init(integerLiteral value: Int) { self = .int(value) }
    init(booleanLiteral value: Bool) { self = .bool(value) }
    init(dictionaryLiteral elements: (String, JSON)...) { self = .object(Dictionary(uniqueKeysWithValues: elements)) }
    init(arrayLiteral elements: JSON...) { self = .array(elements) }
    init(nilLiteral: ()) { self = .null }
}

/// Any JSON the server sends back where the shape is not fixed (error fields).
private struct LooseValue: Decodable {
    let text: String

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(String.self) { text = value }
        else if let value = try? container.decode(Int.self) { text = String(value) }
        else if let value = try? container.decode(Double.self) { text = String(value) }
        else if let value = try? container.decode(Bool.self) { text = String(value) }
        else { text = "" }
    }
}

private struct ErrorEnvelope: Decodable {
    struct Body: Decodable {
        let code: String
        let fields: [String: LooseValue]?
    }
    let error: Body
}

/// Talks to the server the session was opened against.
///
/// The server is not baked in: a person signs in against the address of the
/// install their business runs, so every call takes the base URL from the
/// stored session. Cookies are never sent or kept: the bearer token is the only
/// credential, which also keeps the server's browser-origin check out of the way.
final class APIClient: @unchecked Sendable {
    static let shared = APIClient()

    private let session: URLSession
    private let lock = NSLock()
    private var unauthorizedHandler: (@MainActor @Sendable () -> Void)?

    init(configuration: URLSessionConfiguration = .ephemeral) {
        configuration.timeoutIntervalForRequest = 30
        configuration.httpCookieAcceptPolicy = .never
        configuration.httpShouldSetCookies = false
        configuration.httpCookieStorage = nil
        configuration.waitsForConnectivity = false
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: configuration)
    }

    /// Called when a call made with a session token answers 401: the session ended.
    func setUnauthorizedHandler(_ handler: (@MainActor @Sendable () -> Void)?) {
        lock.lock(); defer { lock.unlock() }
        unauthorizedHandler = handler
    }

    private var currentUnauthorizedHandler: (@MainActor @Sendable () -> Void)? {
        lock.lock(); defer { lock.unlock() }
        return unauthorizedHandler
    }

    // MARK: Coding

    private static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    /// Instants travel as ISO-8601 UTC strings with milliseconds.
    static func parseInstant(_ value: String) -> Date? {
        fractional.date(from: value) ?? plain.date(from: value)
    }

    static func instantString(_ date: Date) -> String {
        fractional.string(from: date)
    }

    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            guard let date = parseInstant(raw) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "Not an instant: \(raw)")
            }
            return date
        }
        return decoder
    }()

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
        return encoder
    }()

    // MARK: URLs

    /// Percent-encodes one path segment (an id or a code).
    static func segment(_ value: String) -> String {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "/:@?#")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    /// `server` + `path` + query. A "+" in a value (a phone number) is encoded,
    /// since a server reads a bare "+" in a query as a space.
    static func url(_ server: String, _ path: String, query: [(String, String?)] = []) -> URL? {
        guard var components = URLComponents(string: server + path) else { return nil }
        let items = query.compactMap { name, value in value.map { URLQueryItem(name: name, value: $0) } }
        if !items.isEmpty {
            components.queryItems = items
            components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        }
        return components.url
    }

    // MARK: Requests

    func request<T: Decodable>(_ method: String, _ url: URL, token: String?, body: JSON? = nil) async throws -> T {
        let data = try await perform(method, url, token: token, body: body)
        do {
            return try APIClient.decoder.decode(T.self, from: data)
        } catch {
            #if DEBUG
            print("Decoding \(T.self) from \(url.path()) failed: \(error)")
            #endif
            throw APIError.unknown
        }
    }

    /// A call whose answer carries nothing the app needs (`204`, `{ok: true}`).
    func send(_ method: String, _ url: URL, token: String?, body: JSON? = nil) async throws {
        _ = try await perform(method, url, token: token, body: body)
    }

    private func perform(_ method: String, _ url: URL, token: String?, body: JSON?) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = try APIClient.encoder.encode(body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw APIError.network
        }
        guard let http = response as? HTTPURLResponse else { throw APIError.network }
        guard (200...299).contains(http.statusCode) else {
            let failure = APIClient.parseError(data, status: http.statusCode)
            if failure.isUnauthorized, token != nil, let handler = currentUnauthorizedHandler {
                await handler()
            }
            throw failure
        }
        return data
    }

    static func parseError(_ data: Data, status: Int) -> APIError {
        guard let envelope = try? decoder.decode(ErrorEnvelope.self, from: data) else {
            return APIError(status: status, code: status >= 500 ? "internal_error" : status == 401 ? "unauthorized" : "unknown_error")
        }
        let fields = (envelope.error.fields ?? [:]).mapValues(\.text)
        return APIError(status: status, code: envelope.error.code, fields: fields)
    }
}
