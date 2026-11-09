import Foundation

/// One function per server route the app uses.
///
/// Every call is made against the signed-in server with the session's bearer
/// token. Paths never end in a slash: the server matches a professional's
/// allowed routes exactly, so a trailing slash would be refused.
struct OpenTurnlyAPI: Sendable {
    let server: String
    let token: String
    var client: APIClient = .shared

    // MARK: Sign-in (no session yet)

    static func health(_ server: String) async throws {
        guard let url = APIClient.url(server, "/api/health") else { throw APIError(status: 0, code: "invalid_server") }
        try await APIClient.shared.send("GET", url, token: nil)
    }

    static func signIn(_ server: String, email: String, password: String) async throws -> SignInResponse {
        guard let url = APIClient.url(server, "/api/mobile/sign-in") else { throw APIError(status: 0, code: "invalid_server") }
        return try await APIClient.shared.request("POST", url, token: nil, body: ["email": .string(email), "password": .string(password)])
    }

    /// A hosted directory: one e-mail and password, every account that opens.
    static func hostedSignIn(_ directory: URL, email: String, password: String) async throws -> [HostedAccount] {
        let response: HostedDirectoryResponse = try await APIClient.shared.request(
            "POST", directory, token: nil, body: ["email": .string(email), "password": .string(password)]
        )
        return response.accounts
    }

    // MARK: Plumbing

    private func url(_ path: String, _ query: [(String, String?)] = []) throws -> URL {
        guard let url = APIClient.url(server, path, query: query) else { throw APIError(status: 0, code: "invalid_server") }
        return url
    }

    private func get<T: Decodable>(_ path: String, _ query: [(String, String?)] = []) async throws -> T {
        try await client.request("GET", url(path, query), token: token)
    }

    private func call<T: Decodable>(_ method: String, _ path: String, _ body: JSON? = nil) async throws -> T {
        try await client.request(method, url(path), token: token, body: body)
    }

    private func send(_ method: String, _ path: String, _ body: JSON? = nil) async throws {
        try await client.send(method, url(path), token: token, body: body)
    }

    private func b(_ business: String) -> String { "/api/businesses/\(APIClient.segment(business))" }
    private func s(_ value: String) -> String { APIClient.segment(value) }
    private func instant(_ date: Date) -> String { APIClient.instantString(date) }

    // MARK: Session and account

    func signOut() async throws { try await send("POST", "/api/mobile/sign-out", [:]) }

    func me() async throws -> User { try await (get("/api/me") as UserEnvelope).user }

    func updateMe(name: String, email: String, currentPassword: String?) async throws -> User {
        var body: [String: JSON] = ["name": .string(name), "email": .string(email)]
        if let currentPassword, !currentPassword.isEmpty { body["currentPassword"] = .string(currentPassword) }
        return try await (call("PATCH", "/api/me", .object(body)) as UserEnvelope).user
    }

    func changePassword(current: String, new: String) async throws {
        try await send("POST", "/api/me/password", ["currentPassword": .string(current), "newPassword": .string(new)])
    }

    // MARK: Businesses

    func businesses() async throws -> [Business] { try await (get("/api/businesses") as BusinessesEnvelope).businesses }

    func business(_ id: String) async throws -> Business { try await (get(b(id)) as BusinessEnvelope).business }

    // MARK: Catalog

    func locations(_ business: String) async throws -> [Location] {
        try await (get(b(business) + "/locations") as LocationsEnvelope).locations
    }

    func services(_ business: String) async throws -> [Service] {
        try await (get(b(business) + "/services") as ServicesEnvelope).services
    }

    func professionals(_ business: String) async throws -> [Professional] {
        try await (get(b(business) + "/professionals") as ProfessionalsEnvelope).professionals
    }

    // MARK: Appointments

    func appointments(_ business: String, from: Date, to: Date, includeCancelled: Bool = true) async throws -> [Appointment] {
        let query: [(String, String?)] = [
            ("from", instant(from)), ("to", instant(to)), ("includeCancelled", includeCancelled ? "true" : nil),
        ]
        return try await (get(b(business) + "/appointments", query) as AppointmentsEnvelope).appointments
    }

    func slots(_ business: String, serviceId: String, date: LocalDate, locationId: String?) async throws -> [Slot] {
        let query: [(String, String?)] = [("serviceId", serviceId), ("date", date.string), ("locationId", locationId)]
        return try await (get(b(business) + "/appointments/slots", query) as SlotsEnvelope).slots
    }

    /// Free times to move an appointment: its own time does not count as busy.
    func rescheduleSlots(_ business: String, appointmentId: String, serviceId: String, date: LocalDate, locationId: String?) async throws -> [Slot] {
        let query: [(String, String?)] = [("serviceId", serviceId), ("date", date.string), ("locationId", locationId)]
        return try await (get(b(business) + "/appointments/\(s(appointmentId))/slots", query) as SlotsEnvelope).slots
    }

    func createAppointment(_ business: String, _ body: [String: JSON]) async throws -> AppointmentRow {
        try await (call("POST", b(business) + "/appointments", .object(body)) as AppointmentRowEnvelope).appointment
    }

    func reschedule(_ business: String, _ id: String, _ body: [String: JSON]) async throws -> AppointmentRow {
        try await (call("PATCH", b(business) + "/appointments/\(s(id))", .object(body)) as AppointmentRowEnvelope).appointment
    }

    func cancel(_ business: String, _ id: String) async throws -> AppointmentStatus {
        try await (call("POST", b(business) + "/appointments/\(s(id))/cancel", [:]) as AppointmentStatusEnvelope).appointment.status
    }

    func markNoShow(_ business: String, _ id: String) async throws -> AppointmentStatus {
        try await (call("POST", b(business) + "/appointments/\(s(id))/no-show", [:]) as AppointmentStatusEnvelope).appointment.status
    }

    func undoNoShow(_ business: String, _ id: String) async throws -> AppointmentStatus {
        try await (call("DELETE", b(business) + "/appointments/\(s(id))/no-show") as AppointmentStatusEnvelope).appointment.status
    }

    // MARK: Customers

    /// Without a query: every customer (admins only). With one: the first 20 matches.
    func customers(_ business: String, query: String? = nil) async throws -> [CustomerSummary] {
        let trimmed = query?.trimmingCharacters(in: .whitespacesAndNewlines)
        return try await (get(b(business) + "/customers", [("q", trimmed?.isEmpty == false ? trimmed : nil)]) as CustomersEnvelope).customers
    }

    func customer(_ business: String, _ id: String) async throws -> CustomerDetail {
        try await get(b(business) + "/customers/\(s(id))")
    }

    func createCustomer(_ business: String, _ body: [String: JSON]) async throws -> Customer {
        try await (call("POST", b(business) + "/customers", .object(body)) as CustomerEnvelope).customer
    }

    func updateCustomer(_ business: String, _ id: String, _ changes: [String: JSON]) async throws -> Customer {
        try await (call("PATCH", b(business) + "/customers/\(s(id))", .object(changes)) as CustomerEnvelope).customer
    }

    // MARK: Sales and deposits

    func sales(_ business: String, from: Date, to: Date) async throws -> [Sale] {
        try await (get(b(business) + "/sales", [("from", instant(from)), ("to", instant(to))]) as SalesEnvelope).sales
    }

    func sale(_ business: String, _ id: String) async throws -> Sale {
        try await (get(b(business) + "/sales/\(s(id))") as SaleEnvelope).sale
    }

    func createSale(_ business: String, _ body: [String: JSON]) async throws -> Sale {
        try await (call("POST", b(business) + "/sales", .object(body)) as SaleEnvelope).sale
    }

    func voidSale(_ business: String, _ id: String) async throws -> Sale {
        try await (call("POST", b(business) + "/sales/\(s(id))/void", [:]) as SaleEnvelope).sale
    }

    func deposits(_ business: String, customerId: String? = nil, from: Date? = nil, to: Date? = nil) async throws -> [Deposit] {
        let query: [(String, String?)] = [("customerId", customerId), ("from", from.map(instant)), ("to", to.map(instant))]
        return try await (get(b(business) + "/deposits", query) as DepositsEnvelope).deposits
    }

    func createDeposit(_ business: String, _ body: [String: JSON]) async throws {
        try await send("POST", b(business) + "/deposits", .object(body))
    }

    // MARK: Gift cards

    func lookupGiftCard(_ business: String, code: String) async throws -> GiftCard {
        try await (get(b(business) + "/gift-cards/lookup", [("code", code.trimmingCharacters(in: .whitespaces).uppercased())]) as GiftCardEnvelope).giftCard
    }

    // MARK: Products

    func products(_ business: String) async throws -> [Product] {
        try await (get(b(business) + "/products") as ProductsEnvelope).products
    }

    // MARK: Reports

    func reportSummary(_ business: String, from: LocalDate, to: LocalDate) async throws -> ReportSummary {
        try await get(b(business) + "/reports/summary", [("from", from.string), ("to", to.string)])
    }

    func reportCommissions(_ business: String, from: LocalDate, to: LocalDate) async throws -> [ReportCommission] {
        try await (get(b(business) + "/reports/commissions", [("from", from.string), ("to", to.string)]) as CommissionsResponse).professionals
    }
}
