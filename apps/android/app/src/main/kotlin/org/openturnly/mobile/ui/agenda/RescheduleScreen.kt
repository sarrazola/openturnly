@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Booking
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/**
 * "Editar cita": another location, service, professional, day or time, from the
 * times free for it. The current time stays picked (dashed) while it is offered.
 */
@Composable
fun RescheduleScreen(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val zone = model.zone
    val locale = AppLocale.locale
    val start = BusinessTime.parse(appointment.startsAt)
    val originalDay = start?.let { BusinessTime.localDate(it, zone) }
    val originalTime = start?.let { BusinessTime.time(it, zone) }
    val professional = !model.isAdmin
    // The appointment's own location stays offered even if it closed since.
    val places = Booking.places(model.catalog.locations, keeping = appointment.locationId, professionalId = model.ownResourceId)

    var locationId by remember { mutableStateOf(appointment.locationId) }
    var serviceId by remember { mutableStateOf(appointment.serviceId?.takeIf { model.catalog.service(it) != null }) }
    var resourceId by remember { mutableStateOf(if (professional) model.ownResourceId else appointment.resourceId) }
    var date by remember { mutableStateOf(originalDay?.takeIf { !it.isBefore(model.today) } ?: model.today) }
    var time by remember { mutableStateOf<String?>(null) }
    var pickingService by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }

    if (!model.catalog.loaded) LaunchedEffect(Unit) { model.refreshCatalog() }
    // The catalog may arrive after the screen: the appointment's service is then picked once.
    LaunchedEffect(model.catalog.loaded) {
        if (serviceId == null && model.catalog.service(appointment.serviceId) != null) serviceId = appointment.serviceId
    }
    val service = model.catalog.service(serviceId)
    val slots = rememberLoader(serviceId, date, locationId, version) {
        val id = serviceId ?: return@rememberLoader emptyList()
        model.call { rescheduleSlots(it, appointment.id, id, date.toString(), locationId) }
    }
    val visible = slots.value?.filter { resourceId == null || resourceId in it.resourceIds }
    val current = originalTime.takeIf { date == originalDay }
    // Keep the current time picked while it still fits; drop a picked time that no longer does.
    LaunchedEffect(visible) {
        if (visible == null) return@LaunchedEffect
        if (current != null && visible.any { it.time == current } && (time == null || visible.none { it.time == time })) time = current
        else if (time != null && visible.none { it.time == time }) time = null
    }

    val action = rememberAction { failure ->
        error = failure.message
        if (failure.code == "slot_taken") { time = null; version++ }
    }
    BackHandler(enabled = action.busy) {}

    DetailScaffold(
        title = Copy.booking.rescheduleTitle,
        onBack = { if (!action.busy) model.nav.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp)) }
                    PrimaryButton(Copy.common.saveChanges, enabled = serviceId != null && time != null, busy = action.busy) {
                        val chosenService = serviceId ?: return@PrimaryButton
                        val chosenTime = time ?: return@PrimaryButton
                        action.run {
                            model.call {
                                reschedule(it, appointment.id, objWithout(
                                    "serviceId" to chosenService, "resourceId" to resourceId, "date" to date.toString(), "time" to chosenTime,
                                    "locationId" to (locationId ?: Omit),
                                ))
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            model.toast(Copy.booking.rescheduled)
                            model.agenda.select(date)
                            model.agenda.reloadFor(originalDay, date)
                            model.nav.pop()
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(Names.full(appointment.customer.firstName, appointment.customer.lastName), style = MaterialTheme.typography.titleLarge)
            Text(Copy.booking.rescheduleHint(appointment.code), style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(top = 2.dp))
            if (start != null && originalDay != null) {
                AppCard(Modifier.padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = ext.muted, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(Copy.booking.currentTime, style = MaterialTheme.typography.labelMedium, color = ext.muted)
                            Text("${BusinessTime.longDay(originalDay, locale, model.today)} · ${BusinessTime.time(start, zone)}", style = MaterialTheme.typography.bodyLarge.tabular)
                            Text(
                                listOfNotNull(appointment.serviceName, appointment.resourceName ?: Copy.agenda.noProfessional).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = ext.muted,
                            )
                        }
                    }
                }
            }
            StepTitle(1, Copy.booking.service, service != null)
            if (places.size > 1) {
                Text(Copy.booking.location, style = MaterialTheme.typography.labelLarge, color = ext.muted, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.forEach { location ->
                        FilterChip(locationId == location.id, {
                            locationId = location.id; time = null; error = null
                            if (serviceId !in bookableServices(model, location.id).map { it.id }) serviceId = null
                            if (!professional && resourceId != null && bookableProfessionals(model, serviceId, location.id).none { it.id == resourceId }) resourceId = null
                        }, { Text(location.name) })
                    }
                }
            }
            ServiceField(model, service) { pickingService = true }
            StepTitle(2, Copy.booking.professional, service != null)
            if (professional) {
                Text(Copy.booking.bookedForYou(appointment.resourceName ?: model.catalog.professional(model.ownResourceId)?.name ?: ""), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
            } else {
                ProfessionalPicker(model, bookableProfessionals(model, serviceId, locationId), resourceId) { resourceId = it }
            }
            StepTitle(3, Copy.booking.date, time != null, trailing = BusinessTime.longDay(date, locale, model.today))
            DateChooser(model, date) { date = it; time = null; error = null }
            Spacer(Modifier.height(14.dp))
            Text(Copy.booking.freeTimes, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            SlotGrid(if (serviceId == null) null else visible, slots.loading, slots.error, time, waitingForService = serviceId == null, current = current) {
                time = it; error = null
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    if (pickingService) {
        val mine = model.catalog.professional(model.ownResourceId)?.serviceIds
        ServicePickerSheet(model, bookableServices(model, locationId).filter { mine == null || it.id in mine || it.id == appointment.serviceId }, serviceId, onPick = {
            serviceId = it.id; pickingService = false; time = null
            if (!professional && resourceId != null && bookableProfessionals(model, it.id, locationId).none { p -> p.id == resourceId }) resourceId = null
        }, onDismiss = { pickingService = false })
    }
}
