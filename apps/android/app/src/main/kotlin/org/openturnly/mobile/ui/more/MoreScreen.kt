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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.Brand
import org.openturnly.mobile.BuildConfig
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.ServerAddress
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.BusinessLogo
import org.openturnly.mobile.ui.Chip
import org.openturnly.mobile.ui.ConfirmDialog
import org.openturnly.mobile.ui.Hairline
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.NavRow
import org.openturnly.mobile.ui.Route
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.rememberLoader

/** Everything that isn't the agenda, customers or sales, for the person's role. */
@Composable
fun MoreScreen(model: AppModel) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val business = model.business ?: return
    val admin = model.isAdmin
    var confirmSignOut by remember { mutableStateOf(false) }
    val lowStock = if (admin) rememberLoader(business.id) {
        model.call { products(it) }.count { product -> product.active && product.alerts.any { (location, alert) -> (product.stock[location] ?: 0L) <= alert } }
    } else null

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(Copy.more.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
        AppCard(padding = PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().clickable { model.nav.push(Route.Business) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BusinessLogo(model, business, 56.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(business.name, style = MaterialTheme.typography.titleLarge)
                    Text(ServerAddress.display(model.server) + "/" + business.slug, style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 1)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(Copy.nav.role(business.access.role), if (admin) ext.brand else ext.neutral)
                        if (business.sample) Chip(Copy.nav.sample, ext.warning)
                    }
                }
            }
            if (model.businesses.size > 1) {
                Hairline()
                NavRow(Copy.nav.switchBusiness, Icons.Outlined.SwapHoriz) { model.showBusinessPicker = true }
            }
        }

        SectionTitle(Copy.more.manage, Modifier.padding(top = 12.dp))
        AppCard(padding = PaddingValues(vertical = 4.dp)) {
            NavRow(Copy.services.title, Icons.Outlined.ContentCut, subtitle = model.catalog.services.count { it.active }.takeIf { model.catalog.loaded }?.let { Copy.more.activeCount(it) }) { model.nav.push(Route.Services) }
            Hairline(Modifier.padding(start = 66.dp))
            NavRow(Copy.team.title, Icons.Outlined.Groups, iconTint = ext.person(1)) { model.nav.push(Route.Professionals) }
            Hairline(Modifier.padding(start = 66.dp))
            NavRow(Copy.hours.title, Icons.Outlined.Schedule, iconTint = ext.person(4)) { model.nav.push(Route.Hours) }
            Hairline(Modifier.padding(start = 66.dp))
            NavRow(Copy.business.title, Icons.Outlined.Storefront, iconTint = ext.person(2), subtitle = Copy.more.bookingPage) { model.nav.push(Route.Business) }
            if (admin) {
                Hairline(Modifier.padding(start = 66.dp))
                NavRow(Copy.access.title, Icons.Outlined.SupervisorAccount, iconTint = ext.person(5)) { model.nav.push(Route.Team) }
            }
        }

        if (admin) {
            SectionTitle(Copy.more.money, Modifier.padding(top = 12.dp))
            AppCard(padding = PaddingValues(vertical = 4.dp)) {
                NavRow(Copy.reports.title, Icons.Outlined.BarChart, iconTint = ext.person(6)) { model.nav.push(Route.Reports) }
                Hairline(Modifier.padding(start = 66.dp))
                NavRow(Copy.cash.title, Icons.Outlined.Payments, iconTint = ext.person(0)) { model.nav.push(Route.Cash) }
                Hairline(Modifier.padding(start = 66.dp))
                NavRow(
                    Copy.inventory.title, Icons.Outlined.Inventory2, iconTint = ext.person(7),
                    subtitle = lowStock?.value?.takeIf { it > 0 }?.let { Copy.inventory.lowStockCount(it) },
                ) { model.nav.push(Route.Inventory) }
                Hairline(Modifier.padding(start = 66.dp))
                NavRow(Copy.giftCards.title, Icons.Outlined.CardGiftcard, iconTint = ext.person(3)) { model.nav.push(Route.GiftCards) }
            }
        }

        SectionTitle(Copy.more.you, Modifier.padding(top = 12.dp))
        AppCard(padding = PaddingValues(vertical = 4.dp)) {
            NavRow(Copy.account.title, Icons.Outlined.AccountCircle, subtitle = model.user?.let { "${it.name} · ${it.email}" }) { model.nav.push(Route.Account) }
            Hairline(Modifier.padding(start = 66.dp))
            NavRow(Copy.nav.signOut, Icons.AutoMirrored.Outlined.Logout, iconTint = MaterialTheme.colorScheme.error, trailing = {}) { confirmSignOut = true }
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
