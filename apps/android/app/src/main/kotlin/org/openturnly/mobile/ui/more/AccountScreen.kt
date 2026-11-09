@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.push.Push
import org.openturnly.mobile.rules.ServerAddress
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.Avatar
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.Field
import org.openturnly.mobile.ui.InfoRow
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.PrimaryButton
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.launch
import org.openturnly.mobile.ui.rememberAction

/** "Mi cuenta": name, sign-in e-mail and password, and notifications on this phone. */
@Composable
fun AccountScreen(model: AppModel) {
    val ext = LocalExtended.current
    val user = model.user ?: return
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var confirmPassword by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var passwordErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val emailChanged = email.trim().lowercase() != user.email.lowercase()

    val details = rememberAction { failure ->
        errors = failure.fields.mapNotNull { (k, _) -> failure.fieldMessage(k)?.let { k to it } }.toMap()
        if (errors.isEmpty()) model.failure(failure)
    }
    val password = rememberAction { failure ->
        passwordErrors = failure.fields.mapNotNull { (k, _) -> failure.fieldMessage(k)?.let { k to it } }.toMap()
        if (failure.code == "wrong_password") passwordErrors = mapOf("currentPassword" to failure.message)
        if (passwordErrors.isEmpty()) model.failure(failure)
    }

    DetailScaffold(title = Copy.account.title, onBack = { model.nav.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(user.name, size = 76.dp)
                Text(user.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                Text(user.email, color = ext.muted)
            }
            SectionTitle(Copy.account.details)
            AppCard {
                Field(name, { name = it; errors = errors - "name" }, Copy.common.name, error = errors["name"])
                Field(email, { email = it; errors = errors - "email" }, Copy.common.email, keyboard = KeyboardType.Email, error = errors["email"])
                AnimatedVisibility(emailChanged) {
                    Field(confirmPassword, { confirmPassword = it; errors = errors - "currentPassword" }, Copy.account.currentPassword, password = true, supporting = Copy.account.emailNeedsPassword, error = errors["currentPassword"])
                }
                PrimaryButton(Copy.common.saveChanges, Modifier.padding(top = 8.dp), busy = details.busy, enabled = name.isNotBlank() && email.isNotBlank()) {
                    details.run {
                        val saved = model.call { updateMe(name.trim(), email.trim(), if (emailChanged) confirmPassword else null) }
                        model.updateUser(saved)
                        confirmPassword = ""
                        errors = emptyMap()
                        model.toast(Copy.account.updated)
                    }
                }
            }
            SectionTitle(Copy.account.passwordTitle)
            AppCard {
                Text(Copy.account.passwordHint, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(bottom = 8.dp))
                Field(current, { current = it; passwordErrors = passwordErrors - "currentPassword" }, Copy.account.currentPassword, password = true, error = passwordErrors["currentPassword"])
                Field(next, { next = it; passwordErrors = passwordErrors - "newPassword" }, Copy.account.newPassword, password = true, supporting = Copy.account.newPasswordHint, error = passwordErrors["newPassword"])
                Field(repeat, { repeat = it; passwordErrors = passwordErrors - "repeat" }, Copy.account.repeatPassword, password = true, error = passwordErrors["repeat"])
                PrimaryButton(Copy.account.changePassword, Modifier.padding(top = 8.dp), busy = password.busy, enabled = current.isNotEmpty() && next.isNotEmpty()) {
                    if (next.length < 10) { passwordErrors = mapOf("newPassword" to Copy.account.newPasswordHint); return@PrimaryButton }
                    if (next != repeat) { passwordErrors = mapOf("repeat" to Copy.account.mismatch); return@PrimaryButton }
                    password.run {
                        model.call { changePassword(current, next) }
                        current = ""; next = ""; repeat = ""; passwordErrors = emptyMap()
                        model.toast(Copy.account.passwordChanged)
                    }
                }
            }
            if (model.notifications.offered) PushSection(model)
            SectionTitle(Copy.account.session)
            AppCard {
                InfoRow(Copy.account.server, ServerAddress.display(model.server))
                model.business?.let { InfoRow(Copy.account.role, Copy.nav.role(it.access.role)) }
                Text(Copy.account.theme, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * "Notifications on this phone", shown only when the brand and the server have
 * push. Off takes this phone's token back from the server; on asks for the
 * permission where the phone needs it, and a refusal points to the settings.
 */
@Composable
private fun PushSection(model: AppModel) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val state = model.notifications
    var permitted by remember { mutableStateOf(Push.permitted(context)) }
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        refused = !granted
        if (granted) state.register()
    }
    // Back from the phone's settings: the switch there may have changed.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = Push.permitted(context)
                if (now && !permitted) state.register()
                permitted = now
                if (now) refused = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Asked before and still not allowed (or switched off in the settings): only the settings can change it.
    val blocked = !state.off && !permitted && (refused || state.permissionAsked || !Push.needsPermission(context))

    SectionTitle(Copy.notifications.section)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(Copy.notifications.onThisPhone, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(Copy.notifications.onThisPhoneHint, style = MaterialTheme.typography.bodySmall, color = ext.muted, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(checked = !state.off, onCheckedChange = { on ->
                if (!on) { state.switchOff(); return@Switch }
                state.switchOn()
                if (Push.needsPermission(context)) {
                    state.markAsked()
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
        }
        AnimatedVisibility(blocked) {
            Column(Modifier.padding(top = 10.dp)) {
                Text(Copy.notifications.blocked(org.openturnly.mobile.Brand.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = {
                        context.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(Copy.notifications.openSettings) }
            }
        }
    }
}
