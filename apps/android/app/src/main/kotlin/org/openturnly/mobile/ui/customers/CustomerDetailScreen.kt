@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Customer
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.CustomerVisit
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Appointments
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.QuickAction
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.Skeleton
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.Tab
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.email
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

fun Customer.summary(visits: Int = 0, lastVisit: String? = null, nextVisit: String? = null) = CustomerSummary(
    id = id, shortId = shortId, firstName = firstName, lastName = lastName, email = email, phone = phone,
    birthDate = birthDate, document = document, gender = gender, notes = notes, createdAt = createdAt,
    visits = visits, lastVisit = lastVisit, nextVisit = nextVisit,
)

/** A customer: how to reach them, their visits, their notes, and their appointments. */
@Composable
fun CustomerDetailScreen(model: AppModel, summary: CustomerSummary) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val locale = AppLocale.locale
    val zone = model.zone
    val loader = rememberLoader(summary.id) { model.call { customer(it, summary.id) } }
    val customer = loader.value?.customer
    val visits = loader.value?.appointments ?: emptyList()
    val now = Instant.now()
    val upcoming = visits.filter { (BusinessTime.parse(it.startsAt) ?: now).isAfter(now) && it.status != "cancelled" }.sortedBy { it.startsAt }
    val past = visits.filter { it !in upcoming }
    val confirmed = visits.filter { it.status == "confirmed" }
    val lastVisit = confirmed.filter { (BusinessTime.parse(it.startsAt) ?: now) <= now }.maxByOrNull { it.startsAt }
    val nextVisit = confirmed.filter { (BusinessTime.parse(it.startsAt) ?: now) > now }.minByOrNull { it.startsAt }
    val name = Names.full(customer?.firstName ?: summary.firstName, customer?.lastName ?: summary.lastName)
    val phone = customer?.phone ?: summary.phone
    val mail = customer?.email ?: summary.email

    DetailScaffold(
        title = name,
        onBack = { model.nav.pop() },
        actions = {
            if (customer != null) IconButton({ model.nav.push(Route.CustomerForm(customer.summary(confirmed.size))) }) { Icon(Icons.Outlined.Edit, Copy.common.edit) }
        },
    ) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, size = 80.dp)
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                    BusinessTime.parse(customer?.createdAt ?: summary.createdAt)?.let {
                        Text("${Copy.customers.since} ${BusinessTime.mediumDate(BusinessTime.localDate(it, zone), locale)}", style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (phone != null) {
                            QuickAction(Icons.Outlined.Phone, Copy.common.call) { context.dial(Phones.dialUri(phone)) }
                            QuickAction(Icons.AutoMirrored.Outlined.Chat, Copy.common.whatsapp, tint = ext.whatsapp) { context.openUrl(Phones.whatsappUrl(phone)) }
                        }
                        if (mail != null) QuickAction(Icons.Outlined.Email, Copy.common.email) { context.email(mail) }
                        QuickAction(Icons.Outlined.EventAvailable, Copy.customers.book) {
                            model.nav.push(Route.NewAppointment(customer = customer?.summary() ?: summary))
                        }
                    }
                }

                if (loader.loading) { SkeletonCards(3, Modifier.padding(horizontal = 0.dp)); return@Column }
                if (customer == null) { ErrorState(loader.error ?: Copy.common.loadFailed, { loader.load() }); return@Column }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Copy.customers.visitsLabel, confirmed.size.toString(), Modifier.weight(1f))
                    StatTile(Copy.customers.lastVisit, lastVisit?.let { shortDate(model, it.startsAt) } ?: "—", Modifier.weight(1f))
                    StatTile(Copy.customers.nextVisit, nextVisit?.let { shortDate(model, it.startsAt) } ?: "—", Modifier.weight(1f), highlight = nextVisit != null)
                }

                SectionTitle(Copy.common.details)
                AppCard {
                    InfoRow(Copy.common.phone, phone?.let { Phones.format(it) } ?: "—")
                    InfoRow(Copy.common.email, mail ?: "—")
                    customer.birthDate?.let { date -> BusinessTime.parseDate(date)?.let { InfoRow(Copy.customers.birthDate, BusinessTime.mediumDate(it, locale)) } }
                    customer.document?.takeIf { it.isNotBlank() }?.let { InfoRow(Copy.customers.document, it) }
                    customer.gender?.takeIf { it.isNotBlank() }?.let { InfoRow(Copy.customers.gender, Copy.customers.genderLabel(it)) }
                }

                Notes(model, customer) { saved -> loader.value = loader.value?.copy(customer = saved) }

                if (upcoming.isNotEmpty()) {
                    SectionTitle(Copy.customers.upcoming)
                    VisitList(model, upcoming)
                }
                SectionTitle(Copy.customers.history)
                if (past.isEmpty()) Text(Copy.customers.noHistory, color = ext.muted, style = MaterialTheme.typography.bodyMedium)
                else VisitList(model, past.sortedByDescending { it.startsAt })
            }
        }
    }
}

private fun shortDate(model: AppModel, instant: String): String =
    BusinessTime.parse(instant)?.let { BusinessTime.mediumDate(BusinessTime.localDate(it, model.zone), AppLocale.locale, withYear = false) } ?: "—"

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    AppCard(modifier, padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabular, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalExtended.current.muted, maxLines = 1)
    }
}

@Composable
private fun Notes(model: AppModel, customer: Customer, onSaved: (Customer) -> Unit) {
    val ext = LocalExtended.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(customer.notes ?: "") }
    val action = rememberAction { model.failure(it) }
    SectionTitle(Copy.common.notes) {
        if (!editing) TextButton({ draft = customer.notes ?: ""; editing = true }) {
            Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.common.edit)
        }
    }
    AppCard {
        if (editing) {
            Field(draft, { draft = it }, Copy.common.notes, singleLine = false, minLines = 3, supporting = Copy.agenda.customerNotesHint, modifier = Modifier.imePadding())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton({ editing = false }) { Text(Copy.common.cancel) }
                TextButton(enabled = !action.busy, onClick = {
                    action.run {
                        val saved = model.call { updateCustomer(it, customer.id, obj("notes" to draft.trim().ifEmpty { null })) }
                        onSaved(saved)
                        editing = false
                        model.toast(Copy.agenda.notesSaved)
                    }
                }) { Text(Copy.common.save, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Text(
                customer.notes?.takeIf { it.isNotBlank() } ?: Copy.agenda.noNotes,
                color = if (customer.notes.isNullOrBlank()) ext.muted else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { draft = customer.notes ?: ""; editing = true },
            )
        }
    }
}

/** A customer's appointments; tapping one opens that day in the agenda. */
@Composable
private fun VisitList(model: AppModel, visits: List<CustomerVisit>) {
    val ext = LocalExtended.current
    val zone = model.zone
    val locale = AppLocale.locale
    AppCard(padding = PaddingValues(vertical = 4.dp)) {
        visits.forEachIndexed { index, visit ->
            if (index > 0) Hairline(Modifier.padding(start = 16.dp))
            val start = BusinessTime.parse(visit.startsAt)
            val day = start?.let { BusinessTime.localDate(it, zone) }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = day != null) {
                    day?.let {
                        model.agenda.select(it)
                        model.nav.popToRoot(Tab.AGENDA)
                        model.nav.select(Tab.AGENDA)
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (day?.let { BusinessTime.mediumDate(it, locale) } ?: "") + (start?.let { " · " + BusinessTime.time(it, zone) } ?: ""),
                        style = MaterialTheme.typography.bodyLarge.tabular, fontWeight = FontWeight.Medium,
                    )
                    Text(listOfNotNull(visit.serviceName, visit.resourceName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val started = start?.let { !it.isAfter(Instant.now()) } ?: false
                    val status = when (visit.status) {
                        "cancelled" -> Appointments.Status.CANCELLED
                        "no_show" -> Appointments.Status.NO_SHOW
                        "pending_payment" -> Appointments.Status.PENDING_PAYMENT
                        else -> if (started) Appointments.Status.CONFIRMED else Appointments.Status.UPCOMING
                    }
                    Chip(Copy.agenda.status(status), ext.status(status))
                    visit.priceAmount?.let { Text(Money.format(it, model.currency), style = MaterialTheme.typography.labelMedium.tabular, color = ext.muted, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}
