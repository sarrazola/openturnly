@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.openturnly.mobile.ui.agenda

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.BusinessLogo
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.Skeleton
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.notifications.NotificationBell
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.tabular

private const val PAGES = 2000
private const val MIDDLE = PAGES / 2

/**
 * The day's appointments: a week strip to move between days, filters by
 * professional and location, and the appointments in parts of the day.
 */
@Composable
fun AgendaScreen(model: AppModel) {
    val agenda = model.agenda
    val zone = model.zone
    val today = BusinessTime.today(zone, agenda.now)
    val catalog = model.catalog
    val business = model.business ?: return
    var picking by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    val canFilter = model.isAdmin || catalog.activeLocations.size > 1
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val dayAppointments = agenda.appointmentsOn(agenda.date)
    val visible = dayAppointments?.let { Appointments.filter(it, agenda.professionalId, agenda.locationId, agenda.showCancelled) }

    // The bell is fresh whenever the agenda shows. Once it has loaded, this phone registers
    // for push, asking for the permission first (once) where the phone needs it.
    val notifications = model.notifications
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) notifications.register()
    }
    LaunchedEffect(business.id) { notifications.refreshCount() }
    val loaded = dayAppointments != null
    LaunchedEffect(business.id, loaded, notifications.serverPush) {
        if (!loaded) return@LaunchedEffect
        if (notifications.shouldAskPermission()) {
            notifications.markAsked()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notifications.register()
        }
    }

    // The long-press menu's confirmations and the work they start.
    var confirmCancel by remember { mutableStateOf<Appointment?>(null) }
    var confirmNoShow by remember { mutableStateOf<Appointment?>(null) }
    val haptics = LocalHapticFeedback.current
    val action = rememberAction { model.failure(it) }
    fun changed(appointment: Appointment, message: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        model.toast(message)
        model.agenda.reloadFor(BusinessTime.parse(appointment.startsAt)?.let { BusinessTime.localDate(it, zone) })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BusinessButton(model)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(agenda.date != today, enter = fadeIn(), exit = fadeOut()) {
                    TextButton({ agenda.select(today); scope.launch { listState.scrollToItem(0) } }) { Text(Copy.common.today) }
                }
                IconButton({ picking = true }) { Icon(Icons.Outlined.CalendarMonth, Copy.agenda.pickDay) }
                if (canFilter) {
                    IconButton({ filtering = true }) {
                        BadgedBox(badge = {
                            if (agenda.professionalId != null || agenda.locationId != null || agenda.showCancelled) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        }) { Icon(Icons.Outlined.FilterList, Copy.agenda.filters) }
                    }
                }
                NotificationBell(model)
            }
            WeekStrip(model, today)
            ActiveFilters(model) { filtering = true }
            PullToRefreshBox(isRefreshing = agenda.refreshing, onRefresh = { agenda.refresh(); model.refreshCatalog(); notifications.refreshCount() }, modifier = Modifier.fillMaxSize()) {
                DayList(
                    model, visible, dayAppointments, today, listState,
                    onCancel = { confirmCancel = it }, onNoShow = { confirmNoShow = it },
                )
            }
        }
        val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        ExtendedFloatingActionButton(
            text = { Text(Copy.agenda.newAppointment) },
            icon = { Icon(Icons.Filled.Add, null) },
            expanded = expanded,
            onClick = { model.nav.push(Route.NewAppointment(date = agenda.date.takeIf { !it.isBefore(today) })) },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (filtering) AgendaFilterSheet(model) { filtering = false }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = BusinessTime.pickerMillis(agenda.date))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { agenda.select(BusinessTime.fromPickerMillis(it)) }
                    picking = false
                    scope.launch { listState.scrollToItem(0) }
                }) { Text(Copy.common.ok) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(Copy.common.cancel) } },
        ) { DatePicker(state) }
    }

    confirmCancel?.let { appointment ->
        ConfirmDialog(
            Copy.agenda.cancelTitle, Copy.agenda.confirmCancel, Copy.agenda.cancelAppointment,
            onConfirm = {
                confirmCancel = null
                action.run { model.call { cancel(it, appointment.id) }; changed(appointment, Copy.agenda.cancelled) }
            },
            onDismiss = { confirmCancel = null }, dismiss = Copy.agenda.keep, danger = true,
        )
    }
    confirmNoShow?.let { appointment ->
        ConfirmDialog(
            Copy.agenda.noShowTitle, Copy.agenda.noShowBody, Copy.agenda.markNoShow,
            onConfirm = {
                confirmNoShow = null
                action.run { model.call { markNoShow(it, appointment.id) }; changed(appointment, Copy.agenda.noShowMarked) }
            },
            onDismiss = { confirmNoShow = null }, dismiss = Copy.agenda.goBack, danger = true,
        )
    }
}

/**
 * The business's logo square in its own round button. With more than one
 * business a chevron follows it and a tap opens the switcher; with one it is
 * only the logo.
 */
@Composable
private fun BusinessButton(model: AppModel) {
    val business = model.business ?: return
    val several = model.businesses.size > 1
    Surface(
        shape = CircleShape, color = Color.Transparent,
        modifier = Modifier.clip(CircleShape)
            .let { if (several) it.clickable { model.showBusinessPicker = true } else it }
            .semantics { contentDescription = if (several) "${business.name}, ${Copy.nav.switchBusiness}" else business.name },
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            BusinessLogo(model, business, 32.dp)
            if (several) Icon(Icons.Filled.KeyboardArrowDown, null, tint = LocalExtended.current.muted, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
    }
}

/** Seven days at a time; the chevrons and a swipe move a week and keep the weekday. */
@Composable
private fun WeekStrip(model: AppModel, today: LocalDate) {
    val agenda = model.agenda
    val locale = AppLocale.locale
    val baseWeek = remember { BusinessTime.weekStart(today) }
    fun pageOf(date: LocalDate) = MIDDLE + BusinessTime.weeksBetween(baseWeek, date).toInt()
    val pager = rememberPagerState(initialPage = pageOf(agenda.date)) { PAGES }

    LaunchedEffect(agenda.date) {
        val target = pageOf(agenda.date)
        if (pager.currentPage != target) pager.animateScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val week = baseWeek.plusWeeks((page - MIDDLE).toLong())
            if (BusinessTime.weekStart(agenda.date) != week) {
                agenda.select(week.plusDays((agenda.date.dayOfWeek.value - 1).toLong()))
            }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            BusinessTime.monthYear(BusinessTime.weekStart(agenda.date).plusDays(3), locale),
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
        )
        IconButton({ agenda.select(agenda.date.minusWeeks(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Copy.agenda.previousWeek, tint = primary) }
        IconButton({ agenda.select(agenda.date.plusWeeks(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, Copy.agenda.nextWeek, tint = primary) }
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
        val week = baseWeek.plusWeeks((page - MIDDLE).toLong())
        val loaded = agenda.weeks[week]
        LaunchedEffect(week) { if (loaded == null) agenda.load(week) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (offset in 0 until 7) {
                val day = week.plusDays(offset.toLong())
                val busy = loaded?.let { list -> Appointments.busy(Appointments.onDay(list, day, model.zone), agenda.professionalId, agenda.locationId) } ?: false
                DayCell(day, locale, selected = day == agenda.date, isToday = day == today, busy = busy, modifier = Modifier.weight(1f)) { agenda.select(day) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, locale: java.util.Locale, selected: Boolean, isToday: Boolean, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "day")
    val content = when {
        selected -> scheme.onPrimary
        isToday -> scheme.primary
        else -> scheme.onSurface
    }
    Column(
        modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(16.dp)).background(background)
            .let { if (isToday && !selected) it.border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) else it }
            .clickable(onClick = onClick).padding(vertical = 8.dp)
            .semantics { contentDescription = BusinessTime.longDay(day, locale) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            BusinessTime.weekdayShort(day, locale).take(3), style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimary.copy(alpha = 0.85f) else LocalExtended.current.muted,
        )
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.tabular, color = content, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.height(8.dp).padding(top = 3.dp), contentAlignment = Alignment.Center) {
            if (busy) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) scheme.onPrimary else scheme.primary.copy(alpha = 0.75f)))
        }
    }
}

/** What the agenda is narrowed to: tap to change it, the cross clears all of it. */
@Composable
private fun ActiveFilters(model: AppModel, onOpen: () -> Unit) {
    val agenda = model.agenda
    val catalog = model.catalog
    val person = catalog.professionals.firstOrNull { it.id == agenda.professionalId }
    val location = catalog.location(agenda.locationId)
    if (person == null && location == null && !agenda.showCancelled) {
        Spacer(Modifier.height(6.dp))
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val summary = listOfNotNull(person?.name, location?.name, Copy.agenda.withCancelled.takeIf { agenda.showCancelled }).joinToString(" · ")
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).height(36.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().clickable(onClick = onOpen).padding(start = if (person != null) 6.dp else 12.dp, end = 4.dp)
                .semantics { contentDescription = "${Copy.agenda.filters}: $summary" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (person != null) {
                Avatar(person.name, size = 24.dp, color = LocalExtended.current.person(catalog.personIndex(person.id)), photoUrl = model.api?.absolute(person.photoUrl))
            } else {
                Icon(Icons.Outlined.FilterList, null, tint = primary, modifier = Modifier.size(18.dp))
            }
            Text(
                summary, color = primary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.size(36.dp).clickable { agenda.clearFilters() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, Copy.agenda.clearFilters, tint = primary, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Whose agenda to see (everyone or one person), at which location, and whether
 * cancelled appointments show. Picking a person closes the sheet; a long team
 * gets a search field.
 */
@Composable
private fun AgendaFilterSheet(model: AppModel, onDismiss: () -> Unit) {
    val agenda = model.agenda
    val catalog = model.catalog
    val locations = catalog.activeLocations
    val team = catalog.professionals.filter { it.active || it.id == agenda.professionalId }
    val searchable = team.size > 8
    var query by remember { mutableStateOf("") }
    val shown = query.trim().let { text -> if (text.isEmpty()) team else team.filter { it.name.contains(text, ignoreCase = true) } }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = searchable)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Copy.agenda.filters, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (agenda.professionalId != null || agenda.locationId != null) {
                        TextButton({ agenda.professionalId = null; agenda.locationId = null }) { Text(Copy.agenda.clearFilters) }
                    }
                }
            }
            if (model.isAdmin && team.size > 1) {
                item { SheetLabel(Copy.agenda.professional) }
                if (searchable) {
                    item {
                        Field(
                            query, { query = it }, Copy.agenda.searchStaff, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            imeAction = ImeAction.Search, leading = { Icon(Icons.Outlined.Search, null) },
                        )
                    }
                }
                if (query.isBlank()) {
                    item {
                        FilterOption(Copy.agenda.allProfessionals, agenda.professionalId == null, leading = {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }) { agenda.professionalId = null; close() }
                    }
                }
                items(shown, key = { it.id }) { professional ->
                    FilterOption(professional.name, agenda.professionalId == professional.id, leading = {
                        Avatar(professional.name, size = 36.dp, color = LocalExtended.current.person(catalog.personIndex(professional.id)), photoUrl = model.api?.absolute(professional.photoUrl))
                    }) { agenda.professionalId = professional.id; close() }
                }
            }
            if (locations.size > 1 && query.isBlank()) {
                item { SheetLabel(Copy.agenda.location) }
                item { FilterOption(Copy.common.allLocations, agenda.locationId == null) { agenda.locationId = null } }
                items(locations, key = { "location-" + it.id }) { location ->
                    FilterOption(location.name, agenda.locationId == location.id) { agenda.locationId = location.id }
                }
            }
            if (model.isAdmin && query.isBlank()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { agenda.showCancelled = !agenda.showCancelled }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Copy.agenda.showCancelledLong, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(agenda.showCancelled, { agenda.showCancelled = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = LocalExtended.current.muted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterOption(title: String, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The list under the strip: the day in words with its summary first, then the
 * appointments by part of the day with the "now" line on today, or the empty,
 * failed or loading state.
 */
@Composable
private fun DayList(
    model: AppModel,
    visible: List<Appointment>?,
    day: List<Appointment>?,
    today: LocalDate,
    listState: LazyListState,
    onCancel: (Appointment) -> Unit,
    onNoShow: (Appointment) -> Unit,
) {
    val agenda = model.agenda
    val zone = model.zone
    val now = agenda.now
    val date = agenda.date
    val sections = visible?.let { Appointments.group(it, zone) } ?: emptyList()
    val ordered = sections.flatMap { it.appointments }
    val line = if (date == today && ordered.isNotEmpty()) Appointments.nowLineAfter(ordered, now) else -1
    val lineAfterId = if (line > 0) ordered[line - 1].id else null

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)) {
        item(key = "day") { DayHeader(model, date, today, visible) }
        when {
            visible == null && agenda.error != null -> item(key = "error") {
                ErrorState(agenda.error ?: Copy.agenda.loadFailed, { agenda.loadCurrent() })
            }
            visible == null -> item(key = "loading") { AgendaSkeleton() }
            visible.isEmpty() -> item(key = "empty") {
                if (Appointments.hiddenByFilters(day ?: emptyList(), agenda.professionalId, agenda.locationId, agenda.showCancelled)) {
                    EmptyState(Icons.Outlined.FilterAltOff, Copy.agenda.emptyFiltered, action = Copy.agenda.clearFilters, onAction = { agenda.clearFilters() })
                } else if (date.isBefore(today)) {
                    EmptyState(Icons.Outlined.EventAvailable, Copy.agenda.emptyTitle)
                } else {
                    EmptyState(
                        Icons.Outlined.EventAvailable, Copy.agenda.emptyTitle,
                        action = Copy.agenda.newAppointment, onAction = { model.nav.push(Route.NewAppointment(date = date)) },
                    )
                }
            }
            else -> {
                if (line == 0) item(key = "now") { NowLine(BusinessTime.time(now, zone), Modifier.animateItem().padding(top = 8.dp)) }
                sections.forEach { section ->
                    item(key = "section-${section.segment}") { SegmentHeader(section.segment, Appointments.count(section.appointments), Modifier.animateItem()) }
                    section.appointments.forEach { appointment ->
                        item(key = appointment.id) {
                            AppointmentCard(
                                model, appointment, Modifier.animateItem().padding(bottom = 10.dp),
                                onCancel = { onCancel(appointment) }, onNoShow = { onNoShow(appointment) },
                            ) { model.nav.push(Route.AppointmentDetail(appointment)) }
                        }
                        if (appointment.id == lineAfterId) item(key = "now") { NowLine(BusinessTime.time(now, zone), Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

/** «Lunes, 9 de noviembre» with its Hoy/Mañana/Ayer chip, then the count, the money expected and what is left to charge. */
@Composable
private fun DayHeader(model: AppModel, date: LocalDate, today: LocalDate, visible: List<Appointment>?) {
    val ext = LocalExtended.current
    val locale = AppLocale.locale
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                BusinessTime.longDay(date, locale, today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when (BusinessTime.relative(date, today)) {
                BusinessTime.Relative.TODAY -> Chip(Copy.common.today, ext.brand)
                BusinessTime.Relative.TOMORROW -> Chip(Copy.common.tomorrow, ext.brand)
                BusinessTime.Relative.YESTERDAY -> Chip(Copy.common.yesterday, ext.brand)
                null -> Unit
            }
        }
        if (visible != null) {
            val summary = Appointments.daySummary(visible, model.agenda.now)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val parts = buildList {
                    add(Copy.agenda.summary(summary.count))
                    if (model.isAdmin && summary.expected > 0) add(Copy.agenda.expected(Money.format(summary.expected, model.currency)))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.tabular, color = ext.muted)
                if (model.isAdmin && summary.toCharge > 0) Chip(Copy.agenda.toCharge(summary.toCharge), ext.warning, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** «POR LA MAÑANA · 2»: the part of the day, counted like the day's summary. */
@Composable
private fun SegmentHeader(segment: Appointments.Segment, count: Int, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (segment) {
        Appointments.Segment.MORNING -> Icons.Outlined.WbTwilight
        Appointments.Segment.AFTERNOON -> Icons.Outlined.WbSunny
        Appointments.Segment.EVENING -> Icons.Outlined.Bedtime
    }
    val name = Copy.agenda.segment(segment)
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            (if (count > 0) "$name · $count" else name).uppercase(AppLocale.locale), style = MaterialTheme.typography.labelMedium,
            color = LocalExtended.current.muted, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** The red line at the current time, with the time in a pill. */
@Composable
private fun NowLine(time: String, modifier: Modifier = Modifier) {
    // The same strong red in both themes, so the white time on it always reads.
    val color = Color(0xFFDC2626)
    Row(
        modifier.fillMaxWidth().padding(bottom = 10.dp).semantics(mergeDescendants = true) { contentDescription = "${Copy.agenda.now}, $time" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time, style = MaterialTheme.typography.labelMedium.tabular, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(Modifier.weight(1f).height(1.5.dp).background(color))
    }
}

/**
 * One appointment in the day: the time, the professional's colour, who, what,
 * the status and the money. A long press offers what the detail offers.
 */
@Composable
private fun AppointmentCard(
    model: AppModel,
    appointment: Appointment,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onNoShow: () -> Unit,
    onClick: () -> Unit,
) {
    val ext = LocalExtended.current
    val zone = model.zone
    val now = model.agenda.now
    val start = BusinessTime.parse(appointment.startsAt)
    val end = BusinessTime.parse(appointment.endsAt)
    val status = Appointments.status(appointment, now)
    val dimmed = status == Appointments.Status.CANCELLED || status == Appointments.Status.NO_SHOW
    val ongoing = status == Appointments.Status.NOW
    val personColor = if (dimmed) ext.subtle else ext.person(model.catalog.personIndex(appointment.resourceId))
    val name = Names.full(appointment.customer.firstName, appointment.customer.lastName)
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.medium, color = ext.card,
            border = if (ongoing) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else BorderStroke(1.dp, ext.cardBorder),
            shadowElevation = if (ext.dark) 0.dp else 0.5.dp,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }),
        ) {
            Row(Modifier.height(IntrinsicSize.Min).alpha(if (dimmed) 0.62f else 1f).padding(14.dp)) {
                Column(Modifier.width(48.dp)) {
                    Text(start?.let { BusinessTime.time(it, zone) } ?: "", style = MaterialTheme.typography.titleSmall.tabular, fontWeight = FontWeight.Bold)
                    Text(end?.let { BusinessTime.time(it, zone) } ?: "", style = MaterialTheme.typography.bodySmall.tabular, color = ext.subtle, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.padding(end = 12.dp).width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(personColor))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (status == Appointments.Status.CANCELLED) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusChip(status)
                    }
                    val minutes = if (start != null && end != null) BusinessTime.duration(BusinessTime.minutes(start, end)) else ""
                    Text(
                        listOf(appointment.serviceName, minutes).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (appointment.resourceName != null) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(personColor))
                            Spacer(Modifier.width(6.dp))
                        }
                        val whoAndWhere = listOfNotNull(
                            appointment.resourceName,
                            appointment.locationName.takeIf { model.catalog.activeLocations.size > 1 },
                        ).joinToString(" · ")
                        Text(whoAndWhere, style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MoneyBadge(model, appointment)
                    }
                }
            }
        }
        CardMenu(model, appointment, menu, { menu = false }, onCancel, onNoShow)
    }
}

/** The status chip of an appointment; «Ahora» carries a dot. */
@Composable
fun StatusChip(status: Appointments.Status, modifier: Modifier = Modifier) {
    Chip(
        Copy.agenda.status(status), LocalExtended.current.status(status), modifier,
        icon = if (status == Appointments.Status.NOW) Icons.Filled.Circle else null,
    )
}

/** The money at the bottom right of a card: the price before it starts, then what is left to do about it. */
@Composable
private fun MoneyBadge(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val currency = model.currency
    when (val badge = Appointments.badge(appointment, model.isAdmin, model.agenda.now)) {
        is Appointments.Badge.Price -> Text(Money.format(badge.amount, currency), style = MaterialTheme.typography.bodySmall.tabular, fontWeight = FontWeight.SemiBold, color = ext.muted)
        Appointments.Badge.NotCharged -> Chip(Copy.agenda.unpaid, ext.warning)
        is Appointments.Badge.Deposited -> Chip(Copy.agenda.deposited(Money.format(badge.amount, currency)), ext.neutral)
        is Appointments.Badge.Left -> Chip(Copy.agenda.left(Money.format(badge.amount, currency)), ext.warning)
        Appointments.Badge.ToClose -> Chip(Copy.agenda.toClose, ext.warning)
        Appointments.Badge.Paid -> Chip(Copy.agenda.paid, ext.info, icon = Icons.Filled.Check)
        Appointments.Badge.None -> Unit
    }
}

/**
 * The long-press menu, in the detail's order: charge, close or view the sale,
 * edit; call and WhatsApp; mark as no-show and cancel (both confirmed first).
 */
@Composable
private fun CardMenu(
    model: AppModel,
    appointment: Appointment,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onNoShow: () -> Unit,
) {
    val context = LocalContext.current
    val ext = LocalExtended.current
    val actions = Appointments.actions(appointment, model.isAdmin, model.agenda.now)
    val phone = appointment.customer.phone
    @Composable
    fun entry(text: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurface, run: () -> Unit) {
        DropdownMenuItem(
            text = { Text(text, color = tint) },
            leadingIcon = { Icon(icon, null, tint = tint) },
            onClick = { onDismiss(); run() },
        )
    }
    DropdownMenu(expanded, onDismiss) {
        if (Appointments.Action.CHARGE in actions) {
            val label = Appointments.chargeAmount(appointment)?.let { Copy.agenda.chargeAmount(Money.format(it, model.currency)) } ?: Copy.agenda.charge
            entry(label, Icons.Outlined.PointOfSale) { model.nav.push(Route.Checkout(appointment)) }
        }
        if (Appointments.Action.CLOSE_SALE in actions) entry(Copy.agenda.closeSale, Icons.Outlined.PointOfSale) { model.nav.push(Route.Checkout(appointment)) }
        if (Appointments.Action.VIEW_SALE in actions) entry(Copy.agenda.viewSale, Icons.Outlined.Receipt) { appointment.sale?.let { model.nav.push(Route.SaleDetail(it.id)) } }
        if (Appointments.Action.RESCHEDULE in actions) entry(Copy.agenda.reschedule, Icons.Outlined.EditCalendar) { model.nav.push(Route.Reschedule(appointment)) }
        if (phone != null) {
            entry(Copy.common.call, Icons.Outlined.Phone) { context.dial(Phones.dialUri(phone)) }
            entry(Copy.common.whatsapp, Icons.AutoMirrored.Outlined.Chat) { context.openUrl(Phones.whatsappUrl(phone)) }
        }
        if (Appointments.Action.NO_SHOW in actions) entry(Copy.agenda.markNoShow, Icons.Outlined.PersonOff, ext.orange.content, onNoShow)
        if (Appointments.Action.CANCEL in actions) entry(Copy.agenda.cancelAppointment, Icons.Outlined.EventBusy, MaterialTheme.colorScheme.error, onCancel)
    }
}

@Composable
private fun AgendaSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.width(120.dp).height(14.dp))
        repeat(4) {
            AppCard {
                Row {
                    Column(Modifier.width(48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(Modifier.width(40.dp).height(14.dp))
                        Skeleton(Modifier.width(32.dp).height(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Skeleton(Modifier.fillMaxWidth(0.55f).height(16.dp))
                        Spacer(Modifier.height(8.dp))
                        Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Skeleton(Modifier.size(8.dp), CircleShape)
                            Spacer(Modifier.width(6.dp))
                            Skeleton(Modifier.width(60.dp).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}
