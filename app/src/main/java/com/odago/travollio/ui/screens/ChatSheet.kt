package com.odago.travollio.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.odago.travollio.data.ChatMessage
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.AccentTint
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.Sage
import com.odago.travollio.ui.components.pressScale
import com.odago.travollio.ui.components.springSnappy
import com.odago.travollio.ui.tvColors

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
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Assistant", style = MaterialTheme.typography.headlineMedium, color = InkPrimary)
                Box(Modifier.weight(1f))
                AgentStatusPill()
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
            Text(
                "Your Travollio AI — ask it to plan, change, price or file anything.",
                style = MaterialTheme.typography.labelSmall,
                color = InkTertiary
            )
            // Persistent diagnostic line: shows exactly what happened on the last send so a
            // silent failure is impossible to miss. It only appears when there's a problem.
            if (lastOutcome != null) {
                Text(
                    "⚠ $lastOutcome",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
                item(key = "welcome") { WelcomeHint() }
            }
            items(state.chat, key = { it.id }) { msg ->
                ChatBubble(msg = msg, onGoto = onGoto)
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
private fun WelcomeHint() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 8.dp)
    ) {
        Text(
            "Plan with your AI assistant",
            style = MaterialTheme.typography.headlineSmall,
            color = InkPrimary
        )
        Text(
            "Try: \"Move the massage to 15:00\", \"What's my budget pace?\", \"Add a sushi dinner on day 3\", or \"Summarize day 2\". You can also attach a receipt or screenshot.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
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

/** Chat bubble: user + assistant both read on the bot's frosted glass so text stays legible
 *  in both themes (the user message is no longer dark-text-on-a-dark-accent pill). The user
 *  bubble is told apart by a teal accent ring; the assistant is plain glass. */
@Composable
private fun ChatBubble(msg: ChatMessage, onGoto: (String) -> Unit) {
    val isUser = msg.role == "user"
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = shape,
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(1.dp, if (isUser) Accent.copy(alpha = 0.55f) else MaterialTheme.tvColors.glassBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                markdownAnnotated(msg.text),
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary
            )
            // Attachments the user sent with this message (from cardJson)
            msg.cardJson?.let { json ->
                attachmentNames(json).forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = if (isUser) Accent else InkSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) Accent else InkSecondary,
                            maxLines = 1
                        )
                    }
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
                            .background(AccentTint)
                            .clickable { onGoto(msg.actionJson) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AccentDeep,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = AccentDeep)
                    }
                }
            }
        }
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

/** File names from a chat message's cardJson ([{"id":1,"name":"receipt.jpg"}]). */
private fun attachmentNames(cardJson: String): List<String> = try {
    val arr = org.json.JSONArray(cardJson)
    (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("name") }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 6.dp, bottomEnd = 18.dp
        ),
        color = MaterialTheme.tvColors.glassSurface
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

/** "…" thinking bubble with three staggered-pulse dots (spring-free — pure alpha pulse). */
@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val dots = listOf(0, 220, 440)
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Attach button — toggles the source picker bubble (camera / gallery / document).
            IconButton(onClick = { attachOpen = !attachOpen }) {
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
                placeholder = { Text("Ask about budget, plan, docs…", color = InkTertiary) },
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentDeep,
                    unfocusedBorderColor = Hairline,
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
                if (canSend) Brush.verticalGradient(listOf(AccentDeep, Accent)) else SolidColor(InkTertiary.copy(alpha = 0.5f))
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
                        tint = InkPrimary
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.5.dp, Accent.copy(alpha = 0.35f)),
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

/** Compact pill that just fills the composer when tapped. */
@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AccentTint,
        modifier = Modifier.pressScale()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AccentDeep, maxLines = 1)
        }
    }
}
