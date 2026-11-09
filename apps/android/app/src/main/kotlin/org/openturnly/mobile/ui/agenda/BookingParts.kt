@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.agenda

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Professional
import org.openturnly.mobile.api.Service
import org.openturnly.mobile.api.Slot
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Skeleton
import org.openturnly.mobile.ui.tabular

/** The services a location offers, active ones only, in the business's order. */
fun bookableServices(model: AppModel, locationId: String?): List<Service> {
    val location = model.catalog.location(locationId)
    return model.catalog.services.filter { service ->
        service.active && (location == null || location.serviceIds.isEmpty() || service.id in location.serviceIds)
    }
}

/** Who can do a service at a location: they offer it, work there and are active. */
fun bookableProfessionals(model: AppModel, serviceId: String?, locationId: String?): List<Professional> {
    val location = model.catalog.location(locationId)
    return model.catalog.professionals.filter { professional ->
        professional.active && (serviceId == null || serviceId in professional.serviceIds) &&
            (location == null || location.resourceIds.isEmpty() || professional.id in location.resourceIds)
    }
}

/** A tappable field that shows the chosen service and opens the list. */
@Composable
fun ServiceField(model: AppModel, service: Service?, onClick: () -> Unit) {
    val ext = LocalExtended.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), color = ext.card,
        border = BorderStroke(1.dp, if (service == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContentCut, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (service == null) Text(Copy.booking.pickService, style = MaterialTheme.typography.bodyLarge, color = ext.muted)
                else {
                    Text(service.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(serviceLine(model, service), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                }
            }
            Icon(Icons.Filled.ExpandMore, null, tint = ext.muted)
        }
    }
}

fun serviceLine(model: AppModel, service: Service): String =
    listOf(BusinessTime.duration(service.durationMin.toLong()), service.priceAmount?.let { Money.format(it, model.currency) } ?: Copy.common.priceOnRequest).joinToString(" · ")

/** The list of services, grouped by category, in a sheet. */
@Composable
fun ServicePickerSheet(model: AppModel, services: List<Service>, selectedId: String?, onPick: (Service) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = LocalExtended.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(Copy.booking.pickService, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val groups = services.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: Copy.services.noCategory }
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            groups.forEach { (category, list) ->
                item(key = "c-$category") {
                    Text(category.uppercase(), style = MaterialTheme.typography.labelMedium, color = ext.muted, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
                }
                items(list, key = { it.id }) { service ->
                    val selected = service.id == selectedId
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(service) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            Text(BusinessTime.duration(service.durationMin.toLong()), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                        Text(service.priceAmount?.let { Money.format(it, model.currency) } ?: Copy.common.priceOnRequest, style = MaterialTheme.typography.bodyMedium.tabular)
                        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 10.dp).size(20.dp))
                    }
                }
            }
        }
    }
}

/** "Quien esté libre" and every professional who can do the service. */
@Composable
fun ProfessionalPicker(model: AppModel, professionals: List<Professional>, selectedId: String?, onPick: (String?) -> Unit) {
    val ext = LocalExtended.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selectedId == null, { onPick(null) }, { Text(Copy.booking.anyFree) },
            leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) },
        )
        professionals.forEach { professional ->
            val color = ext.person(model.catalog.personIndex(professional.id))
            FilterChip(
                selectedId == professional.id, { onPick(professional.id) }, { Text(professional.name) },
                leadingIcon = { Avatar(professional.name, size = 22.dp, color = color, photoUrl = model.api?.absolute(professional.photoUrl)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f), selectedLabelColor = MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/** A calendar for any day first, then the next 60 days as chips. Staff can't book in the past. */
@Composable
fun DateChooser(model: AppModel, date: LocalDate, onPick: (LocalDate) -> Unit) {
    val locale = AppLocale.locale
    val today = model.today
    var picking by remember { mutableStateOf(false) }
    val days = (0L until 60L).map { today.plusDays(it) }
    val ext = LocalExtended.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { picking = true }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(width = 56.dp, height = 64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CalendarMonth, Copy.common.pickDate, tint = MaterialTheme.colorScheme.primary) }
        }
        val shown = if (date in days) days else listOf(date) + days
        shown.forEach { day ->
            val selected = day == date
            Surface(
                onClick = { onPick(day) }, shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else ext.card,
                border = if (selected) null else BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.widthIn(min = 56.dp).height(64.dp),
            ) {
                Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val label = when (day) { today -> Copy.common.today; today.plusDays(1) -> Copy.common.tomorrow; else -> BusinessTime.weekdayShort(day, locale) }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else ext.muted, maxLines = 1)
                    Text(
                        "${day.dayOfMonth} " + day.month.getDisplayName(java.time.format.TextStyle.SHORT, locale).replace(".", ""),
                        style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    )
                }
            }
        }
    }
    if (picking) {
        val todayMillis = BusinessTime.pickerMillis(today)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = BusinessTime.pickerMillis(date),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { onPick(BusinessTime.fromPickerMillis(it)) }; picking = false }) { Text(Copy.common.ok) } },
            dismissButton = { TextButton({ picking = false }) { Text(Copy.common.cancel) } },
        ) { DatePicker(state) }
    }
}

/**
 * The free times of the day, by part of the day. The current time of a moved
 * appointment ([current]) is drawn with a dashed outline.
 */
@Composable
fun SlotGrid(
    slots: List<Slot>?,
    loading: Boolean,
    error: String?,
    selected: String?,
    waitingForService: Boolean,
    current: String? = null,
    onPick: (String) -> Unit,
) {
    val ext = LocalExtended.current
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        when {
            waitingForService -> Text(Copy.booking.pickServiceFirst, style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(vertical = 8.dp))
            loading && slots == null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(8) { Skeleton(Modifier.size(width = 72.dp, height = 40.dp), RoundedCornerShape(12.dp)) }
            }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            slots.isNullOrEmpty() -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ext.warning.container).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.EventBusy, null, tint = ext.warning.content, modifier = Modifier.size(20.dp))
                Text(Copy.booking.noFreeTimes, style = MaterialTheme.typography.bodyMedium, color = ext.warning.content, modifier = Modifier.padding(start = 10.dp))
            }
            else -> {
                val groups = slots.groupBy { slot -> runCatching { Appointments.segment(LocalTime.parse(slot.time)) }.getOrDefault(Appointments.Segment.MORNING) }.toSortedMap()
                groups.forEach { (segment, list) ->
                    Text(Copy.agenda.segment(segment), style = MaterialTheme.typography.labelLarge, color = ext.muted, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { slot ->
                            val isSelected = slot.time == selected
                            val isCurrent = slot.time == current && !isSelected
                            Surface(
                                onClick = { onPick(slot.time) }, shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) primary else ext.card,
                                border = if (isSelected || isCurrent) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(width = 72.dp, height = 42.dp)
                                    .let { if (isCurrent) it.drawBehind { dashedOutline(primary) } else it }
                                    .let { if (slot.time == current) it.semantics { stateDescription = Copy.booking.currentTime } else it },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        slot.time, style = MaterialTheme.typography.titleSmall.tabular, textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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

/** A 1.5 dp dashed rounded outline, inset so the stroke stays inside. */
private fun DrawScope.dashedOutline(color: Color) {
    val stroke = 1.5.dp.toPx()
    val radius = 12.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** A numbered step heading in the booking forms: the number turns into a check once the step is complete. */
@Composable
fun StepTitle(number: Int, title: String, done: Boolean, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            else Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = LocalExtended.current.muted, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
