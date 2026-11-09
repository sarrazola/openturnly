@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.customers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PhoneField
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.rememberAction

/** What the customer form checks before it sends anything, the same on every platform. */
internal object CustomerFormRules {
    const val FIRST_NAME = "firstName"
    const val LAST_NAME = "lastName"
    /** A mobile or an email: the message goes under the contact section. */
    const val CONTACT = "contact"
    const val EMAIL = "email"

    private val emailShape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isEmail(value: String): Boolean = emailShape.matches(value.trim())

    /** The form's problems by field; empty when it can be saved. [phone] is the composed number, null when empty. */
    fun errors(firstName: String, lastName: String, phone: String?, email: String): Map<String, String> = buildMap {
        val mail = email.trim()
        if (firstName.isBlank()) put(FIRST_NAME, Copy.customers.firstNameRequired)
        if (lastName.isBlank()) put(LAST_NAME, Copy.customers.lastNameRequired)
        if (phone == null && !isEmail(mail)) put(CONTACT, Copy.customers.contactRequired)
        if (mail.isNotEmpty() && !isEmail(mail)) put(EMAIL, Copy.customers.emailInvalid)
    }
}

/** Create a customer or edit one; only what changed is sent, and an emptied field is cleared. */
@Composable
fun CustomerFormScreen(model: AppModel, existing: CustomerSummary?) {
    val business = model.business ?: return
    val ext = LocalExtended.current
    val split = Phones.split(existing?.phone)
    var firstName by remember { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember { mutableStateOf(existing?.lastName ?: "") }
    var country by remember { mutableStateOf(split?.first ?: Phones.defaultCountry(business.country)) }
    var national by remember { mutableStateOf(split?.second ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var birthDate by remember { mutableStateOf(BusinessTime.parseDate(existing?.birthDate)) }
    var document by remember { mutableStateOf(existing?.document ?: "") }
    var gender by remember { mutableStateOf(existing?.gender?.takeIf { it in Copy.customers.genders } ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    val action = rememberAction { failure ->
        val mapped = failure.fields.mapNotNull { (key, _) ->
            val field = key.substringAfterLast('.')
            failure.fieldMessage(field)?.let { field to it }
        }.toMap()
        errors = mapped
        formError = if (mapped.isEmpty()) failure.message else null
    }
    // A save on its way can't be left behind (the same as a sheet that can't be swiped away).
    BackHandler(enabled = action.busy) {}
    fun leave() { if (!action.busy) model.nav.pop() }

    fun save() {
        formError = null
        val phone = Phones.compose(country, national)
        errors = CustomerFormRules.errors(firstName, lastName, phone, email)
        if (errors.isNotEmpty()) return
        fun changed(old: String?, new: String?): Boolean = (old?.ifBlank { null }) != (new?.ifBlank { null })
        val values = listOf(
            "firstName" to firstName.trim(), "lastName" to lastName.trim(), "phone" to phone, "email" to email.trim().ifEmpty { null },
            "birthDate" to birthDate?.toString(), "document" to document.trim().ifEmpty { null }, "gender" to gender.ifEmpty { null },
            "notes" to notes.trim().ifEmpty { null },
        )
        action.run {
            if (existing == null) {
                val body = objWithout(*values.map { (k, v) -> k to (v ?: Omit) }.toTypedArray())
                val created = model.call { createCustomer(it, body) }
                val summary = created.summary()
                model.customers.upsert(summary)
                model.toast(Copy.customers.created)
                // Straight to the new customer, so the owner can book right away.
                model.nav.replace(Route.CustomerDetail(summary))
            } else {
                val old = mapOf(
                    "firstName" to existing.firstName, "lastName" to existing.lastName, "phone" to existing.phone?.let { Phones.compose(Phones.split(it)?.first ?: country, Phones.split(it)?.second ?: "") },
                    "email" to existing.email, "birthDate" to existing.birthDate, "document" to existing.document, "gender" to existing.gender, "notes" to existing.notes,
                )
                val diff = values.filter { (k, v) -> changed(old[k], v) }
                if (diff.isNotEmpty()) {
                    val saved = model.call { updateCustomer(it, existing.id, objWithout(*diff.toTypedArray())) }
                    val known = model.customers.list?.firstOrNull { it.id == existing.id } ?: existing
                    model.customers.upsert(saved.summary(known.visits, known.lastVisit, known.nextVisit))
                }
                model.toast(Copy.customers.updated)
                model.nav.pop()
            }
        }
    }

    DetailScaffold(
        title = if (existing == null) Copy.customers.new else Copy.customers.edit,
        onBack = ::leave,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    formError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    PrimaryButton(Copy.common.save, busy = action.busy) { save() }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(firstName, { firstName = it; errors = errors - CustomerFormRules.FIRST_NAME }, Copy.common.firstName, error = errors[CustomerFormRules.FIRST_NAME], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
                Field(lastName, { lastName = it; errors = errors - CustomerFormRules.LAST_NAME }, Copy.common.lastName, error = errors[CustomerFormRules.LAST_NAME], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
            }

            SectionTitle(Copy.customers.contact, Modifier.padding(top = 12.dp))
            PhoneField(country, national, { country = it }, { national = it; errors = errors - "phone" - CustomerFormRules.CONTACT }, error = errors["phone"])
            Field(
                email, { email = it; errors = errors - CustomerFormRules.EMAIL - CustomerFormRules.CONTACT }, Copy.common.email,
                keyboard = KeyboardType.Email, error = errors[CustomerFormRules.EMAIL],
            )
            // The hint turns into the error when neither is there.
            Text(
                Copy.customers.contactRequired, style = MaterialTheme.typography.bodySmall,
                color = if (CustomerFormRules.CONTACT in errors) MaterialTheme.colorScheme.error else ext.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SectionTitle(Copy.common.notes, Modifier.padding(top = 12.dp))
            Field(notes, { notes = it }, Copy.common.notes, singleLine = false, minLines = 3, supporting = Copy.agenda.customerNotesHint)

            SectionTitle(Copy.customers.moreDetails, Modifier.padding(top = 12.dp))
            Box {
                Field(
                    birthDate?.let { BusinessTime.mediumDate(it, AppLocale.locale) } ?: "", {}, Copy.customers.birthDate,
                    readOnly = true, error = errors["birthDate"], supporting = Copy.common.optional,
                    trailing = { Icon(Icons.Outlined.Cake, null) },
                )
                // The whole field opens the calendar.
                Box(Modifier.matchParentSize().clickable(onClickLabel = Copy.common.pickDate) { pickingDate = true })
            }
            Field(document, { document = it }, Copy.customers.document, error = errors["document"], supporting = Copy.common.optional)
            GenderPicker(gender) { gender = it }
        }
    }

    if (pickingDate) {
        val today = model.today
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birthDate?.let { BusinessTime.pickerMillis(it) },
            initialDisplayMode = DisplayMode.Input,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= BusinessTime.pickerMillis(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({ state.selectedDateMillis?.let { birthDate = BusinessTime.fromPickerMillis(it) }; pickingDate = false }) { Text(Copy.common.ok) }
            },
            dismissButton = {
                Row {
                    // The date is optional: it can be taken off again.
                    if (birthDate != null) TextButton({ birthDate = null; pickingDate = false }) { Text(Copy.customers.clearDate) }
                    TextButton({ pickingDate = false }) { Text(Copy.common.cancel) }
                }
            },
        ) { DatePicker(state) }
    }
}

/** «Femenino», «Masculino», «Otro», «Sin definir»: a dropdown, like the other pickers of the app. */
@Composable
private fun GenderPicker(value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.padding(top = 4.dp)) {
        Field(
            Copy.customers.genderLabel(value), {}, Copy.customers.gender, readOnly = true,
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Copy.customers.genders.forEach { option ->
                DropdownMenuItem(
                    text = { Text(Copy.customers.genderLabel(option)) },
                    onClick = { onPick(option); open = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
