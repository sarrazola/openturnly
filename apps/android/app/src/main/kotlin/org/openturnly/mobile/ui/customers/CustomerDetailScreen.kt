@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.Customer
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.CustomerVisit
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.agenda.StatusChip
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.ContactActions
import org.openturnly.mobile.ui.CustomerNotesSection
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.email
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

fun Customer.summary(visits: Int = 0, lastVisit: String? = null, nextVisit: String? = null) = CustomerSummary(
    id = id, shortId = shortId, firstName = firstName, lastName = lastName, email = email, phone = phone,
    birthDate = birthDate, document = document, gender = gender, notes = notes, createdAt = createdAt,
    visits = visits, lastVisit = lastVisit, nextVisit = nextVisit,
)

/**
 * A customer's appointments, read the same way on every platform: what is
 * coming (soonest first), what is past (newest first), and the three numbers
 * at the top.
 */
internal object CustomerVisits {
    data class Summary(
        /** Not cancelled and not started yet, soonest first. */
        val upcoming: List<CustomerVisit>,
        /** Everything else, newest first. */
        val history: List<CustomerVisit>,
        /** «Visitas»: confirmed appointments that have started. */
        val visits: Int,
        /** «Última visita»: the latest of those. */
        val last: CustomerVisit?,
        /** «Próxima cita»: the soonest confirmed appointment still to start. */
        val next: CustomerVisit?,
    )

    fun of(appointments: List<CustomerVisit>, now: Instant): Summary {
        val starts = appointments.associate { it.id to BusinessTime.parse(it.startsAt) }
        fun start(visit: CustomerVisit): Instant = starts[visit.id] ?: Instant.EPOCH
        fun started(visit: CustomerVisit) = starts[visit.id]?.let { !it.isAfter(now) } ?: true
        val upcoming = appointments.filter { !started(it) && it.status != "cancelled" }.sortedBy(::start)
        val ahead = upcoming.map { it.id }.toSet()
        val history = appointments.filter { it.id !in ahead }.sortedByDescending(::start)
        val done = appointments.filter { it.status == "confirmed" && starts[it.id] != null && started(it) }
        return Summary(
            upcoming = upcoming,
            history = history,
            visits = done.size,
            last = done.maxByOrNull(::start),
            next = upcoming.filter { it.status == "confirmed" }.minByOrNull(::start),
        )
    }
}

/** A customer: how to reach them, their visits, their notes, and their appointments. */
@Composable
fun CustomerDetailScreen(model: AppModel, summary: CustomerSummary) {
    val ext = LocalExtended.current
    val locale = AppLocale.locale
    val zone = model.zone
    val loader = rememberLoader(summary.id) { model.call { customer(it, summary.id) } }
    val customer = loader.value?.customer
    val now = maxOf(model.agenda.now, Instant.now())
    val appointments = loader.value?.appointments
    val visits = remember(appointments, now) { CustomerVisits.of(appointments ?: emptyList(), now) }
    val name = Names.full(customer?.firstName ?: summary.firstName, customer?.lastName ?: summary.lastName)
    val phone = (if (customer != null) customer.phone else summary.phone)?.takeIf { it.isNotBlank() }
    val mail = (if (customer != null) customer.email else summary.email)?.takeIf { it.isNotBlank() }
    val today = model.today
    // The list's own numbers travel with the customer, so editing doesn't change them there.
    fun handOff(found: Customer) = found.summary(summary.visits, summary.lastVisit, summary.nextVisit)

    // A row being looked up: one at a time, in either section.
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf<String?>(null) }
    fun open(visit: CustomerVisit) {
        if (opening != null) return
        val businessId = model.business?.id ?: return
        opening = visit.id
        scope.launch {
            try {
                val found = model.agenda.locate(visit.id, visit.startsAt)
                if (model.business?.id != businessId) return@launch
                if (found != null) model.nav.push(Route.AppointmentDetail(found)) else model.toast(Copy.notifications.notFound)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                if (!failure.isUnauthorized) model.failure(failure)
            } finally {
                opening = null
            }
        }
    }

    DetailScaffold(
        title = name,
        onBack = { model.nav.pop() },
        actions = {
            if (customer != null) {
                IconButton({ model.nav.push(Route.CustomerForm(handOff(customer))) }) {
                    Icon(Icons.Outlined.Edit, Copy.common.edit)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, size = 80.dp)
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                    BusinessTime.parse(customer?.createdAt ?: summary.createdAt)?.let {
                        Text(Copy.customers.since(BusinessTime.mediumDate(BusinessTime.localDate(it, zone), locale)), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                    }
                    if (phone != null || mail != null) ContactActions(phone, mail, Modifier.padding(top = 16.dp))
                }

                // Booking needs only who it is for, so it is there before the rest loads.
                PrimaryButton(Copy.customers.newAppointment, icon = Icons.Outlined.EventAvailable) {
                    model.nav.push(Route.NewAppointment(customer = customer?.let(::handOff) ?: summary))
                }

                if (loader.loading) { SkeletonCards(3, Modifier.padding(horizontal = 0.dp)); return@Column }
                if (customer == null) { ErrorState(loader.error ?: Copy.common.loadFailed, { loader.load() }); return@Column }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Copy.customers.visitsLabel, visits.visits.toString(), Modifier.weight(1f))
                    StatTile(Copy.customers.lastVisit, visits.last?.let { tileDate(model, it.startsAt, today) } ?: "—", Modifier.weight(1f))
                    StatTile(Copy.customers.nextVisit, visits.next?.let { tileDate(model, it.startsAt, today) } ?: "—", Modifier.weight(1f), highlight = visits.next != null)
                }

                Details(customer)

                CustomerNotesSection(model, Copy.common.notes, customer.id, customer.notes) { saved ->
                    loader.value = loader.value?.copy(customer = saved)
                }

                if (visits.upcoming.isNotEmpty()) VisitSection(model, Copy.customers.upcoming, visits.upcoming, now, opening, ::open)
                when {
                    visits.history.isNotEmpty() -> VisitSection(model, Copy.customers.history, visits.history, now, opening, ::open)
                    visits.upcoming.isEmpty() -> AppCard {
                        Text(Copy.customers.noHistory, color = ext.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** "9 nov" this year, "9 nov 2025" otherwise. */
private fun tileDate(model: AppModel, instant: String, today: LocalDate): String {
    val day = BusinessTime.parse(instant)?.let { BusinessTime.localDate(it, model.zone) } ?: return "—"
    return BusinessTime.mediumDate(day, AppLocale.locale, withYear = day.year != today.year)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    AppCard(modifier, padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabular, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalExtended.current.muted, maxLines = 1)
    }
}

/** «Datos»: only the rows that have something in them. */
@Composable
private fun Details(customer: Customer) {
    val locale = AppLocale.locale
    val rows = buildList {
        customer.phone?.takeIf { it.isNotBlank() }?.let { add(Copy.common.phone to Phones.format(it)) }
        customer.email?.takeIf { it.isNotBlank() }?.let { add(Copy.common.email to it) }
        BusinessTime.parseDate(customer.birthDate)?.let { add(Copy.customers.birthDate to BusinessTime.mediumDate(it, locale)) }
        customer.document?.takeIf { it.isNotBlank() }?.let { add(Copy.customers.document to it) }
        customer.gender?.takeIf { it.isNotBlank() }?.let { add(Copy.customers.gender to Copy.customers.genderLabel(it)) }
    }
    if (rows.isEmpty()) return
    SectionTitle(Copy.customers.details)
    AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Hairline()
            InfoRow(label, value)
        }
    }
}

/** «Próximas» or «Historial» with how many; tapping a row opens that appointment ([opening] is the one being looked up). */
@Composable
private fun VisitSection(model: AppModel, title: String, visits: List<CustomerVisit>, now: Instant, opening: String?, onOpen: (CustomerVisit) -> Unit) {
    val ext = LocalExtended.current
    val zone = model.zone
    val locale = AppLocale.locale
    SectionTitle(title) { Text(visits.size.toString(), style = MaterialTheme.typography.labelMedium, color = ext.subtle) }
    AppCard(padding = PaddingValues(vertical = 4.dp)) {
        visits.forEachIndexed { index, visit ->
            if (index > 0) Hairline(Modifier.padding(start = 16.dp))
            val start = BusinessTime.parse(visit.startsAt)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = opening == null) { onOpen(visit) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(start?.let { BusinessTime.mediumDate(BusinessTime.localDate(it, zone), locale) }, start?.let { BusinessTime.time(it, zone) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge.tabular, fontWeight = FontWeight.Medium,
                    )
                    Text(
                        listOfNotNull(visit.serviceName.takeIf { it.isNotBlank() }, visit.resourceName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = ext.muted,
                    )
                }
                if (opening == visit.id) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        val status = Appointments.status(visit.status, visit.startsAt, visit.endsAt, now)
                        StatusChip(status)
                        visit.priceAmount?.let {
                            Text(Money.format(it, model.currency), style = MaterialTheme.typography.labelMedium.tabular, color = ext.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
