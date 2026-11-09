@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.openturnly.mobile.ui.customers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.rules.Search
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.openUrl

/**
 * Everyone who books with the business (the tab is for admins). The server sends
 * the whole list at once; it is searched on the phone as you type and drawn
 * lazily under A–Z sections.
 */
@Composable
fun CustomersScreen(model: AppModel) {
    val state = model.customers
    val ext = LocalExtended.current
    val listState = rememberLazyListState()
    LaunchedEffect(model.business?.id) { if (state.list == null) state.load() else state.refreshIfStale() }
    val all = state.list
    val filtered = remember(all, state.query) {
        all?.filter { Search.customer(state.query, Names.full(it.firstName, it.lastName), it.email, it.phone, it.document) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text(Copy.customers.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                // The count of the rows shown, as the list under the search reads.
                if (!filtered.isNullOrEmpty()) {
                    Text(Copy.customers.count(filtered.size), style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            SearchBox(state.query, Copy.customers.search) { state.query = it }
            CustomerList(model, filtered, listState)
        }
        val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        ExtendedFloatingActionButton(
            text = { Text(Copy.customers.new) }, icon = { Icon(Icons.Filled.PersonAdd, null) }, expanded = expanded,
            onClick = { model.nav.push(Route.CustomerForm(null)) },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SearchBox(value: String, placeholder: String, onChange: (String) -> Unit) {
    val ext = LocalExtended.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = ext.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = ext.muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconButton({ onChange("") }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, Copy.common.cancel, tint = ext.muted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun CustomerList(model: AppModel, filtered: List<CustomerSummary>?, listState: LazyListState) {
    val state = model.customers
    val all = state.list
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { state.load(refresh = true) }, modifier = Modifier.fillMaxSize()) {
        when {
            all == null && state.error != null -> LazyColumn(Modifier.fillMaxSize()) { item { ErrorState(state.error ?: Copy.common.loadFailed, { state.load() }) } }
            all == null || filtered == null -> SkeletonCards(6)
            all.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    EmptyState(
                        Icons.Outlined.People, Copy.customers.emptyTitle, Copy.customers.emptyBody,
                        action = Copy.customers.new, onAction = { model.nav.push(Route.CustomerForm(null)) },
                    )
                }
            }
            filtered.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.SearchOff, Copy.customers.noMatches) } }
            else -> {
                val sections = remember(filtered) { Search.sections(filtered) { Names.full(it.firstName, it.lastName) } }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 104.dp)) {
                    sections.forEach { (letter, people) ->
                        stickyHeader(key = "h-$letter") {
                            Text(
                                letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        items(people, key = { it.id }) { customer ->
                            CustomerRow(model, customer, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

/** Avatar, name and how to reach them; the next visit and the visits on the trailing side. Long press: book, call, WhatsApp. */
@Composable
private fun CustomerRow(model: AppModel, customer: CustomerSummary, modifier: Modifier = Modifier) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val locale = AppLocale.locale
    val name = Names.full(customer.firstName, customer.lastName)
    val phone = customer.phone?.takeIf { it.isNotBlank() }
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { model.nav.push(Route.CustomerDetail(customer)) }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    phone?.let { Phones.format(it) } ?: customer.email?.takeIf { it.isNotBlank() } ?: Copy.customers.noContact,
                    style = MaterialTheme.typography.bodyMedium, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                BusinessTime.parse(customer.nextVisit)?.let { next ->
                    Text(
                        Copy.customers.next(BusinessTime.mediumDate(BusinessTime.localDate(next, model.zone), locale, withYear = false)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (customer.visits > 0) Text(Copy.customers.visits(customer.visits), style = MaterialTheme.typography.labelMedium, color = ext.muted)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(72.dp, 0.dp)) {
            DropdownMenuItem(
                text = { Text(Copy.customers.newAppointment) },
                leadingIcon = { Icon(Icons.Outlined.EventAvailable, null) },
                onClick = { menu = false; model.nav.push(Route.NewAppointment(customer = customer)) },
            )
            if (phone != null) {
                DropdownMenuItem(
                    text = { Text(Copy.common.call) },
                    leadingIcon = { Icon(Icons.Outlined.Phone, null) },
                    onClick = { menu = false; context.dial(Phones.dialUri(phone)) },
                )
                DropdownMenuItem(
                    text = { Text(Copy.common.whatsapp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = ext.whatsapp) },
                    onClick = { menu = false; context.openUrl(Phones.whatsappUrl(phone)) },
                )
            }
        }
    }
}
