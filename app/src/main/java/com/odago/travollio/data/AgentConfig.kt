package com.odago.travollio.data

import com.odago.travollio.BuildConfig

/**
 * Agent configuration, injected at build time from gemini.local.properties (gitignored).
 * The key never appears in source or git; it is baked into the APK at build time.
 * DeepSeek (OpenAI-compatible) is the primary cloud agent; Gemini is kept as a fallback.
 * When the cloud key is absent, the app runs the deterministic local engine.
 */
object AgentConfig {

    val apiKey: String get() = BuildConfig.GEMINI_API_KEY
    val model: String get() = BuildConfig.GEMINI_MODEL

    val deepSeekApiKey: String get() = BuildConfig.DEEPSEEK_API_KEY
    val deepSeekModel: String get() = BuildConfig.DEEPSEEK_MODEL
    val deepSeekBaseUrl: String get() = BuildConfig.DEEPSEEK_BASE_URL

    val isConfigured: Boolean get() = deepSeekApiKey.isNotBlank()
}
