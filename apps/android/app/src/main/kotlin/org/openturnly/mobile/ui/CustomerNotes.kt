package org.openturnly.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.api.Customer
import org.openturnly.mobile.api.obj
import org.openturnly.mobile.localization.Copy

/**
 * A customer's private notes, the same on an appointment and on the customer:
 * the notes or «Agregar notas», edited in place with the same hint, saved with
 * «Notas guardadas». Null notes while [loading] shows a skeleton.
 */
@Composable
fun CustomerNotesSection(
    model: AppModel,
    title: String,
    customerId: String,
    notes: String?,
    loading: Boolean = false,
    onSaved: (Customer) -> Unit,
) {
    val ext = LocalExtended.current
    var editing by remember(customerId) { mutableStateOf(false) }
    var draft by remember(customerId) { mutableStateOf("") }
    val action = rememberAction { model.failure(it) }
    fun edit() { draft = notes ?: ""; editing = true }

    SectionTitle(title) {
        if (!loading && !editing) {
            TextButton(::edit) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Copy.common.edit)
            }
        }
    }
    AppCard {
        when {
            loading -> { Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp)); Spacer(Modifier.height(6.dp)); Skeleton(Modifier.fillMaxWidth(0.4f).height(14.dp)) }
            editing -> {
                Field(draft, { draft = it }, Copy.common.notes, singleLine = false, minLines = 3, supporting = Copy.agenda.customerNotesHint, enabled = !action.busy, modifier = Modifier.imePadding())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton({ editing = false }, enabled = !action.busy) { Text(Copy.common.cancel) }
                    TextButton(enabled = !action.busy, onClick = {
                        action.run {
                            val saved = model.call { updateCustomer(it, customerId, obj("notes" to draft.trim().ifEmpty { null })) }
                            onSaved(saved)
                            editing = false
                            model.toast(Copy.agenda.notesSaved)
                        }
                    }) { Text(Copy.common.save, fontWeight = FontWeight.SemiBold) }
                }
            }
            else -> {
                val text = notes?.takeIf { it.isNotBlank() }
                Text(
                    text ?: Copy.agenda.addNotes, style = MaterialTheme.typography.bodyMedium,
                    color = if (text == null) ext.muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = ::edit),
                )
            }
        }
    }
}
