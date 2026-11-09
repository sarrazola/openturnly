@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.BusinessTime
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.ErrorState
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.SkeletonCards
import org.openturnly.mobile.ui.rememberAction
import org.openturnly.mobile.ui.rememberLoader
import org.openturnly.mobile.ui.copyText
import org.openturnly.mobile.ui.shareText

/** "Equipo": who has access to the business, pending invitations, and inviting by e-mail. */
@Composable
fun TeamScreen(model: AppModel) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val loader = rememberLoader(model.business?.id) { model.call { access(it) } }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var inviteUrl by remember { mutableStateOf<String?>(null) }
    val action = rememberAction { failure -> error = failure.fieldMessage("email") ?: failure.message }

    DetailScaffold(title = Copy.access.title, onBack = { model.nav.pop() }) { padding ->
        PullToRefreshBox(loader.refreshing, { loader.load(refresh = true) }, Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(Copy.access.invite)
                AppCard {
                    Text(Copy.access.inviteHint, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(bottom = 8.dp))
                    Field(email, { email = it; error = null }, Copy.common.email, keyboard = KeyboardType.Email, imeAction = ImeAction.Send, error = error)
                    PrimaryButton(Copy.access.send, Modifier.padding(top = 4.dp), busy = action.busy, enabled = email.contains("@")) {
                        action.run {
                            val result = model.call { invite(it, email.trim()) }
                            if (result.status == "invited") { inviteUrl = result.inviteUrl; model.toast(Copy.access.invited) } else model.toast(Copy.access.added)
                            email = ""
                            loader.load(refresh = true)
                        }
                    }
                    inviteUrl?.let { url ->
                        Hairline(Modifier.padding(vertical = 12.dp))
                        Text(Copy.access.inviteLink, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton({ context.copyText(url); model.toast(Copy.common.copied) }, Modifier.weight(1f)) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.common.copy)
                            }
                            FilledTonalButton({ context.shareText(url) }, Modifier.weight(1f)) {
                                Icon(Icons.Outlined.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.common.share)
                            }
                        }
                    }
                }
                val data = loader.value
                when {
                    data == null && loader.error != null -> ErrorState(loader.error!!, { loader.load() })
                    data == null -> SkeletonCards(3, Modifier.padding(horizontal = 0.dp))
                    else -> {
                        SectionTitle(Copy.access.members)
                        AppCard {
                            if (data.members.isEmpty()) Text(Copy.access.empty, color = ext.muted)
                            data.members.forEachIndexed { index, member ->
                                if (index > 0) Hairline(Modifier.padding(vertical = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(member.name.ifEmpty { member.email }, size = 40.dp)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(member.name.ifEmpty { member.email }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                        Text(member.email, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                        member.resourceName?.let { Text(Copy.access.linked(it), style = MaterialTheme.typography.bodySmall, color = ext.muted) }
                                    }
                                    Chip(Copy.nav.role(member.role), if (member.role == "admin") ext.brand else ext.neutral)
                                }
                            }
                            Text(Copy.access.ownerNote, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 12.dp))
                        }
                        if (data.invitations.isNotEmpty()) {
                            SectionTitle(Copy.access.invitations)
                            AppCard {
                                data.invitations.forEachIndexed { index, invitation ->
                                    if (index > 0) Hairline(Modifier.padding(vertical = 8.dp))
                                    Column {
                                        Text(invitation.email, style = MaterialTheme.typography.bodyLarge)
                                        BusinessTime.parse(invitation.expiresAt)?.let {
                                            Text(Copy.access.expires(BusinessTime.mediumDate(BusinessTime.localDate(it, model.zone), AppLocale.locale)), style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
