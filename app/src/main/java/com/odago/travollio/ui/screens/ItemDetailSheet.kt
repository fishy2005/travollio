package com.odago.travollio.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Attachment
import com.odago.travollio.data.Item
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.AccentTint
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.LocalTvColors
import com.odago.travollio.ui.TvColors
import com.odago.travollio.ui.components.KindDot
import com.odago.travollio.ui.components.SectionHeader
import com.odago.travollio.ui.formatLocal
import java.io.File

/**
 * The "moment dossier": everything the agent filed for one itinerary item —
 * venue, address, confirmation, link, cost, and evidence (attachments).
 * Tap an itinerary card to open it; attach more files straight from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: Item,
    state: AppState,
    onDismiss: () -> Unit,
    onAttach: (Item, List<Uri>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val attachments = state.attachments.filter { it.itemId == item.id }
    val ctx = LocalContext.current
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) onAttach(item, uris) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Hairline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            // Kind + time eyebrow
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindDot(kind = item.kind)
                Spacer(Modifier.size(8.dp))
                Text(
                    item.kind.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                Spacer(Modifier.size(12.dp))
                item.timeLocal?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.title, style = MaterialTheme.typography.headlineLarge, color = InkPrimary)

            item.place?.let {
                Spacer(Modifier.height(10.dp))
                DossierRow(icon = { Icon(Icons.Filled.Place, null, tint = AccentDeep, modifier = Modifier.size(18.dp)) }) {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
                }
            }
            item.address?.let {
                DossierRow(icon = {}) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                }
            }
            item.detail.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
            }
            item.confirmation?.let {
                DossierRow(icon = { Icon(Icons.Filled.ConfirmationNumber, null, tint = AccentDeep, modifier = Modifier.size(18.dp)) }) {
                    Text("Confirmation: $it", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                }
            }
            item.link?.let { url ->
                DossierRow(
                    icon = { Icon(Icons.Filled.Link, null, tint = AccentDeep, modifier = Modifier.size(18.dp)) },
                    onClick = { openUrl(ctx, url) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Open link", style = MaterialTheme.typography.bodyMedium, color = AccentDeep)
                        Spacer(Modifier.size(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AccentDeep, modifier = Modifier.size(14.dp))
                    }
                }
            }
            item.costLocal?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    formatLocal(it, item.ccy),
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentDeep
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Hairline, thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))

            // ── Evidence ──
            CompositionLocalProvider(
                LocalTvColors provides LocalTvColors.current.copy(textTertiary = AccentDeep)
            ) {
                SectionHeader(if (attachments.isEmpty()) "Attachments" else "Attachments · ${attachments.size}")
            }
            if (attachments.isEmpty()) {
                Text(
                    "Nothing filed yet. Add a receipt, ticket or booking photo and it stays with this moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary
                )
            } else {
                attachments.forEach { a ->
                    AttachmentRow(a = a, onClick = { openAttachment(ctx, a) })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { attachLauncher.launch("*/*") },
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDeep)
            ) {
                Icon(Icons.Filled.AttachFile, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add photo or file")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Want to change this? Tell Travollio — e.g. \"move the massage to 15:00\".",
                style = MaterialTheme.typography.bodySmall,
                color = InkTertiary
            )
        }
    }
}

@Composable
private fun DossierRow(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 8.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Spacer(Modifier.size(8.dp))
        content()
    }
}

@Composable
private fun AttachmentRow(a: Attachment, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        if (a.mimeType.startsWith("image/")) {
            AttachmentThumb(path = a.filePath)
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentTint,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Description, null, tint = AccentDeep, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = InkPrimary)
            a.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = InkTertiary, modifier = Modifier.size(16.dp))
    }
}

/** Sampled thumbnail from the app-private file — no Coil needed for a handful of images. */
@Composable
private fun AttachmentThumb(path: String) {
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp)
            )
        } else {
            Surface(shape = RoundedCornerShape(10.dp), color = AccentTint, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Description, null, tint = AccentDeep, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Open an attachment via FileProvider — scoped grant, never world-readable. */
private fun openAttachment(ctx: Context, a: Attachment) {
    try {
        val file = File(a.filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, a.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, a.fileName))
    } catch (_: Exception) {
        // no viewer installed / provider issue — swallow, it's a demo convenience
    }
}

private fun openUrl(ctx: Context, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}
