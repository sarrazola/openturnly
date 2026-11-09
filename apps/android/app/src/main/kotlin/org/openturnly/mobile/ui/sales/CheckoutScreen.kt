@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.sales

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.Catalog
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Deposit
import org.openturnly.mobile.api.GiftCard
import org.openturnly.mobile.api.Location
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.Product
import org.openturnly.mobile.api.Professional
import org.openturnly.mobile.api.Service
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Checkout
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/** A row of «Cómo pagó» as typed: the amount stays text so a half-typed number isn't lost. */
private data class PayRow(val key: String, val method: String, val text: String, val giftCardId: String? = null, val giftCardCode: String? = null)

/** A tip as typed. */
private data class TipRow(val key: String, val resourceId: String?, val text: String)

/** Who can be credited at a location: active, and working there. */
private fun teamAt(catalog: Catalog, location: Location?): List<Professional> = catalog.professionals.filter { professional ->
    professional.active && (location == null || location.resourceIds.isEmpty() || professional.id in location.resourceIds)
}

/** What a location offers: active services it lists. */
private fun servicesAt(catalog: Catalog, location: Location?): List<Service> = catalog.services.filter { service ->
    service.active && (location == null || location.serviceIds.isEmpty() || service.id in location.serviceIds)
}

/**
 * «Cobrar» / «Cerrar venta» for an appointment, or «Nueva venta» from Ventas.
 *
 * It works like the web's checkout: the appointment's service as the first line,
 * more services or products, a discount (a code or an amount on the whole sale),
 * tips for the professionals, the customer's available deposits, and how the rest
 * was paid, a gift card's balance included. The payments must add up to the total
 * plus tips before it can be recorded.
 */
@Composable
fun CheckoutScreen(model: AppModel, appointment: Appointment?) {
    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val currency = model.currency
    val catalog = model.catalog
    val locations = catalog.activeLocations
    var keySeed by remember { mutableIntStateOf(1) }
    fun nextKey(prefix: String) = prefix + keySeed++

    var customer by remember {
        mutableStateOf(
            appointment?.customer?.takeIf { it.id.isNotEmpty() }?.let {
                CustomerSummary(id = it.id, shortId = it.shortId, firstName = it.firstName, lastName = it.lastName, phone = it.phone, email = it.email)
            },
        )
    }
    var locationId by remember { mutableStateOf(appointment?.locationId) }
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
    var discountText by remember { mutableStateOf("") }
    var discountCode by remember { mutableStateOf<GiftCard?>(null) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    val tips = remember { mutableStateListOf<TipRow>() }
    val selectedDeposits = remember { mutableStateListOf<String>() }
    var depositsDefaulted by remember { mutableStateOf(false) }
    val payRows = remember { mutableStateListOf(PayRow("p0", "cash", "")) }
    var paymentsEdited by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var internal by remember { mutableStateOf(false) }
    var addingService by remember { mutableStateOf(false) }
    var addingProduct by remember { mutableStateOf(false) }
    var pickingCustomer by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Checkout.Line?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    // The sale's location: the appointment's, or the one picked (the first active by default).
    val location = if (appointment != null) catalog.location(appointment.locationId) else catalog.location(locationId) ?: locations.firstOrNull()
    val team = teamAt(catalog, location)
    val products = rememberLoader(model.business?.id) { model.call { products(it) }.filter { it.active && it.forSale } }
    val forSale = products.value.orEmpty()

    val deposits = rememberLoader(customer?.id) {
        val id = customer?.id ?: return@rememberLoader emptyList<Deposit>()
        model.call { deposits(it, customerId = id) }.filter { it.status == "active" && it.appliedSale == null }
    }
    LaunchedEffect(deposits.value) {
        // The deposits left for this appointment are used by default, as on the web.
        val loaded = deposits.value ?: return@LaunchedEffect
        if (depositsDefaulted || appointment == null) return@LaunchedEffect
        loaded.filter { it.appointmentId == appointment.id }.forEach { if (it.id !in selectedDeposits) selectedDeposits.add(it.id) }
        depositsDefaulted = true
    }

    // The numbers.
    val available = deposits.value.orEmpty()
    val depositSum = available.filter { it.id in selectedDeposits }.sumOf { it.amount }
    val subtotal = Checkout.subtotal(lines)
    val discount = discountCode?.let { Checkout.codeDiscount(subtotal, it.percent, it.amount) } ?: Checkout.saleDiscount(subtotal, Money.parse(discountText, currency))
    val tipList = tips.map { Checkout.Tip(it.key, it.resourceId, Money.parse(it.text, currency) ?: 0L) }
    val summary = Checkout.summary(lines, discount, Checkout.tipTotal(tipList), depositSum)
    val typed = payRows.map { Checkout.Payment(it.key, it.method, Money.parse(it.text, currency) ?: 0L, it.giftCardId, it.giftCardCode) }
    val following = Checkout.followingIndex(typed, paymentsEdited)
    val payments = Checkout.follow(typed, summary.toCollect, paymentsEdited)
    val difference = Checkout.difference(summary.due, depositSum, payments)
    val status = Checkout.status(difference)
    val ready = lines.isNotEmpty() && status == Checkout.Status.Balanced
    LaunchedEffect(summary, payments) { formError = null }

    /** Puts rows the rules worked out back on screen, keeping what was typed where the amount didn't change. */
    fun showPayments(next: List<Checkout.Payment>) {
        val before = payRows.associateBy { it.key }
        val rows = next.map { payment ->
            val old = before[payment.key]
            if (old != null && (Money.parse(old.text, currency) ?: 0L) == payment.amount) old.copy(method = payment.method)
            else PayRow(payment.key, payment.method, Money.inputText(payment.amount, currency), payment.giftCardId, payment.giftCardCode)
        }
        payRows.clear()
        payRows.addAll(rows)
    }

    fun pickLocation(id: String) {
        locationId = id
        // Professionals are limited to the sale's location.
        val there = teamAt(catalog, catalog.location(id))
        val ids = there.map { it.id }.toSet()
        lines.indices.forEach { index -> if (lines[index].resourceId != null && lines[index].resourceId !in ids) lines[index] = lines[index].copy(resourceId = null) }
        tips.indices.forEach { index -> if (tips[index].resourceId !in ids) tips[index] = tips[index].copy(resourceId = there.firstOrNull()?.id) }
    }

    fun switchInternal(next: Boolean) {
        internal = next
        val prices = forSale.associate { it.id to (it.priceAmount to it.internalPriceAmount) }
        Checkout.reprice(lines.toList(), prices, next).forEachIndexed { index, line -> if (lines[index] != line) lines[index] = line }
    }

    val lookup = rememberAction { codeError = it.message }
    fun applyCode() {
        val typedCode = code.trim().uppercase()
        if (typedCode.isEmpty() || lookup.busy) return
        codeError = null
        val toCollect = summary.toCollect
        val current = payments
        lookup.run {
            val found = model.call { lookupGiftCard(it, typedCode) }
            when {
                found.state != "active" -> codeError = Copy.checkout.codeState(Copy.giftCards.state(found.state).lowercase(AppLocale.locale))
                found.kind == "discount" -> { discountCode = found; code = "" }
                else -> {
                    code = ""
                    if (current.none { it.giftCardId == found.id }) {
                        val balance = found.remaining ?: 0L
                        showPayments(Checkout.withGiftCard(current, nextKey("g"), found.id, found.code, balance, toCollect))
                        paymentsEdited = true
                        model.toast(Copy.checkout.cardLeft(Money.format(balance, currency)))
                    }
                }
            }
        }
    }

    val action = rememberAction { failure -> formError = failure.message }
    // Back is ignored while the sale is being recorded.
    BackHandler(enabled = action.busy) {}

    fun submit() {
        formError = null
        if (!ready) return
        val saleLocationId = if (appointment != null) appointment.locationId else location?.id
        val body = objWithout(
            "locationId" to (saleLocationId ?: Omit),
            "customerId" to customer?.id,
            "internal" to (appointment == null && internal),
            "giftCardId" to (discountCode?.id ?: Omit),
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
            "discountAmount" to summary.discount,
            "tips" to Checkout.payableTips(tipList).map { mapOf("resourceId" to it.resourceId, "amount" to it.amount) },
            "payments" to available.filter { it.id in selectedDeposits }.map { mapOf("method" to "deposit", "amount" to it.amount, "depositId" to it.id) } +
                Checkout.payable(payments).map { payment ->
                    buildMap<String, Any?> {
                        put("method", payment.method)
                        put("amount", payment.amount)
                        payment.giftCardId?.let { put("giftCardId", it) }
                    }
                },
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

    DetailScaffold(
        title = if (appointment != null) Copy.checkout.titleAppointment else Copy.checkout.title,
        onBack = { if (!action.busy) model.nav.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    val error = formError
                    val (text, color) = when {
                        error != null -> error to MaterialTheme.colorScheme.error
                        lines.isEmpty() -> Copy.checkout.noLines to ext.warning.content
                        status is Checkout.Status.Missing -> Copy.checkout.missing(Money.format(status.amount, currency)) to ext.warning.content
                        status is Checkout.Status.Over -> Copy.checkout.over(Money.format(status.amount, currency)) to ext.warning.content
                        else -> Copy.checkout.balanced to ext.success.content
                    }
                    val good = error == null && ready
                    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (good) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = color, modifier = Modifier.size(18.dp))
                        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                    }
                    val label = when (val kind = Checkout.submit(summary.toCollect, appointment != null)) {
                        Checkout.Submit.CloseSale -> Copy.checkout.closeSale
                        is Checkout.Submit.Charge -> Copy.checkout.charge(Money.format(kind.amount, currency))
                    }
                    PrimaryButton(label, busy = action.busy, enabled = ready, icon = Icons.Outlined.PointOfSale) { submit() }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Customer (and location for a counter sale).
            if (appointment != null) {
                val name = Names.full(appointment.customer.firstName, appointment.customer.lastName)
                AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, size = 44.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(appointment.code, BusinessTime.time(appointment.startsAt, model.zone), appointment.serviceName).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                val current = customer
                AppCard(onClick = if (current == null) ({ pickingCustomer = true }) else null, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current != null) {
                            val name = Names.full(current.firstName, current.lastName)
                            Avatar(name, size = 40.dp)
                            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            TextButton({ customer = null; selectedDeposits.clear() }) { Text(Copy.checkout.remove) }
                        } else {
                            Icon(Icons.Outlined.PersonSearch, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(Copy.checkout.pickCustomer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                Text("${Copy.checkout.customer} · ${Copy.common.optional}", style = MaterialTheme.typography.bodySmall, color = ext.muted)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ext.subtle)
                        }
                    }
                }
                if (locations.size > 1) {
                    Column {
                        Text(Copy.common.location, style = MaterialTheme.typography.labelLarge, color = ext.muted, modifier = Modifier.padding(bottom = 4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            locations.forEach { place -> FilterChip(location?.id == place.id, { pickLocation(place.id) }, { Text(place.name) }) }
                        }
                    }
                }
            }

            // 2. Services and products.
            SectionTitle(Copy.checkout.items)
            AppCard(padding = PaddingValues(vertical = 4.dp)) {
                if (lines.isEmpty()) Text(Copy.checkout.noLines, color = ext.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                lines.forEachIndexed { index, line ->
                    if (index > 0) Hairline(Modifier.padding(start = 16.dp))
                    Row(Modifier.fillMaxWidth().clickable { editing = line }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (line.kind == "product") Icons.Outlined.Inventory2 else Icons.Outlined.ContentCut, null, tint = ext.muted, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    "${line.quantity} × ${Money.format(line.unitPrice, currency)}",
                                    catalog.professional(line.resourceId)?.name,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted,
                            )
                            // The booking's discount, which comes with the appointment's line.
                            if (line.discountAmount > 0) Text(Money.negative(line.discountAmount, currency), style = MaterialTheme.typography.bodySmall, color = ext.success.content)
                        }
                        Text(Money.format(line.total, currency), style = MaterialTheme.typography.bodyLarge.tabular)
                    }
                }
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ addingService = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.addService) }
                    if (forSale.isNotEmpty()) {
                        FilledTonalButton({ addingProduct = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.addProduct) }
                    }
                }
            }

            // 3. Discount: the code field (a discount code, or a gift card that then pays), then
            // the applied code or an amount on the whole sale.
            SectionTitle(Copy.checkout.discount)
            AppCard {
                Row(verticalAlignment = Alignment.Top) {
                    Field(
                        code, { code = it.uppercase(); codeError = null }, Copy.checkout.code, error = codeError,
                        capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, onDone = { applyCode() },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = code.isNotBlank() && !lookup.busy, onClick = { applyCode() }, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                        if (lookup.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(Copy.checkout.apply)
                    }
                }
                val applied = discountCode
                if (applied != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(applied.code, ext.success, icon = Icons.Outlined.Redeem)
                        Text(Money.negative(summary.discount, currency), style = MaterialTheme.typography.titleSmall.tabular, color = ext.success.content, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        TextButton({ discountCode = null }) { Text(Copy.checkout.removeCode) }
                    }
                } else {
                    Field(
                        discountText, { discountText = it }, Copy.checkout.discount, prefix = Money.symbol(currency) + " ",
                        keyboard = moneyKeyboard(currency), modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // 4. Tips, one per professional.
            SectionTitle(Copy.checkout.tips)
            AppCard(Modifier.animateContentSize()) {
                tips.forEachIndexed { index, tip ->
                    key(tip.key) {
                        fun update(change: (TipRow) -> TipRow) { tips.indexOfFirst { it.key == tip.key }.takeIf { it >= 0 }?.let { tips[it] = change(tips[it]) } }
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfessionalMenu(model, team, tip.resourceId, Modifier.weight(1f)) { id -> update { it.copy(resourceId = id) } }
                            Field(
                                tip.text, { text -> update { it.copy(text = text) } },
                                Copy.checkout.tip, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), modifier = Modifier.weight(1f),
                            )
                            IconButton({ tips.removeAll { it.key == tip.key } }) { Icon(Icons.Filled.Close, Copy.checkout.remove) }
                        }
                    }
                }
                if (tips.isEmpty()) Text(Copy.checkout.tipsHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                val defaultTipResource = appointment?.resourceId ?: team.firstOrNull()?.id
                TextButton({ tips.add(TipRow(nextKey("t"), defaultTipResource, "")) }, enabled = defaultTipResource != null, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.addTip)
                }
            }

            // 5. Deposits the customer left and hasn't used.
            if (available.isNotEmpty()) {
                SectionTitle(Copy.checkout.deposits)
                AppCard(padding = PaddingValues(vertical = 4.dp)) {
                    available.forEachIndexed { index, deposit ->
                        if (index > 0) Hairline(Modifier.padding(start = 56.dp))
                        val checked = deposit.id in selectedDeposits
                        fun toggle(on: Boolean) { if (on) { if (deposit.id !in selectedDeposits) selectedDeposits.add(deposit.id) } else selectedDeposits.remove(deposit.id) }
                        Row(Modifier.fillMaxWidth().clickable { toggle(!checked) }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { toggle(it) })
                            val date = BusinessTime.parse(deposit.createdAt)?.let { BusinessTime.mediumDate(BusinessTime.localDate(it, model.zone), AppLocale.locale, withYear = false) }
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(Money.format(deposit.amount, currency), style = MaterialTheme.typography.bodyLarge.tabular, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(date?.let { Copy.checkout.useDeposit(it) }, Copy.sales.method(deposit.method), deposit.appointmentCode?.let { Copy.sales.forAppointment(it) }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = ext.muted,
                                )
                            }
                        }
                    }
                }
            }

            // 6. How the rest was paid.
            SectionTitle(Copy.checkout.payments)
            AppCard(Modifier.animateContentSize()) {
                payRows.forEachIndexed { index, row ->
                    key(row.key) {
                        fun update(change: (PayRow) -> PayRow) { payRows.indexOfFirst { it.key == row.key }.takeIf { it >= 0 }?.let { payRows[it] = change(payRows[it]) } }
                        if (index > 0) Hairline(Modifier.padding(vertical = 10.dp))
                        if (row.giftCardId != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                                Icon(Icons.Outlined.Redeem, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(Copy.checkout.giftCard(row.giftCardCode.orEmpty()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Checkout.counterMethods.forEach { method ->
                                    FilterChip(row.method == method, { update { it.copy(method = method) } }, { Text(Copy.sales.method(method)) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The first row that isn't a gift card follows «Por cobrar ahora» until someone types an amount.
                            val shown = if (index == following) Money.inputText(payments[index].amount, currency) else row.text
                            Field(
                                shown, { text -> paymentsEdited = true; update { it.copy(text = text) } },
                                Copy.checkout.amount, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), modifier = Modifier.weight(1f),
                            )
                            if (payRows.size > 1) IconButton({ showPayments(payments.filter { it.key != row.key }); paymentsEdited = true }) { Icon(Icons.Filled.Close, Copy.checkout.remove) }
                        }
                    }
                }
                if (payRows.size < Checkout.MAX_PAYMENTS) {
                    TextButton({ showPayments(Checkout.split(payments, difference, nextKey("p"))); paymentsEdited = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.checkout.splitPayment)
                    }
                }
            }

            // 7. Summary.
            AppCard {
                InfoRow(Copy.checkout.subtotal, Money.format(summary.subtotal, currency))
                if (summary.discount > 0) InfoRow(Copy.checkout.discount, Money.negative(summary.discount, currency), valueColor = ext.success.content)
                InfoRow(Copy.checkout.total, Money.format(summary.total, currency))
                if (summary.tips > 0) InfoRow(Copy.checkout.tips, "+" + Money.format(summary.tips, currency))
                if (summary.deposits > 0) InfoRow(Copy.checkout.deposits, Money.negative(summary.deposits, currency))
                Hairline(Modifier.padding(vertical = 6.dp))
                InfoRow(Copy.checkout.toCollect, Money.format(summary.toCollect, currency), strong = true, valueColor = MaterialTheme.colorScheme.primary)
            }

            // 8. Note, and for a counter sale whether it is internal.
            SectionTitle(Copy.checkout.note)
            Field(note, { note = it }, Copy.checkout.note, supporting = Copy.common.optional, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
            if (appointment == null) {
                AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(Copy.checkout.internal, style = MaterialTheme.typography.bodyLarge)
                            Text(Copy.checkout.internalHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                        Switch(internal, { switchInternal(it) })
                    }
                }
            }
        }
    }

    if (addingService) {
        val services = servicesAt(catalog, location)
        val groups = services.groupBy { it.category?.takeIf { category -> category.isNotBlank() } ?: Copy.services.noCategory }
        PickerSheet(Copy.checkout.pickService, onDismiss = { addingService = false }) {
            groups.forEach { (category, list) ->
                item(key = "c-$category") {
                    Text(category.uppercase(), style = MaterialTheme.typography.labelMedium, color = ext.muted, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
                }
                items(list, key = { it.id }) { service ->
                    PickRow(service.name, BusinessTime.duration(service.durationMin.toLong()), service.priceAmount?.let { Money.format(it, currency) } ?: Copy.common.priceOnRequest) {
                        lines.add(Checkout.Line(nextKey("l"), "service", service.name, serviceId = service.id, resourceId = appointment?.resourceId, unitPrice = service.priceAmount ?: 0L))
                        addingService = false
                    }
                }
            }
        }
    }
    if (addingProduct) {
        PickerSheet(Copy.checkout.pickProduct, onDismiss = { addingProduct = false }) {
            items(forSale, key = { it.id }) { product: Product ->
                val stock = location?.let { product.stock[it.id] ?: 0L } ?: product.stock.values.sum()
                val price = Checkout.productPrice(product.priceAmount, product.internalPriceAmount, internal)
                PickRow(product.name, Copy.checkout.inStock(stock), Money.format(price, currency)) {
                    lines.add(Checkout.Line(nextKey("l"), "product", product.name, productId = product.id, resourceId = appointment?.resourceId, unitPrice = price))
                    addingProduct = false
                }
            }
        }
    }
    if (pickingCustomer) {
        CustomerPickerSheet(model, onDismiss = { pickingCustomer = false }) { picked -> customer = picked; selectedDeposits.clear(); pickingCustomer = false }
    }
    editing?.let { line ->
        LineSheet(
            model, line, team, onDismiss = { editing = null },
            onRemove = { lines.removeAll { it.key == line.key }; editing = null },
        ) { updated ->
            val index = lines.indexOfFirst { it.key == line.key }
            if (index >= 0) lines[index] = updated
            editing = null
        }
    }
}

fun moneyKeyboard(currency: String) = if (Money.digits(currency) == 0) KeyboardType.Number else KeyboardType.Decimal

/** The colour dot, name and a menu of the team at the sale's location, for a tip. */
@Composable
private fun ProfessionalMenu(model: AppModel, team: List<Professional>, selectedId: String?, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val ext = LocalExtended.current
    var open by remember { mutableStateOf(false) }
    @Composable
    fun Dot(id: String?) = Box(Modifier.size(10.dp).clip(CircleShape).background(if (id == null) Color.Transparent else ext.person(model.catalog.personIndex(id))))
    Box(modifier.padding(end = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = true }.padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(selectedId)
            Text(
                model.catalog.professional(selectedId)?.name ?: Copy.common.professional, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, Copy.common.professional, tint = ext.muted)
        }
        DropdownMenu(open, { open = false }) {
            team.forEach { professional ->
                DropdownMenuItem(text = { Text(professional.name) }, leadingIcon = { Dot(professional.id) }, onClick = { onPick(professional.id); open = false })
            }
        }
    }
}

@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
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

/** Search a customer for a counter sale, as the new-appointment search does: up to 8 results. */
@Composable
fun CustomerPickerSheet(model: AppModel, onDismiss: () -> Unit, onPick: (CustomerSummary) -> Unit) {
    val ext = LocalExtended.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CustomerSummary>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = null; searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        results = runCatching { model.call { customers(it, q) } }.getOrNull()?.take(8) ?: emptyList()
        searching = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding()) {
            Text(Copy.checkout.pickCustomer, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            Field(
                query, { query = it }, Copy.common.search, placeholder = Copy.booking.searchCustomer,
                leading = { Icon(Icons.Filled.Search, null) },
                trailing = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                supporting = if (query.trim().length < 2) Copy.booking.searchHint else null,
            )
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                val found = results
                if (found != null && found.isEmpty() && !searching) {
                    item { Text(Copy.booking.noCustomerFound, color = ext.muted, modifier = Modifier.padding(vertical = 12.dp)) }
                }
                items(found.orEmpty(), key = { it.id }) { option ->
                    val name = Names.full(option.firstName, option.lastName)
                    Row(Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, size = 36.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val contact = listOfNotNull(option.phone?.let { Phones.format(it) }, option.email).joinToString(" · ")
                            if (contact.isNotEmpty()) Text(contact, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                    }
                }
            }
        }
    }
}

/** One line's price, quantity and who did it. The appointment's own line keeps its quantity and can't be removed. */
@Composable
private fun LineSheet(model: AppModel, line: Checkout.Line, team: List<Professional>, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (Checkout.Line) -> Unit) {
    val currency = model.currency
    val own = line.appointmentId != null
    var price by remember { mutableStateOf(Money.inputText(line.unitPrice, currency)) }
    var quantity by remember { mutableIntStateOf(line.quantity) }
    var resourceId by remember { mutableStateOf(line.resourceId) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(line.name, style = MaterialTheme.typography.titleLarge)
            Field(price, { price = it }, Copy.checkout.unitPrice, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency))
            if (!own) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Copy.checkout.quantity, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton({ if (quantity > 1) quantity-- }, enabled = quantity > 1) { Icon(Icons.Filled.Remove, null) }
                    Text(quantity.toString(), style = MaterialTheme.typography.titleMedium.tabular)
                    IconButton({ if (quantity < 999) quantity++ }, enabled = quantity < 999) { Icon(Icons.Filled.Add, null) }
                }
            }
            Text(Copy.common.professional, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(resourceId == null, { resourceId = null }, { Text(Copy.checkout.nobody) })
                team.forEach { professional ->
                    FilterChip(resourceId == professional.id, { resourceId = professional.id }, { Text(professional.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!own) OutlinedButton(onRemove, Modifier.weight(1f).height(52.dp)) { Text(Copy.checkout.remove, color = MaterialTheme.colorScheme.error) }
                PrimaryButton(Copy.common.done, Modifier.weight(1f)) {
                    onSave(line.copy(unitPrice = Money.parse(price, currency) ?: 0L, quantity = quantity, resourceId = resourceId))
                }
            }
        }
    }
}
