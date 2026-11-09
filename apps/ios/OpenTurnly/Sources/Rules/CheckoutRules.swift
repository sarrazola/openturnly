import Foundation

/// The arithmetic of a sale, the same way the web's checkout does it.
///
/// The server checks that payments add up to the total plus tips exactly, but it
/// does not compute the discount of a counter discount code: the client sends it.
enum CheckoutRules {
    struct Line: Equatable, Identifiable {
        let id: UUID
        var kind: Kind
        var serviceId: String?
        var productId: String?
        var appointmentId: String?
        var name: String
        var resourceId: String?
        var quantity: Int
        var unitPrice: Int
        /// The booking discount an appointment's line carries.
        var discountAmount: Int

        enum Kind: String { case service, product }

        init(id: UUID = UUID(), kind: Kind, serviceId: String? = nil, productId: String? = nil, appointmentId: String? = nil,
             name: String, resourceId: String? = nil, quantity: Int = 1, unitPrice: Int, discountAmount: Int = 0) {
            self.id = id
            self.kind = kind
            self.serviceId = serviceId
            self.productId = productId
            self.appointmentId = appointmentId
            self.name = name
            self.resourceId = resourceId
            self.quantity = quantity
            self.unitPrice = unitPrice
            self.discountAmount = discountAmount
        }

        var total: Int { max(0, quantity * unitPrice - discountAmount) }
    }

    struct Payment: Equatable, Identifiable {
        let id: UUID
        var method: String
        var amount: Int
        var giftCardId: String?
        var giftCardCode: String?

        init(id: UUID = UUID(), method: String, amount: Int, giftCardId: String? = nil, giftCardCode: String? = nil) {
            self.id = id
            self.method = method
            self.amount = amount
            self.giftCardId = giftCardId
            self.giftCardCode = giftCardCode
        }
    }

    /// Methods a person picks at the counter (deposits and gift cards come from elsewhere).
    static let counterMethods = ["cash", "card", "transfer", "other"]

    static func subtotal(_ lines: [Line]) -> Int { lines.reduce(0) { $0 + $1.total } }

    /// A discount code's value on this subtotal: a percent rounded, or an amount capped.
    static func codeDiscount(percent: Int?, amount: Int?, subtotal: Int) -> Int {
        if let percent { return Int((Double(subtotal) * Double(percent) / 100).rounded()) }
        return min(amount ?? 0, subtotal)
    }

    static func total(subtotal: Int, discount: Int) -> Int { max(0, subtotal - discount) }

    /// What has to be paid: the total plus tips.
    static func due(total: Int, tips: Int) -> Int { total + tips }

    /// What is left after the deposits used.
    static func remaining(due: Int, deposits: Int) -> Int { max(0, due - deposits) }

    /// Paid minus due: zero when it balances, negative when something is missing.
    static func difference(due: Int, deposits: Int, payments: [Payment]) -> Int {
        deposits + payments.reduce(0) { $0 + $1.amount } - due
    }

    /// Lines with amount zero are dropped, as the server would.
    static func payable(_ payments: [Payment]) -> [Payment] { payments.filter { $0.amount > 0 } }
}
