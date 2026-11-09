package org.openturnly.mobile.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Sale

enum class Tab { AGENDA, CUSTOMERS, SALES, MORE }

/** Every screen that can be pushed over a tab. */
sealed interface Route {
    data class AppointmentDetail(val appointment: Appointment) : Route
    data class NewAppointment(val customer: CustomerSummary? = null, val date: LocalDate? = null) : Route
    data class Reschedule(val appointment: Appointment) : Route
    data class Checkout(val appointment: Appointment?) : Route
    data class CustomerDetail(val customer: CustomerSummary) : Route
    data class CustomerForm(val customer: CustomerSummary?) : Route
    data class SaleDetail(val saleId: String, val sale: Sale? = null) : Route
    data object BookingLink : Route
    data object Reports : Route
    data object Account : Route
}

/** A route on a stack, with an id so its saved state survives going back to it. */
data class Entry(val id: Long, val route: Route)

/** One back stack per tab, like the platform's own apps: switching tabs keeps your place. */
class Navigator {
    private var nextId = 1L
    var tab by mutableStateOf(Tab.AGENDA)
    val stacks = mutableStateMapOf<Tab, List<Entry>>()
    /** Whether the last change went deeper (push) or back (pop), for the transition. */
    var forward by mutableStateOf(true); private set

    fun stack(tab: Tab = this.tab): List<Entry> = stacks[tab] ?: emptyList()
    val top: Entry? get() = stack().lastOrNull()

    fun push(route: Route, onTab: Tab = tab) {
        forward = true
        tab = onTab
        stacks[onTab] = stack(onTab) + Entry(nextId++, route)
    }

    /** Replaces the screen on top (after saving a form that opens what it created). */
    fun replace(route: Route) {
        forward = true
        val current = stack()
        stacks[tab] = current.dropLast(1) + Entry(nextId++, route)
    }

    fun pop(): Boolean {
        val current = stack()
        if (current.isEmpty()) return false
        forward = false
        stacks[tab] = current.dropLast(1)
        return true
    }

    fun popToRoot(tab: Tab = this.tab) { forward = false; stacks[tab] = emptyList() }

    fun select(next: Tab) {
        if (next == tab) popToRoot(next) else { forward = true; tab = next }
    }

    fun reset() { stacks.clear(); tab = Tab.AGENDA; forward = true }
}
