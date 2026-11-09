@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Phones

/** A country (flag and dial code) next to the national number: staff bookings need both. */
@Composable
fun PhoneField(
    country: Phones.Country,
    national: String,
    onCountry: (Phones.Country) -> Unit,
    onNational: (String) -> Unit,
    error: String? = null,
    label: String = Copy.common.phone,
) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedButton(
            onClick = { picking = true }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(top = 8.dp).height(56.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        ) {
            Text("${country.flag} +${country.code}", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ArrowDropDown, Copy.booking.country)
        }
        Field(
            national, { value -> onNational(value.filter { it.isDigit() || it == ' ' || it == '+' }) }, label,
            keyboard = KeyboardType.Phone, error = error, imeAction = ImeAction.Next, modifier = Modifier.weight(1f),
        )
    }
    if (picking) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = state) {
            Text(Copy.booking.country, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn {
                items(Phones.sortedByName, key = { it.iso }) { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onCountry(option); picking = false }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.flag, modifier = Modifier.width(36.dp))
                        Text(option.name, modifier = Modifier.weight(1f))
                        Text("+${option.code}", color = LocalExtended.current.muted)
                    }
                }
            }
        }
    }
}
