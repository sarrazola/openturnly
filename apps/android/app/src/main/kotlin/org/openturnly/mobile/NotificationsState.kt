package org.openturnly.mobile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.AppNotification
import org.openturnly.mobile.api.NotificationList
import org.openturnly.mobile.api.OpenTurnlyApi
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.push.NotificationTap
import org.openturnly.mobile.push.Push
import org.openturnly.mobile.rules.Inbox
import org.openturnly.mobile.rules.NotificationData
import org.openturnly.mobile.storage.PushPreferences
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.Tab

/**
 * The inbox of the open business (the bell's count and the list) and this
 * phone's push registration.
 *
 * The inbox comes from the server and works with or without push. Push needs
 * three things: a brand that carries it, a server that sends it, and a person
 * who has not switched it off on this phone.
 */
class NotificationsState(private val model: AppModel, private val context: Application) {
    /** Unread in the open business: the bell. */
    var unread by mutableStateOf(0); private set
    /** Unread across every business: the launcher's count. */
    var unreadTotal by mutableStateOf(0); private set
    var list by mutableStateOf<List<AppNotification>?>(null); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** The ones that were unread while the inbox has been open: they keep their dot. */
    var fresh by mutableStateOf<Set<String>>(emptySet()); private set
    /** The row whose appointment is being looked up. */
    var opening by mutableStateOf<String?>(null); private set

    /** False once the server answered that it has no inbox (404): the bell hides. */
    var supported by mutableStateOf(true); private set

    /** Whether the server sends push, once it has said so. */
    var serverPush by mutableStateOf<Boolean?>(null); private set
    /** Switched off on this phone, from "My account". */
    var off by mutableStateOf(PushPreferences.off(context)); private set

    private var inboxOpen = false
    private var countJob: Job? = null
    private var listJob: Job? = null
    private var settingsJob: Job? = null
    /** The session and push token last registered, so the agenda showing again does not repeat it. */
    private var registered: Pair<String, String>? = null
    private var pendingTap: NotificationTap? = null

    /** "My account" offers the switch only when push can work at all. */
    val offered: Boolean get() = Push.available && serverPush == true
    private val wanted: Boolean get() = offered && !off && model.api != null

    init {
        Push.onToken = { token -> model.viewModelScope.launch { registered = null; register(token) } }
        Push.onMessage = { message -> model.viewModelScope.launch { received(message) } }
    }

    fun detach() {
        Push.onToken = null
        Push.onMessage = null
    }

    // MARK: Business and session

    /** After a business opens: fresh counts, the server's push setting, and a tap waiting for it. */
    fun businessOpened(changed: Boolean) {
        if (changed) {
            countJob?.cancel()
            listJob?.cancel()
            list = null
            fresh = emptySet()
            unread = 0
            error = null
        }
        refreshCount()
        loadSettings()
        consumeTap()
    }

    /** Before the server forgets the session: take this phone's token back, briefly and quietly. */
    suspend fun forgetDevice(api: OpenTurnlyApi?) {
        val token = PushPreferences.token(context)
        if (api == null || !Push.available || token == null) return
        runCatching { withTimeoutOrNull(5_000) { api.forgetDevice(token) } }
    }

    fun signedOut() {
        countJob?.cancel()
        listJob?.cancel()
        settingsJob?.cancel()
        list = null
        fresh = emptySet()
        unread = 0
        unreadTotal = 0
        error = null
        opening = null
        supported = true
        serverPush = null
        registered = null
        inboxOpen = false
        Push.clear(context)
    }

    private fun loadSettings() {
        if (!Push.available || serverPush != null || settingsJob?.isActive == true) return
        settingsJob = model.viewModelScope.launch {
            try {
                serverPush = model.call { mobileSettings() }.push.enabled
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                // An older server has no such route: it sends nothing. Offline, ask again later.
                if (failure.status != 0) serverPush = false
            }
        }
    }

    // MARK: Counts and the inbox

    /** The bell's count, from the server; the open inbox reloads instead. */
    fun refreshCount() {
        if (!supported) return
        if (inboxOpen) { load(quiet = true); return }
        val businessId = model.business?.id ?: return
        if (countJob?.isActive == true) return
        countJob = model.viewModelScope.launch {
            try {
                val answer = model.call { notifications(businessId) }
                if (model.business?.id == businessId) take(answer)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                // A server without an inbox hides the bell; otherwise it keeps its last count until the next refresh.
                if (Inbox.unsupported(failure.status)) supported = false
            }
        }
    }

    /** Opened from the bell, or shown again after an appointment it opened (`again`): that keeps the dots. */
    fun inboxOpened(again: Boolean) {
        inboxOpen = true
        if (!again) fresh = emptySet()
        load(quiet = again && list != null)
    }

    fun inboxClosed() { inboxOpen = false }

    /** Loads the inbox and marks what it shows as read; quietly when nobody pulled. */
    fun load(refresh: Boolean = false, quiet: Boolean = false) {
        val businessId = model.business?.id ?: return
        listJob?.cancel()
        if (!quiet) { if (refresh && list != null) refreshing = true else loading = list == null }
        listJob = model.viewModelScope.launch {
            try {
                val answer = model.call { notifications(businessId) }
                if (model.business?.id != businessId) return@launch
                list = answer.notifications
                fresh = fresh + answer.notifications.filter { it.unread }.map { it.id }
                error = null
                take(answer)
                if (answer.unread > 0) {
                    val left = model.call { markNotificationsRead(businessId = businessId) }
                    if (model.business?.id == businessId) { unread = 0; total(left) }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (Inbox.unsupported(failure.status)) supported = false
                if (!failure.isUnauthorized) error = failure.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    private fun take(answer: NotificationList) {
        unread = answer.unread
        total(answer.unreadTotal)
        if (list != null) list = answer.notifications
    }

    private fun total(count: Int) {
        unreadTotal = count
        // Nothing left to read anywhere: the posted ones go, and the launcher's dot with them.
        if (count == 0) Push.clear(context)
    }

    private fun markRead(id: String) {
        model.viewModelScope.launch {
            try {
                total(model.call { markNotificationsRead(ids = listOf(id)) })
                refreshCount()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                // Opening the inbox marks it read anyway.
            }
        }
    }

    private fun received(message: NotificationData) {
        message.badge?.let { unreadTotal = it }
        if (message.businessId == null || message.businessId == model.business?.id) refreshCount()
    }

    // MARK: Opening an appointment

    /** Opens the appointment a notification is about, found in the week it starts. */
    fun open(appointmentId: String?, startsAt: String?, row: String? = null) {
        if (appointmentId == null || !Inbox.opens(appointmentId)) { model.toast(Copy.notifications.notFound); return }
        val businessId = model.business?.id ?: return
        if (opening != null) return
        opening = row ?: appointmentId
        model.viewModelScope.launch {
            try {
                val appointment = model.agenda.locate(appointmentId, startsAt)
                if (model.business?.id != businessId) return@launch
                if (appointment != null) model.nav.push(Route.AppointmentDetail(appointment), Tab.AGENDA)
                else model.toast(Copy.notifications.notFound)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (!failure.isUnauthorized) model.failure(failure)
            } finally {
                opening = null
            }
        }
    }

    /** A tapped notification: kept until a session and its business are open. */
    fun tapped(tap: NotificationTap) {
        pendingTap = tap
        consumeTap()
    }

    /** The business a waiting tap is about, so a launch from it opens that one. */
    val tappedBusinessId: String? get() = pendingTap?.businessId

    private fun consumeTap() {
        val tap = pendingTap ?: return
        if (model.screen != AppModel.Screen.MAIN || model.api == null) return
        val current = model.business ?: return
        val target = tap.businessId?.let { id -> model.businesses.firstOrNull { it.id == id } }
        if (target != null && target.id != current.id) {
            // Opening it comes back here, with that business open.
            model.openBusiness(target)
            return
        }
        pendingTap = null
        // A business this account does not open (another account's notification): nothing to show.
        if (tap.businessId != null && target == null) return
        tap.notificationId?.let { markRead(it) }
        if (tap.appointmentId != null) open(tap.appointmentId, tap.startsAt)
        else model.nav.push(Route.Notifications, Tab.AGENDA)
    }

    // MARK: Push registration

    /** The agenda asks once, after it has loaded, before it registers. */
    fun shouldAskPermission(): Boolean = wanted && Push.needsPermission(context) && !PushPreferences.asked(context)

    fun markAsked() = PushPreferences.storeAsked(context)

    val permissionAsked: Boolean get() = PushPreferences.asked(context)

    /** Gives this phone's token to the server, once per session and token. */
    fun register(known: String? = null) {
        val api = model.api ?: return
        if (!wanted || !Push.permitted(context)) return
        model.viewModelScope.launch {
            val token = known ?: Push.token() ?: return@launch
            val key = api.token to token
            if (registered == key || model.api !== api || !wanted) return@launch
            try {
                api.registerDevice(token)
                registered = key
                PushPreferences.storeToken(context, token)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (failure.isUnauthorized && model.api === api) model.expire()
            }
        }
    }

    /** "Notifications on this phone": off takes the token back from the server. */
    fun switchOff() {
        off = true
        PushPreferences.storeOff(context, true)
        registered = null
        val api = model.api ?: return
        val token = PushPreferences.token(context) ?: return
        model.viewModelScope.launch { runCatching { api.forgetDevice(token) } }
    }

    /** On again: the screen asks for the permission first when the phone needs it. */
    fun switchOn() {
        off = false
        PushPreferences.storeOff(context, false)
        register()
    }
}
