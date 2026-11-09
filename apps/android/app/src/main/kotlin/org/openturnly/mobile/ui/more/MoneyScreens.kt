@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.more

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.Product
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.customers.SearchBox
import org.openturnly.mobile.ui.customers.fold
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.sales.Span
import org.openturnly.mobile.ui.sales.SpanBar
import org.openturnly.mobile.ui.sales.moneyKeyboard
import org.openturnly.mobile.ui.sales.spanRange
import org.openturnly.mobile.ui.tabular

/** "Caja": money in and out over a period, by method, and the cash in the drawer. */
@Composable
fun CashScreen(model: AppModel) {
    val ext = LocalExtended.current
    val currency = model.currency
    val today = model.today
    var span by rememberSaveable { mutableStateOf(Span.DAY) }
    var anchor by rememberSaveable { mutableStateOf(today) }
    var movement by remember { mutableStateOf<String?>(null) }
    val (from, to) = spanRange(span, anchor)
    val loader = rememberLoader(model.business?.id, from, to) {
        val (start, end) = BusinessTime.range(from, to, model.zone)
        model.call { cash(it, BusinessTime.iso(start), BusinessTime.iso(end)) }
    }
    DetailScaffold(title = Copy.cash.title, onBack = { model.nav.pop() }) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpanBar(span, anchor, today, { span = it }, { anchor = it })
                val report = loader.value
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        report == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                        report == null -> SkeletonCards(3, Modifier.padding(horizontal = 0.dp))
                        else -> {
                            AppCard {
                                Text(Copy.cash.inDrawer, style = MaterialTheme.typography.labelLarge, color = ext.muted)
                                Text(Money.format(report.cashInDrawer, currency), style = MaterialTheme.typography.headlineMedium.tabular, color = MaterialTheme.colorScheme.primary)
                                Text(Copy.cash.inDrawerHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton({ movement = "income" }, Modifier.weight(1f)) { Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.cash.income) }
                                    FilledTonalButton({ movement = "expense" }, Modifier.weight(1f)) { Icon(Icons.Outlined.RemoveCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.cash.expense) }
                                }
                            }
                            AppCard {
                                val flows = report.flows
                                InfoRow(Copy.cash.sales, Money.format(flows.sales, currency))
                                InfoRow(Copy.cash.deposits, Money.format(flows.deposits, currency))
                                if (flows.internal != 0L) InfoRow(Copy.cash.internal, Money.format(flows.internal, currency))
                                if (flows.giftCards != 0L) InfoRow(Copy.cash.giftCards, Money.format(flows.giftCards, currency))
                                InfoRow(Copy.cash.otherIncome, Money.format(flows.otherIncome, currency))
                                InfoRow(Copy.cash.expenses, Money.negative(flows.expenses, currency), valueColor = MaterialTheme.colorScheme.error)
                                Hairline(Modifier.padding(vertical = 6.dp))
                                InfoRow(Copy.cash.result, Money.format(flows.result, currency), strong = true)
                            }
                            SectionTitle(Copy.sales.byMethod)
                            AppCard {
                                val m = report.byMethod
                                listOf("cash" to m.cash, "card" to m.card, "transfer" to m.transfer, "other" to m.other, "online" to m.online).filter { it.second != 0L }
                                    .ifEmpty { listOf("cash" to 0L) }.forEach { (method, amount) -> InfoRow(Copy.sales.method(method), Money.format(amount, currency)) }
                            }
                            SectionTitle(Copy.cash.movements)
                            AppCard {
                                val rows = report.movements
                                if (rows.isEmpty()) Text(Copy.cash.noMovements, color = ext.muted)
                                rows.forEachIndexed { index, row ->
                                    if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
                                    val at = BusinessTime.parse(row.createdAt)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(listOfNotNull(Copy.cash.kind(row.kind), row.category).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                listOfNotNull(at?.let { BusinessTime.time(it, model.zone) }, row.method?.let { Copy.sales.method(it) }, row.description, row.createdByName).joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val negative = row.kind == "expense" || row.kind == "transfer_out" || row.amount < 0
                                        Text(
                                            (if (negative) "−" else "+") + Money.format(kotlin.math.abs(row.amount), currency), style = MaterialTheme.typography.bodyLarge.tabular,
                                            color = if (negative) MaterialTheme.colorScheme.error else ext.success.content,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    movement?.let { kind -> MovementSheet(model, kind, onDismiss = { movement = null }) { movement = null; loader.load(refresh = true) } }
}

@Composable
private fun MovementSheet(model: AppModel, kind: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val currency = model.currency
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("cash") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf(model.catalog.activeLocations.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    val action = rememberAction { error = it.message }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (kind == "income") Copy.cash.newIncome else Copy.cash.newExpense, style = MaterialTheme.typography.titleLarge)
            if (model.catalog.activeLocations.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.catalog.activeLocations.forEach { location -> FilterChip(locationId == location.id, { locationId = location.id }, { Text(location.name) }) }
                }
            }
            Field(amount, { amount = it; error = null }, Copy.checkout.amount, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), error = error)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val methods = listOf("cash", "card", "transfer", "other")
                methods.forEachIndexed { index, value ->
                    SegmentedButton(method == value, { method = value }, SegmentedButtonDefaults.itemShape(index, methods.size), icon = {}) { Text(Copy.sales.method(value), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Field(category, { category = it }, Copy.cash.category, supporting = Copy.common.optional)
            Field(description, { description = it }, Copy.common.description, supporting = Copy.common.optional)
            PrimaryButton(Copy.common.save, busy = action.busy) {
                val minor = Money.parse(amount, currency)
                if (minor == null || minor < 1) { error = Copy.checkout.amountRequired; return@PrimaryButton }
                val location = locationId ?: return@PrimaryButton
                action.run {
                    model.call {
                        addCashMovement(it, objWithout(
                            "kind" to kind, "locationId" to location, "amount" to minor, "method" to method,
                            "category" to (category.trim().ifEmpty { null } ?: Omit), "description" to (description.trim().ifEmpty { null } ?: Omit),
                        ))
                    }
                    model.toast(Copy.cash.saved)
                    onSaved()
                }
            }
        }
    }
}

private fun Product.isLow(): Boolean = active && alerts.any { (location, alert) -> (stock[location] ?: 0L) <= alert }

/** "Inventario": products with their stock, and which ones are running low. */
@Composable
fun InventoryScreen(model: AppModel) {
    val ext = LocalExtended.current
    var query by rememberSaveable { mutableStateOf("") }
    val loader = rememberLoader(model.business?.id) {
        coroutineScope {
            val products = async { model.call { products(it) } }
            val attributes = async { model.call { productAttributes(it) } }
            products.await() to attributes.await().associate { it.id to it.name }
        }
    }
    DetailScaffold(title = Copy.inventory.title, onBack = { model.nav.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBox(query, Copy.inventory.search) { query = it }
            PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize()) {
                val data = loader.value
                when {
                    data == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                    data == null -> SkeletonCards(5)
                    data.first.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.Inventory2, Copy.inventory.empty, Copy.inventory.emptyBody) } }
                    else -> {
                        val (products, names) = data
                        val q = fold(query.trim())
                        val shown = products.filter { q.isEmpty() || fold(it.name).contains(q) || (it.sku?.let { s -> fold(s).contains(q) } == true) }
                        val low = products.count { it.isLow() }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (low > 0) item {
                                Chip(Copy.inventory.lowStockCount(low), ext.warning, icon = Icons.Outlined.WarningAmber)
                            }
                            items(shown, key = { it.id }) { product ->
                                AppCard(onClick = { model.nav.push(Route.ProductDetail(product)) }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (product.active) MaterialTheme.colorScheme.onSurface else ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(names[product.categoryId], names[product.brandId], product.sku).joinToString(" · ").ifEmpty { " " },
                                                style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1,
                                            )
                                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (product.isLow()) Chip(Copy.inventory.lowStock, ext.warning)
                                                if (!product.active) Chip(Copy.inventory.inactive, ext.neutral)
                                                if (!product.forSale) Chip(Copy.inventory.notForSale, ext.neutral)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(Money.format(product.priceAmount, model.currency), style = MaterialTheme.typography.bodyLarge.tabular)
                                            Text(Copy.inventory.inStock(product.stock.values.sum()), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A product: prices, stock at each location, and counting it. */
@Composable
fun ProductDetailScreen(model: AppModel, initial: Product) {
    val ext = LocalExtended.current
    val currency = model.currency
    var product by remember { mutableStateOf(initial) }
    var counting by remember { mutableStateOf(false) }
    DetailScaffold(title = product.name, onBack = { model.nav.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Text(product.name, style = MaterialTheme.typography.headlineSmall)
                product.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = ext.muted, modifier = Modifier.padding(top = 4.dp)) }
                Hairline(Modifier.padding(vertical = 10.dp))
                product.sku?.let { InfoRow(Copy.inventory.sku, it) }
                InfoRow(Copy.inventory.price, Money.format(product.priceAmount, currency))
                product.internalPriceAmount?.let { InfoRow(Copy.inventory.internalPrice, Money.format(it, currency)) }
                product.costAmount?.let { InfoRow(Copy.inventory.cost, Money.format(it, currency)) }
            }
            SectionTitle(Copy.inventory.stockByLocation)
            AppCard {
                val locations = model.catalog.locations.ifEmpty { emptyList() }
                if (locations.isEmpty()) InfoRow(Copy.inventory.stock, product.stock.values.sum().toString())
                locations.forEachIndexed { index, location ->
                    if (index > 0) Hairline(Modifier.padding(vertical = 4.dp))
                    val stock = product.stock[location.id] ?: 0L
                    val alert = product.alerts[location.id]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(location.name, style = MaterialTheme.typography.bodyLarge)
                            alert?.let { Text("${Copy.inventory.alertAt} $it", style = MaterialTheme.typography.bodySmall, color = ext.muted) }
                        }
                        if (alert != null && stock <= alert) Chip(Copy.inventory.lowStock, ext.warning, Modifier.padding(end = 8.dp))
                        Text(stock.toString(), style = MaterialTheme.typography.titleMedium.tabular)
                    }
                }
                FilledTonalButton({ counting = true }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(Copy.inventory.count) }
            }
        }
    }
    if (counting) {
        var locationId by remember { mutableStateOf(model.catalog.activeLocations.firstOrNull()?.id) }
        var counted by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val action = rememberAction { error = it.message }
        ModalBottomSheet(onDismissRequest = { counting = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.inventory.count, style = MaterialTheme.typography.titleLarge)
                Text(Copy.inventory.countHint, color = ext.muted)
                if (model.catalog.activeLocations.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.catalog.activeLocations.forEach { location -> FilterChip(locationId == location.id, { locationId = location.id }, { Text(location.name) }) }
                    }
                }
                Field(counted, { counted = it.filter { c -> c.isDigit() }; error = null }, Copy.inventory.counted, keyboard = KeyboardType.Number, error = error)
                PrimaryButton(Copy.common.save, busy = action.busy) {
                    val quantity = counted.toLongOrNull() ?: return@PrimaryButton
                    val location = locationId ?: return@PrimaryButton
                    action.run {
                        val result = model.call { addStockMovement(it, obj("kind" to "adjustment", "productId" to product.id, "locationId" to location, "quantity" to quantity)) }
                        product = product.copy(stock = product.stock + (location to result.balance))
                        model.toast(Copy.inventory.countSaved)
                        counting = false
                    }
                }
            }
        }
    }
}

/** "Gift cards": discount codes and cards with balance, their state and use. */
@Composable
fun GiftCardsScreen(model: AppModel) {
    val ext = LocalExtended.current
    val scope = rememberCoroutineScope()
    val currency = model.currency
    val loader = rememberLoader(model.business?.id) { model.call { giftCards(it) } }
    DetailScaffold(title = Copy.giftCards.title, onBack = { model.nav.pop() }) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            val list = loader.value
            when {
                list == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                list == null -> SkeletonCards(4)
                list.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.CardGiftcard, Copy.giftCards.empty, Copy.giftCards.emptyBody) } }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { card ->
                        AppCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(card.code, style = MaterialTheme.typography.titleMedium.tabular)
                                        val tone = when (card.state) { "active" -> ext.success; "scheduled" -> ext.info; "inactive" -> ext.neutral; else -> ext.warning }
                                        Chip(Copy.giftCards.state(card.state), tone)
                                    }
                                    val value = if (card.kind == "discount") (card.percent?.let { "$it %" } ?: card.amount?.let { Money.format(it, currency) } ?: "")
                                        else card.amount?.let { Money.format(it, currency) } ?: ""
                                    Text("${if (card.kind == "discount") Copy.giftCards.discount else Copy.giftCards.balance} · $value", style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                    Text(
                                        listOfNotNull(
                                            if (card.kind == "balance") card.remaining?.let { Copy.giftCards.left(Money.format(it, currency)) } else Copy.giftCards.uses(card.uses, card.maxUses),
                                            card.validUntil?.let { BusinessTime.parseDate(it) }?.let { Copy.giftCards.until(BusinessTime.mediumDate(it, AppLocale.locale)) },
                                            card.customer?.let { Names.full(it.firstName, it.lastName) },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = ext.muted,
                                    )
                                }
                                Switch(card.active, { active ->
                                    loader.value = loader.value?.map { if (it.id == card.id) it.copy(active = active) else it }
                                    scope.launch {
                                        try {
                                            val saved = model.call { updateGiftCard(it, card.id, obj("active" to active)) }
                                            loader.value = loader.value?.map { if (it.id == saved.id) saved else it }
                                            model.toast(Copy.giftCards.updated)
                                        } catch (failure: ApiException) { model.failure(failure); loader.load(refresh = true) }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
