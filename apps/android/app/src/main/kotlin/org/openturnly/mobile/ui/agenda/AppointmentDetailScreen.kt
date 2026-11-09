@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.openturnly.mobile.ui.agenda

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.api.objWithout
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
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.NavRow
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.QuickAction
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.Skeleton
import org.openturnly.mobile.ui.copyText
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.email
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/**
 * One appointment: who, what, when and how it's paid, with exactly the actions
 * the web offers for its status and the person's role.
 */
@Composable
fun AppointmentDetailScreen(model: AppModel, initial: Appointment) {
    // The agenda reloads after every action; this screen follows it.
    val appointment = model.agenda.find(initial.id) ?: initial
    val ext = LocalExtended.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val zone = model.zone
    val locale = AppLocale.locale
    val currency = model.currency
    val start = BusinessTime.parse(appointment.startsAt)
    val end = BusinessTime.parse(appointment.endsAt)
    val day = start?.let { BusinessTime.localDate(it, zone) }
    val status = Appointments.status(appointment, model.agenda.now)
    val actions = Appointments.actions(appointment, model.isAdmin, model.agenda.now)
    val customerName = Names.full(appointment.customer.firstName, appointment.customer.lastName)

    var confirmCancel by remember { mutableStateOf(false) }
    var confirmNoShow by remember { mutableStateOf(false) }
    var depositSheet by remember { mutableStateOf(false) }
    val action = rememberAction { model.failure(it) }

    fun done(message: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        model.toast(message)
        model.agenda.reloadFor(day)
    }

    DetailScaffold(
        title = Copy.agenda.detailTitle,
        onBack = { model.nav.pop() },
        bottomBar = {
            val primary = actions.firstOrNull { it in setOf(Appointments.Action.CHARGE, Appointments.Action.CLOSE_SALE, Appointments.Action.VIEW_SALE, Appointments.Action.UNDO_NO_SHOW) }
            val reschedule = Appointments.Action.RESCHEDULE in actions
            if (primary != null || reschedule) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (reschedule) {
                            OutlinedButton(
                                onClick = { model.nav.push(Route.Reschedule(appointment)) },
                                modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.small,
                            ) {
                                Icon(Icons.Outlined.EditCalendar, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(Copy.agenda.reschedule)
                            }
                        }
                        when (primary) {
                            Appointments.Action.CHARGE -> PrimaryButton(
                                Copy.agenda.charge + (Appointments.owed(appointment).takeIf { it > 0 }?.let { " " + Money.format(it, currency) } ?: ""),
                                Modifier.weight(1f), icon = Icons.Outlined.PointOfSale,
                            ) { model.nav.push(Route.Checkout(appointment)) }
                            Appointments.Action.CLOSE_SALE -> PrimaryButton(Copy.agenda.closeSale, Modifier.weight(1f), icon = Icons.Outlined.PointOfSale) { model.nav.push(Route.Checkout(appointment)) }
                            Appointments.Action.VIEW_SALE -> PrimaryButton(Copy.agenda.viewSale, Modifier.weight(1f), icon = Icons.Outlined.Receipt) {
                                appointment.sale?.let { model.nav.push(Route.SaleDetail(it.id)) }
                            }
                            Appointments.Action.UNDO_NO_SHOW -> PrimaryButton(Copy.agenda.undoNoShow, Modifier.weight(1f), icon = Icons.Outlined.HowToReg, busy = action.busy) {
                                action.run { model.call { undoNoShow(it, appointment.id) }; done(Copy.agenda.noShowUndone) }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // What and when.
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(Copy.agenda.status(status), ext.status(status))
                    Spacer(Modifier.weight(1f))
                    Text(appointment.code, style = MaterialTheme.typography.labelLarge.tabular, color = ext.muted)
                }
                Text(appointment.serviceName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                if (day != null && end != null) {
                    Text(BusinessTime.longDay(day, locale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Outlined.AccessTime, null, tint = ext.muted, modifier = Modifier.size(16.dp))
                        Text(
                            "${BusinessTime.time(start, zone)} – ${BusinessTime.time(end, zone)} · ${BusinessTime.duration(BusinessTime.minutes(start, end))}",
                            style = MaterialTheme.typography.bodyMedium.tabular, color = ext.muted, modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Hairline(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val professional = model.catalog.professional(appointment.resourceId)
                    Avatar(appointment.resourceName ?: "?", size = 32.dp, color = ext.person(model.catalog.personIndex(appointment.resourceId)), photoUrl = model.api?.absolute(professional?.photoUrl))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(Copy.common.professional, style = MaterialTheme.typography.labelSmall, color = ext.muted)
                        Text(appointment.resourceName ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    if (model.catalog.activeLocations.size > 1 && appointment.locationName != null) {
                        Icon(Icons.Outlined.Storefront, null, tint = ext.muted, modifier = Modifier.size(16.dp))
                        Text(appointment.locationName, style = MaterialTheme.typography.bodyMedium, color = ext.muted, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            if (status == Appointments.Status.PENDING_PAYMENT) {
                AppCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, tint = ext.warning.content, modifier = Modifier.size(20.dp))
                        Text(Copy.agenda.pendingPaymentHint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }

            // Who.
            SectionTitle(Copy.agenda.customer)
            AppCard(padding = PaddingValues(0.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(customerName, size = 48.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(customerName, style = MaterialTheme.typography.titleMedium)
                        appointment.customer.phone?.let { Text(Phones.format(it), style = MaterialTheme.typography.bodyMedium.tabular, color = ext.muted) }
                        appointment.customer.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ext.muted) }
                    }
                }
                val phone = appointment.customer.phone
                val mail = appointment.customer.email
                if (phone != null || mail != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (phone != null) {
                            QuickAction(Icons.Outlined.Phone, Copy.common.call) { context.dial(Phones.dialUri(phone)) }
                            QuickAction(Icons.AutoMirrored.Outlined.Chat, Copy.common.whatsapp, tint = ext.whatsapp) { context.openUrl(Phones.whatsappUrl(phone)) }
                        }
                        if (mail != null) QuickAction(Icons.Outlined.Email, Copy.common.email) { context.email(mail) }
                    }
                }
                if (Appointments.Action.VIEW_CUSTOMER in actions) {
                    Hairline()
                    NavRow(Copy.agenda.viewCustomer, Icons.Outlined.Person) {
                        model.nav.push(Route.CustomerDetail(CustomerSummary(id = appointment.customer.id, shortId = appointment.customer.shortId, firstName = appointment.customer.firstName, lastName = appointment.customer.lastName, email = appointment.customer.email, phone = appointment.customer.phone)))
                    }
                }
            }

            if (model.isAdmin && appointment.customer.id.isNotEmpty()) CustomerNotes(model, appointment.customer.id)

            // Money.
            SectionTitle(Copy.agenda.payment)
            AppCard {
                InfoRow(Copy.common.price, appointment.priceAmount?.let { Money.format(it, currency) } ?: Copy.common.priceOnRequest)
                if (appointment.discountAmount > 0) {
                    InfoRow(Copy.agenda.discountCode + (appointment.giftCode?.let { " · $it" } ?: ""), Money.negative(appointment.discountAmount, currency), valueColor = ext.success.content)
                }
                if (model.isAdmin && appointment.status == "confirmed") {
                    if (appointment.depositAmount > 0) InfoRow(Copy.checkout.deposits, Money.negative(appointment.depositAmount, currency))
                    Hairline(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        when (Appointments.payment(appointment)) {
                            Appointments.Payment.PAID -> {
                                Chip(Copy.agenda.paid, ext.info)
                                Text(Copy.agenda.saleNumber(appointment.sale?.number ?: 0), modifier = Modifier.padding(start = 8.dp).weight(1f), color = ext.muted)
                            }
                            Appointments.Payment.COVERED -> {
                                Chip(Copy.agenda.paid, ext.success)
                                Text(Copy.agenda.depositLeft(Money.format(appointment.depositAmount, currency)), modifier = Modifier.padding(start = 8.dp).weight(1f), color = ext.muted)
                            }
                            Appointments.Payment.PARTIAL -> Text(Copy.agenda.dueLeft(Money.format(Appointments.owed(appointment), currency)), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            else -> {
                                Chip(Copy.agenda.unpaid, ext.warning)
                                Spacer(Modifier.weight(1f))
                                Text(Money.format(Appointments.owed(appointment), currency), style = MaterialTheme.typography.titleMedium.tabular)
                            }
                        }
                    }
                }
            }

            // The rest of what the web lists.
            SectionTitle(Copy.common.details)
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoRow(Copy.agenda.bookingCode, appointment.code, Modifier.weight(1f))
                    IconButton({ context.copyText(appointment.code); model.toast(Copy.common.copied) }) {
                        Icon(Icons.Outlined.ContentCopy, Copy.common.copy, tint = ext.muted, modifier = Modifier.size(18.dp))
                    }
                }
                InfoRow(Copy.agenda.bookedVia, Copy.agenda.source(appointment.source, appointment.sourceLabel))
                BusinessTime.parse(appointment.createdAt)?.let { created ->
                    InfoRow(Copy.agenda.bookedOn, BusinessTime.mediumDate(BusinessTime.localDate(created, zone), locale) + " · " + BusinessTime.time(created, zone))
                }
                appointment.answers.forEach { answer -> InfoRow(answer.label, answer.value) }
            }

            // Secondary actions.
            val secondary = actions.filter { it in setOf(Appointments.Action.DEPOSIT, Appointments.Action.NO_SHOW, Appointments.Action.CANCEL) }
            if (secondary.isNotEmpty()) {
                SectionTitle(Copy.agenda.actions)
                AppCard(padding = PaddingValues(vertical = 4.dp)) {
                    secondary.forEachIndexed { index, item ->
                        if (index > 0) Hairline(Modifier.padding(start = 66.dp))
                        when (item) {
                            Appointments.Action.DEPOSIT -> NavRow(Copy.agenda.newDeposit, Icons.Outlined.Savings) { depositSheet = true }
                            Appointments.Action.NO_SHOW -> NavRow(Copy.agenda.markNoShowLong, Icons.Outlined.PersonOff, iconTint = ext.orange.content) { confirmNoShow = true }
                            Appointments.Action.CANCEL -> NavRow(Copy.agenda.cancelAppointment, Icons.Outlined.EventBusy, iconTint = MaterialTheme.colorScheme.error) { confirmCancel = true }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            Copy.agenda.cancelTitle, Copy.agenda.confirmCancel, Copy.agenda.cancelAppointment,
            onConfirm = {
                confirmCancel = false
                action.run {
                    model.call { cancel(it, appointment.id) }
                    done(Copy.agenda.cancelled)
                    model.nav.pop()
                }
            },
            onDismiss = { confirmCancel = false }, dismiss = Copy.agenda.keep, danger = true,
        )
    }
    if (confirmNoShow) {
        ConfirmDialog(
            Copy.agenda.noShowTitle, Copy.agenda.noShowBody, Copy.agenda.markNoShow,
            onConfirm = {
                confirmNoShow = false
                action.run { model.call { markNoShow(it, appointment.id) }; done(Copy.agenda.noShowMarked) }
            },
            onDismiss = { confirmNoShow = false },
        )
    }
    if (depositSheet) DepositSheet(model, appointment) { depositSheet = false }
}

/** The customer's private notes, editable in place: appointments have no notes of their own. */
@Composable
private fun CustomerNotes(model: AppModel, customerId: String) {
    val ext = LocalExtended.current
    val loader = rememberLoader(customerId) { model.call { customer(it, customerId) } }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val action = rememberAction { model.failure(it) }
    SectionTitle(Copy.agenda.customerNotes) {
        if (loader.value != null && !editing) {
            TextButton({ draft = loader.value?.customer?.notes ?: ""; editing = true }) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.common.edit)
            }
        }
    }
    AppCard {
        when {
            loader.loading -> { Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp)); Spacer(Modifier.height(6.dp)); Skeleton(Modifier.fillMaxWidth(0.4f).height(14.dp)) }
            editing -> {
                Field(draft, { draft = it }, Copy.common.notes, singleLine = false, minLines = 3, supporting = Copy.agenda.customerNotesHint, modifier = Modifier.imePadding())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton({ editing = false }) { Text(Copy.common.cancel) }
                    TextButton(enabled = !action.busy, onClick = {
                        action.run {
                            val saved = model.call { updateCustomer(it, customerId, obj("notes" to draft.trim().ifEmpty { null })) }
                            loader.value = loader.value?.copy(customer = saved)
                            editing = false
                            model.toast(Copy.agenda.notesSaved)
                        }
                    }) { Text(Copy.common.save, fontWeight = FontWeight.SemiBold) }
                }
            }
            else -> {
                val notes = loader.value?.customer?.notes
                Text(
                    notes?.takeIf { it.isNotBlank() } ?: Copy.agenda.noNotes, style = MaterialTheme.typography.bodyMedium,
                    color = if (notes.isNullOrBlank()) ext.muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { draft = notes ?: ""; editing = true },
                )
            }
        }
    }
}

/** "Registrar abono": money the customer leaves in advance for this appointment. */
@Composable
fun DepositSheet(model: AppModel, appointment: Appointment, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = model.currency
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("transfer") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val action = rememberAction { error = it.message }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Copy.checkout.depositTitle, style = MaterialTheme.typography.titleLarge)
            Text(Copy.checkout.depositHint, style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted)
            Field(
                amount, { amount = it; error = null }, Copy.checkout.amount, prefix = Money.symbol(currency) + " ",
                keyboard = if (org.openturnly.mobile.rules.Money.digits(currency) == 0) KeyboardType.Number else KeyboardType.Decimal, error = error,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val methods = listOf("cash", "card", "transfer", "other")
                methods.forEachIndexed { index, value ->
                    SegmentedButton(method == value, { method = value }, SegmentedButtonDefaults.itemShape(index, methods.size), icon = {}) {
                        Text(Copy.sales.method(value), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Field(note, { note = it }, Copy.common.note, supporting = Copy.common.optional)
            PrimaryButton(Copy.checkout.depositSave, busy = action.busy, icon = Icons.Outlined.Payments) {
                val minor = Money.parse(amount, currency)
                if (minor == null || minor < 1) { error = Copy.checkout.amountRequired; return@PrimaryButton }
                action.run {
                    model.call {
                        createDeposit(it, objWithout(
                            "customerId" to appointment.customer.id, "appointmentId" to appointment.id,
                            "locationId" to (appointment.locationId ?: Omit), "amount" to minor, "method" to method,
                            "note" to (note.trim().ifEmpty { null } ?: Omit),
                        ))
                    }
                    model.toast(Copy.checkout.depositCreated)
                    model.agenda.reloadFor(BusinessTime.parse(appointment.startsAt)?.let { BusinessTime.localDate(it, model.zone) })
                    onDismiss()
                }
            }
        }
    }
}
