@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Deposit
import org.openturnly.mobile.api.Sale
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.SalesTotals
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

enum class Span { DAY, WEEK, MONTH }

/** A day, week or month around an anchor day, inclusive. Weeks start on Monday. */
fun spanRange(span: Span, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (span) {
    Span.DAY -> anchor to anchor
    Span.WEEK -> BusinessTime.weekStart(anchor).let { it to it.plusDays(6) }
    Span.MONTH -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
}

fun spanStep(span: Span, anchor: LocalDate, by: Long): LocalDate = when (span) {
    Span.DAY -> anchor.plusDays(by)
    Span.WEEK -> anchor.plusWeeks(by)
    Span.MONTH -> anchor.plusMonths(by)
}

/** Whether the period shown covers a day: the «Hoy» button shows when it doesn't cover today. */
fun spanIncludes(span: Span, anchor: LocalDate, day: LocalDate): Boolean {
    val (from, to) = spanRange(span, anchor)
    return !day.isBefore(from) && !day.isAfter(to)
}

/** «Hoy» / «Lun 9 nov», «2 nov – 8 nov», «Noviembre 2026». */
fun spanLabel(span: Span, anchor: LocalDate, today: LocalDate): String {
    val locale = AppLocale.locale
    val (from, to) = spanRange(span, anchor)
    return when (span) {
        Span.DAY -> if (anchor == today) Copy.common.today else BusinessTime.shortDay(anchor, locale)
        Span.WEEK -> "${BusinessTime.mediumDate(from, locale, withYear = false)} – ${BusinessTime.mediumDate(to, locale, withYear = false)}"
        Span.MONTH -> BusinessTime.monthYear(anchor, locale)
    }
}

/**
 * The period chooser: day, week or month, arrows around the label, the label
 * opening a calendar, and «Hoy» when the period shown doesn't include today.
 */
@Composable
fun SpanBar(span: Span, anchor: LocalDate, today: LocalDate, onSpan: (Span) -> Unit, onAnchor: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(Copy.sales.day, Copy.sales.week, Copy.sales.month)
            Span.entries.forEachIndexed { index, value ->
                SegmentedButton(span == value, { onSpan(value) }, SegmentedButtonDefaults.itemShape(index, Span.entries.size)) { Text(labels[index]) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onAnchor(spanStep(span, anchor, -1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Copy.sales.previous) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val label = spanLabel(span, anchor, today)
                TextButton({ picking = true }, Modifier.semantics { contentDescription = "${Copy.sales.pickDate}: $label" }) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = LocalExtended.current.muted)
                }
                if (!spanIncludes(span, anchor, today)) TextButton({ onAnchor(today) }) { Text(Copy.common.today) }
            }
            IconButton({ onAnchor(spanStep(span, anchor, 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, Copy.sales.next) }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = BusinessTime.pickerMillis(anchor))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { onAnchor(BusinessTime.fromPickerMillis(it)) }; picking = false }) { Text(Copy.common.ok) } },
            dismissButton = { TextButton({ picking = false }) { Text(Copy.common.cancel) } },
        ) { DatePicker(state) }
    }
}

/** «Ventas»: what came in during a period, how it was paid, and the deposits received. */
@Composable
fun SalesScreen(model: AppModel) {
    val ext = LocalExtended.current
    val today = model.today
    var span by rememberSaveable { mutableStateOf(Span.DAY) }
    var anchor by rememberSaveable { mutableStateOf(today) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val (from, to) = spanRange(span, anchor)
    val loader = rememberLoader(model.business?.id, from, to) {
        val (start, end) = BusinessTime.range(from, to, model.zone)
        coroutineScope {
            val sales = async { model.call { sales(it, BusinessTime.iso(start), BusinessTime.iso(end)) } }
            val deposits = async { model.call { deposits(it, BusinessTime.iso(start), BusinessTime.iso(end)) } }
            sales.await() to deposits.await()
        }
    }
    val listState = rememberLazyListState()
    val newSale = { model.nav.push(Route.Checkout(null)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(Copy.sales.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp))
            SpanBar(span, anchor, today, { span = it }, { anchor = it })
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text(Copy.sales.tabSales) }, unselectedContentColor = ext.muted)
                Tab(tab == 1, { tab = 1 }, text = { Text(Copy.sales.tabDeposits) }, unselectedContentColor = ext.muted)
            }
            PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize()) {
                val data = loader.value
                val error = loader.error
                when {
                    data == null && error != null -> Column(Modifier.fillMaxSize()) { ErrorState(error, { loader.load() }) }
                    data == null -> SkeletonCards(4, Modifier.padding(top = 12.dp))
                    tab == 0 -> SalesList(model, data.first, withDay = span != Span.DAY, listState, newSale)
                    else -> DepositsList(model, data.second)
                }
            }
        }
        val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        ExtendedFloatingActionButton(
            text = { Text(Copy.sales.new) }, icon = { Icon(Icons.Filled.Add, null) }, expanded = expanded,
            onClick = newSale,
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SalesList(model: AppModel, sales: List<Sale>, withDay: Boolean, listState: LazyListState, onNewSale: () -> Unit) {
    val ext = LocalExtended.current
    val currency = model.currency
    val summary = SalesTotals.summary(sales)

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "summary") {
            AppCard {
                Text(Copy.sales.collected, style = MaterialTheme.typography.labelLarge, color = ext.muted)
                Text(Money.format(summary.collected, currency), style = MaterialTheme.typography.headlineMedium.tabular, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(Copy.sales.count(summary.count), summary.average.takeIf { summary.count > 0 }?.let { Copy.sales.average(Money.format(it, currency)) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(top = 2.dp),
                )
                if (summary.tips > 0) Text(Copy.sales.includesTips(Money.format(summary.tips, currency)), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                if (summary.byMethod.isNotEmpty()) {
                    Hairline(Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Text(
                        Copy.sales.byMethod.uppercase(), style = MaterialTheme.typography.labelMedium, color = ext.muted, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                    summary.byMethod.forEach { (method, amount) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Copy.sales.method(method), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Money.format(amount, currency), style = MaterialTheme.typography.bodyMedium.tabular, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (sales.isEmpty()) item(key = "empty") { EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, Copy.sales.empty, action = Copy.sales.new, onAction = onNewSale) }
        items(sales, key = { it.id }) { sale -> SaleRow(model, sale, withDay) { model.nav.push(Route.SaleDetail(sale.id, sale)) } }
    }
}

/**
 * One sale: the customer, its items, «#12 · 10:20 · Efectivo, Tarjeta» (with the
 * day over a week or a month), and what came in with a chip when void or internal.
 */
@Composable
fun SaleRow(model: AppModel, sale: Sale, withDay: Boolean = false, onClick: () -> Unit) {
    val ext = LocalExtended.current
    val zone = model.zone
    val at = BusinessTime.parse(sale.createdAt)
    val void = sale.status == "void"
    AppCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    sale.customer?.let { Names.full(it.firstName, it.lastName) } ?: Copy.sales.noCustomer,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val items = SalesTotals.items(sale)
                if (items.isNotEmpty()) Text(items, style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val whenText = at?.let {
                    val time = BusinessTime.time(it, zone)
                    if (withDay) "${BusinessTime.shortDay(BusinessTime.localDate(it, zone), AppLocale.locale)} $time" else time
                }
                val methods = SalesTotals.methods(sale).joinToString(", ") { Copy.sales.method(it) }.takeIf { it.isNotEmpty() }
                Text(
                    listOfNotNull("#${sale.number}", whenText, methods).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(sale.total + sale.tipAmount, model.currency), style = MaterialTheme.typography.titleMedium.tabular,
                    textDecoration = if (void) TextDecoration.LineThrough else null,
                    color = if (void) ext.muted else MaterialTheme.colorScheme.onSurface,
                )
                when {
                    void -> Chip(Copy.sales.void, ext.danger, Modifier.padding(top = 4.dp))
                    sale.internal -> Chip(Copy.sales.internal, ext.neutral, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DepositsList(model: AppModel, deposits: List<Deposit>) {
    val ext = LocalExtended.current
    val zone = model.zone
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (deposits.isEmpty()) item(key = "empty") { EmptyState(Icons.Outlined.Savings, Copy.sales.emptyDeposits) }
        items(deposits, key = { it.id }) { deposit ->
            val at = BusinessTime.parse(deposit.createdAt)
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            deposit.customer?.let { Names.full(it.firstName, it.lastName) } ?: Copy.sales.noCustomer,
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                at?.let { BusinessTime.shortDay(BusinessTime.localDate(it, zone), AppLocale.locale) },
                                at?.let { BusinessTime.time(it, zone) },
                                Copy.sales.method(deposit.method),
                                deposit.appointmentCode?.let { Copy.sales.forAppointment(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = ext.muted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(deposit.amount, model.currency), style = MaterialTheme.typography.titleMedium.tabular)
                        val (label, tone) = when {
                            deposit.status != "active" -> Copy.sales.depositReturned to ext.neutral
                            deposit.appliedSale != null -> Copy.sales.depositUsed(deposit.appliedSale.number) to ext.info
                            else -> Copy.sales.depositAvailable to ext.success
                        }
                        Chip(label, tone, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
