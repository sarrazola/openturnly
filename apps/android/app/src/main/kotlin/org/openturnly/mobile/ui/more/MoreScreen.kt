@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.Brand
import org.openturnly.mobile.BuildConfig
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.BusinessLogo
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.IconBadge
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.NavRow
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.openUrl

/** The booking link, the numbers and the account; setting the business up is in the web panel. */
@Composable
fun MoreScreen(model: AppModel) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val business = model.business ?: return
    val admin = model.isAdmin
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(Copy.more.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
        // Who and where: the business and your role in it. Not a button; switching has its own row.
        AppCard(padding = PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BusinessLogo(model, business, 56.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(business.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(Copy.nav.role(business.access.role), ext.brand)
                        if (business.sample) Chip(Copy.nav.sample, ext.neutral)
                    }
                }
            }
            if (model.businesses.size > 1) {
                Hairline()
                NavRow(Copy.nav.switchBusiness, Icons.Outlined.SwapHoriz) { model.showBusinessPicker = true }
            }
        }

        AppCard(Modifier.padding(top = 12.dp), padding = PaddingValues(vertical = 4.dp)) {
            NavRow(Copy.more.bookingLink, Icons.Outlined.QrCode2, iconTint = MaterialTheme.colorScheme.primary) { model.nav.push(Route.BookingLink) }
            if (admin) {
                Hairline(Modifier.padding(start = 66.dp))
                NavRow(Copy.reports.title, Icons.Outlined.BarChart, iconTint = ext.warning.content) { model.nav.push(Route.Reports) }
            }
        }

        if (admin) {
            AppCard(Modifier.padding(top = 12.dp), padding = PaddingValues(vertical = 4.dp)) {
                NavRow(Copy.more.webPanel, Icons.Outlined.Language, iconTint = ext.neutral.content, trailing = {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = ext.muted, modifier = Modifier.size(18.dp))
                }) { context.openUrl("${model.server}/admin") }
            }
            Text(Copy.more.webPanelHint, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }

        SectionTitle(Copy.more.you, Modifier.padding(top = 12.dp))
        AppCard(padding = PaddingValues(vertical = 4.dp)) {
            NavRow(Copy.account.title, Icons.Outlined.AccountCircle, iconTint = ext.neutral.content, subtitle = model.user?.let { "${it.name} · ${it.email}" }) { model.nav.push(Route.Account) }
            Hairline(Modifier.padding(start = 66.dp))
            SignOutRow(busy = model.signOutBusy) { confirmSignOut = true }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "${Brand.name} · ${Copy.more.version(BuildConfig.VERSION_NAME)}", style = MaterialTheme.typography.bodySmall, color = ext.muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
            Brand.privacyPolicyUrl?.let { url -> Text(Copy.signIn.privacy, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { context.openUrl(url.toString()) }) }
            Brand.supportUrl?.let { url -> Text(Copy.signIn.support, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { context.openUrl(url.toString()) }) }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(Copy.nav.signOutTitle, Copy.nav.signOutBody, Copy.nav.signOut, onConfirm = { confirmSignOut = false; model.signOut() }, onDismiss = { confirmSignOut = false }, danger = true)
    }
}

/** «Cerrar sesión», in red like every action that can't be undone, with progress while it runs. */
@Composable
private fun SignOutRow(busy: Boolean, onClick: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(Icons.AutoMirrored.Outlined.Logout, danger)
        Text(Copy.nav.signOut, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = danger, modifier = Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = danger)
    }
}
