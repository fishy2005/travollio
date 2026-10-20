package com.odago.travollio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.odago.travollio.BuildConfig
import com.odago.travollio.data.AgentConfig
import com.odago.travollio.data.AgentProvider
import com.odago.travollio.data.SettingsPrefs
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Engagement
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.Sage
import com.odago.travollio.ui.tvColors
import com.odago.travollio.update.Updater
import com.odago.travollio.update.UpdateInfo
import com.odago.travollio.update.CheckResult
import com.odago.travollio.ui.components.GlassCard
import com.odago.travollio.ui.components.SectionHeader
import java.io.File
import kotlinx.coroutines.launch

private enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, ERROR }

/** Settings: appearance, build info and the in-app updater ("scan for updates & install"). */
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    state: com.odago.travollio.data.AppState = com.odago.travollio.data.AppState.empty()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(Phase.IDLE) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var apk by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun check() {
        phase = Phase.CHECKING
        error = null
        note = null
        scope.launch {
            when (val r = Updater.check()) {
                is CheckResult.Newer -> {
                    info = r.info; apk = null; phase = Phase.AVAILABLE
                }
                is CheckResult.Current -> {
                    info = r.info; apk = null; phase = Phase.UP_TO_DATE
                }
                CheckResult.UpToDate -> {
                    info = null; apk = null; phase = Phase.UP_TO_DATE
                }
                is CheckResult.Unreachable -> {
                    info = null; apk = null
                    note = "Couldn't reach GitHub right now — see if you're online."
                    phase = Phase.UP_TO_DATE
                }
            }
        }
    }

    fun download() {
        val target = info ?: return
        phase = Phase.DOWNLOADING
        progress = 0
        scope.launch {
            try {
                val f = Updater.download(context, target) { p -> progress = p }
                apk = f
                phase = Phase.DOWNLOADED
            } catch (e: Exception) {
                error = e.message ?: "Download failed."
                phase = Phase.ERROR
            }
        }
    }

    fun install() {
        val f = apk ?: return
        if (Updater.canInstall(context)) {
            Updater.install(context, f)
        } else {
            Updater.requestInstallPermission(context)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.displayMedium,
                color = InkPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Updates, build info, and this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }

        item { SectionHeader("Explorer") }
        item { ExplorerCard(state) }

        item { SectionHeader("Updates") }

        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateStatusRow(phase, info, progress)

                    when (phase) {
                        Phase.CHECKING -> { /* inline spinner shown above */ }
                        Phase.UP_TO_DATE, Phase.IDLE -> {
                            Button(
                                onClick = ::check,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (phase == Phase.UP_TO_DATE) "Check again" else "Scan for updates")
                            }
                        }
                        Phase.AVAILABLE -> {
                            Button(
                                onClick = ::download,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Download v${info?.versionName}")
                            }
                            TextButton(onClick = ::check, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch latest list again")
                            }
                        }
                        Phase.DOWNLOADING -> Unit
                        Phase.DOWNLOADED -> {
                            Button(
                                onClick = ::install,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install v${info?.versionName}")
                            }
                            Text(
                                "Tap Install on the system screen to finish. The app will update in place.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        Phase.ERROR -> {
                            Button(
                                onClick = { if (info != null) download() else check() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Try again")
                            }
                        }
                    }

                    note?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.tvColors.textTertiary, modifier = Modifier.size(14.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    }

                    error?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Appearance") }
        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Dark mode", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                        Text(
                            "Off for the light theme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedThumbColor = InkSecondary,
                            uncheckedTrackColor = MaterialTheme.tvColors.hairline,
                            uncheckedBorderColor = MaterialTheme.tvColors.hairline
                        )
                    )
                }
            }
        }

        item { SectionHeader("AI Assistant") }
        item { AiSettingsCard() }

        item { SectionHeader("Live data") }
        item { LiveDataCard() }

        item { SectionHeader("About") }
        item {
            GlassCard(fill = MaterialTheme.colorScheme.primaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Travollio", style = MaterialTheme.typography.titleSmall, color = AccentDeep)
                    VersionRow("Installed", "${Updater.currentVersionName}  (code ${Updater.currentVersionCode})")
                    VersionRow("Channel", "GitHub Releases")
                    VersionRow("Feed", "${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}")
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusRow(phase: Phase, info: UpdateInfo?, progress: Int) {
    when (phase) {
        Phase.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
            Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        }
        Phase.UP_TO_DATE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Sage, modifier = Modifier.size(18.dp))
            Column {
                Text("You're up to date (v${Updater.currentVersionName}).", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                Text("No newer build published.", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        Phase.AVAILABLE -> {
            val i = info
            if (i != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Text(
                        "Update available — v${i.versionName} (code ${i.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                }
                if (i.notes.isNotBlank()) {
                    Text(i.notes, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
        }
        Phase.DOWNLOADING -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Downloading v${info?.versionName}…", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = Accent,
                    trackColor = MaterialTheme.tvColors.hairline,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Text("$progress%", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        Phase.DOWNLOADED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            Text("Ready to install v${info?.versionName}.", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
        }
        Phase.IDLE, Phase.ERROR -> {
            Text(
                "Scan to see if a newer build than v${Updater.currentVersionName} is available.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.labelMedium, color = InkPrimary)
    }
}

/**
 * AI Assistant connection settings: pick a provider (OpenAI-compatible, incl. LM Studio /
 * Ollama / custom), enter the API key + model, test the connection, and save.
 * Persists via [AgentConfig.save]; the live agent (GeminiAgent) picks these up on next message.
 */
@Composable
private fun AiSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf(AgentConfig.provider) }
    var baseUrl by remember { mutableStateOf(AgentConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(AgentConfig.apiKey) }
    var model by remember { mutableStateOf(AgentConfig.model) }
    var vision by remember { mutableStateOf(AgentConfig.vision) }
    var showProvider by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun onProviderSelect(p: AgentProvider) {
        provider = p
        if (p != AgentProvider.CUSTOM) {
            baseUrl = p.baseUrl
            model = p.defaultModel
        }
        testResult = null
        saved = false
    }

    fun save() {
        AgentConfig.provider = provider
        AgentConfig.baseUrl = baseUrl
        AgentConfig.apiKey = apiKey
        AgentConfig.model = model
        AgentConfig.vision = vision
        AgentConfig.save(context)
        saved = true
        testResult = null
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI model", style = MaterialTheme.typography.titleSmall, color = AccentDeep)

            // Provider picker (open-code-style: choose a provider, or custom endpoint).
            Box {
                Surface(
                    onClick = { showProvider = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Provider", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                            Text(provider.label, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = InkSecondary)
                    }
                }
                DropdownMenu(expanded = showProvider, onDismissRequest = { showProvider = false }) {
                    AgentProvider.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text((if (p.isLocal) "● " else "") + p.label, color = InkPrimary) },
                            onClick = { onProviderSelect(p); showProvider = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it; saved = false },
                label = { Text("Base URL") }, singleLine = true
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it; saved = false },
                label = { Text("Model") }, singleLine = true
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it; saved = false },
                label = { Text(if (provider.needsApiKey) "API key" else "API key (optional)") },
                singleLine = true,
                visualTransformation = if (apiKey.isEmpty()) VisualTransformation.None else PasswordVisualTransformation()
            )

            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Send images (vision)", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                    Text(
                        "Needs a vision-capable model (e.g. a VL variant).",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = vision,
                    onCheckedChange = { vision = it; saved = false },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Accent,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedThumbColor = InkSecondary,
                        uncheckedTrackColor = MaterialTheme.tvColors.hairline,
                        uncheckedBorderColor = MaterialTheme.tvColors.hairline
                    )
                )
            }

            if (provider.isLocal) {
                Text(
                    "Local model — set the Base URL to this computer's LAN address, e.g. http://192.168.1.5:1234/v1",
                    style = MaterialTheme.typography.bodySmall, color = InkSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::save) { Text("Save", color = AccentDeep) }
                TextButton(
                    onClick = {
                        testing = true; testResult = null; saved = false
                        scope.launch {
                            testResult = AgentConfig.test(baseUrl, apiKey, model)
                            testing = false
                        }
                    },
                    enabled = !testing
                ) { Text(if (testing) "Testing…" else "Test connection", color = AccentDeep) }
                if (!provider.needsApiKey) {
                    TextButton(onClick = { AgentConfig.reset(context); provider = AgentConfig.provider; baseUrl = AgentConfig.baseUrl; apiKey = AgentConfig.apiKey; model = AgentConfig.model; vision = AgentConfig.vision; saved = true; testResult = null }) {
                        Text("Reset", color = InkSecondary)
                    }
                }
            }

            if (saved) Text("Saved ✓", style = MaterialTheme.typography.bodySmall, color = Sage)
            testResult?.let { r ->
                val ok = r.startsWith("Connected")
                Text(r, style = MaterialTheme.typography.bodySmall, color = if (ok) Sage else InkSecondary)
            }
        }
    }
}

/**
 * Live on-phone data: optional OpenWeatherMap key for forecasts (weather_get,
 * daily weather alerts). Blank = offline; everything else keeps working.
 * Free key: openweathermap.org → Sign up → API keys.
 */
@Composable
private fun LiveDataCard() {
    val context = LocalContext.current
    var key by remember { mutableStateOf(SettingsPrefs.weatherApiKey(context)) }
    var saved by remember { mutableStateOf(false) }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Weather", style = MaterialTheme.typography.titleSmall, color = AccentDeep)
            Text(
                "Powers live forecasts and rain alerts. Free key from openweathermap.org.",
                style = MaterialTheme.typography.bodySmall, color = InkSecondary
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it; saved = false },
                label = { Text("OpenWeatherMap key (optional)") }, singleLine = true,
                visualTransformation = if (key.isEmpty()) VisualTransformation.None else PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    SettingsPrefs.setWeatherApiKey(context, key)
                    saved = true
                }) { Text("Save", color = AccentDeep) }
            }
            if (saved) Text("Saved ✓", style = MaterialTheme.typography.bodySmall, color = Sage)
        }
    }
}

/** Explorer progress card: XP, level bar, streak — the lifetime "how far along are you" readout. */
@Composable
private fun ExplorerCard(state: com.odago.travollio.data.AppState) {
    val xp = Engagement.xp(state)
    val level = Engagement.level(xp)
    val nextAt = Engagement.xpForNextLevel(level)
    val prevAt = if (level > 1) Engagement.xpForNextLevel(level - 1) else 0
    val ratio = (((xp - prevAt).coerceAtLeast(0)).toFloat() / (nextAt - prevAt).coerceAtLeast(1)).coerceIn(0f, 1f)
    val streak = Engagement.streak(state)

    GlassCard(cornerRadius = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("EXPLORER", style = MaterialTheme.typography.labelSmall, color = AccentDeep)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level $level",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$xp XP",
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentDeep
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99))
                    .background(MaterialTheme.tvColors.hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(7.dp)
                        .clip(RoundedCornerShape(99))
                        .background(Accent)
                )
            }
            Text(
                "${(nextAt - xp).coerceAtLeast(0)} XP to level ${level + 1}" +
                    if (streak > 0) " · ${streak}d streak" else " · no streak yet — tick something off today",
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary
            )
            Text(
                "XP: tick a moment +15 · log a spend +3 · save a photo +10 · complete a trip +60",
                style = MaterialTheme.typography.bodySmall,
                color = InkTertiary
            )
        }
    }
}
