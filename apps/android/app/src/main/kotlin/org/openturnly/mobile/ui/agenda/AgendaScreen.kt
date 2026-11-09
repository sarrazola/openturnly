@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.openturnly.mobile.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
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
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.BusinessLogo
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.EmptyState
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.Skeleton
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
    val locale = AppLocale.locale
    val today = BusinessTime.today(zone, agenda.now)
    val catalog = model.catalog
    val business = model.business ?: return
    var picking by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    val canFilter = model.isAdmin || catalog.locations.count { it.active } > 1
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val dayAppointments = agenda.appointmentsOn(agenda.date)
    val visible = dayAppointments?.let { Appointments.filter(it, agenda.professionalId, agenda.locationId, agenda.showCancelled) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: the business (tap to switch) and the day.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = model.businesses.size > 1) { model.showBusinessPicker = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BusinessLogo(model, business, 28.dp)
                    Text(
                        business.name, style = MaterialTheme.typography.titleSmall, color = LocalExtended.current.muted,
                        modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (model.businesses.size > 1) Icon(Icons.Filled.KeyboardArrowDown, Copy.nav.switchBusiness, tint = LocalExtended.current.muted, modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(agenda.date != today, enter = fadeIn(), exit = fadeOut()) {
                    TextButton({ agenda.select(today) }) { Text(Copy.common.today) }
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
            }
            Text(
                dayTitle(agenda.date, today, locale), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
            )
            WeekStrip(model, today)
            ActiveFilters(model) { filtering = true }
            PullToRefreshBox(isRefreshing = agenda.refreshing, onRefresh = { agenda.refresh(); model.refreshCatalog() }, modifier = Modifier.fillMaxSize()) {
                when {
                    visible == null && agenda.error != null -> Column(Modifier.fillMaxSize()) { ErrorState(agenda.error ?: Copy.agenda.loadFailed, { agenda.loadCurrent() }) }
                    visible == null -> AgendaSkeleton()
                    visible.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            if (dayAppointments?.isNotEmpty() == true) EmptyState(Icons.Outlined.FilterAltOff, Copy.agenda.emptyFiltered)
                            else if (agenda.date.isBefore(today)) EmptyState(Icons.Outlined.EventAvailable, Copy.agenda.emptyTitle)
                            else EmptyState(
                                Icons.Outlined.EventAvailable, Copy.agenda.emptyTitle, Copy.agenda.emptyBody,
                                action = Copy.booking.create, onAction = { model.nav.push(Route.NewAppointment(date = agenda.date)) },
                            )
                        }
                    }
                    else -> DayList(model, visible, today, listState)
                }
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
}

private fun dayTitle(date: LocalDate, today: LocalDate, locale: java.util.Locale): String = when (date) {
    today -> Copy.common.today + ", " + BusinessTime.mediumDate(date, locale, withYear = false)
    today.plusDays(1) -> Copy.common.tomorrow + ", " + BusinessTime.mediumDate(date, locale, withYear = false)
    today.minusDays(1) -> Copy.common.yesterday + ", " + BusinessTime.mediumDate(date, locale, withYear = false)
    else -> BusinessTime.longDay(date, locale).let { if (date.year != today.year) "$it ${date.year}" else it }
}

/** Seven days at a time; swiping moves a week and keeps the weekday. */
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
    Text(
        BusinessTime.monthYear(agenda.date, locale), style = MaterialTheme.typography.labelLarge, color = LocalExtended.current.muted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 6.dp), beyondViewportPageCount = 1) { page ->
        val week = baseWeek.plusWeeks((page - MIDDLE).toLong())
        val loaded = agenda.weeks[week]
        LaunchedEffect(week) { if (loaded == null) agenda.load(week) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (offset in 0 until 7) {
                val day = week.plusDays(offset.toLong())
                val busy = loaded?.let { list -> Appointments.onDay(list, day, model.zone).count { Appointments.isActive(it) } } ?: 0
                DayCell(day, locale, selected = day == agenda.date, isToday = day == today, busy = busy, modifier = Modifier.weight(1f)) { agenda.select(day) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, locale: java.util.Locale, selected: Boolean, isToday: Boolean, busy: Int, modifier: Modifier, onClick: () -> Unit) {
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
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            BusinessTime.weekdayShort(day, locale).take(3), style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimary.copy(alpha = 0.85f) else LocalExtended.current.muted,
        )
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.tabular, color = content, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(minOf(busy, 3)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) scheme.onPrimary else scheme.primary.copy(alpha = 0.7f)))
            }
        }
    }
}

/** What the agenda is narrowed to: tap to change it, the cross clears it. */
@Composable
private fun ActiveFilters(model: AppModel, onOpen: () -> Unit) {
    val agenda = model.agenda
    val catalog = model.catalog
    val person = catalog.professionals.firstOrNull { it.id == agenda.professionalId }
    val location = catalog.location(agenda.locationId)
    if (person == null && location == null) {
        Spacer(Modifier.height(8.dp))
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).height(36.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().clickable(onClick = onOpen).padding(start = if (person != null) 6.dp else 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (person != null) {
                Avatar(person.name, size = 24.dp, color = LocalExtended.current.person(catalog.personIndex(person.id)), photoUrl = model.api?.absolute(person.photoUrl))
            } else {
                Icon(Icons.Outlined.FilterList, null, tint = primary, modifier = Modifier.size(18.dp))
            }
            Text(
                listOfNotNull(person?.name, location?.name).joinToString(" · "), color = primary,
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(
            Modifier.size(36.dp).clickable { agenda.professionalId = null; agenda.locationId = null },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, Copy.agenda.clearFilters, tint = primary, modifier = Modifier.size(16.dp)) }
    }
}

/**
 * Whose agenda to see (everyone or one person), at which location, and whether
 * cancelled appointments show. Picking a person closes the sheet; a long team
 * gets a search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaFilterSheet(model: AppModel, onDismiss: () -> Unit) {
    val agenda = model.agenda
    val catalog = model.catalog
    val locations = catalog.locations.filter { it.active }
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

@Composable
private fun DayList(model: AppModel, appointments: List<Appointment>, today: LocalDate, listState: androidx.compose.foundation.lazy.LazyListState) {
    val zone = model.zone
    val now = model.agenda.now
    val sections = Appointments.group(appointments, zone)
    val isToday = model.agenda.date == today
    val firstUpcoming = if (isToday) appointments.sortedBy { it.startsAt }.firstOrNull { (BusinessTime.parse(it.startsAt) ?: Instant.EPOCH).isAfter(now) }?.id else null
    val allPast = isToday && firstUpcoming == null
    val active = appointments.filter { Appointments.isActive(it) }
    val toCharge = if (model.isAdmin) appointments.count { Appointments.payment(it) in setOf(Appointments.Payment.UNPAID, Appointments.Payment.PARTIAL, Appointments.Payment.COVERED) && Appointments.started(it, now) } else 0

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)) {
        item(key = "summary") {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Copy.agenda.summary(active.size), style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted)
                if (toCharge > 0) Chip(Copy.agenda.toCharge(toCharge), LocalExtended.current.warning)
                Spacer(Modifier.weight(1f))
                if (model.isAdmin) {
                    val value = active.sumOf { it.priceAmount ?: 0L }
                    if (value > 0) Text(Money.format(value, model.currency), style = MaterialTheme.typography.bodyMedium.tabular, color = LocalExtended.current.muted)
                }
            }
        }
        sections.forEach { section ->
            item(key = "section-${section.segment}") {
                Text(
                    Copy.agenda.segment(section.segment), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).animateItem(),
                )
            }
            section.appointments.forEach { appointment ->
                if (appointment.id == firstUpcoming) item(key = "now") { NowMarker(now, zone, Modifier.animateItem()) }
                item(key = appointment.id) {
                    AppointmentCard(model, appointment, Modifier.animateItem().padding(bottom = 10.dp)) {
                        model.nav.push(Route.AppointmentDetail(appointment))
                    }
                }
            }
        }
        if (allPast && appointments.isNotEmpty()) item(key = "now") { NowMarker(now, zone) }
    }
}

@Composable
private fun NowMarker(now: Instant, zone: java.time.ZoneId, modifier: Modifier = Modifier) {
    // The same strong red in both themes, so the white time on it always reads.
    val color = Color(0xFFDC2626)
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            BusinessTime.time(now, zone), style = MaterialTheme.typography.labelMedium.tabular, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Box(Modifier.weight(1f).height(1.5.dp).background(color))
    }
}

/** One appointment in the day: time on the left, the card with who, what and the state. */
@Composable
fun AppointmentCard(model: AppModel, appointment: Appointment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ext = LocalExtended.current
    val zone = model.zone
    val start = BusinessTime.parse(appointment.startsAt)
    val end = BusinessTime.parse(appointment.endsAt)
    val status = Appointments.status(appointment, model.agenda.now)
    val faded = status == Appointments.Status.CANCELLED || status == Appointments.Status.NO_SHOW
    val alpha by animateFloatAsState(if (faded) 0.62f else 1f, label = "fade")
    val personColor = ext.person(model.catalog.personIndex(appointment.resourceId))
    val name = Names.full(appointment.customer.firstName, appointment.customer.lastName)

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(alpha)) {
        Column(Modifier.width(50.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
            Text(start?.let { BusinessTime.time(it, zone) } ?: "", style = MaterialTheme.typography.titleSmall.tabular, fontWeight = FontWeight.Bold)
            Text(end?.let { BusinessTime.time(it, zone) } ?: "", style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted)
        }
        Spacer(Modifier.width(12.dp))
        AppCard(Modifier.weight(1f), onClick = onClick, padding = PaddingValues(0.dp)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(personColor))
                Column(Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (status == Appointments.Status.NO_SHOW) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Chip(Copy.agenda.status(status), ext.status(status))
                    }
                    val minutes = if (start != null && end != null) BusinessTime.duration(BusinessTime.minutes(start, end)) else ""
                    Text(
                        listOf(appointment.serviceName, minutes).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = ext.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val professional = model.catalog.professional(appointment.resourceId)
                        if (appointment.resourceName != null) {
                            Avatar(appointment.resourceName, size = 22.dp, color = personColor, photoUrl = model.api?.absolute(professional?.photoUrl))
                            Text(appointment.resourceName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp).weight(1f), maxLines = 1)
                        } else Spacer(Modifier.weight(1f))
                        if (model.catalog.activeLocations.size > 1 && appointment.locationName != null) {
                            Text(appointment.locationName, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(end = 8.dp), maxLines = 1)
                        }
                        PaymentBadge(model, appointment)
                    }
                }
            }
        }
    }
}

/** Paid, deposit, owed or the price, as far as this person may see money. */
@Composable
fun PaymentBadge(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val currency = model.currency
    if (!model.isAdmin) {
        appointment.priceAmount?.let { Text(Money.format(it, currency), style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted) }
        return
    }
    when (Appointments.payment(appointment)) {
        Appointments.Payment.PAID -> Chip(Copy.agenda.paid, ext.info, icon = Icons.Filled.Check)
        Appointments.Payment.COVERED, Appointments.Payment.PARTIAL -> Chip(Copy.agenda.deposited(Money.format(appointment.depositAmount, currency)), ext.neutral)
        Appointments.Payment.UNPAID -> if (Appointments.started(appointment, model.agenda.now)) Chip(Copy.agenda.unpaid, ext.warning)
            else appointment.priceAmount?.let { Text(Money.format(it, currency), style = MaterialTheme.typography.bodySmall.tabular, color = ext.muted) }
        Appointments.Payment.NONE -> Unit
    }
}

@Composable
private fun AgendaSkeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.width(120.dp).height(14.dp))
        repeat(4) {
            Row {
                Column(Modifier.width(50.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Skeleton(Modifier.width(40.dp).height(14.dp))
                    Skeleton(Modifier.width(32.dp).height(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                AppCard(Modifier.weight(1f)) {
                    Skeleton(Modifier.fillMaxWidth(0.55f).height(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Skeleton(Modifier.size(22.dp), CircleShape)
                        Spacer(Modifier.width(6.dp))
                        Skeleton(Modifier.width(60.dp).height(10.dp))
                    }
                }
            }
        }
    }
}
