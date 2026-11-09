@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openturnly.mobile.api.ApiClient
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Names
import org.openturnly.mobile.rules.Phones

/** A white (or raised dark) card with the app's 16dp corners and a hairline border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val ext = LocalExtended.current
    val shape = MaterialTheme.shapes.medium
    val inner: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(padding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = ext.card, border = BorderStroke(1.dp, ext.cardBorder), shadowElevation = if (ext.dark) 0.dp else 0.5.dp) { inner() }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = ext.card, border = BorderStroke(1.dp, ext.cardBorder), shadowElevation = if (ext.dark) 0.dp else 0.5.dp) { inner() }
    }
}

/** A pill with a tinted background and strong text: statuses and badges. */
@Composable
fun Chip(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(tone.container).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tone.content, modifier = Modifier.size(13.dp))
        Text(text, color = tone.content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Round initials, tinted by the name (or by a given colour), or the person's photo. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color? = null, photoUrl: String? = null) {
    val ext = LocalExtended.current
    val tint = color ?: ext.avatar(name)
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (ext.dark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Names.initials(name), color = tint, fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp, maxLines = 1,
        )
        if (photoUrl != null) RemoteImage(photoUrl, Modifier.size(size).clip(CircleShape))
    }
}

/** A small rounded square with an icon in it: the leading element of settings rows. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val ext = LocalExtended.current
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = if (ext.dark) 0.2f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtended.current.muted,
            letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Label on the left, value on the right: the detail screens' rows. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, strong: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = LocalExtended.current.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
        Text(
            value, color = valueColor, style = if (strong) MaterialTheme.typography.titleMedium.tabular else MaterialTheme.typography.bodyMedium.tabular,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = LocalExtended.current.cardBorder)

/** A row that opens something: icon, title, optional subtitle and value, chevron. */
@Composable
fun NavRow(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconBadge(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtended.current.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted)
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LocalExtended.current.subtle)
    }
}

/** Friendly empty or failed state: an icon in a soft circle and one line. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalExtended.current.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A load that failed: «No se pudo cargar», what went wrong in the server's words, and a retry. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    EmptyState(Icons.Outlined.WifiOff, Copy.common.loadFailed, message.takeIf { it != Copy.common.loadFailed }, modifier, Copy.common.retry, onRetry)

/** A tinted line above a form or a screen: a session that ended, an appointment that is gone. */
@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, tone: Tone = LocalExtended.current.warning) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tone.container).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tone.content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tone.content, modifier = Modifier.weight(1f))
    }
}

/** A soft pulsing block where content is about to appear. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    val ext = LocalExtended.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shift")
    val base = ext.skeleton
    val highlight = if (ext.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val brush = Brush.linearGradient(
        colors = listOf(base, lerpColor(base, highlight), base),
        start = Offset(-400f + 1200f * shift, 0f), end = Offset(-100f + 1200f * shift, 0f),
    )
    Box(modifier.clip(shape).background(brush))
}

private fun lerpColor(a: Color, b: Color): Color = androidx.compose.ui.graphics.lerp(a, b.copy(alpha = 1f), b.alpha)

/** Card-shaped skeletons for a list that is loading. */
@Composable
fun SkeletonCards(count: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(40.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Skeleton(Modifier.fillMaxWidth(0.6f).height(14.dp))
                        Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                    }
                }
            }
        }
    }
}

/** The pushed screens' frame: a back arrow, a title, actions, and the content. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val behavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Copy.common.back) } },
                actions = actions,
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floating,
        content = content,
    )
}

/** A full-width primary button that shows progress while its work runs. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(), onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !busy, colors = colors,
        modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
    ) {
        // The button greys out while it works; the spinner takes the greyed text's colour so it shows.
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** A labelled text field with the server's (or the form's) error under it. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    password: Boolean = false,
    prefix: String? = null,
    suffix: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = LocalExtended.current.subtle) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            capitalization = if (keyboard == KeyboardType.Email || keyboard == KeyboardType.Uri || password) KeyboardCapitalization.None else capitalization,
            imeAction = imeAction, autoCorrectEnabled = keyboard == KeyboardType.Text && !password,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        singleLine = singleLine, minLines = minLines, enabled = enabled, readOnly = readOnly,
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        prefix = prefix?.let { { Text(it) } }, suffix = suffix?.let { { Text(it) } },
        leadingIcon = leading,
        trailingIcon = if (password) {
            {
                IconButton({ reveal = !reveal }) {
                    Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (reveal) Copy.signIn.hidePassword else Copy.signIn.showPassword)
                }
            }
        } else trailing,
        shape = RoundedCornerShape(14.dp),
    )
}

/** A yes/no question before something that can't be taken back. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = Copy.common.cancel,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/** Call, WhatsApp and email as wide tinted buttons with a caption, the same as on the iPhone. */
@Composable
fun ContactActions(phone: String?, email: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ext = LocalExtended.current
    val mailColor = if (ext.dark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (phone != null) {
            ContactAction(Icons.Filled.Phone, Copy.common.call, MaterialTheme.colorScheme.primary) { context.dial(Phones.dialUri(phone)) }
            ContactAction(Icons.AutoMirrored.Filled.Chat, Copy.common.whatsapp, ext.whatsapp) { context.openUrl(Phones.whatsappUrl(phone)) }
        }
        if (email != null) ContactAction(Icons.Filled.Email, Copy.common.email, mailColor) { context.email(email) }
    }
}

@Composable
private fun RowScope.ContactAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).heightIn(min = 62.dp).clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (LocalExtended.current.dark) 0.2f else 0.11f))
            .clickable(onClickLabel = label, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = color, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Opens another app (dialer, WhatsApp, mail, browser) without crashing when none is there. */
fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (error: ActivityNotFoundException) { false }

fun Context.openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
fun Context.copyText(text: String) {
    getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText(text, text))
}
fun Context.dial(uri: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
fun Context.email(address: String) = launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
fun Context.shareText(text: String, title: String? = null) =
    launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))

/** Public images (logos, staff photos), cached in memory for the session. */
object ImageCache {
    private val cache = LruCache<String, ImageBitmap>(48)
    fun get(url: String): ImageBitmap? = cache.get(url)
    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val bytes = runCatching { ApiClient.bytes(url) }.getOrNull() ?: return null
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return null
        cache.put(url, bitmap)
        return bitmap
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(url) { mutableStateOf(ImageCache.get(url)) }
    LaunchedEffect(url) { if (bitmap == null) bitmap = ImageCache.load(url) }
    bitmap?.let { Image(it, null, modifier, contentScale = contentScale) }
}

/** The brand's turning clock while the saved session is checked, centred where the splash drew its icon. */
@Composable
fun BrandLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ClockLoader() }
}
