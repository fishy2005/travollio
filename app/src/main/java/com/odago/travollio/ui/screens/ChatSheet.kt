package com.odago.travollio.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.odago.travollio.agent.AgentEngine
import com.odago.travollio.data.AgentConfig
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Attachment
import com.odago.travollio.data.ChatMessage
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.AccentTint
import com.odago.travollio.ui.Blush
import com.odago.travollio.ui.BlushTint
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.Sage
import com.odago.travollio.ui.components.pressScale
import com.odago.travollio.ui.components.springSnappy
import com.odago.travollio.ui.tvColors

/** Terracotta brush used for Pablo's identity surfaces (header orb, user bubbles). */
private fun accentBrushChat(): Brush =
    Brush.verticalGradient(listOf(Color(0xFFD06837), Color(0xFF9C4520)))

/**
 * The AI assistant as a full-screen, first-class part of the app (like a chat app).
 * Lives on its own tab. Conversational, with attachments (photos/screenshots/receipts)
 * and a live "thinking" state. No longer hidden behind a small orb.
 */
@Composable
fun AssistantScreen(
    state: AppState,
    isThinking: Boolean,
    lastOutcome: String?,
    streamingText: String,
    onSend: (String, List<Uri>) -> Unit,
    onGoto: (String) -> Unit,
    onReset: () -> Unit
) {
    val listState = rememberLazyListState()
    var showResetDialog by remember { mutableStateOf(false) }
    val isStreaming = streamingText.isNotEmpty()

    // Keep the newest message in view: re-anchors when a message lands, when the thinking
    // bubble appears, and when the keyboard opens (IME inset changes shrink the list).
    // While a reply is streaming we pin to the bottom (no animation — a fresh delta arrives
    // every few ms, so animating each one would be janky).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(state.chat.size, isThinking, imeBottom, isStreaming, streamingText.length) {
        if (isStreaming) {
            val target = (state.chat.lastIndex + 1).coerceAtLeast(0)
            listState.scrollToItem(target)
        } else if (state.chat.isNotEmpty()) {
            val target = (state.chat.lastIndex + if (isThinking) 1 else 0).coerceAtLeast(0)
            // jump when a big change (keyboard just opened), animate otherwise
            if (imeBottom > 0) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Header — Pablo's identity: gradient orb avatar, name, live status.
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accentBrushChat()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFDF3E7),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.size(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pablo", style = MaterialTheme.typography.headlineSmall, color = InkPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val online = AgentConfig.isConfigured
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (online) Sage else InkTertiary)
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            if (online) "Online · ${AgentConfig.label}" else "Offline — local agent",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTertiary
                        )
                    }
                }
                if (!AgentConfig.isConfigured) AgentStatusPill()
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Restart agent",
                        tint = InkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // Persistent diagnostic line: shows exactly what happened on the last send so a
            // silent failure is impossible to miss. It only appears when there's a problem.
            if (lastOutcome != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BlushTint,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        lastOutcome,
                        style = MaterialTheme.typography.bodySmall,
                        color = Blush,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = Hairline, thickness = 0.5.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.chat.isEmpty()) {
                item(key = "welcome") { WelcomeHint(state, onSend) }
            }
            items(state.chat, key = { it.id }) { msg ->
                ChatBubble(msg = msg, attachments = state.attachments, onGoto = onGoto)
            }
            // Live-streaming reply: render the partial text as an assistant bubble as it arrives.
            if (isStreaming) {
                item(key = "streaming") { StreamingBubble(streamingText) }
            } else if (isThinking) {
                item(key = "thinking") { ThinkingBubble() }
            }
        }

        HorizontalDivider(color = Hairline, thickness = 0.5.dp)
        ChatInput(state = state, isThinking = isThinking, onSend = onSend)
        Spacer(Modifier.height(10.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Restart agent?", color = InkPrimary) },
            text = {
                Text(
                    "This clears the whole conversation. The agent will no longer remember previous messages — it will only know what is already saved in your trip.",
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) { Text("Restart", color = AccentDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = InkSecondary) }
            }
        )
    }
}

/** Friendly empty state so a first visit isn't a blank wall. */
@Composable
private fun WelcomeHint(state: AppState, onSend: (String, List<Uri>) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = com.odago.travollio.ui.SoftShadowA, spotColor = com.odago.travollio.ui.SoftShadowB)
                .clip(RoundedCornerShape(20.dp))
                .background(accentBrushChat()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFDF3E7),
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Hey, I'm Pablo",
            style = MaterialTheme.typography.displaySmall,
            color = InkPrimary
        )
        Text(
            "Your travel operator. I plan days, book ideas into your itinerary, watch your budget and file your papers — just tell me.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        welcomeSuggestions(state).forEach { (icon, title, prompt) ->
            WelcomeSuggestion(icon, title, prompt) { onSend(prompt, emptyList()) }
        }
    }
}

/** Context-aware starter cards on the welcome hero — one tap sends straight to Pablo. */
private fun welcomeSuggestions(state: AppState): List<Triple<ImageVector, String, String>> {
    if (state.trip == null) {
        return listOf(
            Triple(Icons.Filled.FlightTakeoff, "Plan a trip to Tokyo", "Plan a trip to Tokyo, 5 days, ¥120k budget. Start with days and sights."),
            Triple(Icons.Filled.Description, "Import my Word itinerary", "I'll attach my Word itinerary — create the trip from it."),
            Triple(Icons.Filled.Luggage, "What should I pack?", "What should I pack for my trip? Make me a packing list.")
        )
    }
    val cards = mutableListOf<Triple<ImageVector, String, String>>()
    cards += Triple(Icons.Filled.WbSunny, "Plan the rest of today", "Fill in a good plan for the rest of today — leave no dead hours.")
    cards += Triple(Icons.Filled.Luggage, "Generate my packing list", "Generate me a packing list for this trip.")
    cards += Triple(Icons.Filled.AccountBalanceWallet, "How's my budget?", "How's my budget pace right now?")
    return cards.take(3)
}

@Composable
private fun WelcomeSuggestion(icon: ImageVector, title: String, prompt: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(icon, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
        }
    }
}

@Composable
private fun AgentStatusPill() {
    val online = AgentConfig.isConfigured
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Text(
            AgentConfig.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (online) AccentDeep else Sage,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Chat bubble: user messages get the terracotta gradient and sit right;
 *  Pablo's replies are quiet glass cards anchored left with a loose "tail". */
@Composable
private fun ChatBubble(msg: ChatMessage, attachments: List<Attachment>, onGoto: (String) -> Unit) {
    val isUser = msg.role == "user"
    // Resolve sent attachments (from cardJson) against the live attachment store.
    val attachItems = msg.cardJson?.let { chatAttachmentItems(it) } ?: emptyList()
    val images = attachItems.mapNotNull { item -> attachments.firstOrNull { it.id == item.id && it.mimeType.startsWith("image/") } }
    val files = attachItems.filterNot { item -> images.any { it.id == item.id } }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val maxW = maxWidth * (if (isUser) 0.82f else 0.9f)
        val bubbleShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isUser) 20.dp else 7.dp,
            bottomEnd = if (isUser) 7.dp else 20.dp
        )
        Surface(
            shape = bubbleShape,
            color = if (isUser) Color.Transparent else MaterialTheme.tvColors.glassSurface,
            border = if (isUser) null else BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
            modifier = Modifier.widthIn(max = maxW)
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (isUser) Modifier.background(accentBrushChat(), bubbleShape)
                        else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // When the user only sent images (no caption), the "(attachment)" placeholder
                // text is meaningless against real thumbnails — hide it and let the images speak.
                val onAccent = MaterialTheme.colorScheme.onPrimary
                val showText = !(isUser && images.isNotEmpty() && msg.text == "(attachment)")
                if (showText) {
                    Text(
                        markdownAnnotated(msg.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) onAccent else InkPrimary
                    )
                }
                // Reply-side images render as a scrollable filmstrip; user tappable to open full.
                if (images.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = if (showText) 8.dp else 0.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        images.forEach { ChatImageThumb(it) }
                    }
                }
                // Non-image attachments: a labelled file chip.
                files.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = (if (isUser) onAccent else InkSecondary).copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (isUser) onAccent else InkSecondary).copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
                // Deep-link button: only on assistant turns that changed something.
                if (!isUser && msg.actionJson != null) {
                    val label = actionLabel(msg.actionJson)
                    if (label != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Accent)
                                .clickable { onGoto(msg.actionJson) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** 1:1 rounded thumbnail for an image the user sent, sample-downscaled for the list.
 *  Tapping opens the full-size image via FileProvider. */
@Composable
private fun ChatImageThumb(attachment: Attachment) {
    val ctx = LocalContext.current
    var bmp by remember(attachment.filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(attachment.filePath) {
        bmp = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(attachment.filePath, opts)
        }.getOrNull()
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(shape)
            .background(AccentTint)
            .clickable { openAttachment(ctx, attachment) },
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Icon(
            Icons.Filled.Photo,
            contentDescription = attachment.fileName,
            tint = AccentDeep,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** Open a sent attachment via FileProvider — scoped grant, never world-readable. */
private fun openAttachment(ctx: android.content.Context, a: Attachment) {
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

/** Human label for a deep-link action, or null if it shouldn't render a button. */
private fun actionLabel(actionJson: String): String? = try {
    val o = org.json.JSONObject(actionJson)
    when (o.optString("type")) {
        "item" -> "View change"
        "day" -> {
            val d = o.optInt("day", -1)
            if (d >= 1) "Open Day $d" else "View day"
        }
        "money" -> "Open budget"
        "trip" -> "View trip"
        else -> null
    }
} catch (_: Exception) {
    null
}

/** An attachment reference stored in a chat message's cardJson. */
private data class ChatAttachmentItem(val id: Long, val name: String)

/** Attachments referenced by a chat message's cardJson ([{"id":1,"name":"receipt.jpg"}]). */
private fun chatAttachmentItems(cardJson: String): List<ChatAttachmentItem> = try {
    val arr = org.json.JSONArray(cardJson)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optLong("id", -1L)
        if (id < 0) null else ChatAttachmentItem(id, o.optString("name"))
    }
} catch (_: Exception) {
    emptyList()
}

/**
 * Lightweight chat markdown: renders **bold**, `inline code`, `# / ## / ###`
 * headings, `- / * / •` bullets and `1.` numbered list lines as plain, readable
 * text (Compose has no markdown parser out of the box, so this keeps replies
 * looking tidy without a heavy dependency). Unknown tokens show verbatim.
 */
private fun markdownAnnotated(raw: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = raw.split("\n")
    lines.forEachIndexed { idx, full ->
        val line = full.trimEnd()
        if (line.isBlank()) {
            builder.append("\n")
            return@forEachIndexed
        }
        var prefix = ""
        var content = line
        var isBold = false
        when {
            Regex("^#{1,3}\\s+").containsMatchIn(line) -> {
                content = line.replace(Regex("^#{1,3}\\s+"), "")
                isBold = true
            }
            Regex("^[-*•]\\s+").containsMatchIn(line) -> {
                prefix = "   •  "
                content = line.replace(Regex("^[-*•]\\s+"), "")
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(line) -> {
                val m = Regex("^(\\d+[.)])\\s+").find(line)
                val num = m?.groupValues?.get(1) ?: ""
                prefix = "$num  "
                content = line.replace(Regex("^\\d+[.)]\\s+"), "")
            }
        }
        appendInline(builder, prefix, false)
        appendInline(builder, content, isBold)
        if (idx < lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

/** Append text, applying **bold** / `code` spans within it. */
private fun appendInline(builder: AnnotatedString.Builder, text: String, forceBold: Boolean) {
    if (text.isBlank()) { builder.append(text); return }
    var last = 0
    val regex = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
    for (m in regex.findAll(text)) {
        if (m.range.first > last) builder.append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") && token.endsWith("**") -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.substring(2, token.length - 2)) }
            token.startsWith("`") && token.endsWith("`") -> builder.withStyle(SpanStyle(background = Color(0x22000000))) { append(token.substring(1, token.length - 1)) }
            else -> builder.append(token)
        }
        last = m.range.last + 1
    }
    if (last < text.length) builder.append(text.substring(last))
}

/** Live partial assistant reply, rendered as an assistant-styled bubble with a soft
 *  "typing" caret so the user sees the reply forming in real time. */
@Composable
private fun StreamingBubble(text: String) {
    val caretAlpha by rememberInfiniteTransition(label = "streamCaret").animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700), repeatMode = RepeatMode.Reverse),
        label = "streamCaretAlpha"
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxW = maxWidth * 0.9f
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomStart = 7.dp, bottomEnd = 20.dp
            ),
            color = MaterialTheme.tvColors.glassSurface,
            border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
            modifier = Modifier.widthIn(max = maxW)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    Text(
                        markdownAnnotated(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                }
                Text("›", color = Accent.copy(alpha = caretAlpha), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "…" thinking bubble: Pablo's typing state lives in his own reply card. */
@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val dots = listOf(0, 220, 440)
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 7.dp, bottomEnd = 20.dp),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            dots.forEach { delay ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = delay),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$delay"
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun ChatInput(state: AppState, isThinking: Boolean, onSend: (String, List<Uri>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attachOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // Choose the source explicitly (Camera / Gallery / Document) so the right content
    // type lands in the agent: photos feed the vision path, PDF/Doc get text-extracted.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) pending = uris }
    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) pending = uris }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraUri?.let { pending = pending + it }; cameraUri = null }
    val takePhoto = {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val canSend = (text.isNotBlank() || pending.isNotEmpty()) && !isThinking

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        // Context-aware quick suggestions — tap to drop into the field (edit, then send —
        // handy when you also want to attach a Word itinerary).
        if (pending.isEmpty() && text.isBlank()) {
            val suggestions = suggestionChips(state)
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { s ->
                        SuggestionChip(label = s, onClick = {
                            text = s
                        })
                    }
                }
            }
        }
        // Pending attachment chips (shown once files are picked, before sending)
        if (pending.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pending.take(3).forEach { uri ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AccentTint,
                        modifier = Modifier.pressScale()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = null,
                                tint = AccentDeep,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(
                                uri.lastPathSegment?.substringAfterLast('/') ?: "file",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentDeep,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { pending = pending - uri },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = AccentDeep,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                if (pending.size > 3) {
                    Text(
                        "+${pending.size - 3}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkTertiary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // Attach source picker bubble: choose where the file comes from so the right
        // content type reaches the agent (photos → vision, PDF/Doc → text extraction).
        if (attachOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttachOption("Camera", Icons.Filled.PhotoCamera) { attachOpen = false; takePhoto() }
                AttachOption("Gallery", Icons.Filled.Photo) { attachOpen = false; galleryLauncher.launch("image/*") }
                AttachOption("PDF / Doc", Icons.Filled.Description) { attachOpen = false; docLauncher.launch("*/*") }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            // Attach button — toggles the source picker bubble (camera / gallery / document).
            IconButton(onClick = { attachOpen = !attachOpen }, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(
                    if (attachOpen) Icons.Filled.Close else Icons.Filled.AttachFile,
                    contentDescription = "Attach photo or file",
                    tint = if (attachOpen) Accent else InkSecondary
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Pablo…", color = InkTertiary) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.tvColors.glassSurface,
                    unfocusedContainerColor = MaterialTheme.tvColors.glassSurface,
                    cursorColor = AccentDeep,
                    focusedTextColor = InkPrimary,
                    unfocusedTextColor = InkPrimary
                )
            )
            Spacer(Modifier.size(8.dp))
            // Round send button; springs up when there is something to send
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.9f else if (canSend) 1f else 0.94f,
                animationSpec = springSnappy(),
                label = "sendScale"
            )
            val sendBrush =
                if (canSend) Brush.verticalGradient(listOf(Color(0xFFD06837), Color(0xFF9C4520)))
                else SolidColor(InkTertiary.copy(alpha = 0.5f))
            Surface(
                onClick = {
                    if (canSend) {
                        onSend(text.trim(), pending)
                        text = ""
                        pending = emptyList()
                    }
                },
                enabled = canSend,
                interactionSource = interaction,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(46.dp)
                    .background(sendBrush, CircleShape)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color(0xFFFDF3E7)
                    )
                }
            }
        }
    }
}

/** Context-aware short suggestions that drop into the input field. */
private fun suggestionChips(state: AppState): List<String> = buildList {
    if (state.trip == null) {
        add("Plan a trip: Tokyo, 5 days, ¥120k budget")
        add("Create my trip from a Word itinerary")
    } else {
        val today = java.time.LocalDate.now().toString()
        val onToday = state.days.any { it.date == today }
        if (onToday) add("What's today's plan?")
        if (onToday) add("Log today's lunch cost")
        add("Change a time in the plan")
        add("Archive this trip and start a new one")
        add("Show my spent budget so far")
        add("Import a Word itinerary into a new trip")
    }
}.take(5)

/** One tappable option in the attach-source bubble (camera / gallery / document). */
@Composable
private fun AttachOption(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
        modifier = Modifier.pressScale()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Icon(icon, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkPrimary)
        }
    }
}

/** Compact ghost chip that fills the composer when tapped. */
@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(0.75.dp, MaterialTheme.tvColors.hairline),
        modifier = Modifier.pressScale()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(12.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.tvColors.textSecondary, maxLines = 1)
        }
    }
}
