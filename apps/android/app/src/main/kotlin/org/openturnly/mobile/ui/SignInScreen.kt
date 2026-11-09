package org.openturnly.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.Brand
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
    var accountError by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<HostedAccount>>(emptyList()) }
    var forgot by remember { mutableStateOf(false) }

    fun describe(failure: ApiException): String = when {
        failure.code == "invalid_credentials" || failure.status == 401 -> s.wrongCredentials
        failure.status == 404 || failure.status == 405 -> s.notAServer
        failure.status == 0 && failure.code == "network" -> s.unreachable
        else -> failure.message
    }

    fun finish(chosen: HostedAccount) {
        busy = true
        scope.launch {
            try {
                val base = ServerAddress.normalize(chosen.server) ?: throw ApiException(0, "invalid_server")
                accounts = emptyList()
                model.signedIn(base, chosen.token, chosen.user)
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
                error = if (failure.status == 0) s.unreachable else s.notAServer
            } finally { busy = false }
        }
    }

    fun submit() {
        if (email.isBlank() || password.isEmpty() || busy) return
        error = null; accountError = null
        val accountServer = if (mode == "account") Brand.hostedServer(account) ?: run { accountError = s.invalidAccount; return } else null
        busy = true
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
                        val base = accountServer ?: server
                        val answer = OpenTurnlyApi.signIn(base, email, password)
                        model.signedIn(base, answer.token, answer.user)
                    }
                }
            } catch (failure: CancellationException) { throw failure } catch (failure: ApiException) {
                error = describe(failure)
            } finally { busy = false }
        }
    }

    /**
     * Where "Forgot your password?" leads: the server's own page when the server
     * is known (typed, or named by the account), the brand's recovery page for the
     * account directory, or nowhere (a note that the administrator sets passwords).
     */
    fun recoveryUrl(): String? = when (mode) {
        "server" -> ServerAddress.forgotPasswordUrl(server)
        "account" -> Brand.hostedServer(account)?.let { ServerAddress.forgotPasswordUrl(it) } ?: Brand.passwordRecoveryUrl?.toString()
        else -> Brand.passwordRecoveryUrl?.toString()
    }

    val primary = MaterialTheme.colorScheme.primary
    val ext = LocalExtended.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (ext.dark) 0.35f else 0.55f), 0.45f to MaterialTheme.colorScheme.background),
        ),
    ) {
        // The form sits in the middle of the screen; the server line and the links stay at
        // the bottom. When the keyboard is up, or the screen is short, it all scrolls.
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            val screen = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = screen).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The mark turns while a sign-in or "Continue" is in progress.
                    ClockLoader(animating = busy, showsName = false)
                    Spacer(Modifier.height(14.dp))
                    Text(Brand.name, style = MaterialTheme.typography.titleSmall, color = primary)
                    AnimatedContent(askServer, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "signin") { serverStep ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (serverStep) {
                                Text(s.serverTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                                Text(s.serverHint(Brand.name), style = MaterialTheme.typography.bodyLarge, color = ext.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                                model.signInNotice?.let { NoticeBanner(it, Modifier.padding(bottom = 16.dp)) }
                                Field(
                                    draft, { draft = it; error = null }, s.serverLabel, placeholder = s.serverPlaceholder,
                                    keyboard = KeyboardType.Uri, imeAction = ImeAction.Go, onDone = { connect() },
                                    leading = { androidx.compose.material3.Icon(Icons.Outlined.Dns, null) },
                                )
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
                                Spacer(Modifier.height(16.dp))
                                PrimaryButton(s.connect, enabled = draft.isNotBlank(), busy = busy) { connect() }
                                if (directory != null || hosted != null) {
                                    TextButton({ mode = if (directory != null) "directory" else "account"; askServer = false; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                        Text(s.useHosted(Brand.hostedLabel), color = ext.muted)
                                    }
                                }
                            } else {
                                Text(s.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                                Text(s.subtitle, style = MaterialTheme.typography.bodyLarge, color = ext.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                                model.signInNotice?.let { NoticeBanner(it, Modifier.padding(bottom = 16.dp)) }
                                if (mode == "account") {
                                    Field(
                                        account, { account = it; accountError = null; error = null }, s.accountLabel, placeholder = s.accountPlaceholder,
                                        supporting = s.accountHint, error = accountError, keyboard = KeyboardType.Uri, enabled = !busy,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Field(email, { email = it; error = null }, s.emailLabel, placeholder = s.emailPlaceholder, keyboard = KeyboardType.Email, enabled = !busy)
                                Spacer(Modifier.height(12.dp))
                                Field(password, { password = it; error = null }, s.passwordLabel, password = true, imeAction = ImeAction.Go, onDone = { submit() }, enabled = !busy)
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                                Spacer(Modifier.height(20.dp))
                                PrimaryButton(s.submit, enabled = email.isNotBlank() && password.isNotEmpty(), busy = busy) { submit() }
                                TextButton({ recoveryUrl()?.let { context.openUrl(it) } ?: run { forgot = true } }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(s.forgot) }
                                if (mode != "server") {
                                    TextButton({ askServer = true; error = null }, Modifier.fillMaxWidth()) { Text(s.otherAddress, color = ext.muted) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!askServer && mode == "server") ServerLine(server) { askServer = true; error = null }
                if (Brand.privacyPolicyUrl != null || Brand.supportUrl != null) {
                    Row(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
                        Brand.privacyPolicyUrl?.let { url -> Text(s.privacy, color = ext.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { context.openUrl(url.toString()) }.padding(8.dp)) }
                        Brand.supportUrl?.let { url -> Text(s.support, color = ext.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { context.openUrl(url.toString()) }.padding(8.dp)) }
                    }
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
                    Text(s.chooseAccountBody, color = ext.muted)
                    accounts.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { finish(option) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(option.name.ifEmpty { option.server }, size = 36.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(option.name.ifEmpty { ServerAddress.display(option.server) }, fontWeight = FontWeight.SemiBold)
                                Text(ServerAddress.display(option.server), color = ext.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ accounts = emptyList() }) { Text(Copy.common.cancel) } },
        )
    }
}

/** «Conectado a localhost:4100 · Cambiar»: which server the form signs in to, kept small, with the way back. */
@Composable
private fun ServerLine(server: String, onChange: () -> Unit) {
    val muted = LocalExtended.current.muted
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            Copy.signIn.connectedTo(ServerAddress.display(server)), style = MaterialTheme.typography.bodySmall, color = muted,
            maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Text(" · ", style = MaterialTheme.typography.bodySmall, color = muted)
        Text(
            Copy.signIn.changeServer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onChange).heightIn(min = 36.dp).wrapContentHeight().padding(horizontal = 4.dp),
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
        PrimaryButton(Copy.common.retry, Modifier.widthIn(max = 320.dp)) { model.restore() }
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
                Chip(Copy.nav.role(business.access.role), LocalExtended.current.brand)
                if (business.sample) Chip(Copy.nav.sample, LocalExtended.current.neutral)
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
