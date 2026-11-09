@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.Appointment
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Omit
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
import org.openturnly.mobile.ui.CustomerNotesSection
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.ContactActions
import org.openturnly.mobile.ui.IconBadge
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.NavRow
import org.openturnly.mobile.ui.NoticeBanner
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.copyText
import org.openturnly.mobile.ui.dial
import org.openturnly.mobile.ui.email
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.tabular

/**
 * One appointment: the header (customer, status, code, when, what, who,
 * where), the customer's contact and notes, the payment, the booking answers,
 * the details and the secondary actions, with the main ones in a bar pinned to
 * the bottom. Exactly the actions the web offers for its status and the role.
 */
@Composable
fun AppointmentDetailScreen(model: AppModel, initial: Appointment) {
    // The agenda reloads after every action; this screen follows it. When the appointment is
    // no longer in its week, the server is asked once; if it has no such appointment, it is gone.
    var latest by remember { mutableStateOf(initial) }
    var gone by remember { mutableStateOf(false) }
    val fromAgenda = model.agenda.find(initial.id)
    val appointment = fromAgenda ?: latest
    LaunchedEffect(fromAgenda) {
        if (fromAgenda != null) { latest = fromAgenda; gone = false; return@LaunchedEffect }
        try {
            val found = model.agenda.locate(initial.id, latest.startsAt)
            if (found == null) gone = true else latest = found
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ApiException) {
            // Offline: keep the copy we have.
        }
    }

    val ext = LocalExtended.current
    val haptics = LocalHapticFeedback.current
    val zone = model.zone
    val day = BusinessTime.parse(appointment.startsAt)?.let { BusinessTime.localDate(it, zone) }
    val actions = if (gone) emptyList() else Appointments.actions(appointment, model.isAdmin, model.agenda.now)
    val isAdmin = model.isAdmin

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
        bottomBar = { BottomActions(model, appointment, actions, action.busy) { action.run { model.call { undoNoShow(it, appointment.id) }; done(Copy.agenda.noShowUndone) } } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (gone) NoticeBanner(Copy.agenda.gone)
            Header(model, appointment)
            if (appointment.status == "pending_payment") NoticeBanner(Copy.agenda.pendingPaymentHint, icon = Icons.Outlined.Schedule)
            CustomerCard(model, appointment, showCustomer = Appointments.Action.VIEW_CUSTOMER in actions)
            if (isAdmin && appointment.customer.id.isNotEmpty()) {
                val notes = rememberLoader(appointment.customer.id) { model.call { customer(it, appointment.customer.id) } }
                Column {
                    CustomerNotesSection(
                        model, Copy.agenda.customerNotes, appointment.customer.id, notes.value?.customer?.notes,
                        loading = notes.value == null && notes.loading,
                    ) { saved -> notes.value = notes.value?.copy(customer = saved) }
                }
            }
            if (isAdmin) PaymentCard(model, appointment)
            if (appointment.answers.isNotEmpty()) {
                Column {
                    SectionTitle(Copy.agenda.answers)
                    AppCard {
                        appointment.answers.forEachIndexed { index, answer ->
                            if (index > 0) Hairline()
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(answer.label, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                Text(answer.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
            Column {
                SectionTitle(Copy.agenda.details)
                AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    InfoRow(Copy.agenda.bookedVia, Copy.agenda.source(appointment.source, appointment.sourceLabel))
                    BusinessTime.parse(appointment.createdAt)?.let { created ->
                        Hairline()
                        InfoRow(Copy.agenda.bookedOn, BusinessTime.mediumDate(BusinessTime.localDate(created, zone), AppLocale.locale) + " · " + BusinessTime.time(created, zone))
                    }
                }
            }
            // The secondary actions, in the web's order.
            val rows = listOf(Appointments.Action.DEPOSIT, Appointments.Action.NO_SHOW, Appointments.Action.CANCEL).filter { it in actions }
            if (isAdmin && rows.isNotEmpty()) {
                Column {
                    SectionTitle(Copy.agenda.actions)
                    AppCard(padding = PaddingValues(vertical = 4.dp)) {
                        rows.forEachIndexed { index, item ->
                            if (index > 0) Hairline(Modifier.padding(start = 66.dp))
                            when (item) {
                                Appointments.Action.DEPOSIT -> NavRow(Copy.agenda.newDeposit, Icons.Outlined.Savings) { depositSheet = true }
                                Appointments.Action.NO_SHOW -> ActionRow(Copy.agenda.markNoShow, Icons.Outlined.PersonOff, ext.orange.content, enabled = !action.busy) { confirmNoShow = true }
                                Appointments.Action.CANCEL -> ActionRow(Copy.agenda.cancelAppointment, Icons.Outlined.EventBusy, MaterialTheme.colorScheme.error, enabled = !action.busy) { confirmCancel = true }
                                else -> Unit
                            }
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
            onDismiss = { confirmNoShow = false }, dismiss = Copy.agenda.goBack, danger = true,
        )
    }
    if (depositSheet) DepositSheet(model, appointment) { depositSheet = false }
}

/** The customer, the status and code, and the when, what, who and where of it. */
@Composable
private fun Header(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val zone = model.zone
    val locale = AppLocale.locale
    val today = model.today
    val start = BusinessTime.parse(appointment.startsAt)
    val end = BusinessTime.parse(appointment.endsAt)
    val day = start?.let { BusinessTime.localDate(it, zone) }
    val name = Names.full(appointment.customer.firstName, appointment.customer.lastName)
    val status = Appointments.status(appointment, model.agenda.now)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, size = 56.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    Text(
                        appointment.code, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = ext.muted,
                        modifier = Modifier.padding(start = 8.dp).clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClickLabel = Copy.common.copy) { context.copyText(appointment.code); model.toast(Copy.common.copied) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .semantics { contentDescription = "${Copy.common.code} ${appointment.code}" },
                    )
                }
            }
        }
        Hairline(Modifier.padding(vertical = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (day != null) {
                val relative = when (BusinessTime.relative(day, today)) {
                    BusinessTime.Relative.TODAY -> Copy.common.today
                    BusinessTime.Relative.TOMORROW -> Copy.common.tomorrow
                    BusinessTime.Relative.YESTERDAY -> Copy.common.yesterday
                    null -> null
                }
                HeaderRow(Icons.Outlined.CalendarMonth, listOfNotNull(relative, BusinessTime.longDay(day, locale, today)).joinToString(" · "))
            }
            if (start != null && end != null) {
                HeaderRow(Icons.Outlined.AccessTime, "${BusinessTime.time(start, zone)} – ${BusinessTime.time(end, zone)} · ${BusinessTime.duration(BusinessTime.minutes(start, end))}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ContentCut, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(appointment.serviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                // Professionals see what the appointment costs here; admins in "Pago".
                if (!model.isAdmin) Appointments.priceAfterDiscount(appointment)?.let {
                    Text(Money.format(it, model.currency), style = MaterialTheme.typography.bodyMedium.tabular, color = ext.muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(ext.person(model.catalog.personIndex(appointment.resourceId))))
                }
                Text(appointment.resourceName ?: Copy.agenda.noProfessional, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
            }
            if (model.catalog.activeLocations.size > 1 && appointment.locationName != null) HeaderRow(Icons.Outlined.Place, appointment.locationName)
        }
    }
}

@Composable
private fun HeaderRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
    }
}

/** «CLIENTE»: how to reach them, and (admins) the way to their page. */
@Composable
private fun CustomerCard(model: AppModel, appointment: Appointment, showCustomer: Boolean) {
    val ext = LocalExtended.current
    val customer = appointment.customer
    val phone = customer.phone?.takeIf { it.isNotBlank() }
    val mail = customer.email?.takeIf { it.isNotBlank() }
    Column {
        SectionTitle(Copy.agenda.customer)
        AppCard(padding = PaddingValues(0.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (phone == null && mail == null) {
                    Text(Copy.agenda.noContact, style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (phone != null) ContactLine(Icons.Outlined.Phone, Phones.format(phone))
                        if (mail != null) ContactLine(Icons.Outlined.Email, mail)
                    }
                    ContactActions(phone, mail)
                }
                if (showCustomer) {
                    ViewCustomerButton {
                        model.nav.push(Route.CustomerDetail(CustomerSummary(id = customer.id, shortId = customer.shortId, firstName = customer.firstName, lastName = customer.lastName, email = customer.email, phone = customer.phone)))
                    }
                }
            }
        }
    }
}

/** «Ver cliente»: a soft brand button inside the customer card, as on the iPhone. */
@Composable
private fun ViewCustomerButton(onClick: () -> Unit) {
    val ext = LocalExtended.current
    val brand = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(ext.brand.container)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AccountCircle, null, tint = brand, modifier = Modifier.size(20.dp))
        Text(Copy.agenda.viewCustomer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = brand, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ext.subtle, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ContactLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalExtended.current.subtle, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.tabular, modifier = Modifier.padding(start = 10.dp))
    }
}

/** «PAGO»: price, discount and deposits, then (confirmed) where the payment stands. */
@Composable
private fun PaymentCard(model: AppModel, appointment: Appointment) {
    val ext = LocalExtended.current
    val currency = model.currency
    Column {
        SectionTitle(Copy.agenda.payment)
        AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow(Copy.common.price, appointment.priceAmount?.let { Money.format(it, currency) } ?: Copy.common.priceOnRequest)
            if (appointment.discountAmount > 0) {
                InfoRow(
                    listOfNotNull(Copy.agenda.discount, appointment.giftCode?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    Money.negative(appointment.discountAmount, currency), valueColor = ext.success.content,
                )
            }
            if (appointment.depositAmount > 0) InfoRow(Copy.agenda.deposits, Money.negative(appointment.depositAmount, currency))
            if (appointment.status == "confirmed") {
                Hairline(Modifier.padding(vertical = 6.dp))
                Box(Modifier.padding(vertical = 6.dp)) {
                    when (Appointments.payment(appointment)) {
                        Appointments.Payment.PAID -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Chip(Copy.agenda.paid, ext.info, icon = Icons.Filled.Check)
                            Text(Copy.agenda.saleNumber(appointment.sale?.number ?: 0), color = ext.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                        Appointments.Payment.COVERED -> Column {
                            Chip(Copy.agenda.toClose, ext.warning)
                            Text(Copy.agenda.coveredHint, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 6.dp))
                        }
                        Appointments.Payment.PARTIAL -> Column {
                            Text(Copy.agenda.dueLeft(Money.format(Appointments.owed(appointment), currency)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(Copy.agenda.deposited(Money.format(appointment.depositAmount, currency)), style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Chip(Copy.agenda.unpaid, ext.warning)
                            Spacer(Modifier.weight(1f))
                            Appointments.chargeAmount(appointment)?.let {
                                Text(Money.format(it, currency), style = MaterialTheme.typography.titleLarge.tabular, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row of the «ACCIONES» list that does something right here (after a confirmation). */
@Composable
private fun ActionRow(title: String, icon: ImageVector, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tint)
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
    }
}

/**
 * The bar pinned to the bottom: «Editar cita» on the left, the main money or
 * no-show action on the right; one of them alone takes the whole width.
 */
@Composable
private fun BottomActions(model: AppModel, appointment: Appointment, actions: List<Appointments.Action>, busy: Boolean, undoNoShow: () -> Unit) {
    val primary = actions.firstOrNull { it in setOf(Appointments.Action.CHARGE, Appointments.Action.CLOSE_SALE, Appointments.Action.VIEW_SALE, Appointments.Action.UNDO_NO_SHOW) }
    val reschedule = Appointments.Action.RESCHEDULE in actions
    if (primary == null && !reschedule) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reschedule) {
                OutlinedButton(
                    onClick = { model.nav.push(Route.Reschedule(appointment)) },
                    modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Outlined.EditCalendar, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(Copy.agenda.reschedule, maxLines = 1)
                }
            }
            when (primary) {
                Appointments.Action.CHARGE -> PrimaryButton(
                    Appointments.chargeAmount(appointment)?.let { Copy.agenda.chargeAmount(Money.format(it, model.currency)) } ?: Copy.agenda.charge,
                    Modifier.weight(1f), icon = Icons.Outlined.PointOfSale,
                ) { model.nav.push(Route.Checkout(appointment)) }
                Appointments.Action.CLOSE_SALE -> PrimaryButton(Copy.agenda.closeSale, Modifier.weight(1f), icon = Icons.Outlined.PointOfSale) { model.nav.push(Route.Checkout(appointment)) }
                Appointments.Action.VIEW_SALE -> PrimaryButton(Copy.agenda.viewSale, Modifier.weight(1f), icon = Icons.Outlined.Receipt) {
                    appointment.sale?.let { model.nav.push(Route.SaleDetail(it.id)) }
                }
                Appointments.Action.UNDO_NO_SHOW -> PrimaryButton(Copy.agenda.undoNoShow, Modifier.weight(1f), icon = Icons.Outlined.HowToReg, busy = busy, onClick = undoNoShow)
                else -> Unit
            }
        }
    }
}

/** "Registrar abono": money the customer leaves in advance for this appointment. */
@Composable
fun DepositSheet(model: AppModel, appointment: Appointment, onDismiss: () -> Unit) {
    val currency = model.currency
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("transfer") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val action = rememberAction { error = it.message }
    // While it saves, the sheet stays: neither a swipe nor back takes it away.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !action.busy })
    BackHandler(enabled = action.busy) {}
    ModalBottomSheet(onDismissRequest = { if (!action.busy) onDismiss() }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Copy.checkout.depositTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                listOf(Names.full(appointment.customer.firstName, appointment.customer.lastName), appointment.serviceName, appointment.code).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
            Text(Copy.checkout.depositHint, style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted)
            Field(
                amount, { amount = it; error = null }, Copy.checkout.amount, prefix = Money.symbol(currency) + " ",
                keyboard = if (Money.digits(currency) == 0) KeyboardType.Number else KeyboardType.Decimal, error = error, enabled = !action.busy,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val methods = listOf("cash", "card", "transfer", "other")
                methods.forEachIndexed { index, value ->
                    SegmentedButton(method == value, { method = value }, SegmentedButtonDefaults.itemShape(index, methods.size), icon = {}, enabled = !action.busy) {
                        Text(Copy.sales.method(value), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Field(note, { note = it }, Copy.common.note, supporting = Copy.common.optional, enabled = !action.busy)
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
