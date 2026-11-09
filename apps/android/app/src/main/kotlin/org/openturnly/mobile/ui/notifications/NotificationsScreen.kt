@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.AppNotification
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Inbox
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.IconBadge
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SkeletonCards

/**
 * The agenda header's bell: the open business's unread count, and the way into
 * the inbox. A server without an inbox (it answers 404) shows no bell at all.
 */
@Composable
fun NotificationBell(model: AppModel) {
    if (!model.notifications.supported) return
    val unread = model.notifications.unread
    val badge = Inbox.badge(unread)
    // The count sits outside the button, which clips its content to a circle: "9+" would be cut.
    Box {
        IconButton({ model.nav.push(Route.Notifications) }, Modifier.semantics { contentDescription = Copy.notifications.bell(unread) }) {
            Icon(if (badge != null) Icons.Filled.Notifications else Icons.Outlined.Notifications, null)
        }
        // The button already says how many («Avisos, 2 sin leer»): the badge is not read twice.
        if (badge != null) Badge(Modifier.align(Alignment.TopStart).offset(x = 26.dp, y = 6.dp).clearAndSetSemantics {}) { Text(badge) }
    }
}

/**
 * "Avisos": the open business's latest notifications, newest first. Opening it
 * marks them read; the ones that were unread keep a dot while it stays open.
 * A row opens the appointment it is about; one without an appointment is not tappable.
 */
@Composable
fun NotificationsScreen(model: AppModel) {
    val state = model.notifications
    // Kept with this screen's place on the stack: coming back from an appointment is not a new visit.
    var visited by rememberSaveable { mutableStateOf(false) }
    // Open only while the app is on screen: a message arriving in the background must stay unread.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { state.inboxOpened(again = visited); visited = true }
                Lifecycle.Event.ON_STOP -> state.inboxClosed()
                else -> Unit
            }
        }
        // A started screen receives ON_START right away.
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            state.inboxClosed()
        }
    }
    DetailScaffold(title = Copy.notifications.title, onBack = { model.nav.pop() }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing, onRefresh = { state.load(refresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val list = state.list
            // A newer one arrived while this was open: show it rather than keep the old first row in place.
            val listState = rememberLazyListState()
            val newest = list?.firstOrNull()?.id
            var seen by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(newest) {
                if (newest != null && seen != null && newest != seen) listState.scrollToItem(0)
                if (newest != null) seen = newest
            }
            when {
                list == null && state.error != null -> LazyColumn(Modifier.fillMaxSize()) {
                    item { ErrorState(state.error ?: Copy.common.loadFailed, { state.load() }) }
                }
                list == null -> SkeletonCards(modifier = Modifier.padding(top = 8.dp))
                list.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item { EmptyState(Icons.Outlined.NotificationsNone, Copy.notifications.emptyTitle, Copy.notifications.emptyBody) }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { notification ->
                        val onClick = if (Inbox.opens(notification.appointmentId)) {
                            { state.open(notification.appointmentId, notification.appointmentStartsAt, notification.id) }
                        } else null
                        NotificationRow(model, notification, highlighted = notification.id in state.fresh, busy = state.opening == notification.id, onClick = onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(model: AppModel, notification: AppNotification, highlighted: Boolean, busy: Boolean, onClick: (() -> Unit)?) {
    val ext = LocalExtended.current
    val primary = MaterialTheme.colorScheme.primary
    val created = BusinessTime.parse(notification.createdAt)
    // The agenda's clock ticks every minute, so "5 min ago" moves along while this is open.
    val age = created?.let { Copy.notifications.age(Inbox.age(it, maxOf(model.agenda.now, Instant.now()), model.zone), AppLocale.locale) }
    val (icon, tint) = kindIcon(Inbox.kind(notification.kind))
    // Every kind opens its appointment: a cancelled one is still there, and its detail says so.
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon, tint)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (age != null) Text(age, style = MaterialTheme.typography.labelMedium, color = ext.muted, modifier = Modifier.padding(start = 8.dp))
                }
                if (notification.body.isNotBlank()) {
                    Text(notification.body, style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Box(Modifier.padding(top = 6.dp).size(18.dp), contentAlignment = Alignment.Center) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    highlighted -> Box(
                        Modifier.size(9.dp).clip(CircleShape).background(primary)
                            .semantics { contentDescription = Copy.notifications.unread },
                    )
                }
            }
        }
    }
}

@Composable
private fun kindIcon(kind: Inbox.Kind): Pair<ImageVector, Color> {
    val ext = LocalExtended.current
    return when (kind) {
        Inbox.Kind.BOOKED -> Icons.Outlined.EventAvailable to MaterialTheme.colorScheme.primary
        Inbox.Kind.RESCHEDULED -> Icons.Outlined.Update to ext.info.content
        Inbox.Kind.CANCELLED -> Icons.Outlined.EventBusy to ext.danger.content
        Inbox.Kind.OTHER -> Icons.Outlined.Notifications to ext.muted
    }
}
