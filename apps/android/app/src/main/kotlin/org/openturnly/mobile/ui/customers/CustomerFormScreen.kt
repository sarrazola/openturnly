@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.customers

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import java.time.LocalDate
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.CustomerSummary
import org.openturnly.mobile.api.Omit
import org.openturnly.mobile.api.objWithout
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.rules.Phones
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PhoneField
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.rememberAction

/** Create a customer or edit one; only what changed is sent, and an emptied field is cleared. */
@Composable
fun CustomerFormScreen(model: AppModel, existing: CustomerSummary?) {
    val business = model.business ?: return
    val split = Phones.split(existing?.phone)
    var firstName by remember { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember { mutableStateOf(existing?.lastName ?: "") }
    var country by remember { mutableStateOf(split?.first ?: Phones.defaultCountry(business.country)) }
    var national by remember { mutableStateOf(split?.second ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var birthDate by remember { mutableStateOf(BusinessTime.parseDate(existing?.birthDate)) }
    var document by remember { mutableStateOf(existing?.document ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
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

    fun save() {
        formError = null
        val phone = Phones.compose(country, national)
        val next = HashMap<String, String>()
        if (firstName.isBlank()) next["firstName"] = Copy.customers.firstNameRequired
        if (lastName.isBlank()) next["lastName"] = Copy.customers.lastNameRequired
        if (phone == null && email.isBlank()) next["phone"] = Copy.customers.contactRequired
        errors = next
        if (next.isNotEmpty()) return
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
                model.nav.replace(Route.CustomerDetail(summary))
            } else {
                val old = mapOf(
                    "firstName" to existing.firstName, "lastName" to existing.lastName, "phone" to existing.phone?.let { Phones.compose(Phones.split(it)?.first ?: country, Phones.split(it)?.second ?: "") },
                    "email" to existing.email, "birthDate" to existing.birthDate, "document" to existing.document, "gender" to existing.gender, "notes" to existing.notes,
                )
                val diff = values.filter { (k, v) -> changed(old[k], v) }
                if (diff.isNotEmpty()) {
                    val saved = model.call { updateCustomer(it, existing.id, objWithout(*diff.toTypedArray())) }
                    model.customers.upsert(saved.summary(existing.visits, existing.lastVisit, existing.nextVisit))
                }
                model.toast(Copy.customers.updated)
                model.nav.pop()
            }
        }
    }

    DetailScaffold(
        title = if (existing == null) Copy.customers.new else Copy.customers.edit,
        onBack = { model.nav.pop() },
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
                Field(firstName, { firstName = it; errors = errors - "firstName" }, Copy.common.firstName, error = errors["firstName"], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
                Field(lastName, { lastName = it; errors = errors - "lastName" }, Copy.common.lastName, error = errors["lastName"], capitalization = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
            }
            PhoneField(country, national, { country = it }, { national = it; errors = errors - "phone" }, error = errors["phone"])
            Field(email, { email = it; errors = errors - "email" }, Copy.common.email, keyboard = KeyboardType.Email, error = errors["email"])
            Text(Copy.booking.contactHint, style = MaterialTheme.typography.bodySmall, color = LocalExtended.current.muted)

            SectionTitle(Copy.customers.moreDetails, Modifier.padding(top = 12.dp))
            Field(
                birthDate?.let { org.openturnly.mobile.rules.BusinessTime.mediumDate(it, AppLocale.locale) } ?: "", {}, Copy.customers.birthDate,
                readOnly = true, error = errors["birthDate"],
                trailing = { IconButton({ pickingDate = true }) { Icon(Icons.Outlined.Cake, Copy.common.pickDate) } },
                supporting = Copy.common.optional,
            )
            Field(document, { document = it }, Copy.customers.document, supporting = Copy.common.optional)
            Text(Copy.customers.gender, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf("female", "male", "other", "")
                options.forEachIndexed { index, value ->
                    SegmentedButton(gender == value, { gender = value }, SegmentedButtonDefaults.itemShape(index, options.size), icon = {}) {
                        Text(if (value.isEmpty()) "—" else Copy.customers.genderLabel(value), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            SectionTitle(Copy.common.notes, Modifier.padding(top = 12.dp))
            Field(notes, { notes = it }, Copy.common.notes, singleLine = false, minLines = 3, supporting = Copy.agenda.customerNotesHint)
        }
    }

    if (pickingDate) {
        val today = model.today
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birthDate?.let { BusinessTime.pickerMillis(it) },
            initialDisplayMode = androidx.compose.material3.DisplayMode.Input,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= BusinessTime.pickerMillis(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton({ birthDate = state.selectedDateMillis?.let { BusinessTime.fromPickerMillis(it) }; pickingDate = false }) { Text(Copy.common.ok) } },
            dismissButton = { TextButton({ birthDate = null; pickingDate = false }) { Text(Copy.common.delete) } },
        ) { DatePicker(state) }
    }
}
