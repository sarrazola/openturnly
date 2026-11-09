package org.openturnly.mobile

import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.Business
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Location
import org.openturnly.mobile.api.OpenTurnlyApi
import org.openturnly.mobile.api.Professional
import org.openturnly.mobile.api.Service
import org.openturnly.mobile.api.User
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.storage.LastServer
import org.openturnly.mobile.storage.SessionStore
import org.openturnly.mobile.storage.StoredSession
import org.openturnly.mobile.ui.Navigator

/** The business's locations, services and team: what most screens need to draw. */
data class Catalog(
    val locations: List<Location> = emptyList(),
    val services: List<Service> = emptyList(),
    val professionals: List<Professional> = emptyList(),
    val loaded: Boolean = false,
) {
    val activeLocations: List<Location> get() = locations.filter { it.active }
    fun professional(id: String?): Professional? = professionals.firstOrNull { it.id == id }
    fun service(id: String?): Service? = services.firstOrNull { it.id == id }
    fun location(id: String?): Location? = locations.firstOrNull { it.id == id }
    /** Each professional's colour slot is their position in the team, as on the web's calendar. */
    fun personIndex(id: String?): Int = professionals.indexOfFirst { it.id == id }
}

/**
 * The root state: which screen is up, which server and token it runs on, which
 * business is open, and the data several screens share (the catalog, the
 * agenda's loaded weeks and the customer list).
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    enum class Screen { LOADING, SIGN_IN, RECONNECT, BUSINESSES, NO_BUSINESS, MAIN }

    var screen by mutableStateOf(Screen.LOADING); private set
    var api by mutableStateOf<OpenTurnlyApi?>(null); private set
    var user by mutableStateOf<User?>(null); private set
    var businesses by mutableStateOf<List<Business>>(emptyList()); private set
    var business by mutableStateOf<Business?>(null); private set
    var catalog by mutableStateOf(Catalog()); private set
    var signOutBusy by mutableStateOf(false); private set
    var showBusinessPicker by mutableStateOf(false)
    /** A one-off message for the sign-in screen (a session that ended). */
    var signInNotice by mutableStateOf<String?>(null)

    val nav = Navigator()
    val snackbar = SnackbarHostState()
    val agenda = AgendaState(this)
    val customers = CustomersState(this)
    val notifications = NotificationsState(this, application)

    private val context get() = getApplication<Application>()
    private var generation = 0
    private var toastJob: Job? = null

    val server: String get() = api?.server ?: ""
    val isAdmin: Boolean get() = business?.isAdmin == true
    val zone: ZoneId get() = BusinessTime.zone(business?.timezone)
    val currency: String get() = business?.currency ?: "USD"
    val today: LocalDate get() = BusinessTime.today(zone)
    /** The professional profile a professional is linked to. */
    val ownResourceId: String? get() = business?.access?.resourceId?.takeIf { business?.isAdmin == false }

    // MARK: Session

    fun restore() {
        generation += 1
        val current = generation
        screen = Screen.LOADING
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { SessionStore.load(context) }
            if (current != generation) return@launch
            if (stored == null) { screen = Screen.SIGN_IN; return@launch }
            val next = OpenTurnlyApi(stored.server, stored.token)
            try {
                val me = next.me()
                val list = next.businesses()
                if (current != generation) return@launch
                api = next
                user = me
                businesses = list
                pickBusiness(list, stored.businessId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (current != generation) return@launch
                if (failure.isUnauthorized) {
                    SessionStore.clear(context)
                    signInNotice = Copy.signIn.sessionEnded
                    screen = Screen.SIGN_IN
                } else {
                    // An offline launch must not erase a valid session.
                    api = next
                    screen = Screen.RECONNECT
                }
            }
        }
    }

    /** After `POST /api/mobile/sign-in` (or a directory account) answered with a token. */
    suspend fun signedIn(server: String, token: String, signedInUser: User) {
        val next = OpenTurnlyApi(server, token)
        val list = next.businesses()
        withContext(Dispatchers.IO) { SessionStore.store(context, StoredSession(server, token)) }
        LastServer.store(context, server)
        generation += 1
        api = next
        user = signedInUser
        businesses = list
        signInNotice = null
        pickBusiness(list, null)
    }

    private fun pickBusiness(list: List<Business>, storedId: String?) {
        // A tapped notification opens its own business, over the one last used.
        val remembered = list.firstOrNull { it.id == notifications.tappedBusinessId } ?: list.firstOrNull { it.id == storedId }
        when {
            remembered != null -> openBusiness(remembered)
            list.size == 1 -> openBusiness(list.first())
            list.isEmpty() -> screen = Screen.NO_BUSINESS
            else -> screen = Screen.BUSINESSES
        }
    }

    fun openBusiness(next: Business) {
        val changed = business?.id != next.id
        business = next
        showBusinessPicker = false
        api?.let { current ->
            viewModelScope.launch(Dispatchers.IO) { runCatching { SessionStore.store(context, StoredSession(current.server, current.token, next.id)) } }
        }
        if (changed) {
            nav.reset()
            catalog = Catalog()
            agenda.reset(BusinessTime.today(BusinessTime.zone(next.timezone)))
            customers.reset()
        }
        screen = Screen.MAIN
        refreshCatalog()
        agenda.loadCurrent()
        notifications.businessOpened(changed)
    }

    /** Re-reads the business (after editing it) and the list the switcher shows. */
    fun refreshBusiness() {
        val id = business?.id ?: return
        viewModelScope.launch {
            runCatching { call { it2 -> business(it2) } }.getOrNull()?.let { fresh ->
                if (business?.id == id) business = fresh
                businesses = businesses.map { if (it.id == fresh.id) fresh else it }
            }
        }
    }

    fun refreshBusinesses() {
        viewModelScope.launch {
            runCatching { api?.businesses() }.getOrNull()?.let { businesses = it }
        }
    }

    fun refreshCatalog() {
        val id = business?.id ?: return
        viewModelScope.launch {
            try {
                val locations = async { call { locations(id) } }
                val services = async { call { services(id) } }
                val professionals = async { call { professionals(id) } }
                val next = Catalog(locations.await(), services.await(), professionals.await(), true)
                if (business?.id == id) catalog = next
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                // Screens that need the catalog offer a retry; the agenda still works without it.
            }
        }
    }

    fun replaceCatalog(next: Catalog) { catalog = next }

    fun retryConnection() = restore()

    fun signOut() {
        if (signOutBusy) return
        signOutBusy = true
        val current = api
        viewModelScope.launch {
            // This phone stops ringing first, while the session can still say so.
            notifications.forgetDevice(current)
            runCatching { current?.signOut() }
            forget()
            signOutBusy = false
        }
    }

    /** A 401 anywhere: the session is over, on the server's word. */
    fun expire() {
        if (screen == Screen.SIGN_IN) return
        forget()
        signInNotice = Copy.signIn.sessionEnded
    }

    private fun forget() {
        generation += 1
        SessionStore.clear(context)
        api = null
        user = null
        businesses = emptyList()
        business = null
        catalog = Catalog()
        nav.reset()
        agenda.reset(LocalDate.now())
        customers.reset()
        notifications.signedOut()
        showBusinessPicker = false
        screen = Screen.SIGN_IN
    }

    fun updateUser(next: User) { user = next }

    /** The server to offer on the sign-in screen: the last one used, or the brand's default. */
    val rememberedServer: String get() = LastServer.load(context) ?: Brand.defaultServer

    // MARK: Calls

    /**
     * Runs a call against the open session. A 401 ends the session here, once,
     * for every screen; other failures are the caller's to show.
     */
    suspend fun <T> call(block: suspend OpenTurnlyApi.(String) -> T): T {
        val current = api ?: throw ApiException(401, "unauthorized")
        try {
            return current.block(business?.id ?: "")
        } catch (failure: ApiException) {
            if (failure.isUnauthorized && current === api) expire()
            throw failure
        }
    }

    fun toast(message: String) {
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    fun failure(error: ApiException) = toast(error.message)

    val locale get() = AppLocale.locale

    override fun onCleared() {
        notifications.detach()
    }
}

/**
 * The agenda's days. A whole week (Monday to Monday, in the business's zone) is
 * loaded in one call, so moving between its days is instant and the week strip
 * can mark the days that have appointments.
 */
class AgendaState(private val model: AppModel) {
    var date by mutableStateOf(LocalDate.now())
    val weeks = mutableStateMapOf<LocalDate, List<Appointment>>()
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var professionalId by mutableStateOf<String?>(null)
    var locationId by mutableStateOf<String?>(null)
    var showCancelled by mutableStateOf(false)
    /** Ticks so "now" markers and statuses move along while the screen stays open. */
    var now by mutableStateOf(Instant.now())
    private val jobs = HashMap<LocalDate, Job>()

    fun reset(today: LocalDate) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        weeks.clear()
        date = today
        professionalId = null
        locationId = null
        showCancelled = false
        error = null
        loading = false
    }

    /** The ✕ on the filter tag: everyone, every location, no cancelled ones. */
    fun clearFilters() {
        professionalId = null
        locationId = null
        showCancelled = false
    }

    fun select(day: LocalDate) {
        date = day
        val week = BusinessTime.weekStart(day)
        if (!weeks.containsKey(week)) load(week)
    }

    fun loadCurrent() = load(BusinessTime.weekStart(date))

    fun refresh() = load(BusinessTime.weekStart(date), refresh = true)

    fun load(week: LocalDate, refresh: Boolean = false) {
        val businessId = model.business?.id ?: return
        if (jobs[week]?.isActive == true && !refresh) return
        jobs[week]?.cancel()
        val zone = model.zone
        if (refresh) refreshing = true else if (!weeks.containsKey(week)) loading = true
        jobs[week] = model.viewModelScope.launch {
            try {
                val (from, to) = BusinessTime.range(week, week.plusDays(6), zone)
                val list = model.call { appointments(it, BusinessTime.iso(from), BusinessTime.iso(to)) }
                if (model.business?.id == businessId) {
                    weeks[week] = list
                    error = null
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (!failure.isUnauthorized) error = failure.message
            } finally {
                now = Instant.now()
                if (BusinessTime.weekStart(date) == week) { loading = false; refreshing = false }
                else if (jobs.values.none { it.isActive }) { loading = false; refreshing = false }
            }
        }
    }

    fun appointmentsOn(day: LocalDate): List<Appointment>? {
        val week = weeks[BusinessTime.weekStart(day)] ?: return null
        return org.openturnly.mobile.rules.Appointments.onDay(week, day, model.zone)
    }

    fun find(id: String): Appointment? = weeks.values.asSequence().flatten().firstOrNull { it.id == id }

    /**
     * One appointment by id, fresh from the server. There is no route for a single
     * one, so the week it starts in (by `startsAt`) is loaded again and kept like
     * any loaded week. A notification is about a change, so a copy loaded before
     * is not trusted. Null when it is not there.
     */
    suspend fun locate(id: String, startsAt: String?): Appointment? {
        val start = BusinessTime.parse(startsAt) ?: return find(id)
        val businessId = model.business?.id ?: return null
        val week = BusinessTime.weekStart(BusinessTime.localDate(start, model.zone))
        val (from, to) = BusinessTime.range(week, week.plusDays(6), model.zone)
        val list = model.call { appointments(it, BusinessTime.iso(from), BusinessTime.iso(to)) }
        if (model.business?.id != businessId) return null
        weeks[week] = list
        val found = list.firstOrNull { it.id == id } ?: return null
        // Moved here from another loaded week: the copy there is out of date.
        weeks.keys.filter { it != week }.forEach { other ->
            weeks[other]?.takeIf { old -> old.any { it.id == id } }?.let { old -> weeks[other] = old.filterNot { it.id == id } }
        }
        return found
    }

    /** After an action changed an appointment: reload the weeks it was and is in. */
    fun reloadFor(vararg days: LocalDate?) {
        days.filterNotNull().map { BusinessTime.weekStart(it) }.distinct().forEach { load(it, refresh = true) }
    }

    /** Drops every loaded week but the shown one (after a booking moved things around). */
    fun invalidate() {
        val keep = BusinessTime.weekStart(date)
        weeks.keys.filter { it != keep }.forEach { weeks.remove(it) }
        load(keep, refresh = true)
    }
}

/** The whole customer list, loaded once for admins and filtered on the phone as they type. */
class CustomersState(private val model: AppModel) {
    var list by mutableStateOf<List<CustomerSummary>?>(null); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var query by mutableStateOf("")
    private var job: Job? = null
    private var loadedAt = 0L

    /** Shown again after a while: a quiet refresh, so bookings made elsewhere show up. */
    fun refreshIfStale() { if (list != null && System.currentTimeMillis() - loadedAt > 60_000) load(refresh = true) }

    fun reset() { job?.cancel(); list = null; error = null; query = ""; loading = false; refreshing = false }

    fun load(refresh: Boolean = false) {
        if (!model.isAdmin) return
        if (job?.isActive == true && !refresh) return
        job?.cancel()
        if (refresh && list != null) refreshing = true else loading = true
        val businessId = model.business?.id
        job = model.viewModelScope.launch {
            try {
                val all = model.call { customers(it) }
                if (model.business?.id == businessId) { list = all; error = null; loadedAt = System.currentTimeMillis() }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (!failure.isUnauthorized) error = failure.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    /** Puts a created or edited customer in the list without reloading it. */
    fun upsert(customer: CustomerSummary) {
        val current = list ?: return
        list = (current.filter { it.id != customer.id } + customer)
            .sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() }))
    }
}
