@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.sales

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Deposit
import org.openturnly.mobile.api.GiftCard
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.Product
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Checkout
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.agenda.bookableServices
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

private data class PaymentRow(val key: Int, val method: String, val amount: String)

/**
 * "Cobrar" / "Cerrar venta" for an appointment, or "Nueva venta" from Ventas.
 *
 * It works like the web's checkout: the appointment's service as the first
 * line, more services or products, a code or gift card, a tip, the customer's
 * available deposits, and how the rest was paid. The amounts must add up to the
 * total plus tips before it can be recorded.
 */
@Composable
fun CheckoutScreen(model: AppModel, appointment: Appointment?) {
    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val currency = model.currency
    val catalog = model.catalog
    val locations = catalog.activeLocations
    var keySeed by remember { mutableStateOf(1) }
    fun nextKey() = "l${keySeed++}"

    var customer by remember { mutableStateOf(appointment?.customer?.let { CustomerSummary(id = it.id, firstName = it.firstName, lastName = it.lastName, phone = it.phone, email = it.email) }) }
    var locationId by remember { mutableStateOf(appointment?.locationId ?: locations.firstOrNull()?.id) }
    val lines = remember {
        mutableStateListOf<Checkout.Line>().apply {
            if (appointment != null) add(
                Checkout.Line(
                    key = "appointment", kind = "service", name = appointment.serviceName, serviceId = appointment.serviceId,
                    appointmentId = appointment.id, resourceId = appointment.resourceId, unitPrice = appointment.priceAmount ?: 0L,
                    discountAmount = appointment.discountAmount,
                ),
            )
        }
    }
    var giftCard by remember { mutableStateOf<GiftCard?>(null) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var tipText by remember { mutableStateOf("") }
    var tipResource by remember { mutableStateOf(appointment?.resourceId) }
    val selectedDeposits = remember { mutableStateListOf<String>() }
    val payments = remember { mutableStateListOf(PaymentRow(0, "cash", "")) }
    var note by remember { mutableStateOf("") }
    var internal by remember { mutableStateOf(false) }
    var addingService by remember { mutableStateOf(false) }
    var addingProduct by remember { mutableStateOf(false) }
    var pickingCustomer by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Checkout.Line?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val deposits = rememberLoader(customer?.id) {
        val id = customer?.id ?: return@rememberLoader emptyList<Deposit>()
        model.call { deposits(it, customerId = id) }.filter { it.status == "active" && it.appliedSale == null }
    }
    LaunchedEffect(deposits.value) {
        // Deposits left for this appointment are used by default, as on the web.
        deposits.value?.filter { it.appointmentId != null && it.appointmentId == appointment?.id }?.forEach { if (it.id !in selectedDeposits) selectedDeposits.add(it.id) }
    }

    val subtotal = Checkout.subtotal(lines)
    val discount = if (giftCard?.kind == "discount") Checkout.codeDiscount(subtotal, giftCard?.percent, giftCard?.amount) else 0L
    val total = Checkout.total(subtotal, discount)
    val tips = Money.parse(tipText, currency) ?: 0L
    val due = Checkout.due(total, tips)
    val depositSum = deposits.value.orEmpty().filter { it.id in selectedDeposits }.sumOf { it.amount }
    val giftPay = if (giftCard?.kind == "balance") Checkout.giftCardPayment(giftCard?.remaining ?: 0L, due - depositSum) else 0L
    val remaining = Checkout.remaining(due, depositSum, giftPay)
    val extra = payments.drop(1).sumOf { Money.parse(it.amount, currency) ?: 0L }
    val firstAmount = remaining - extra
    val paid = depositSum + giftPay + maxOf(0L, firstAmount) + extra
    val balance = Checkout.balance(due, paid)
    val closing = appointment != null && remaining == 0L && depositSum > 0

    val action = rememberAction { failure -> formError = failure.message }

    fun submit() {
        formError = null
        if (lines.isEmpty()) { formError = Copy.checkout.noLines; return }
        if (balance != 0L) { formError = if (balance < 0) Copy.checkout.missing(Money.format(-balance, currency)) else Copy.checkout.over(Money.format(balance, currency)); return }
        if (tips > 0 && tipResource == null) { formError = Copy.checkout.tipsHint; return }
        val paymentList = buildList<Map<String, Any?>> {
            deposits.value.orEmpty().filter { it.id in selectedDeposits }.forEach { add(mapOf("method" to "deposit", "amount" to it.amount, "depositId" to it.id)) }
            if (giftPay > 0) add(mapOf("method" to "gift_card", "amount" to giftPay, "giftCardId" to giftCard?.id))
            if (firstAmount > 0) add(mapOf("method" to payments.first().method, "amount" to firstAmount))
            payments.drop(1).forEach { row -> Money.parse(row.amount, currency)?.takeIf { it > 0 }?.let { add(mapOf("method" to row.method, "amount" to it)) } }
        }
        val body = objWithout(
            "locationId" to (locationId ?: Omit),
            "customerId" to customer?.id,
            "internal" to internal,
            "giftCardId" to (giftCard?.takeIf { it.kind == "discount" }?.id ?: Omit),
            "items" to lines.map { line ->
                buildMap<String, Any?> {
                    put("kind", line.kind)
                    put("serviceId", line.serviceId)
                    line.productId?.let { put("productId", it) }
                    line.appointmentId?.let { put("appointmentId", it) }
                    put("resourceId", line.resourceId)
                    put("quantity", line.quantity)
                    put("unitPrice", line.unitPrice)
                    put("discountAmount", line.discountAmount)
                }
            },
            "discountAmount" to discount,
            "tips" to (if (tips > 0 && tipResource != null) listOf(mapOf("resourceId" to tipResource, "amount" to tips)) else emptyList()),
            "payments" to paymentList,
            "note" to (note.trim().ifEmpty { null } ?: Omit),
        )
        action.run {
            val sale = model.call { createSale(it, body) }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            model.toast(Copy.checkout.done(sale.number))
            appointment?.let { model.agenda.reloadFor(BusinessTime.parse(it.startsAt)?.let { at -> BusinessTime.localDate(at, model.zone) }) }
            model.nav.pop()
        }
    }

    val title = appointment?.let { Copy.checkout.titleAppointment(Names.full(it.customer.firstName, it.customer.lastName)) } ?: Copy.checkout.title
    DetailScaffold(
        title = title,
        onBack = { model.nav.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    val status = when {
                        balance == 0L -> Copy.checkout.balanced to ext.success.content
                        balance < 0 -> Copy.checkout.missing(Money.format(-balance, currency)) to MaterialTheme.colorScheme.error
                        else -> Copy.checkout.over(Money.format(balance, currency)) to MaterialTheme.colorScheme.error
                    }
                    Text(formError ?: status.first, color = if (formError != null) MaterialTheme.colorScheme.error else status.second, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                    PrimaryButton(
                        if (closing) Copy.checkout.closeSale else Copy.checkout.charge(Money.format(remaining, currency)),
                        busy = action.busy, enabled = lines.isNotEmpty(), icon = Icons.Outlined.PointOfSale,
                    ) { submit() }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Customer and location.
            if (appointment == null) {
                SectionTitle(Copy.checkout.customer)
                AppCard(onClick = { pickingCustomer = true }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val current = customer
                        if (current != null) {
                            Avatar(Names.full(current.firstName, current.lastName), size = 36.dp)
                            Text(Names.full(current.firstName, current.lastName), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            IconButton({ customer = null; selectedDeposits.clear() }) { Icon(Icons.Filled.Close, null) }
                        } else {
                            Icon(Icons.Outlined.PersonSearch, null, tint = MaterialTheme.colorScheme.primary)
                            Text(Copy.checkout.noCustomer, style = MaterialTheme.typography.bodyLarge, color = ext.muted, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        }
                    }
                }
                if (locations.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        locations.forEach { location -> FilterChip(locationId == location.id, { locationId = location.id }, { Text(location.name) }) }
                    }
                }
            }

            // Lines.
            SectionTitle(Copy.checkout.items)
            AppCard(padding = PaddingValues(vertical = 4.dp)) {
                if (lines.isEmpty()) Text(Copy.checkout.noLines, color = ext.muted, modifier = Modifier.padding(16.dp))
                lines.forEachIndexed { index, line ->
                    if (index > 0) Hairline(Modifier.padding(start = 16.dp))
                    Row(Modifier.fillMaxWidth().clickable { editing = line }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (line.kind == "product") Icons.Outlined.Inventory2 else Icons.Outlined.ContentCut, null, tint = ext.muted, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    "${line.quantity} × ${Money.format(line.unitPrice, currency)}",
                                    catalog.professional(line.resourceId)?.name ?: Copy.checkout.nobody,
                                    line.discountAmount.takeIf { it > 0 }?.let { Money.negative(it, currency) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted,
                            )
                        }
                        Text(Money.format(line.total, currency), style = MaterialTheme.typography.bodyLarge.tabular)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ addingService = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.addService) }
                    FilledTonalButton({ addingProduct = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.addProduct) }
                }
            }

            // Code or gift card.
            SectionTitle(Copy.checkout.code)
            AppCard {
                val applied = giftCard
                if (applied != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Redeem, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(applied.code, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (applied.kind == "discount") (applied.percent?.let { "$it %" } ?: applied.amount?.let { Money.format(it, currency) } ?: "") + " · " + Money.negative(discount, currency)
                                else Copy.checkout.cardLeft(Money.format(applied.remaining ?: 0L, currency)),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted,
                            )
                        }
                        TextButton({ giftCard = null }) { Text(Copy.checkout.removeCode) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(code, { code = it; codeError = null }, Copy.checkout.code, error = codeError, capitalization = KeyboardCapitalization.Characters, modifier = Modifier.weight(1f))
                        val lookup = rememberAction { codeError = it.message }
                        TextButton(enabled = code.isNotBlank() && !lookup.busy, onClick = {
                            lookup.run {
                                val found = model.call { lookupGiftCard(it, code.trim().uppercase()) }
                                if (found.state != "active") codeError = Copy.checkout.codeState(Copy.giftCards.state(found.state)) else { giftCard = found; code = "" }
                            }
                        }) { Text(Copy.checkout.apply) }
                    }
                }
            }

            // Tip.
            SectionTitle(Copy.checkout.tip)
            AppCard {
                Field(tipText, { tipText = it }, Copy.checkout.tip, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), supporting = Copy.checkout.tipsHint)
                if (tips > 0) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        catalog.professionals.filter { it.active }.forEach { professional ->
                            FilterChip(tipResource == professional.id, { tipResource = professional.id }, { Text(professional.name) })
                        }
                    }
                }
            }

            // Deposits.
            val available = deposits.value.orEmpty()
            if (available.isNotEmpty()) {
                SectionTitle(Copy.checkout.deposits)
                AppCard(padding = PaddingValues(vertical = 4.dp)) {
                    available.forEach { deposit ->
                        val checked = deposit.id in selectedDeposits
                        Row(
                            Modifier.fillMaxWidth().clickable { if (checked) selectedDeposits.remove(deposit.id) else selectedDeposits.add(deposit.id) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, { if (it) selectedDeposits.add(deposit.id) else selectedDeposits.remove(deposit.id) })
                            val date = BusinessTime.parse(deposit.createdAt)?.let { BusinessTime.mediumDate(BusinessTime.localDate(it, model.zone), AppLocale.locale) } ?: ""
                            Column(Modifier.weight(1f)) {
                                Text(Copy.checkout.useDeposit(date), style = MaterialTheme.typography.bodyLarge)
                                Text(Copy.sales.method(deposit.method) + (deposit.appointmentCode?.let { " · " + Copy.sales.forAppointment(it) } ?: ""), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                            }
                            Text(Money.format(deposit.amount, currency), style = MaterialTheme.typography.bodyLarge.tabular, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }

            // How the rest was paid.
            SectionTitle(Copy.checkout.payments)
            AppCard(Modifier.animateContentSize()) {
                payments.forEachIndexed { index, row ->
                    if (index > 0) Hairline(Modifier.padding(vertical = 10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cash", "card", "transfer", "other").forEach { method ->
                            FilterChip(row.method == method, { payments[index] = row.copy(method = method) }, { Text(Copy.sales.method(method)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index == 0) {
                            Text(Copy.checkout.amount, color = ext.muted, modifier = Modifier.weight(1f))
                            Text(Money.format(maxOf(0L, firstAmount), currency), style = MaterialTheme.typography.titleMedium.tabular)
                        } else {
                            Field(row.amount, { payments[index] = row.copy(amount = it) }, Copy.checkout.amount, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), modifier = Modifier.weight(1f))
                            IconButton({ payments.removeAt(index) }) { Icon(Icons.Filled.Close, Copy.common.delete) }
                        }
                    }
                }
                if (payments.size < 4) {
                    TextButton({ payments.add(PaymentRow(keySeed++, "card", "")) }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.splitPayment) }
                }
            }

            // Totals.
            AppCard {
                InfoRow(Copy.checkout.subtotal, Money.format(subtotal, currency))
                if (discount > 0) InfoRow(Copy.checkout.discount, Money.negative(discount, currency), valueColor = ext.success.content)
                InfoRow(Copy.checkout.total, Money.format(total, currency), strong = true)
                if (tips > 0) InfoRow(Copy.checkout.tip, Money.format(tips, currency))
                if (depositSum > 0) InfoRow(Copy.checkout.deposits, Money.negative(depositSum, currency))
                if (giftPay > 0) InfoRow(Copy.sales.method("gift_card"), Money.negative(giftPay, currency))
                Hairline(Modifier.padding(vertical = 6.dp))
                InfoRow(Copy.checkout.toPay, Money.format(remaining, currency), strong = true, valueColor = MaterialTheme.colorScheme.primary)
            }

            Field(note, { note = it }, Copy.checkout.note, supporting = Copy.common.optional)
            if (appointment == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Copy.checkout.internal, style = MaterialTheme.typography.bodyLarge)
                        Text(Copy.checkout.internalHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                    }
                    Switch(internal, { internal = it })
                }
            }
        }
    }

    if (addingService) {
        val services = bookableServices(model, locationId)
        PickerSheet(Copy.checkout.pickService, onDismiss = { addingService = false }) {
            items(services, key = { it.id }) { service ->
                PickRow(service.name, BusinessTime.duration(service.durationMin.toLong()), service.priceAmount?.let { Money.format(it, currency) } ?: Copy.common.priceOnRequest) {
                    lines.add(Checkout.Line(nextKey(), "service", service.name, serviceId = service.id, resourceId = appointment?.resourceId, unitPrice = service.priceAmount ?: 0L))
                    addingService = false
                }
            }
        }
    }
    if (addingProduct) {
        val products = rememberLoader(Unit) { model.call { products(it) }.filter { it.active && it.forSale } }
        PickerSheet(Copy.checkout.pickProduct, onDismiss = { addingProduct = false }) {
            val list = products.value
            if (list == null) item { Text("…", modifier = Modifier.padding(20.dp)) }
            else if (list.isEmpty()) item { Text(Copy.checkout.noProducts, color = ext.muted, modifier = Modifier.padding(20.dp)) }
            else items(list, key = { it.id }) { product: Product ->
                val stock = locationId?.let { product.stock[it] } ?: product.stock.values.sum()
                val price = if (internal && product.internalPriceAmount != null) product.internalPriceAmount else product.priceAmount
                PickRow(product.name, Copy.checkout.inStock(stock), Money.format(price, currency)) {
                    lines.add(Checkout.Line(nextKey(), "product", product.name, productId = product.id, resourceId = appointment?.resourceId, unitPrice = price))
                    addingProduct = false
                }
            }
        }
    }
    if (pickingCustomer) CustomerPickerSheet(model, onDismiss = { pickingCustomer = false }) { picked -> customer = picked; selectedDeposits.clear(); pickingCustomer = false }
    editing?.let { line ->
        LineSheet(model, line, removable = line.appointmentId == null, onDismiss = { editing = null }, onRemove = { lines.removeAll { it.key == line.key }; editing = null }) { updated ->
            val index = lines.indexOfFirst { it.key == line.key }
            if (index >= 0) lines[index] = updated
            editing = null
        }
    }
}

fun moneyKeyboard(currency: String) = if (Money.digits(currency) == 0) KeyboardType.Number else KeyboardType.Decimal

@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp), content = content)
    }
}

@Composable
private fun PickRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtended.current.muted)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular)
    }
}

/** Search a customer for a sale (or none: a walk-in product sale). */
@Composable
fun CustomerPickerSheet(model: AppModel, onDismiss: () -> Unit, onPick: (CustomerSummary) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CustomerSummary>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = runCatching { model.call { customers(it, query.trim()) } }.getOrNull() ?: emptyList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding()) {
            Text(Copy.checkout.customer, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            Field(query, { query = it }, Copy.common.search, placeholder = Copy.booking.searchCustomer, supporting = Copy.booking.searchHint)
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(results, key = { it.id }) { option ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Names.full(option.firstName, option.lastName), size = 36.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(Names.full(option.firstName, option.lastName), style = MaterialTheme.typography.bodyLarge)
                            Text(option.phone ?: option.email ?: "", style = MaterialTheme.typography.bodySmall, color = LocalExtended.current.muted)
                        }
                    }
                }
            }
        }
    }
}

/** One line's price, quantity, discount and who did it. */
@Composable
private fun LineSheet(model: AppModel, line: Checkout.Line, removable: Boolean, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (Checkout.Line) -> Unit) {
    val currency = model.currency
    var price by remember { mutableStateOf(Money.inputText(line.unitPrice, currency)) }
    var discount by remember { mutableStateOf(if (line.discountAmount > 0) Money.inputText(line.discountAmount, currency) else "") }
    var quantity by remember { mutableStateOf(line.quantity) }
    var resourceId by remember { mutableStateOf(line.resourceId) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(line.name, style = MaterialTheme.typography.titleLarge)
            Field(price, { price = it }, Copy.checkout.unitPrice, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency))
            Field(discount, { discount = it }, Copy.checkout.discount, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), supporting = Copy.common.optional)
            if (line.appointmentId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Copy.checkout.quantity, modifier = Modifier.weight(1f))
                    IconButton({ if (quantity > 1) quantity-- }) { Icon(Icons.Filled.Remove, null) }
                    Text(quantity.toString(), style = MaterialTheme.typography.titleMedium.tabular)
                    IconButton({ if (quantity < 999) quantity++ }) { Icon(Icons.Filled.Add, null) }
                }
            }
            Text(Copy.common.professional, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(resourceId == null, { resourceId = null }, { Text(Copy.checkout.nobody) })
                model.catalog.professionals.filter { it.active }.forEach { professional ->
                    FilterChip(resourceId == professional.id, { resourceId = professional.id }, { Text(professional.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (removable) OutlinedButton(onRemove, Modifier.weight(1f)) { Text(Copy.common.delete, color = MaterialTheme.colorScheme.error) }
                PrimaryButton(Copy.common.done, Modifier.weight(1f)) {
                    val unit = Money.parse(price, currency) ?: 0L
                    val off = (Money.parse(discount, currency) ?: 0L).coerceIn(0L, unit * quantity)
                    onSave(line.copy(unitPrice = unit, discountAmount = off, quantity = quantity, resourceId = resourceId))
                }
            }
        }
    }
}
