package com.odago.travollio.data

import android.content.Context
import com.odago.travollio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A selectable agent provider. All are OpenAI-compatible chat-completions
 * endpoints (the Travollio tool-calling loop speaks that wire format), so picking
 * a provider only needs to swap the base URL + model + API key. LM Studio and
 * Ollama run locally (no key required); "Custom" lets the user type any OpenAI-
 * compatible endpoint.
 */
enum class AgentProvider(
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val needsApiKey: Boolean
) {
    LM_STUDIO("LM Studio", "http://localhost:1234/v1", "local-model", false),
    OLLAMA("Ollama", "http://localhost:11434/v1", "llama3.2", false),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", true),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", true),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true),
    TOGETHER("Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", true),
    CUSTOM("Custom", "", "", true);

    val isLocal: Boolean get() = !needsApiKey
}

/**
 * The active agent connection, user-configurable at runtime (Settings → AI Assistant).
 *
 * Defaults to the build-time-baked DeepSeek config so the app works out of the box;
 * once the user configures a provider, their choice is persisted and takes precedence.
 * All fields are volatile because reads happen on worker threads (coroutines) while the
 * UI thread writes them.
 */
object AgentConfig {

    @Volatile var provider: AgentProvider = AgentProvider.DEEPSEEK
    @Volatile var baseUrl: String = BuildConfig.DEEPSEEK_BASE_URL
    @Volatile var apiKey: String = BuildConfig.DEEPSEEK_API_KEY
    @Volatile var model: String = BuildConfig.DEEPSEEK_MODEL
    @Volatile var vision: Boolean = false

    /** Restore the persisted config (falls back to the baked defaults). Call once at startup. */
    fun load(context: Context) {
        val p = SettingsPrefs.agentProvider(context)
        provider = p.provider
        baseUrl = if (p.baseUrl.isNotBlank()) p.baseUrl else p.provider.baseUrl.ifBlank { BuildConfig.DEEPSEEK_BASE_URL }
        apiKey = p.apiKey.ifBlank { BuildConfig.DEEPSEEK_API_KEY }
        model = p.model.ifBlank { p.provider.defaultModel.ifBlank { BuildConfig.DEEPSEEK_MODEL } }
        vision = p.vision
    }

    fun save(context: Context) {
        SettingsPrefs.setAgent(context, provider, baseUrl, apiKey, model, vision)
    }

    /** Reset to the build-time baked connection (DeepSeek). */
    fun reset(context: Context) {
        provider = AgentProvider.DEEPSEEK
        baseUrl = BuildConfig.DEEPSEEK_BASE_URL
        apiKey = BuildConfig.DEEPSEEK_API_KEY
        model = BuildConfig.DEEPSEEK_MODEL
        vision = false
        SettingsPrefs.setAgent(context, provider, baseUrl, apiKey, model, vision)
    }

    /** Ready to talk if it needs a key and has one, or is a local key-less provider. */
    val isConfigured: Boolean
        get() = !provider.needsApiKey || apiKey.isNotBlank()

    val isLocal: Boolean get() = provider.isLocal

    /** Short label for the assistant status pill, e.g. "LM Studio · online". */
    val label: String
        get() = provider.label + if (isConfigured) " · online" else " · offline"

    /**
     * Quick connectivity check against the given OpenAI-compatible endpoint.
     * Returns a human-readable result (never throws). Network happens on IO.
     */
    suspend fun test(baseUrl: String, apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        try {
            if (baseUrl.isBlank()) return@withContext "Enter a base URL first."
            val conn = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Travollio")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            val body = JSONObject()
                .put("model", model.ifBlank { "local-model" })
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                .put("max_tokens", 5)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                "Connected ✓ (HTTP $code)"
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(160) ?: "" }.getOrDefault("")
                "Failed (HTTP $code)${if (err.isNotBlank()) " — $err" else ""}"
            }
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }
}
