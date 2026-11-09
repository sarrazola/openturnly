@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.delay
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PhoneField
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader

/**
 * "Nueva cita": the customer (found or created here), the service, who does it
 * (or whoever is free), the day and one of the free times the server offers.
 */
@Composable
fun NewAppointmentScreen(model: AppModel, presetCustomer: CustomerSummary?, presetDate: LocalDate?) {
    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val business = model.business ?: return
    val catalog = model.catalog
    val locations = catalog.activeLocations
    val professional = !model.isAdmin

    var customer by remember { mutableStateOf(presetCustomer) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CustomerSummary>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(Phones.defaultCountry(business.country)) }
    var national by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var locationId by remember { mutableStateOf(model.agenda.locationId ?: locations.firstOrNull()?.id) }
    var serviceId by remember { mutableStateOf<String?>(null) }
    var resourceId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(presetDate?.takeIf { !it.isBefore(model.today) } ?: model.today) }
    var time by remember { mutableStateOf<String?>(null) }
    var pickingService by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var slotsVersion by remember { mutableStateOf(0) }

    if (!catalog.loaded) LaunchedEffect(Unit) { model.refreshCatalog() }
    if (locationId == null && locations.isNotEmpty()) locationId = locations.first().id

    val services = bookableServices(model, locationId)
    val service = catalog.service(serviceId)
    val people = bookableProfessionals(model, serviceId, locationId)

    // Searching customers as the person types (the server returns the first 20).
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = null; searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        results = runCatching { model.call { customers(it, q) } }.getOrNull() ?: emptyList()
        searching = false
    }

    val slots = rememberLoader(serviceId, date, locationId, slotsVersion) {
        val id = serviceId ?: return@rememberLoader emptyList()
        model.call { slots(it, id, date.toString(), if (locations.size > 1) locationId else null) }
    }
    val visibleSlots = slots.value?.filter { resourceId == null || resourceId in it.resourceIds }
    LaunchedEffect(visibleSlots) { if (time != null && visibleSlots != null && visibleSlots.none { it.time == time }) time = null }

    val action = rememberAction { failure ->
        when (failure.code) {
            "already_booked" -> {
                val at = BusinessTime.parse(failure.fields["startsAt"])
                val label = at?.let { BusinessTime.shortDay(BusinessTime.localDate(it, model.zone), AppLocale.locale) + " " + BusinessTime.time(it, model.zone) } ?: ""
                formError = Copy.booking.alreadyBooked(label, failure.fields["code"] ?: "")
            }
            "slot_taken" -> { formError = Copy.booking.slotTaken; time = null; slotsVersion++ }
            else -> {
                errors = failure.fields.mapNotNull { (key, _) -> failure.fieldMessage(key.substringAfterLast('.'))?.let { key.substringAfterLast('.') to it } }.toMap()
                formError = failure.message
            }
        }
    }

    fun submit() {
        formError = null
        val fieldErrors = HashMap<String, String>()
        val phone = Phones.compose(country, national)
        if (customer == null) {
            if (!creating) { formError = Copy.booking.pickCustomer; return }
            if (firstName.isBlank()) fieldErrors["firstName"] = Copy.customers.firstNameRequired
            if (lastName.isBlank()) fieldErrors["lastName"] = Copy.customers.lastNameRequired
            if (phone == null && email.isBlank()) fieldErrors["phone"] = Copy.customers.contactRequired
        }
        errors = fieldErrors
        if (fieldErrors.isNotEmpty()) return
        val chosenService = serviceId ?: run { formError = Copy.booking.pickService; return }
        val chosenTime = time ?: run { formError = Copy.booking.pickTime; return }
        action.run {
            val body = objWithout(
                "locationId" to (locationId ?: Omit),
                "serviceId" to chosenService,
                "resourceId" to (if (professional) model.ownResourceId else resourceId),
                "date" to date.toString(),
                "time" to chosenTime,
                "customerId" to (customer?.id ?: Omit),
                "customer" to (if (customer == null) objWithout(
                    "firstName" to firstName.trim(), "lastName" to lastName.trim(),
                    "phone" to (phone ?: Omit), "email" to (email.trim().ifEmpty { null } ?: Omit),
                ) else Omit),
            )
            model.call { book(it, body) }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            model.toast(Copy.booking.created)
            model.agenda.select(date)
            model.agenda.reloadFor(date)
            if (customer == null) model.customers.load(refresh = true)
            model.nav.pop()
        }
    }

    DetailScaffold(
        title = Copy.booking.title,
        onBack = { model.nav.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp)) }
                    if (service != null && time != null) {
                        Text(
                            "${service.name} · ${BusinessTime.shortDay(date, AppLocale.locale)} · $time",
                            style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(bottom = 10.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PrimaryButton(Copy.booking.create, busy = action.busy, icon = Icons.Outlined.EventAvailable) { submit() }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(Copy.booking.hint, style = MaterialTheme.typography.bodyMedium, color = ext.muted)

            // 1. Customer
            StepTitle(1, Copy.booking.customer, customer != null || (creating && firstName.isNotBlank()))
            val chosen = customer
            if (chosen != null) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Names.full(chosen.firstName, chosen.lastName), size = 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(Names.full(chosen.firstName, chosen.lastName), style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(chosen.phone?.let { Phones.format(it) }, chosen.email).joinToString(" · ").ifEmpty { Copy.customers.noContact }, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                        IconButton({ customer = null }) { Icon(Icons.Filled.Close, Copy.booking.changeCustomer) }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    SegmentedButton(!creating, { creating = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(Copy.booking.existing) }
                    SegmentedButton(creating, { creating = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(Copy.booking.newCustomer) }
                }
                AnimatedContent(creating, label = "customer-mode") { isNew ->
                    if (!isNew) {
                        Column {
                            Field(
                                query, { query = it }, Copy.common.search, placeholder = Copy.booking.searchCustomer,
                                leading = { Icon(Icons.Filled.Search, null) },
                                trailing = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                                supporting = if (query.trim().length < 2) Copy.booking.searchHint else null,
                            )
                            val found = results
                            if (found != null) {
                                AppCard(Modifier.padding(top = 4.dp), padding = PaddingValues(vertical = 4.dp)) {
                                    if (found.isEmpty()) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(Copy.booking.noCustomerFound, color = ext.muted, modifier = Modifier.weight(1f))
                                            TextButton({
                                                creating = true
                                                val words = query.trim().split(" ")
                                                if (words.firstOrNull()?.any { it.isLetter() } == true) { firstName = words.first(); lastName = words.drop(1).joinToString(" ") }
                                                else national = query.filter { it.isDigit() }
                                            }) { Text(Copy.booking.createCustomer) }
                                        }
                                    }
                                    found.forEachIndexed { index, option ->
                                        if (index > 0) Hairline(Modifier.padding(start = 64.dp))
                                        Row(
                                            Modifier.fillMaxWidth().clickable { customer = option; query = ""; results = null }.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Avatar(Names.full(option.firstName, option.lastName), size = 36.dp)
                                            Column(Modifier.padding(start = 12.dp)) {
                                                Text(Names.full(option.firstName, option.lastName), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                                Text(listOfNotNull(option.phone?.let { Phones.format(it) }, option.email).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(firstName, { firstName = it; errors = errors - "firstName" }, Copy.common.firstName, error = errors["firstName"], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
                                Field(lastName, { lastName = it; errors = errors - "lastName" }, Copy.common.lastName, error = errors["lastName"], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
                            }
                            PhoneField(country, national, { country = it }, { national = it; errors = errors - "phone" }, error = errors["phone"])
                            Field(email, { email = it; errors = errors - "email" }, Copy.common.email, keyboard = KeyboardType.Email, error = errors["email"], supporting = Copy.common.optional)
                            Text(Copy.booking.contactHint, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                    }
                }
            }

            // 2. Location and service
            StepTitle(2, Copy.booking.service, service != null)
            if (locations.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    locations.forEach { location ->
                        FilterChip(locationId == location.id, { locationId = location.id; time = null; if (serviceId !in bookableServices(model, location.id).map { it.id }) serviceId = null }, { Text(location.name) })
                    }
                }
            }
            ServiceField(model, service) { pickingService = true }

            // 3. Professional
            if (professional) {
                StepTitle(3, Copy.booking.professional, true)
                Text(Copy.booking.bookedForYou(model.catalog.professional(model.ownResourceId)?.name ?: model.user?.name ?: ""), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
            } else {
                StepTitle(3, Copy.booking.professional, true)
                ProfessionalPicker(model, people, resourceId) { resourceId = it }
            }

            // 4. Day and time
            StepTitle(4, Copy.booking.date, time != null)
            DateChooser(model, date) { date = it; time = null }
            Spacer(Modifier.height(14.dp))
            Text(Copy.booking.freeTimes, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            SlotGrid(if (serviceId == null) null else visibleSlots, slots.loading, slots.error, time, waitingForService = serviceId == null) {
                time = it; formError = null
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    if (pickingService) {
        ServicePickerSheet(model, services, serviceId, onPick = {
            serviceId = it.id; pickingService = false
            if (resourceId != null && bookableProfessionals(model, it.id, locationId).none { p -> p.id == resourceId }) resourceId = null
        }, onDismiss = { pickingService = false })
    }
}
