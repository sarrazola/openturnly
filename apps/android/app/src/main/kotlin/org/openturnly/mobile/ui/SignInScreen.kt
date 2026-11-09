package org.openturnly.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.Brand
import org.openturnly.mobile.R
import org.openturnly.mobile.api.ApiClient
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.api.Business
import org.openturnly.mobile.api.HostedAccount
import org.openturnly.mobile.api.OpenTurnlyApi
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.ServerAddress

/**
 * Where the session comes from.
 *
 * With no hosted values in the brand file (this repository), the person types
 * their server's address, then signs in there. A publisher's build may add an
 * account directory (e-mail and password alone) or an account-name template;
 * "Use another address" is always there for a custom domain.
 */
@Composable
fun SignInScreen(model: AppModel) {
    val s = Copy.signIn
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val directory = Brand.hostedSignInUrl
    val hosted = Brand.hosted

    // Which way in: an account directory, an account name, or an address.
    var mode by remember { mutableStateOf(if (directory != null) "directory" else if (hosted != null) "account" else "server") }
    val remembered = remember { model.rememberedServer }
    var server by remember { mutableStateOf(remembered) }
    var askServer by remember { mutableStateOf(mode == "server" && remembered.isEmpty()) }
    var draft by remember { mutableStateOf(ServerAddress.display(remembered)) }
    var account by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<HostedAccount>>(emptyList()) }
    var forgot by remember { mutableStateOf(false) }

    fun describe(failure: ApiException): String = when {
        failure.code == "invalid_credentials" || failure.status == 401 -> s.wrongCredentials
        failure.status == 404 || failure.status == 405 -> s.notOpenTurnly
        else -> failure.message
    }

    fun finish(chosen: HostedAccount) {
        busy = true
        scope.launch {
            try {
                val base = ServerAddress.normalize(chosen.server) ?: throw ApiException(0, "invalid_server")
                model.signedIn(base, chosen.token, chosen.user)
                accounts = emptyList()
            } catch (failure: CancellationException) { throw failure } catch (failure: ApiException) {
                error = describe(failure)
            } finally { busy = false }
        }
    }

    fun connect() {
        val normalized = ServerAddress.normalize(draft)
        if (normalized == null) { error = s.invalidServer; return }
        busy = true; error = null
        scope.launch {
            try {
                ApiClient.send(normalized, "/health", "GET")
                server = normalized
                askServer = false
                mode = "server"
            } catch (failure: CancellationException) { throw failure } catch (failure: ApiException) {
                error = if (failure.status == 0) failure.message else s.notOpenTurnly
            } finally { busy = false }
        }
    }

    fun submit() {
        if (email.isBlank() || password.isEmpty() || busy) return
        busy = true; error = null
        scope.launch {
            try {
                when (mode) {
                    "directory" -> {
                        val found = OpenTurnlyApi.hostedSignIn(directory!!, email, password)
                        when {
                            found.isEmpty() -> error = s.wrongCredentials
                            found.size == 1 -> {
                                val only = found.first()
                                model.signedIn(ServerAddress.normalize(only.server) ?: throw ApiException(0, "invalid_server"), only.token, only.user)
                            }
                            else -> accounts = found
                        }
                    }
                    else -> {
                        val base = if (mode == "account") Brand.hostedServer(account) ?: run { error = s.invalidServer; return@launch } else server
                        val answer = OpenTurnlyApi.signIn(base, email, password)
                        model.signedIn(base, answer.token, answer.user)
                    }
                }
            } catch (failure: CancellationException) { throw failure } catch (failure: ApiException) {
                error = describe(failure)
            } finally { busy = false }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (LocalExtended.current.dark) 0.35f else 0.55f), 0.45f to MaterialTheme.colorScheme.background),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                Spacer(Modifier.height(56.dp))
                Image(painterResource(R.drawable.brand_logo), Brand.name, Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(20.dp))
                Text(Brand.name, style = MaterialTheme.typography.titleMedium, color = primary)
                AnimatedContent(askServer, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "signin") { serverStep ->
                    Column {
                        if (serverStep) {
                            Text(s.serverTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                            Text(s.serverHint, style = MaterialTheme.typography.bodyLarge, color = LocalExtended.current.muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                            Field(
                                draft, { draft = it; error = null }, s.serverLabel, placeholder = s.serverPlaceholder,
                                keyboard = KeyboardType.Uri, imeAction = ImeAction.Go, onDone = { connect() },
                                leading = { androidx.compose.material3.Icon(Icons.Outlined.Dns, null) },
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(s.connect, enabled = draft.isNotBlank(), busy = busy) { connect() }
                            if (directory != null || hosted != null) {
                                TextButton({ mode = if (directory != null) "directory" else "account"; askServer = false; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                    Text(s.useHosted(Brand.hostedLabel))
                                }
                            }
                        } else {
                            Text(s.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                            Text(s.subtitle, style = MaterialTheme.typography.bodyLarge, color = LocalExtended.current.muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                            if (mode == "server") {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable { askServer = true; error = null }.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.Icon(Icons.Outlined.Dns, null, tint = primary, modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(s.connectedTo, style = MaterialTheme.typography.labelMedium, color = LocalExtended.current.muted)
                                        Text(ServerAddress.display(server), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    Text(s.changeServer, color = primary, style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            if (mode == "account") {
                                Field(account, { account = it; error = null }, s.accountLabel, placeholder = s.accountPlaceholder, supporting = s.accountHint, keyboard = KeyboardType.Uri)
                                Spacer(Modifier.height(8.dp))
                            }
                            Field(email, { email = it; error = null }, s.emailLabel, placeholder = s.emailPlaceholder, keyboard = KeyboardType.Email, enabled = !busy)
                            Spacer(Modifier.height(12.dp))
                            Field(password, { password = it; error = null }, s.passwordLabel, password = true, imeAction = ImeAction.Go, onDone = { submit() }, enabled = !busy)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                            model.signInNotice?.takeIf { error == null }?.let {
                                Text(it, color = LocalExtended.current.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                            Spacer(Modifier.height(20.dp))
                            PrimaryButton(s.submit, enabled = email.isNotBlank() && password.isNotEmpty(), busy = busy) { submit() }
                            TextButton({ forgot = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(s.forgot) }
                            if (mode != "server") {
                                TextButton({ askServer = true; error = null }, Modifier.fillMaxWidth()) { Text(s.otherAddress) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
                    Brand.privacyPolicyUrl?.let { url -> Text(s.privacy, color = LocalExtended.current.muted, modifier = Modifier.clickable { context.openUrl(url.toString()) }) }
                    Brand.supportUrl?.let { url -> Text(s.support, color = LocalExtended.current.muted, modifier = Modifier.clickable { context.openUrl(url.toString()) }) }
                }
            }
        }
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text(s.forgotTitle) }, text = { Text(s.forgotBody) },
            confirmButton = { TextButton({ forgot = false }) { Text(Copy.common.ok) } },
        )
    }
    if (accounts.size > 1) {
        AlertDialog(
            onDismissRequest = { accounts = emptyList() },
            title = { Text(s.chooseAccount) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.chooseAccountBody, color = LocalExtended.current.muted)
                    accounts.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { finish(option) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(option.name.ifEmpty { option.server }, size = 36.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(option.name.ifEmpty { ServerAddress.display(option.server) }, fontWeight = FontWeight.SemiBold)
                                Text(ServerAddress.display(option.server), color = LocalExtended.current.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ accounts = emptyList() }) { Text(Copy.common.cancel) } },
        )
    }
}

/** The saved session could not reach its server: try again, or sign out. */
@Composable
fun ReconnectScreen(model: AppModel) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(androidx.compose.material.icons.Icons.Outlined.Dns, Copy.signIn.reconnectTitle, Copy.signIn.reconnectBody)
        PrimaryButton(Copy.common.retry, Modifier.widthIn(max = 320.dp)) { model.retryConnection() }
        TextButton({ model.signOut() }, Modifier.padding(top = 12.dp)) { Text(Copy.nav.signOut) }
    }
}

@Composable
fun NoBusinessScreen(model: AppModel) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(Icons.Outlined.Storefront, Copy.signIn.noBusinesses, Copy.signIn.noBusinessesBody)
        OutlinedButton({ model.restore() }) { Text(Copy.common.retry) }
        TextButton({ model.signOut() }, Modifier.padding(top = 12.dp)) { Text(Copy.nav.signOut) }
    }
}

/** After sign-in, when the person works in several businesses. */
@Composable
fun BusinessPickerScreen(model: AppModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(Copy.nav.chooseBusiness, style = MaterialTheme.typography.headlineMedium)
        model.user?.let { Text(it.email, color = LocalExtended.current.muted, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) }
        AppCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            model.businesses.forEachIndexed { index, business ->
                if (index > 0) Hairline(Modifier.padding(start = 72.dp))
                BusinessRow(model, business, selected = false) { model.openBusiness(business) }
            }
        }
        TextButton({ model.signOut() }, Modifier.fillMaxWidth().padding(top = 16.dp)) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(Copy.nav.signOut)
        }
    }
}

@Composable
fun BusinessRow(model: AppModel, business: Business, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BusinessLogo(model, business, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(business.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Chip(Copy.nav.role(business.access.role), if (business.isAdmin) LocalExtended.current.brand else LocalExtended.current.neutral)
                if (business.sample) Chip(Copy.nav.sample, LocalExtended.current.warning)
            }
        }
        if (selected) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** The business's logo when it has one, its initials otherwise. */
@Composable
fun BusinessLogo(model: AppModel, business: Business, size: androidx.compose.ui.unit.Dp) {
    val url = model.api?.absolute(business.logoUrl)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(org.openturnly.mobile.rules.Names.initials(business.name), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (url != null) RemoteImage(url, Modifier.size(size))
    }
}
