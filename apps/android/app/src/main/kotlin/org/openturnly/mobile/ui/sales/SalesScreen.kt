@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
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

/** A day, week or month around an anchor day, inclusive. */
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

fun spanLabel(span: Span, anchor: LocalDate, today: LocalDate): String {
    val locale = AppLocale.locale
    val (from, to) = spanRange(span, anchor)
    return when (span) {
        Span.DAY -> if (anchor == today) Copy.common.today else BusinessTime.shortDay(anchor, locale)
        Span.WEEK -> "${BusinessTime.mediumDate(from, locale, withYear = false)} – ${BusinessTime.mediumDate(to, locale, withYear = false)}"
        Span.MONTH -> BusinessTime.monthYear(anchor, locale)
    }
}

/** The period chooser shared by sales and the cash register: day, week or month, and arrows. */
@Composable
fun SpanBar(span: Span, anchor: LocalDate, today: LocalDate, onSpan: (Span) -> Unit, onAnchor: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(Copy.sales.day, Copy.sales.week, Copy.sales.month)
            Span.entries.forEachIndexed { index, value ->
                SegmentedButton(span == value, { onSpan(value) }, SegmentedButtonDefaults.itemShape(index, 3)) { Text(labels[index]) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onAnchor(spanStep(span, anchor, -1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Copy.common.back) }
            Text(
                spanLabel(span, anchor, today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).clickable { onAnchor(today) },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton({ onAnchor(spanStep(span, anchor, 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
        }
    }
}

/** "Ventas": what was charged in a period, how it was paid, and the deposits received. */
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
                when {
                    data == null && loader.error != null -> Column(Modifier.fillMaxSize()) { ErrorState(loader.error!!, { loader.load() }) }
                    data == null -> SkeletonCards(4, Modifier.padding(top = 12.dp))
                    tab == 0 -> SalesList(model, data.first, listState)
                    else -> DepositsList(model, data.second)
                }
            }
        }
        val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        ExtendedFloatingActionButton(
            text = { Text(Copy.sales.new) }, icon = { Icon(Icons.Filled.Add, null) }, expanded = expanded,
            onClick = { model.nav.push(Route.Checkout(null)) },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesList(model: AppModel, sales: List<Sale>, listState: androidx.compose.foundation.lazy.LazyListState) {
    val ext = LocalExtended.current
    val currency = model.currency
    val paid = sales.filter { it.status == "paid" }
    val counted = paid.filter { !it.internal }
    val total = counted.sumOf { it.total }
    val tips = paid.sumOf { it.tipAmount }
    val byMethod = paid.flatMap { it.payments }.groupBy { it.method }.mapValues { (_, list) -> list.sumOf { it.amount } }.filterValues { it > 0 }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Text(Copy.sales.totalSold, style = MaterialTheme.typography.labelLarge, color = ext.muted)
                Text(Money.format(total, currency), style = MaterialTheme.typography.headlineMedium.tabular, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Copy.sales.count(counted.size), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                    if (tips > 0) Text("${Copy.sales.tips}: ${Money.format(tips, currency)}", style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                }
                if (byMethod.isNotEmpty()) {
                    Hairline(Modifier.padding(vertical = 12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        byMethod.forEach { (method, amount) -> Chip("${Copy.sales.method(method)} · ${Money.format(amount, currency)}", ext.neutral) }
                    }
                }
            }
        }
        if (sales.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, Copy.sales.empty) }
        items(sales, key = { it.id }) { sale -> SaleRow(model, sale) { model.nav.push(Route.SaleDetail(sale.id, sale)) } }
    }
}

@Composable
fun SaleRow(model: AppModel, sale: Sale, onClick: () -> Unit) {
    val ext = LocalExtended.current
    val zone = model.zone
    val at = BusinessTime.parse(sale.createdAt)
    val void = sale.status == "void"
    AppCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${sale.number}", style = MaterialTheme.typography.titleMedium.tabular)
                    Text(
                        listOfNotNull(at?.let { BusinessTime.shortDay(BusinessTime.localDate(it, zone), AppLocale.locale) }, at?.let { BusinessTime.time(it, zone) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted,
                    )
                }
                Text(
                    sale.customer?.let { Names.full(it.firstName, it.lastName) } ?: Copy.sales.noCustomer,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val names = sale.items.map { it.name }
                Text(
                    names.take(2).joinToString(", ") + if (names.size > 2) " +${names.size - 2}" else "",
                    style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(sale.total + sale.tipAmount, model.currency), style = MaterialTheme.typography.titleMedium.tabular,
                    textDecoration = if (void) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (void) ext.muted else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (void) Chip(Copy.sales.void, ext.danger)
                    if (sale.internal) Chip(Copy.sales.internal, ext.neutral)
                    if (!void && !sale.internal) sale.payments.map { it.method }.distinct().take(2).forEach { Chip(Copy.sales.method(it), ext.info) }
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
        if (deposits.isEmpty()) item { EmptyState(Icons.Outlined.Savings, Copy.sales.emptyDeposits) }
        items(deposits, key = { it.id }) { deposit ->
            val at = BusinessTime.parse(deposit.createdAt)
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(deposit.customer?.let { Names.full(it.firstName, it.lastName) } ?: Copy.sales.noCustomer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                at?.let { BusinessTime.shortDay(BusinessTime.localDate(it, zone), AppLocale.locale) + " · " + BusinessTime.time(it, zone) },
                                Copy.sales.method(deposit.method),
                                deposit.appointmentCode?.let { Copy.sales.forAppointment(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = ext.muted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(deposit.amount, model.currency), style = MaterialTheme.typography.titleMedium.tabular)
                        val (label, tone) = when {
                            deposit.status == "void" -> Copy.sales.depositReturned to ext.danger
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
