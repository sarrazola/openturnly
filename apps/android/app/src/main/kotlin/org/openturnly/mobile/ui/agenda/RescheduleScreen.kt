@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.agenda

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
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/** "Editar cita": another day, time, professional or service, from the times free for it. */
@Composable
fun RescheduleScreen(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val zone = model.zone
    val locale = AppLocale.locale
    val start = BusinessTime.parse(appointment.startsAt)
    val originalDay = start?.let { BusinessTime.localDate(it, zone) }
    val professional = !model.isAdmin

    var serviceId by remember { mutableStateOf(appointment.serviceId?.takeIf { model.catalog.service(it) != null }) }
    var resourceId by remember { mutableStateOf(if (professional) model.ownResourceId else appointment.resourceId) }
    var date by remember { mutableStateOf(originalDay?.takeIf { !it.isBefore(model.today) } ?: model.today) }
    var time by remember { mutableStateOf<String?>(null) }
    var pickingService by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }

    if (!model.catalog.loaded) LaunchedEffect(Unit) { model.refreshCatalog() }
    val service = model.catalog.service(serviceId)
    val slots = rememberLoader(serviceId, date, version) {
        val id = serviceId ?: return@rememberLoader emptyList()
        model.call { rescheduleSlots(it, appointment.id, id, date.toString(), null) }
    }
    val visible = slots.value?.filter { resourceId == null || resourceId in it.resourceIds }
    LaunchedEffect(visible) { if (time != null && visible != null && visible.none { it.time == time }) time = null }

    val action = rememberAction { failure ->
        error = failure.message
        if (failure.code == "slot_taken") { time = null; version++ }
    }

    DetailScaffold(
        title = Copy.booking.rescheduleTitle,
        onBack = { model.nav.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp)) }
                    PrimaryButton(Copy.common.saveChanges, enabled = serviceId != null && time != null, busy = action.busy) {
                        val chosenService = serviceId ?: return@PrimaryButton
                        val chosenTime = time ?: return@PrimaryButton
                        action.run {
                            model.call {
                                reschedule(it, appointment.id, obj("serviceId" to chosenService, "resourceId" to resourceId, "date" to date.toString(), "time" to chosenTime))
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
            Text(
                Names.full(appointment.customer.firstName, appointment.customer.lastName),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(Copy.booking.rescheduleHint(appointment.code), style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(top = 2.dp))
            if (start != null && originalDay != null) {
                AppCard(Modifier.padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = ext.muted, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(Copy.booking.currentTime, style = MaterialTheme.typography.labelMedium, color = ext.muted)
                            Text(
                                "${BusinessTime.longDay(originalDay, locale)} · ${BusinessTime.time(start, zone)}",
                                style = MaterialTheme.typography.bodyLarge.tabular,
                            )
                            Text(listOfNotNull(appointment.serviceName, appointment.resourceName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                    }
                }
            }
            StepTitle(1, Copy.booking.service, service != null)
            ServiceField(model, service) { pickingService = true }
            StepTitle(2, Copy.booking.professional, true)
            if (professional) {
                Text(Copy.booking.bookedForYou(appointment.resourceName ?: ""), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
            } else {
                ProfessionalPicker(model, bookableProfessionals(model, serviceId, appointment.locationId), resourceId) { resourceId = it }
            }
            StepTitle(3, Copy.booking.date, time != null)
            DateChooser(model, date) { date = it; time = null; error = null }
            Spacer(Modifier.height(14.dp))
            Text(Copy.booking.freeTimes, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            SlotGrid(if (serviceId == null) null else visible, slots.loading, slots.error, time, waitingForService = serviceId == null) {
                time = it; error = null
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    if (pickingService) {
        ServicePickerSheet(model, bookableServices(model, appointment.locationId), serviceId, onPick = {
            serviceId = it.id; pickingService = false; time = null
            if (!professional && resourceId != null && bookableProfessionals(model, it.id, appointment.locationId).none { p -> p.id == resourceId }) resourceId = null
        }, onDismiss = { pickingService = false })
    }
}
