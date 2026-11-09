@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Sale
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/** A sale: what was sold, to whom, how it was paid, and voiding it. */
@Composable
fun SaleDetailScreen(model: AppModel, saleId: String, initial: Sale?) {
    val ext = LocalExtended.current
    val currency = model.currency
    val zone = model.zone
    val loader = rememberLoader(saleId) { model.call { sale(it, saleId) } }
    if (loader.value == null && initial != null && loader.loading) loader.value = initial
    val sale = loader.value
    var confirmVoid by remember { mutableStateOf(false) }
    val action = rememberAction { model.failure(it) }

    DetailScaffold(title = sale?.let { Copy.sales.saleTitle(it.number) } ?: "", onBack = { model.nav.pop() }) { padding ->
        if (sale == null) {
            if (loader.error != null) ErrorState(loader.error!!, { loader.load() }, Modifier.padding(padding)) else SkeletonCards(3, Modifier.padding(padding))
            return@DetailScaffold
        }
        val at = BusinessTime.parse(sale.createdAt)
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(if (sale.status == "void") Copy.sales.void else Copy.sales.paid, if (sale.status == "void") ext.danger else ext.success)
                    if (sale.internal) Chip(Copy.sales.internal, ext.neutral)
                }
                Text(Money.format(sale.total + sale.tipAmount, currency), style = MaterialTheme.typography.headlineMedium.tabular, modifier = Modifier.padding(top = 10.dp))
                at?.let { Text(BusinessTime.longDay(BusinessTime.localDate(it, zone), AppLocale.locale) + " · " + BusinessTime.time(it, zone), color = ext.muted) }
                Hairline(Modifier.padding(vertical = 10.dp))
                InfoRow(Copy.checkout.customer, sale.customer?.let { Names.full(it.firstName, it.lastName) } ?: Copy.sales.noCustomer)
                if (model.catalog.activeLocations.size > 1) sale.locationName?.let { InfoRow(Copy.common.location, it) }
                sale.soldByName?.let { InfoRow(Copy.sales.soldBy, it) }
            }

            SectionTitle(Copy.sales.items)
            AppCard {
                sale.items.forEachIndexed { index, item ->
                    if (index > 0) Hairline(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull("${item.quantity} × ${Money.format(item.unitPrice, currency)}", item.resourceName).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted,
                            )
                            if (item.discountAmount > 0) Text(Copy.checkout.discount + " " + Money.negative(item.discountAmount, currency), style = MaterialTheme.typography.bodySmall, color = ext.success.content)
                        }
                        Text(Money.format(item.total, currency), style = MaterialTheme.typography.bodyLarge.tabular)
                    }
                }
                Hairline(Modifier.padding(vertical = 10.dp))
                InfoRow(Copy.checkout.subtotal, Money.format(sale.subtotal, currency))
                if (sale.discountAmount > 0) InfoRow(Copy.checkout.discount, Money.negative(sale.discountAmount, currency), valueColor = ext.success.content)
                InfoRow(Copy.checkout.total, Money.format(sale.total, currency), strong = true)
                sale.tips.forEach { tip -> InfoRow(Copy.sales.tipFor(tip.resourceName ?: "—"), Money.format(tip.amount, currency)) }
            }

            SectionTitle(Copy.sales.payments)
            AppCard {
                sale.payments.forEach { payment -> InfoRow(Copy.sales.method(payment.method), Money.format(payment.amount, currency)) }
                if (sale.payments.isEmpty()) Text("—", color = ext.muted)
            }
            sale.note?.takeIf { it.isNotBlank() }?.let {
                SectionTitle(Copy.checkout.note)
                AppCard { Text(it) }
            }
            if (sale.status == "paid") {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { confirmVoid = true }, enabled = !action.busy, modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = MaterialTheme.shapes.small,
                ) { Icon(Icons.Outlined.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(Copy.sales.voidConfirm) }
            }
        }
    }
    if (confirmVoid && sale != null) {
        ConfirmDialog(
            Copy.sales.voidTitle(sale.number), Copy.sales.voidBody, Copy.sales.voidConfirm,
            onConfirm = {
                confirmVoid = false
                action.run {
                    loader.value = model.call { voidSale(it, sale.id) }
                    model.toast(Copy.sales.voided)
                    model.agenda.invalidate()
                }
            },
            onDismiss = { confirmVoid = false }, danger = true,
        )
    }
}
