package com.odago.travollio.data

import android.content.Context

/** The persisted agent connection (provider + endpoint). */
data class AgentSettings(
    val provider: AgentProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** Tiny persisted setting store for app-level preferences (SharedPreferences). */
object SettingsPrefs {
    private const val FILE = "travollio_settings"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_AGENT_PROVIDER = "agent_provider"
    private const val KEY_AGENT_BASEURL = "agent_base_url"
    private const val KEY_AGENT_KEY = "agent_api_key"
    private const val KEY_AGENT_MODEL = "agent_model"

    fun isDarkTheme(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true) // defaults to dark (locked design)

    fun setDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }

    /** The last-selected agent provider, defaulting to DeepSeek (the baked config). */
    fun agentProvider(context: Context): AgentSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val prov = runCatching { AgentProvider.valueOf(p.getString(KEY_AGENT_PROVIDER, null) ?: "") }
            .getOrDefault(AgentProvider.DEEPSEEK)
        return AgentSettings(
            provider = prov,
            baseUrl = p.getString(KEY_AGENT_BASEURL, "") ?: "",
            apiKey = p.getString(KEY_AGENT_KEY, "") ?: "",
            model = p.getString(KEY_AGENT_MODEL, "") ?: ""
        )
    }

    fun setAgent(context: Context, provider: AgentProvider, baseUrl: String, apiKey: String, model: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AGENT_PROVIDER, provider.name)
            .putString(KEY_AGENT_BASEURL, baseUrl)
            .putString(KEY_AGENT_KEY, apiKey)
            .putString(KEY_AGENT_MODEL, model)
            .apply()
    }
}
