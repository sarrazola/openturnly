@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.more

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
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.openturnly.mobile.api.HourRange
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.Professional
import org.openturnly.mobile.api.Service
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.sales.moneyKeyboard
import org.openturnly.mobile.ui.tabular

/** Services by category, with their duration and price; admins switch them on and off here. */
@Composable
fun ServicesScreen(model: AppModel) {
    val ext = LocalExtended.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(model.business?.id) { model.call { services(it) } }
    DetailScaffold(
        title = Copy.services.title, onBack = { model.nav.pop() },
        floating = {
            if (model.isAdmin) ExtendedFloatingActionButton(
                text = { Text(Copy.services.new) }, icon = { Icon(Icons.Filled.Add, null) }, onClick = { model.nav.push(Route.ServiceForm(null)) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            val list = loader.value
            when {
                list == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                list == null -> SkeletonCards(5)
                list.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.ContentCut, Copy.services.empty, if (model.isAdmin) Copy.services.emptyBody else null) } }
                else -> {
                    val groups = list.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: Copy.services.noCategory }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                        groups.forEach { (category, services) ->
                            item(key = "c-$category") { SectionTitle(category, Modifier.padding(top = 8.dp)) }
                            item(key = "g-$category") {
                                AppCard(padding = PaddingValues(vertical = 4.dp)) {
                                    services.forEachIndexed { index, service ->
                                        if (index > 0) Hairline(Modifier.padding(start = 16.dp))
                                        Row(
                                            Modifier.fillMaxWidth().clickable(enabled = model.isAdmin) { model.nav.push(Route.ServiceForm(service)) }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(service.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (service.active) MaterialTheme.colorScheme.onSurface else ext.muted)
                                                Text(
                                                    listOf(BusinessTime.duration(service.durationMin.toLong()), service.priceAmount?.let { Money.format(it, model.currency) } ?: Copy.common.priceOnRequest).joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted,
                                                )
                                                if (service.paymentMode != "none") Chip("${Copy.services.onlinePayment}: ${Copy.services.paymentMode(service.paymentMode)}", ext.info, Modifier.padding(top = 4.dp))
                                            }
                                            if (model.isAdmin) {
                                                Switch(service.active, { active ->
                                                    loader.value = loader.value?.map { if (it.id == service.id) it.copy(active = active) else it }
                                                    scope.launch {
                                                        try {
                                                            model.call { updateService(it, service.id, obj("active" to active)) }
                                                            model.refreshCatalog()
                                                        } catch (failure: ApiException) {
                                                            model.failure(failure); loader.load(refresh = true)
                                                        }
                                                    }
                                                })
                                            } else if (!service.active) Chip(Copy.common.inactive, ext.neutral)
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

/** Create or edit a service: name, category, duration, price, and whether it can be booked. */
@Composable
fun ServiceFormScreen(model: AppModel, existing: Service?) {
    val ext = LocalExtended.current
    val currency = model.currency
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var duration by remember { mutableStateOf((existing?.durationMin ?: 30).toString()) }
    var price by remember { mutableStateOf(existing?.priceAmount?.let { Money.inputText(it, currency) } ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val action = rememberAction { failure ->
        errors = failure.fields.mapNotNull { (k, _) -> failure.fieldMessage(k)?.let { k to it } }.toMap()
        if (errors.isEmpty()) model.failure(failure)
    }
    val categories = model.catalog.services.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()

    fun save() {
        val minutes = duration.toIntOrNull()
        val amount = Money.parse(price, currency)
        val next = HashMap<String, String>()
        if (name.isBlank()) next["name"] = Copy.services.nameRequired
        if (minutes == null || minutes !in 5..600) next["durationMin"] = Copy.services.durationInvalid
        if (amount == null) next["priceAmount"] = Copy.services.priceRequired
        errors = next
        if (next.isNotEmpty()) return
        action.run {
            val body = objWithout(
                "name" to name.trim(), "category" to (category.trim().ifEmpty { null } ?: if (existing == null) Omit else null),
                "description" to (description.trim().ifEmpty { null } ?: if (existing == null) Omit else null),
                "durationMin" to minutes, "priceAmount" to amount, "active" to active,
            )
            if (existing == null) model.call { createService(it, body) } else model.call { updateService(it, existing.id, body) }
            model.refreshCatalog()
            model.toast(if (existing == null) Copy.services.created else Copy.services.updated)
            model.nav.pop()
        }
    }

    DetailScaffold(
        title = if (existing == null) Copy.services.new else Copy.services.edit, onBack = { model.nav.pop() },
        actions = { if (existing != null) IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, Copy.common.delete) } },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                PrimaryButton(Copy.common.save, Modifier.navigationBarsPadding().imePadding().padding(16.dp), busy = action.busy) { save() }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Field(name, { name = it; errors = errors - "name" }, Copy.common.name, error = errors["name"])
            Field(category, { category = it }, Copy.services.category, supporting = Copy.services.categoryHint)
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { option -> FilterChip(category == option, { category = option }, { Text(option) }) }
                }
            }
            Field(description, { description = it }, Copy.common.description, singleLine = false, minLines = 2, supporting = Copy.common.optional)
            Field(duration, { duration = it.filter { c -> c.isDigit() }; errors = errors - "durationMin" }, Copy.services.durationMin, keyboard = KeyboardType.Number, error = errors["durationMin"])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    FilterChip(duration == minutes.toString(), { duration = minutes.toString() }, { Text(BusinessTime.duration(minutes.toLong())) })
                }
            }
            Field(price, { price = it; errors = errors - "priceAmount" }, Copy.common.price, prefix = Money.symbol(currency) + " ", keyboard = moneyKeyboard(currency), error = errors["priceAmount"])
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Copy.common.active, style = MaterialTheme.typography.bodyLarge)
                    Text(Copy.services.activeHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                }
                Switch(active, { active = it })
            }
            if (existing != null && existing.paymentMode != "none") {
                Text("${Copy.services.onlinePayment}: ${Copy.services.paymentMode(existing.paymentMode)}", style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(Copy.services.deleteTitle, Copy.services.deleteBody, Copy.common.delete, danger = true, onDismiss = { confirmDelete = false }, onConfirm = {
            confirmDelete = false
            action.run {
                model.call { deleteService(it, existing.id) }
                model.refreshCatalog()
                model.toast(Copy.services.deleted)
                model.nav.pop()
            }
        })
    }
}

/** The team: who serves customers, what they do and whose hours they keep. */
@Composable
fun ProfessionalsScreen(model: AppModel) {
    val ext = LocalExtended.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(model.business?.id) { model.call { professionals(it) } }
    DetailScaffold(
        title = Copy.team.title, onBack = { model.nav.pop() },
        floating = {
            if (model.isAdmin) ExtendedFloatingActionButton(
                text = { Text(Copy.team.new) }, icon = { Icon(Icons.Filled.Add, null) }, onClick = { model.nav.push(Route.ProfessionalDetail(null)) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            val list = loader.value
            when {
                list == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                list == null -> SkeletonCards(4)
                list.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyState(Icons.Outlined.Groups, Copy.team.empty, Copy.team.emptyBody) } }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { professional ->
                        val index = list.indexOf(professional)
                        AppCard(onClick = { model.nav.push(Route.ProfessionalDetail(professional)) }, padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(professional.name, size = 48.dp, color = ext.person(index), photoUrl = model.api?.absolute(professional.photoUrl))
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(professional.name, style = MaterialTheme.typography.titleMedium, color = if (professional.active) MaterialTheme.colorScheme.onSurface else ext.muted)
                                    Text(Copy.team.servicesCount(professional.serviceIds.size), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                    Chip(if (professional.hasOwnHours) Copy.team.ownHours else Copy.team.locationHours, if (professional.hasOwnHours) ext.brand else ext.neutral, Modifier.padding(top = 4.dp))
                                }
                                if (model.isAdmin) Switch(professional.active, { active ->
                                    loader.value = loader.value?.map { if (it.id == professional.id) it.copy(active = active) else it }
                                    scope.launch {
                                        try { model.call { updateProfessional(it, professional.id, obj("active" to active)) }; model.refreshCatalog() }
                                        catch (failure: ApiException) { model.failure(failure); loader.load(refresh = true) }
                                    }
                                }) else if (!professional.active) Chip(Copy.common.inactive, ext.neutral)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A professional's basics, services and commissions, and their weekly hours as a view. */
@Composable
fun ProfessionalDetailScreen(model: AppModel, existing: Professional?) {
    val ext = LocalExtended.current
    val admin = model.isAdmin
    val services = model.catalog.services
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var serviceRate by remember { mutableStateOf(existing?.serviceCommission?.let { trimNumber(it) } ?: "0") }
    var productRate by remember { mutableStateOf(existing?.productCommission?.let { trimNumber(it) } ?: "0") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(existing?.serviceIds ?: services.filter { it.active }.map { it.id }) } }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val action = rememberAction { model.failure(it) }
    val hours = rememberLoader(existing?.id, model.catalog.locations.size) {
        val id = existing?.id ?: return@rememberLoader emptyList<Pair<String, List<HourRange>?>>()
        val locations = model.catalog.locations.filter { existing.locationIds.isEmpty() || it.id in existing.locationIds }
        coroutineScope {
            locations.map { location -> async { location.name to model.call { hours(it, location.id) }.professionals[id] } }.map { it.await() }
        }
    }
    val locationHours = rememberLoader(existing?.id, "location") {
        if (existing == null) return@rememberLoader emptyMap<String, List<HourRange>>()
        val locations = model.catalog.locations.filter { existing.locationIds.isEmpty() || it.id in existing.locationIds }
        coroutineScope { locations.map { location -> async { location.name to model.call { hours(it, location.id) }.business } }.associate { it.await() } }
    }

    fun save() {
        val sr = serviceRate.replace(',', '.').toDoubleOrNull()
        val pr = productRate.replace(',', '.').toDoubleOrNull()
        val next = HashMap<String, String>()
        if (name.isBlank()) next["name"] = Copy.team.nameRequired
        if (sr == null || sr !in 0.0..100.0) next["serviceCommission"] = Copy.team.percentInvalid
        if (pr == null || pr !in 0.0..100.0) next["productCommission"] = Copy.team.percentInvalid
        errors = next
        if (next.isNotEmpty()) return
        action.run {
            val body = obj("name" to name.trim(), "active" to active, "serviceIds" to chosen.toList(), "serviceCommission" to sr, "productCommission" to pr)
            if (existing == null) model.call { createProfessional(it, body) } else model.call { updateProfessional(it, existing.id, body) }
            model.refreshCatalog()
            model.toast(if (existing == null) Copy.team.created else Copy.team.updated)
            model.nav.pop()
        }
    }

    DetailScaffold(
        title = existing?.name ?: Copy.team.new, onBack = { model.nav.pop() },
        bottomBar = {
            if (admin) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                PrimaryButton(Copy.common.save, Modifier.navigationBarsPadding().imePadding().padding(16.dp), busy = action.busy) { save() }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(existing.name, size = 84.dp, color = ext.person(model.catalog.personIndex(existing.id)), photoUrl = model.api?.absolute(existing.photoUrl))
                }
            }
            if (admin) {
                Field(name, { name = it; errors = errors - "name" }, Copy.common.name, error = errors["name"])
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Copy.common.active, style = MaterialTheme.typography.bodyLarge)
                        Text(Copy.team.activeHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                    }
                    Switch(active, { active = it })
                }
                SectionTitle(Copy.team.commissions)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(serviceRate, { serviceRate = it }, Copy.team.serviceCommission, keyboard = KeyboardType.Decimal, error = errors["serviceCommission"], suffix = "%", modifier = Modifier.weight(1f))
                    Field(productRate, { productRate = it }, Copy.team.productCommission, keyboard = KeyboardType.Decimal, error = errors["productCommission"], suffix = "%", modifier = Modifier.weight(1f))
                }
            } else {
                Text(existing?.name ?: "", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            SectionTitle(Copy.team.services)
            AppCard(padding = PaddingValues(vertical = 4.dp)) {
                val shown = if (admin) services else services.filter { it.id in chosen }
                shown.forEachIndexed { index, service ->
                    if (index > 0) Hairline(Modifier.padding(start = 56.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = admin) { if (service.id in chosen) chosen.remove(service.id) else chosen.add(service.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(service.id in chosen, if (admin) ({ checked -> if (checked) chosen.add(service.id) else chosen.remove(service.id) }) else null, enabled = admin)
                        Text(service.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(BusinessTime.duration(service.durationMin.toLong()), style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
            if (existing != null) {
                SectionTitle(Copy.team.weeklyHours)
                val own = hours.value
                val fallback = locationHours.value
                when {
                    own == null || fallback == null -> SkeletonCards(1, Modifier.padding(horizontal = 0.dp))
                    else -> own.forEach { (locationName, ranges) ->
                        AppCard(Modifier.padding(bottom = 8.dp)) {
                            if (model.catalog.locations.size > 1) Text(locationName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                            if (ranges.isNullOrEmpty()) {
                                Text(Copy.team.usesLocationHours, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(bottom = 6.dp))
                                WeekView(fallback[locationName] ?: emptyList())
                            } else WeekView(ranges)
                        }
                    }
                }
                Text(Copy.team.hoursOnWeb, style = MaterialTheme.typography.bodySmall, color = ext.muted)
            }
        }
    }
}

private fun trimNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Monday to Sunday with each day's ranges, or "Cerrado". */
@Composable
fun WeekView(ranges: List<HourRange>) {
    val ext = LocalExtended.current
    val locale = AppLocale.locale
    BusinessTime.weekOrder.forEach { weekday ->
        val day = ranges.filter { it.weekday == weekday }.sortedBy { it.start }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(BusinessTime.weekdayName(weekday, locale), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
            Text(
                if (day.isEmpty()) Copy.hours.closed else day.joinToString("  ·  ") { "${it.start} – ${it.end}" },
                style = MaterialTheme.typography.bodyMedium.tabular, color = if (day.isEmpty()) ext.muted else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (day.isEmpty()) FontWeight.Normal else FontWeight.Medium,
            )
        }
    }
}
