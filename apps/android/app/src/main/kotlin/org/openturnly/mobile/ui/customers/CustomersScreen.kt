@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.openturnly.mobile.ui.customers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import kotlinx.coroutines.delay
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.QuickAction
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.email
import org.openturnly.mobile.ui.openUrl

/** Lowercase without accents, so "maria" finds "María". */
fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** The same rules as the server's search: name, email or document; phone when there are 3+ digits. */
fun matches(customer: CustomerSummary, query: String): Boolean {
    val q = fold(query.trim())
    if (q.isEmpty()) return true
    val digits = query.filter { it.isDigit() }
    if (digits.length >= 3 && (customer.phone?.filter { it.isDigit() }?.contains(digits) == true)) return true
    return fold(Names.full(customer.firstName, customer.lastName)).contains(q) ||
        (customer.email?.let { fold(it).contains(q) } == true) ||
        (customer.document?.let { fold(it).contains(q) } == true)
}

/**
 * Everyone who books with the business. Admins get the whole list (searched on
 * the phone as they type); professionals can only search, as the server allows.
 */
@Composable
fun CustomersScreen(model: AppModel) {
    val state = model.customers
    val ext = LocalExtended.current
    val listState = rememberLazyListState()
    var contact by remember { mutableStateOf<CustomerSummary?>(null) }
    LaunchedEffect(model.business?.id) { if (model.isAdmin) { if (state.list == null) state.load() else state.refreshIfStale() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text(Copy.customers.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                state.list?.let { Text(Copy.customers.count(it.size), style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(bottom = 4.dp)) }
            }
            SearchBox(state.query, Copy.customers.search) { state.query = it }
            if (model.isAdmin) AdminList(model, listState) else ProfessionalSearch(model) { contact = it }
        }
        if (model.isAdmin) {
            val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
            ExtendedFloatingActionButton(
                text = { Text(Copy.customers.new) }, icon = { Icon(Icons.Filled.PersonAdd, null) }, expanded = expanded,
                onClick = { model.nav.push(Route.CustomerForm(null)) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    contact?.let { ContactSheet(model, it) { contact = null } }
}

@Composable
fun SearchBox(value: String, placeholder: String, onChange: (String) -> Unit) {
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
private fun AdminList(model: AppModel, listState: androidx.compose.foundation.lazy.LazyListState) {
    val state = model.customers
    val all = state.list
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { state.load(refresh = true) }, modifier = Modifier.fillMaxSize()) {
        when {
            all == null && state.error != null -> Column(Modifier.fillMaxSize()) { ErrorState(state.error ?: Copy.common.loadFailed, { state.load() }) }
            all == null -> SkeletonCards(6)
            all.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.People, Copy.customers.emptyTitle, Copy.customers.emptyBody) } }
            else -> {
                val filtered = remember(all, state.query) { all.filter { matches(it, state.query) } }
                if (filtered.isEmpty()) {
                    LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.SearchOff, Copy.customers.noMatches) } }
                } else {
                    val groups = remember(filtered) {
                        filtered.groupBy { fold(it.firstName.ifBlank { it.lastName }).firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 104.dp)) {
                        groups.forEach { (letter, people) ->
                            stickyHeader(key = "h-$letter") {
                                Text(
                                    letter.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 6.dp),
                                )
                            }
                            items(people, key = { it.id }) { customer ->
                                CustomerRow(model, customer, Modifier.animateItem()) { model.nav.push(Route.CustomerDetail(customer)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerRow(model: AppModel, customer: CustomerSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ext = LocalExtended.current
    val locale = AppLocale.locale
    val name = Names.full(customer.firstName, customer.lastName)
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                customer.phone?.let { Phones.format(it) } ?: customer.email ?: Copy.customers.noContact,
                style = MaterialTheme.typography.bodyMedium, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (customer.visits > 0) Text(Copy.customers.visits(customer.visits), style = MaterialTheme.typography.labelMedium, color = ext.muted)
            val next = BusinessTime.parse(customer.nextVisit)
            if (next != null) Text(
                Copy.customers.next(BusinessTime.mediumDate(BusinessTime.localDate(next, model.zone), locale, withYear = false)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Professionals search by name or phone (two letters at least) and reach the customer from here. */
@Composable
private fun ProfessionalSearch(model: AppModel, onPick: (CustomerSummary) -> Unit) {
    val query = model.customers.query.trim()
    var results by remember { mutableStateOf<List<CustomerSummary>?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.length < 2) { results = null; return@LaunchedEffect }
        loading = true
        delay(300)
        results = runCatching { model.call { customers(it, query) } }.getOrNull() ?: emptyList()
        loading = false
    }
    val found = results
    when {
        query.length < 2 -> EmptyState(Icons.Filled.Search, Copy.customers.proSearchTitle, Copy.customers.proSearchBody)
        found == null || loading && found.isEmpty() -> SkeletonCards(3)
        found.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, Copy.customers.noMatches)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(found, key = { it.id }) { customer -> CustomerRow(model, customer) { onPick(customer) } }
        }
    }
}

/** Call, WhatsApp, e-mail or book: what a professional can do with a customer. */
@Composable
fun ContactSheet(model: AppModel, customer: CustomerSummary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ext = LocalExtended.current
    val name = Names.full(customer.firstName, customer.lastName)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name, size = 64.dp)
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
            customer.phone?.let { Text(Phones.format(it), color = ext.muted) }
            customer.email?.let { Text(it, color = ext.muted) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                customer.phone?.let { phone ->
                    QuickAction(Icons.Outlined.Phone, Copy.common.call) { context.dial(Phones.dialUri(phone)) }
                    QuickAction(Icons.AutoMirrored.Outlined.Chat, Copy.common.whatsapp, tint = ext.whatsapp) { context.openUrl(Phones.whatsappUrl(phone)) }
                }
                customer.email?.let { mail -> QuickAction(Icons.Outlined.Email, Copy.common.email) { context.email(mail) } }
                QuickAction(Icons.Outlined.EventAvailable, Copy.customers.book) { onDismiss(); model.nav.push(Route.NewAppointment(customer = customer)) }
            }
        }
    }
}
