@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.HourRange
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/** The location's weekly hours (editable by admins), the online-booking rules and upcoming closures. */
@Composable
fun HoursScreen(model: AppModel) {
    val ext = LocalExtended.current
    val business = model.business ?: return
    val locations = model.catalog.locations
    var locationId by remember { mutableStateOf(locations.firstOrNull { it.active }?.id) }
    var editing by remember { mutableStateOf(false) }
    val draft = remember { mutableStateMapOf<Int, List<Pair<String, String>>>() }
    var error by remember { mutableStateOf<String?>(null) }
    val loader = rememberLoader(locationId) { model.call { hours(it, locationId) } }
    val closures = if (model.isAdmin) rememberLoader(business.id) { model.call { closures(it) } } else null
    val action = rememberAction { error = it.message }
    LaunchedEffect(loader.value?.locationId) { if (locationId == null) locationId = loader.value?.locationId }

    fun startEditing() {
        draft.clear()
        BusinessTime.weekOrder.forEach { weekday -> draft[weekday] = loader.value?.business.orEmpty().filter { it.weekday == weekday }.sortedBy { it.start }.map { it.start to it.end } }
        error = null
        editing = true
    }

    DetailScaffold(
        title = Copy.hours.title, onBack = { if (editing) editing = false else model.nav.pop() },
        actions = { if (model.isAdmin && !editing && loader.value != null) IconButton({ startEditing() }) { Icon(Icons.Outlined.Edit, Copy.common.edit) } },
        bottomBar = {
            if (editing) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    PrimaryButton(Copy.common.saveChanges, busy = action.busy) {
                        val ranges = draft.flatMap { (weekday, list) -> list.map { HourRange(weekday, it.first, it.second) } }
                        if (ranges.any { it.end <= it.start }) { error = Copy.hours.endBeforeStart; return@PrimaryButton }
                        val target = loader.value?.locationId ?: locationId ?: return@PrimaryButton
                        action.run {
                            model.call { saveHours(it, target, null, ranges) }
                            model.toast(Copy.hours.saved)
                            editing = false
                            loader.load(refresh = true)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (locations.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    locations.forEach { location -> FilterChip(locationId == location.id, { if (!editing) locationId = location.id }, { Text(location.name) }) }
                }
            }
            val hours = loader.value
            when {
                hours == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                hours == null -> SkeletonCards(1, Modifier.padding(horizontal = 0.dp))
                editing -> AppCard {
                    BusinessTime.weekOrder.forEachIndexed { index, weekday ->
                        if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
                        val ranges = draft[weekday].orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(BusinessTime.weekdayName(weekday, AppLocale.locale), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(if (ranges.isEmpty()) Copy.hours.closed else Copy.hours.open, color = ext.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Switch(ranges.isNotEmpty(), { open -> draft[weekday] = if (open) listOf("09:00" to "18:00") else emptyList() })
                        }
                        ranges.forEachIndexed { rangeIndex, (start, end) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                TimeButton(start) { value -> draft[weekday] = ranges.toMutableList().also { it[rangeIndex] = value to end } }
                                Text("–", modifier = Modifier.padding(horizontal = 8.dp))
                                TimeButton(end) { value -> draft[weekday] = ranges.toMutableList().also { it[rangeIndex] = start to value } }
                                Spacer(Modifier.weight(1f))
                                IconButton({ draft[weekday] = ranges.filterIndexed { i, _ -> i != rangeIndex } }) { Icon(Icons.Filled.Close, Copy.common.delete, tint = ext.muted) }
                            }
                        }
                        if (ranges.isNotEmpty()) TextButton({ draft[weekday] = ranges + ((ranges.last().second) to "20:00") }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.hours.addRange)
                        }
                    }
                }
                else -> AppCard { WeekView(hours.business) }
            }

            SectionTitle(Copy.hours.bookingRules)
            AppCard {
                InfoRow(Copy.hours.slotInterval, "${business.slotIntervalMin} min")
                InfoRow(Copy.hours.minNotice, Copy.hours.minutesLabel(business.minNoticeMin))
                InfoRow(Copy.hours.maxAdvance, Copy.hours.days(business.maxAdvanceDays))
                InfoRow(Copy.hours.holidays, if (business.closedOnHolidays) Copy.common.yes else Copy.common.no)
            }
            if (closures != null) {
                SectionTitle(Copy.hours.closures)
                AppCard {
                    val list = closures.value
                    when {
                        list == null -> Text("…", color = ext.muted)
                        list.isEmpty() -> Text(Copy.hours.noClosures, color = ext.muted)
                        else -> list.forEachIndexed { index, closure ->
                            if (index > 0) Hairline(Modifier.padding(vertical = 6.dp))
                            val from = BusinessTime.parseDate(closure.date)
                            val to = BusinessTime.parseDate(closure.endDate)
                            val dates = listOfNotNull(from, to?.takeIf { it != from }).joinToString(" – ") { BusinessTime.mediumDate(it, AppLocale.locale) }
                            val scope = model.catalog.professional(closure.resourceId)?.name ?: model.catalog.location(closure.locationId)?.name ?: Copy.hours.wholeBusiness
                            Column {
                                Text(dates, style = MaterialTheme.typography.bodyLarge.tabular, fontWeight = FontWeight.Medium)
                                Text(listOfNotNull(scope, closure.note).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A wall-clock time that opens the platform's time picker, in 24 hours. */
@Composable
fun TimeButton(value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, shape = MaterialTheme.shapes.small) { Text(value, style = MaterialTheme.typography.titleSmall.tabular) }
    if (open) {
        val (h, m) = value.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val state = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton({ onPick("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text(Copy.common.ok) } },
            dismissButton = { TextButton({ open = false }) { Text(Copy.common.cancel) } },
            text = { TimePicker(state) },
        )
    }
}
