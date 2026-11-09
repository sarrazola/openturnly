package org.openturnly.mobile.api

import java.net.URL
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import org.openturnly.mobile.api.ApiClient.encodePath
import org.openturnly.mobile.api.ApiClient.query

/**
 * One function per route the app calls, against the server and token of the
 * signed-in session. Paths never end in a slash: the server's allowlist for
 * professionals matches them exactly.
 */
class OpenTurnlyApi(val server: String, val token: String) {
    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T = ApiClient.request(server, path, serializer, token = token)
    private suspend fun <T> call(method: String, path: String, serializer: KSerializer<T>, body: JsonElement? = null): T =
        ApiClient.request(server, path, serializer, method, body ?: obj(), token)
    private suspend fun send(method: String, path: String, body: JsonElement? = null) = ApiClient.send(server, path, method, body, token)

    private fun b(businessId: String) = "/businesses/${encodePath(businessId)}"

    /** Public images are served from relative paths. */
    fun absolute(path: String?): String? = path?.let { if (it.startsWith("http")) it else server + it }

    // Session

    suspend fun signOut() = send("POST", "/mobile/sign-out")
    suspend fun me(): User = get("/me", MeResponse.serializer()).user
    suspend fun updateMe(name: String, email: String, currentPassword: String?): User =
        call("PATCH", "/me", MeResponse.serializer(), objWithout("name" to name, "email" to email, "currentPassword" to (currentPassword ?: Omit))).user
    suspend fun changePassword(currentPassword: String, newPassword: String) =
        send("POST", "/me/password", obj("currentPassword" to currentPassword, "newPassword" to newPassword))

    // Notifications

    suspend fun mobileSettings(): MobileSettings = get("/mobile/settings", MobileSettings.serializer())
    /** Ties this phone's push token to the session: signing out stops it. */
    suspend fun registerDevice(pushToken: String) = send("POST", "/mobile/devices", obj("token" to pushToken, "platform" to "android"))
    suspend fun forgetDevice(pushToken: String) = send("DELETE", "/mobile/devices", obj("token" to pushToken))
    suspend fun notifications(id: String): NotificationList = get("/me/notifications" + query("businessId" to id), NotificationList.serializer())
    /** One business's notifications, or the ones listed; answers how many stay unread across all. */
    suspend fun markNotificationsRead(businessId: String? = null, ids: List<String>? = null): Int =
        call("POST", "/me/notifications/read", NotificationsRead.serializer(), objWithout("businessId" to (businessId ?: Omit), "ids" to (ids ?: Omit))).unreadTotal

    // Businesses

    suspend fun businesses(): List<Business> = get("/businesses", BusinessList.serializer()).businesses
    suspend fun business(id: String): Business = get(b(id), BusinessEnvelope.serializer()).business

    // Catalog

    suspend fun locations(id: String): List<Location> = get("${b(id)}/locations", LocationList.serializer()).locations
    suspend fun services(id: String): List<Service> = get("${b(id)}/services", ServiceList.serializer()).services
    suspend fun professionals(id: String): List<Professional> = get("${b(id)}/professionals", ProfessionalList.serializer()).professionals

    // Appointments

    suspend fun appointments(id: String, from: String, to: String, includeCancelled: Boolean = true): List<Appointment> =
        get("${b(id)}/appointments" + query("from" to from, "to" to to, "includeCancelled" to if (includeCancelled) "true" else null), AppointmentList.serializer()).appointments
    suspend fun slots(id: String, serviceId: String, date: String, locationId: String?): List<Slot> =
        get("${b(id)}/appointments/slots" + query("serviceId" to serviceId, "date" to date, "locationId" to locationId), SlotList.serializer()).slots
    suspend fun rescheduleSlots(id: String, appointmentId: String, serviceId: String, date: String, locationId: String?): List<Slot> =
        get("${b(id)}/appointments/${encodePath(appointmentId)}/slots" + query("serviceId" to serviceId, "date" to date, "locationId" to locationId), SlotList.serializer()).slots
    suspend fun book(id: String, body: JsonElement): AppointmentRow = call("POST", "${b(id)}/appointments", AppointmentRowEnvelope.serializer(), body).appointment
    suspend fun reschedule(id: String, appointmentId: String, body: JsonElement): AppointmentRow =
        call("PATCH", "${b(id)}/appointments/${encodePath(appointmentId)}", AppointmentRowEnvelope.serializer(), body).appointment
    suspend fun cancel(id: String, appointmentId: String) = send("POST", "${b(id)}/appointments/${encodePath(appointmentId)}/cancel")
    suspend fun markNoShow(id: String, appointmentId: String) = send("POST", "${b(id)}/appointments/${encodePath(appointmentId)}/no-show")
    suspend fun undoNoShow(id: String, appointmentId: String) = send("DELETE", "${b(id)}/appointments/${encodePath(appointmentId)}/no-show")

    // Customers

    suspend fun customers(id: String, q: String? = null): List<CustomerSummary> =
        get("${b(id)}/customers" + query("q" to q), CustomerList.serializer()).customers
    suspend fun customer(id: String, customerId: String): CustomerDetail = get("${b(id)}/customers/${encodePath(customerId)}", CustomerDetail.serializer())
    suspend fun createCustomer(id: String, body: JsonElement): Customer = call("POST", "${b(id)}/customers", CustomerEnvelope.serializer(), body).customer
    suspend fun updateCustomer(id: String, customerId: String, body: JsonElement): Customer =
        call("PATCH", "${b(id)}/customers/${encodePath(customerId)}", CustomerEnvelope.serializer(), body).customer

    // Sales, deposits, gift cards

    suspend fun sales(id: String, from: String, to: String, locationId: String? = null): List<Sale> =
        get("${b(id)}/sales" + query("from" to from, "to" to to, "locationId" to locationId), SaleList.serializer()).sales
    suspend fun sale(id: String, saleId: String): Sale = get("${b(id)}/sales/${encodePath(saleId)}", SaleEnvelope.serializer()).sale
    suspend fun createSale(id: String, body: JsonElement): Sale = call("POST", "${b(id)}/sales", SaleEnvelope.serializer(), body).sale
    suspend fun voidSale(id: String, saleId: String): Sale = call("POST", "${b(id)}/sales/${encodePath(saleId)}/void", SaleEnvelope.serializer()).sale
    suspend fun deposits(id: String, from: String? = null, to: String? = null, customerId: String? = null): List<Deposit> =
        get("${b(id)}/deposits" + query("from" to from, "to" to to, "customerId" to customerId), DepositList.serializer()).deposits
    suspend fun createDeposit(id: String, body: JsonElement): Deposit = call("POST", "${b(id)}/deposits", DepositEnvelope.serializer(), body).deposit
    suspend fun lookupGiftCard(id: String, code: String): GiftCard = get("${b(id)}/gift-cards/lookup" + query("code" to code), GiftCardEnvelope.serializer()).giftCard

    // Products and reports

    suspend fun products(id: String): List<Product> = get("${b(id)}/products", ProductList.serializer()).products
    suspend fun reportSummary(id: String, from: String, to: String, locationId: String? = null): ReportSummary =
        get("${b(id)}/reports/summary" + query("from" to from, "to" to to, "locationId" to locationId), ReportSummary.serializer())
    suspend fun reportTips(id: String, from: String, to: String, locationId: String? = null): TipsReport =
        get("${b(id)}/reports/tips" + query("from" to from, "to" to to, "locationId" to locationId), TipsReport.serializer())
    suspend fun reportCommissions(id: String, from: String, to: String, locationId: String? = null): CommissionsReport =
        get("${b(id)}/reports/commissions" + query("from" to from, "to" to to, "locationId" to locationId), CommissionsReport.serializer())

    companion object {
        /** `POST /api/mobile/sign-in` on a server the person typed or picked. */
        suspend fun signIn(server: String, email: String, password: String): SignInResponse =
            ApiClient.request(server, "/mobile/sign-in", SignInResponse.serializer(), "POST", obj("email" to email.trim(), "password" to password))

        /** An account directory run by whoever published this build. */
        suspend fun hostedSignIn(directory: URL, email: String, password: String): List<HostedAccount> =
            ApiClient.request(directory, HostedSignInResponse.serializer(), "POST", obj("email" to email.trim(), "password" to password)).accounts
    }
}
