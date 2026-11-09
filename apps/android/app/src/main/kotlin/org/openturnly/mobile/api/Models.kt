package org.openturnly.mobile.api

import kotlinx.serialization.Serializable

// What the server sends, typed. Decoding ignores fields the app does not use
// and fills defaults for what an older server may leave out, so a newer server
// never breaks an installed app. Money is always in the currency's minor unit.

@Serializable
data class User(val id: String = "", val name: String = "", val email: String = "", val role: String = "member")

@Serializable
data class SignInResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User)

/** One account a directory sign-in opens: its own server, token and person. */
@Serializable
data class HostedAccount(val server: String, val name: String = "", val token: String, val user: User = User())

@Serializable
data class HostedSignInResponse(val accounts: List<HostedAccount> = emptyList())

@Serializable
data class Access(val role: String = "professional", val resourceId: String? = null)

@Serializable
data class Business(
    val id: String,
    val shortId: String = "",
    val slug: String = "",
    val name: String = "",
    val description: String? = null,
    val timezone: String = "UTC",
    val currency: String = "USD",
    val locale: String = "es",
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val slotIntervalMin: Int = 30,
    val minNoticeMin: Int = 60,
    val maxAdvanceDays: Int = 30,
    val closedOnHolidays: Boolean = true,
    val customerChangeNoticeMin: Int? = null,
    val brandColor: String? = null,
    val sample: Boolean = false,
    val country: String? = null,
    val logoUrl: String? = null,
    val access: Access = Access(),
) {
    /** Owners and admins manage everything inside a business; professionals get their own agenda. */
    val isAdmin: Boolean get() = access.role == "owner" || access.role == "admin"
}

@Serializable
data class BusinessList(val businesses: List<Business> = emptyList())

@Serializable
data class BusinessEnvelope(val business: Business)

@Serializable
data class Location(
    val id: String,
    val name: String = "",
    val address: String? = null,
    val phone: String? = null,
    val active: Boolean = true,
    val position: Int = 0,
    val serviceIds: List<String> = emptyList(),
    val resourceIds: List<String> = emptyList(),
    val appointmentCount: Int = 0,
)

@Serializable
data class LocationList(val locations: List<Location> = emptyList())

@Serializable
data class Service(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val category: String? = null,
    val durationMin: Int = 30,
    val priceAmount: Long? = null,
    val paymentMode: String = "none",
    val depositAmount: Long? = null,
    val active: Boolean = true,
    val position: Int = 0,
    val locationIds: List<String> = emptyList(),
)

@Serializable
data class ServiceList(val services: List<Service> = emptyList())

@Serializable
data class Professional(
    val id: String,
    val name: String = "",
    val active: Boolean = true,
    val position: Int = 0,
    val serviceCommission: Double = 0.0,
    val productCommission: Double = 0.0,
    val serviceIds: List<String> = emptyList(),
    val locationIds: List<String> = emptyList(),
    val hasOwnHours: Boolean = false,
    val photoUrl: String? = null,
)

@Serializable
data class ProfessionalList(val professionals: List<Professional> = emptyList())

@Serializable
data class AppointmentCustomer(
    val id: String = "",
    val shortId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class SaleRef(val id: String, val number: Int = 0)

@Serializable
data class Answer(val id: String = "", val label: String = "", val value: String = "")

@Serializable
data class Appointment(
    val id: String,
    val code: String = "",
    val startsAt: String,
    val endsAt: String,
    val status: String = "confirmed",
    val locationId: String? = null,
    val locationName: String? = null,
    val serviceId: String? = null,
    val serviceName: String = "",
    val resourceId: String? = null,
    val resourceName: String? = null,
    val priceAmount: Long? = null,
    val discountAmount: Long = 0,
    val giftCode: String? = null,
    val answers: List<Answer> = emptyList(),
    val source: String = "admin",
    val sourceLabel: String? = null,
    val createdAt: String? = null,
    val cancelledAt: String? = null,
    val customer: AppointmentCustomer = AppointmentCustomer(),
    val sale: SaleRef? = null,
    val depositAmount: Long = 0,
)

@Serializable
data class AppointmentList(val appointments: List<Appointment> = emptyList())

/** The bare row a create or reschedule answers with; the agenda is reloaded for the full shape. */
@Serializable
data class AppointmentRow(val id: String, val code: String = "", val startsAt: String = "", val status: String = "confirmed")

@Serializable
data class AppointmentRowEnvelope(val appointment: AppointmentRow)

@Serializable
data class Slot(val time: String, val resourceIds: List<String> = emptyList())

@Serializable
data class SlotList(val slots: List<Slot> = emptyList())

@Serializable
data class CustomerSummary(
    val id: String,
    val shortId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val birthDate: String? = null,
    val document: String? = null,
    val gender: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val visits: Int = 0,
    val lastVisit: String? = null,
    val nextVisit: String? = null,
)

@Serializable
data class CustomerList(val customers: List<CustomerSummary> = emptyList())

@Serializable
data class Customer(
    val id: String,
    val shortId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val birthDate: String? = null,
    val document: String? = null,
    val gender: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CustomerEnvelope(val customer: Customer)

@Serializable
data class CustomerVisit(
    val id: String,
    val code: String = "",
    val startsAt: String,
    val endsAt: String,
    val status: String = "confirmed",
    val serviceName: String = "",
    val resourceName: String? = null,
    val priceAmount: Long? = null,
    val source: String = "admin",
)

@Serializable
data class CustomerDetail(val customer: Customer, val appointments: List<CustomerVisit> = emptyList())

@Serializable
data class SaleCustomer(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class SaleItem(
    val id: String = "",
    val kind: String = "service",
    val appointmentId: String? = null,
    val serviceId: String? = null,
    val productId: String? = null,
    val name: String = "",
    val resourceId: String? = null,
    val resourceName: String? = null,
    val quantity: Int = 1,
    val unitPrice: Long = 0,
    val discountAmount: Long = 0,
    val total: Long = 0,
)

@Serializable
data class SaleTip(val id: String = "", val resourceId: String? = null, val resourceName: String? = null, val amount: Long = 0)

@Serializable
data class SalePayment(val id: String = "", val method: String = "cash", val amount: Long = 0, val depositId: String? = null, val giftCardId: String? = null)

@Serializable
data class Sale(
    val id: String,
    val number: Int = 0,
    val status: String = "paid",
    val internal: Boolean = false,
    val locationId: String? = null,
    val locationName: String? = null,
    val subtotal: Long = 0,
    val discountAmount: Long = 0,
    val total: Long = 0,
    val tipAmount: Long = 0,
    val note: String? = null,
    val createdAt: String = "",
    val voidedAt: String? = null,
    val soldByName: String? = null,
    val customer: SaleCustomer? = null,
    val items: List<SaleItem> = emptyList(),
    val tips: List<SaleTip> = emptyList(),
    val payments: List<SalePayment> = emptyList(),
)

@Serializable
data class SaleList(val sales: List<Sale> = emptyList())

@Serializable
data class SaleEnvelope(val sale: Sale)

@Serializable
data class DepositCustomer(val id: String = "", val firstName: String = "", val lastName: String = "")

@Serializable
data class Deposit(
    val id: String,
    val amount: Long = 0,
    val method: String = "cash",
    val note: String? = null,
    val status: String = "active",
    val createdAt: String = "",
    val voidedAt: String? = null,
    val appointmentId: String? = null,
    val appointmentCode: String? = null,
    val locationId: String? = null,
    val locationName: String? = null,
    val appliedSale: SaleRef? = null,
    val customer: DepositCustomer? = null,
)

@Serializable
data class DepositList(val deposits: List<Deposit> = emptyList())

@Serializable
data class DepositEnvelope(val deposit: Deposit)

@Serializable
data class GiftCardCustomer(val id: String = "", val firstName: String = "", val lastName: String = "")

@Serializable
data class GiftCard(
    val id: String,
    val code: String = "",
    val kind: String = "discount",
    val percent: Int? = null,
    val amount: Long? = null,
    val maxUses: Int? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val note: String? = null,
    val active: Boolean = true,
    val uses: Int = 0,
    val spent: Long = 0,
    val remaining: Long? = null,
    val state: String = "active",
    val customer: GiftCardCustomer? = null,
    val createdAt: String? = null,
)

@Serializable
data class GiftCardEnvelope(val giftCard: GiftCard)

@Serializable
data class Product(
    val id: String,
    val name: String = "",
    val sku: String? = null,
    val categoryId: String? = null,
    val brandId: String? = null,
    val content: Double? = null,
    val unit: String? = null,
    val description: String? = null,
    val priceAmount: Long = 0,
    val internalPriceAmount: Long? = null,
    val costAmount: Long? = null,
    val taxRate: Double? = null,
    val forSale: Boolean = true,
    val active: Boolean = true,
    val stock: Map<String, Long> = emptyMap(),
    val alerts: Map<String, Long> = emptyMap(),
)

@Serializable
data class ProductList(val products: List<Product> = emptyList())

@Serializable
data class Period(val from: String = "", val to: String = "")

@Serializable
data class Figures(val bookings: Int = 0, val occupancy: Double? = null, val newCustomers: Int = 0, val billed: Long = 0)

@Serializable
data class SeriesPoint(val start: String, val bookings: Int = 0, val billed: Long = 0)

@Serializable
data class Series(val unit: String = "day", val points: List<SeriesPoint> = emptyList())

@Serializable
data class TopService(val name: String = "", val quantity: Int = 0, val amount: Long = 0)

@Serializable
data class ProfessionalBookings(val name: String = "", val bookings: Int = 0)

@Serializable
data class TodayAppointment(
    val id: String, val code: String = "", val time: String = "", val service: String = "",
    val professional: String? = null, val location: String? = null, val customer: String? = null, val paid: Boolean = false,
)

@Serializable
data class ReportSummary(
    val period: Period = Period(),
    val previous: Period = Period(),
    val current: Figures = Figures(),
    val before: Figures = Figures(),
    val series: Series = Series(),
    val origin: Map<String, Int> = emptyMap(),
    val topServices: List<TopService> = emptyList(),
    val byProfessional: List<ProfessionalBookings> = emptyList(),
    val today: List<TodayAppointment> = emptyList(),
)

@Serializable
data class TipRow(
    val id: String, val amount: Long = 0, val resourceId: String? = null, val resourceName: String? = null,
    val sale: SaleRef? = null, val createdAt: String = "", val locationName: String? = null, val customer: String? = null,
)

@Serializable
data class TipTotal(val name: String = "", val amount: Long = 0, val count: Int = 0)

@Serializable
data class TipsReport(val tips: List<TipRow> = emptyList(), val totals: List<TipTotal> = emptyList())

@Serializable
data class CommissionRow(
    val id: String = "", val name: String = "", val serviceRate: Double = 0.0, val productRate: Double = 0.0,
    val servicesCount: Int = 0, val services: Long = 0, val serviceCommission: Long = 0,
    val productsCount: Int = 0, val products: Long = 0, val productCommission: Long = 0,
    val tips: Long = 0, val total: Long = 0,
)

@Serializable
data class CommissionsReport(val professionals: List<CommissionRow> = emptyList())

