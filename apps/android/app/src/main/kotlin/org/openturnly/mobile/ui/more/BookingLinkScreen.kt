@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui.more

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.QrCode
import org.openturnly.mobile.ui.AppCard
import org.openturnly.mobile.ui.BusinessLogo
import org.openturnly.mobile.ui.DetailScaffold
import org.openturnly.mobile.ui.LocalExtended
import org.openturnly.mobile.ui.SectionTitle
import org.openturnly.mobile.ui.launch
import org.openturnly.mobile.ui.openUrl
import org.openturnly.mobile.ui.copyText
import org.openturnly.mobile.ui.shareText

/** The booking page to share with customers: its address, copy, share, open, and a QR code. */
@Composable
fun BookingLinkScreen(model: AppModel) {
    val ext = LocalExtended.current
    val context = LocalContext.current
    val business = model.business ?: return
    val link = "${model.server}/${business.slug}"
    val qr = remember(link) { QrCode.encode(link) }

    DetailScaffold(title = Copy.more.bookingLink, onBack = { model.nav.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BusinessLogo(model, business, 64.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(business.name, style = MaterialTheme.typography.headlineSmall)
                    business.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ext.muted, maxLines = 2) }
                }
            }

            SectionTitle(Copy.business.bookingLink)
            AppCard {
                Text(Copy.business.bookingLinkHint, style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                Text(
                    link, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)).padding(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ context.copyText(link); model.toast(Copy.business.linkCopied) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.common.copy, maxLines = 1)
                    }
                    FilledTonalButton({ context.shareText("${Copy.business.shareText} $link", business.name) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.common.share, maxLines = 1)
                    }
                    FilledTonalButton({ context.openUrl(link) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, Copy.common.open, Modifier.size(18.dp)) }
                }
            }

            if (qr != null) {
                SectionTitle(Copy.business.qrTitle)
                AppCard {
                    Text(Copy.business.qrHint, style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
                            QrView(qr, Modifier.fillMaxSize())
                        }
                    }
                    Text(business.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                    OutlinedButton({ shareQr(context, qr, business.name, link) }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Icon(Icons.Outlined.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Copy.business.shareQr)
                    }
                }
            }
        }
    }
}

/** The modules as squares, black on the white card. */
@Composable
fun QrView(qr: QrCode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / qr.size
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.isDark(x, y)) drawRect(Color(0xFF111111), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** Writes the code as a PNG with its quiet zone and hands it to the share sheet. */
private fun shareQr(context: Context, qr: QrCode, title: String, link: String) {
    val scale = 16
    val border = 4
    val side = (qr.size + border * 2) * scale
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.WHITE)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
    for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr.isDark(x, y)) {
        val left = ((x + border) * scale).toFloat(); val top = ((y + border) * scale).toFloat()
        canvas.drawRect(left, top, left + scale, top + scale, paint)
    }
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(folder, "booking-qr.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, link)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.launch(Intent.createChooser(send, title))
}
