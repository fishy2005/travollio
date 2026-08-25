package com.odago.travollio.data

import android.content.Context

/** Tiny persisted setting store for app-level preferences (SharedPreferences). */
object SettingsPrefs {
    private const val FILE = "travollio_settings"
    private const val KEY_DARK = "dark_theme"

    fun isDarkTheme(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true) // defaults to dark (locked design)

    fun setDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }
}
