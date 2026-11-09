import Foundation

// Shapes the server returns. Decoding ignores fields the app does not use, and
// anything not guaranteed by the server is optional, so a newer server that adds
// or drops a column does not break an older app.

// MARK: People and access

struct User: Codable, Hashable {
    let id: String
    var name: String
    var email: String
    /// `owner` for the person who installed the server, `member` for everyone else.
    let role: String?

    var isInstallOwner: Bool { role == "owner" }
}

struct SignInResponse: Decodable {
    let token: String
    let user: User
}

struct UserEnvelope: Decodable { let user: User }

/// One account a hosted directory opens with an e-mail and password.
struct HostedAccount: Decodable, Identifiable, Hashable {
    let server: String
    let name: String?
    let token: String
    let user: User
    var id: String { server + "|" + user.id }
}

struct HostedDirectoryResponse: Decodable { let accounts: [HostedAccount] }

// MARK: Businesses

struct BusinessAccess: Codable, Hashable {
    /// `owner`, `admin` or `professional`.
    let role: String
    /// The professional profile a professional is linked to.
    let resourceId: String?
}

struct Business: Codable, Identifiable, Hashable {
    let id: String
    let shortId: String?
    let slug: String
    var name: String
    var description: String?
    var phone: String?
    var email: String?
    var address: String?
    let timezone: String
    let currency: String
    let locale: String?
    let slotIntervalMin: Int?
    let minNoticeMin: Int?
    let maxAdvanceDays: Int?
    let closedOnHolidays: Bool?
    let brandColor: String?
    let sample: Bool?
    let country: String?
    let logoUrl: String?
    let access: BusinessAccess
}

struct BusinessesEnvelope: Decodable { let businesses: [Business] }
struct BusinessEnvelope: Decodable { let business: Business }

extension Business {
    /// The install owner and business admins can do everything inside a business;
    /// a professional only works with their own agenda.
    var isAdmin: Bool { access.role != "professional" }
    var professionalId: String? { isAdmin ? nil : access.resourceId }
}

// MARK: Catalog

struct Location: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var address: String?
    var phone: String?
    var active: Bool
    let position: Int?
    let serviceIds: [String]?
    let resourceIds: [String]?
    let appointmentCount: Int?
}

struct LocationsEnvelope: Decodable { let locations: [Location] }

struct Service: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var description: String?
    var category: String?
    var durationMin: Int
    /// Null in older data means "price on request".
    var priceAmount: Int?
    var paymentMode: String?
    var depositAmount: Int?
    var active: Bool
    let position: Int?
    let locationIds: [String]?
}

struct ServicesEnvelope: Decodable { let services: [Service] }

struct Professional: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var active: Bool
    let position: Int?
    var serviceCommission: Double?
    var productCommission: Double?
    var serviceIds: [String]?
    var locationIds: [String]?
    let hasOwnHours: Bool?
    let photoUrl: String?
}

struct ProfessionalsEnvelope: Decodable { let professionals: [Professional] }

// MARK: Appointments

enum AppointmentStatus: String, Codable, Hashable {
    case confirmed
    case cancelled
    case noShow = "no_show"
    case pendingPayment = "pending_payment"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = AppointmentStatus(rawValue: raw) ?? .unknown
    }
}

struct AppointmentCustomer: Codable, Hashable {
    let id: String
    let shortId: String?
    let firstName: String
    let lastName: String
    let email: String?
    let phone: String?

    var fullName: String { [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ") }
}

struct BookingAnswer: Codable, Hashable {
    let id: String?
    let label: String
    let value: String
}

struct SaleReference: Codable, Hashable {
    let id: String
    let number: Int
}

struct Appointment: Decodable, Identifiable, Hashable {
    let id: String
    let code: String
    let startsAt: Date
    let endsAt: Date
    let status: AppointmentStatus
    let locationId: String?
    let locationName: String?
    let serviceId: String?
    let serviceName: String
    let resourceId: String?
    let resourceName: String?
    let priceAmount: Int?
    let discountAmount: Int
    let giftCode: String?
    let answers: [BookingAnswer]?
    let source: String?
    let sourceLabel: String?
    let createdAt: Date?
    let cancelledAt: Date?
    let customer: AppointmentCustomer
    let sale: SaleReference?
    let depositAmount: Int
}

struct AppointmentsEnvelope: Decodable { let appointments: [Appointment] }

/// A free time for a service on one date, with the professionals free then.
struct Slot: Decodable, Hashable {
    let time: String
    let resourceIds: [String]
}

struct SlotsEnvelope: Decodable { let slots: [Slot] }

/// The bare row create and reschedule answer with.
struct AppointmentRow: Decodable {
    let id: String
    let code: String
    let startsAt: Date
}

struct AppointmentRowEnvelope: Decodable { let appointment: AppointmentRow }

struct AppointmentStatusEnvelope: Decodable {
    struct Body: Decodable {
        let id: String
        let status: AppointmentStatus
    }
    let appointment: Body
}

// MARK: Customers

struct CustomerSummary: Decodable, Identifiable, Hashable {
    let id: String
    let shortId: String?
    let firstName: String
    let lastName: String
    let email: String?
    let phone: String?
    let birthDate: String?
    let document: String?
    let gender: String?
    let notes: String?
    let createdAt: Date?
    let visits: Int?
    let lastVisit: Date?
    let nextVisit: Date?

    var fullName: String { [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ") }
}

struct CustomersEnvelope: Decodable { let customers: [CustomerSummary] }

struct Customer: Decodable, Identifiable, Hashable {
    let id: String
    let shortId: String?
    let firstName: String
    let lastName: String
    let email: String?
    let phone: String?
    let birthDate: String?
    let document: String?
    let gender: String?
    let notes: String?
    let createdAt: Date?

    var fullName: String { [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ") }
}

struct CustomerEnvelope: Decodable { let customer: Customer }

/// One appointment in a customer's history.
struct CustomerAppointment: Decodable, Identifiable, Hashable {
    let id: String
    let code: String
    let startsAt: Date
    let endsAt: Date
    let status: AppointmentStatus
    let serviceName: String
    let resourceName: String?
    let priceAmount: Int?
    let source: String?
}

struct CustomerDetail: Decodable {
    let customer: Customer
    let appointments: [CustomerAppointment]
}

// MARK: Sales

struct SaleCustomer: Decodable, Hashable {
    let id: String
    let firstName: String
    let lastName: String
    let phone: String?
    let email: String?

    var fullName: String { [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ") }
}

struct SaleItem: Decodable, Identifiable, Hashable {
    let id: String
    let kind: String
    let appointmentId: String?
    let name: String
    let resourceName: String?
    let quantity: Int
    let unitPrice: Int
    let discountAmount: Int
    let total: Int
}

struct SaleTip: Decodable, Identifiable, Hashable {
    let id: String
    let resourceName: String?
    let amount: Int
}

struct SalePayment: Decodable, Identifiable, Hashable {
    let id: String
    let method: String
    let amount: Int
}

struct Sale: Decodable, Identifiable, Hashable {
    let id: String
    let number: Int
    let status: String
    let `internal`: Bool?
    let locationName: String?
    let subtotal: Int
    let discountAmount: Int
    let total: Int
    let tipAmount: Int
    let note: String?
    let createdAt: Date
    let voidedAt: Date?
    let soldByName: String?
    let customer: SaleCustomer?
    let items: [SaleItem]
    let tips: [SaleTip]?
    let payments: [SalePayment]

    var isVoid: Bool { status == "void" }
    var isInternal: Bool { self.internal ?? false }
    /// What the customer handed over: the total plus tips.
    var collected: Int { total + tipAmount }
}

struct SalesEnvelope: Decodable { let sales: [Sale] }
struct SaleEnvelope: Decodable { let sale: Sale }

struct DepositCustomer: Decodable, Hashable {
    let id: String
    let firstName: String
    let lastName: String
}

struct Deposit: Decodable, Identifiable, Hashable {
    let id: String
    let amount: Int
    let method: String
    let note: String?
    let status: String
    let createdAt: Date
    let appointmentId: String?
    let appointmentCode: String?
    let locationName: String?
    let appliedSale: SaleReference?
    let customer: DepositCustomer?

    /// Active and not used by a sale yet.
    var isAvailable: Bool { status == "active" && appliedSale == nil }
}

struct DepositsEnvelope: Decodable { let deposits: [Deposit] }

// MARK: Gift cards

struct GiftCardCustomer: Decodable, Hashable {
    let id: String
    let firstName: String
    let lastName: String
}

struct GiftCard: Decodable, Identifiable, Hashable {
    let id: String
    let code: String
    /// `discount` (a code that takes something off) or `balance` (money on a card).
    let kind: String
    let percent: Int?
    let amount: Int?
    let maxUses: Int?
    let validFrom: String?
    let validUntil: String?
    let note: String?
    let active: Bool
    let uses: Int?
    let spent: Int?
    let remaining: Int?
    /// `active`, `inactive`, `scheduled`, `expired`, `used_up` or `spent`.
    let state: String
    let customer: GiftCardCustomer?
}

struct GiftCardEnvelope: Decodable { let giftCard: GiftCard }

// MARK: Inventory

struct Product: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let sku: String?
    let priceAmount: Int
    let internalPriceAmount: Int?
    let costAmount: Int?
    let taxRate: Double?
    let content: Double?
    let unit: String?
    let forSale: Bool
    let active: Bool
    let stock: [String: Int]?
    let alerts: [String: Int]?

    var totalStock: Int { (stock ?? [:]).values.reduce(0, +) }

    /// At or under its alert in any location.
    var isLow: Bool {
        guard active else { return false }
        for (location, alert) in alerts ?? [:] where (stock?[location] ?? 0) <= alert { return true }
        return false
    }
}

struct ProductsEnvelope: Decodable { let products: [Product] }

// MARK: Cash

// MARK: Reports

struct ReportFigures: Decodable, Hashable {
    let bookings: Int
    let occupancy: Double?
    let newCustomers: Int
    let billed: Int
}

struct ReportPeriod: Decodable, Hashable {
    let from: String
    let to: String
}

struct ReportPoint: Decodable, Hashable {
    let start: String
    let bookings: Int
    let billed: Int
}

struct ReportSeries: Decodable, Hashable {
    let unit: String
    let points: [ReportPoint]
}

struct ReportTopService: Decodable, Hashable {
    let name: String
    let quantity: Int
    let amount: Int
}

struct ReportByProfessional: Decodable, Hashable {
    let name: String
    let bookings: Int
}

struct ReportToday: Decodable, Identifiable, Hashable {
    let id: String
    let code: String?
    let time: String
    let service: String?
    let professional: String?
    let customer: String?
    let paid: Bool?
}

struct ReportSummary: Decodable {
    let period: ReportPeriod
    let previous: ReportPeriod?
    let current: ReportFigures
    let before: ReportFigures?
    let series: ReportSeries?
    let origin: [String: Int]?
    let topServices: [ReportTopService]?
    let byProfessional: [ReportByProfessional]?
    let today: [ReportToday]?
}

struct ReportCommission: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let serviceRate: Double?
    let productRate: Double?
    let servicesCount: Int
    let services: Int
    let serviceCommission: Int
    let productsCount: Int
    let products: Int
    let productCommission: Int
    let tips: Int
    let total: Int
}

struct CommissionsResponse: Decodable { let professionals: [ReportCommission] }

// MARK: Team

struct Invitation: Decodable, Identifiable, Hashable {
    let id: String
    let email: String
    let expiresAt: Date?
    let createdAt: Date?
}


// MARK: Notifications

/// What the signed-in server offers the app.
struct MobileSettings: Decodable {
    struct Push: Decodable { let enabled: Bool }
    /// Missing on a server that predates push: the same as switched off.
    let push: Push?

    var pushEnabled: Bool { push?.enabled ?? false }
}

/// One entry in the notifications screen: an appointment booked, moved or cancelled.
struct AppNotification: Decodable, Identifiable, Hashable {
    let id: String
    /// `appointment_booked`, `appointment_rescheduled` or `appointment_cancelled`;
    /// a newer server may add others (see `NotificationKind`).
    let kind: String
    let title: String
    let body: String
    let businessId: String?
    let appointmentId: String?
    /// When the appointment is, so it can be found in the agenda.
    let appointmentStartsAt: Date?
    let createdAt: Date
    let readAt: Date?
}

struct NotificationsPage: Decodable {
    let notifications: [AppNotification]
    /// Unread in the business asked about.
    let unread: Int
    /// Unread in every business, for the number on the app's icon.
    let unreadTotal: Int
}

struct UnreadTotal: Decodable { let unreadTotal: Int }
