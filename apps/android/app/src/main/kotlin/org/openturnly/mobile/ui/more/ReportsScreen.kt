@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.ReportSummary
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Periods
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/** The key numbers the web's reports show, for a period: summary, tips and commissions. */
@Composable
fun ReportsScreen(model: AppModel) {
    var preset by rememberSaveable { mutableStateOf(Periods.Preset.WEEK) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val range = Periods.range(preset, model.today)
    val from = range.start.toString()
    val to = range.end.toString()
    val summary = rememberLoader(model.business?.id, from, to, tab == 0) { if (tab == 0) model.call { reportSummary(it, from, to) } else null }
    val tips = rememberLoader(model.business?.id, from, to, tab == 1) { if (tab == 1) model.call { reportTips(it, from, to) } else null }
    val commissions = rememberLoader(model.business?.id, from, to, tab == 2) { if (tab == 2) model.call { reportCommissions(it, from, to) } else null }

    DetailScaffold(title = Copy.reports.title, onBack = { model.nav.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Periods.Preset.entries.forEach { option -> FilterChip(preset == option, { preset = option }, { Text(Copy.reports.preset(option)) }) }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(Copy.reports.summary, Copy.reports.tips, Copy.reports.commissions).forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }, unselectedContentColor = LocalExtended.current.muted) }
            }
            val refreshing = summary.refreshing || tips.refreshing || commissions.refreshing
            PullToRefreshBox(refreshing, { listOf(summary, tips, commissions)[tab].load(refresh = true) }, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val loader = listOf(summary, tips, commissions)[tab]
                    when {
                        loader.value == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                        loader.value == null -> SkeletonCards(3, Modifier.padding(horizontal = 0.dp))
                        tab == 0 -> Summary(model, summary.value!!)
                        tab == 1 -> Tips(model, tips.value!!)
                        else -> Commissions(model, commissions.value!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(model: AppModel, report: ReportSummary) {
    val ext = LocalExtended.current
    val currency = model.currency
    val current = report.current
    val before = report.before
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi(Copy.reports.bookings, current.bookings.toString(), Periods.change(current.bookings.toDouble(), before.bookings.toDouble()), before.bookings.toString(), Modifier.weight(1f))
        val points = if (current.occupancy != null && before.occupancy != null) ((current.occupancy - before.occupancy) * 100).roundToInt() else null
        Kpi(Copy.reports.occupancy, current.occupancy?.let { "${(it * 100).roundToInt()} %" } ?: "—",
            points?.toDouble()?.div(100), before.occupancy?.let { "${(it * 100).roundToInt()} %" } ?: "—", Modifier.weight(1f), unit = Copy.reports.points)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi(Copy.reports.newCustomers, current.newCustomers.toString(), Periods.change(current.newCustomers.toDouble(), before.newCustomers.toDouble()), before.newCustomers.toString(), Modifier.weight(1f))
        Kpi(Copy.reports.billed, Money.format(current.billed, currency), Periods.change(current.billed.toDouble(), before.billed.toDouble()), Money.format(before.billed, currency), Modifier.weight(1f))
    }

    var measure by rememberSaveable { mutableIntStateOf(0) }
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(measure == 0, { measure = 0 }, { Text(Copy.reports.chartBookings) })
            FilterChip(measure == 1, { measure = 1 }, { Text(Copy.reports.chartBilled) })
        }
        val points = report.series.points
        if (points.isEmpty()) Text(Copy.reports.nothing, color = ext.muted)
        else {
            val values = points.map { if (measure == 0) it.bookings.toDouble() else it.billed.toDouble() }
            val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val color = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            Text(
                if (measure == 0) values.sum().roundToInt().toString() else Money.format(values.sum().toLong(), currency),
                style = MaterialTheme.typography.titleLarge.tabular, modifier = Modifier.padding(top = 8.dp),
            )
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)) {
                val gap = 4.dp.toPx()
                val width = (size.width - gap * (values.size - 1)) / values.size
                values.forEachIndexed { index, value ->
                    val left = index * (width + gap)
                    drawRoundRect(track, Offset(left, 0f), Size(width, size.height), CornerRadius(6f, 6f))
                    val height = (value / max * size.height).toFloat().coerceAtLeast(if (value > 0) 4f else 0f)
                    drawRoundRect(color, Offset(left, size.height - height), Size(width, height), CornerRadius(6f, 6f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val locale = AppLocale.locale
                Text(BusinessTime.parseDate(points.first().start)?.let { BusinessTime.mediumDate(it, locale, withYear = false) } ?: "", style = MaterialTheme.typography.labelSmall, color = ext.muted, modifier = Modifier.weight(1f))
                Text(BusinessTime.parseDate(points.last().start)?.let { BusinessTime.mediumDate(it, locale, withYear = false) } ?: "", style = MaterialTheme.typography.labelSmall, color = ext.muted)
            }
        }
    }

    if (report.origin.isNotEmpty()) {
        SectionTitle(Copy.reports.origin)
        AppCard {
            val total = report.origin.values.sum().coerceAtLeast(1)
            report.origin.entries.sortedByDescending { it.value }.forEach { (key, count) -> BarRow(Copy.reports.originLabel(key), count.toString(), count.toFloat() / total) }
        }
    }
    SectionTitle(Copy.reports.topSold)
    AppCard {
        if (report.topServices.isEmpty()) Text(Copy.reports.nothing, color = ext.muted)
        report.topServices.forEachIndexed { index, item ->
            if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Copy.reports.sold(item.quantity), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                }
                Text(Money.format(item.amount, currency), style = MaterialTheme.typography.bodyLarge.tabular)
            }
        }
    }
    SectionTitle(Copy.reports.byProfessional)
    AppCard {
        val max = report.byProfessional.maxOfOrNull { it.bookings }?.coerceAtLeast(1) ?: 1
        if (report.byProfessional.isEmpty()) Text(Copy.reports.nothing, color = ext.muted)
        report.byProfessional.forEach { row -> BarRow(row.name.ifEmpty { Copy.reports.noProfessional }, row.bookings.toString(), row.bookings.toFloat() / max) }
    }
    SectionTitle(Copy.reports.today)
    AppCard {
        if (report.today.isEmpty()) Text(Copy.reports.noneToday, color = ext.muted)
        report.today.forEachIndexed { index, row ->
            if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.time, style = MaterialTheme.typography.titleSmall.tabular, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.customer ?: "—", style = MaterialTheme.typography.bodyLarge)
                    Text(listOfNotNull(row.service, row.professional).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                }
                if (row.paid) Chip(Copy.reports.paidBadge, ext.info)
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, change: Double?, before: String, modifier: Modifier, unit: String = "%") {
    val ext = LocalExtended.current
    AppCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ext.muted)
        Text(value, style = MaterialTheme.typography.headlineSmall.tabular, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
        if (change == null) Text(Copy.reports.noComparison, style = MaterialTheme.typography.labelSmall, color = ext.muted, maxLines = 1)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            val up = change >= 0
            val tone = if (up) ext.success else ext.danger
            Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = tone.content, modifier = Modifier.size(16.dp))
            Text("${if (up) "+" else ""}${(change * 100).roundToInt()} $unit", style = MaterialTheme.typography.labelMedium, color = tone.content, modifier = Modifier.padding(start = 4.dp))
            Text(" · " + Copy.reports.vs(before), style = MaterialTheme.typography.labelSmall, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyMedium.tabular, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun Tips(model: AppModel, report: org.openturnly.mobile.api.TipsReport) {
    val ext = LocalExtended.current
    val currency = model.currency
    if (report.totals.isEmpty()) { EmptyState(Icons.Outlined.BarChart, Copy.reports.noTips); return }
    AppCard {
        Text(Copy.reports.tips, style = MaterialTheme.typography.labelLarge, color = ext.muted)
        Text(Money.format(report.totals.sumOf { it.amount }, currency), style = MaterialTheme.typography.headlineMedium.tabular, color = MaterialTheme.colorScheme.primary)
    }
    AppCard {
        report.totals.forEachIndexed { index, total ->
            if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
            InfoRow("${total.name.ifEmpty { Copy.reports.noProfessional }} · ${total.count}", Money.format(total.amount, currency))
        }
    }
    AppCard {
        report.tips.forEachIndexed { index, tip ->
            if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
            val at = BusinessTime.parse(tip.createdAt)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tip.resourceName ?: "—", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(at?.let { BusinessTime.shortDay(BusinessTime.localDate(it, model.zone), AppLocale.locale) }, tip.sale?.let { Copy.agenda.saleNumber(it.number) }, tip.customer).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = ext.muted,
                    )
                }
                Text(Money.format(tip.amount, currency), style = MaterialTheme.typography.bodyLarge.tabular)
            }
        }
    }
}

@Composable
private fun Commissions(model: AppModel, report: org.openturnly.mobile.api.CommissionsReport) {
    val ext = LocalExtended.current
    val currency = model.currency
    if (report.professionals.isEmpty()) { EmptyState(Icons.Outlined.BarChart, Copy.reports.noProfessionals); return }
    AppCard {
        Text(Copy.reports.toPay, style = MaterialTheme.typography.labelLarge, color = ext.muted)
        Text(Money.format(report.professionals.sumOf { it.total }, currency), style = MaterialTheme.typography.headlineMedium.tabular, color = MaterialTheme.colorScheme.primary)
    }
    report.professionals.forEach { row ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Money.format(row.total, currency), style = MaterialTheme.typography.titleMedium.tabular, color = MaterialTheme.colorScheme.primary)
            }
            Hairline(Modifier.padding(vertical = 8.dp))
            InfoRow("${Copy.reports.services} (${row.servicesCount})", Copy.reports.rateOf(trim(row.serviceRate), Money.format(row.services, currency)) + " = " + Money.format(row.serviceCommission, currency))
            InfoRow("${Copy.reports.products} (${row.productsCount})", Copy.reports.rateOf(trim(row.productRate), Money.format(row.products, currency)) + " = " + Money.format(row.productCommission, currency))
            InfoRow(Copy.reports.tips, Money.format(row.tips, currency))
        }
    }
}

private fun trim(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
