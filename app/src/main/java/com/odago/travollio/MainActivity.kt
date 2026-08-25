package com.odago.travollio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.odago.travollio.data.SettingsPrefs
import com.odago.travollio.state.TripViewModel
import com.odago.travollio.ui.TravollioTheme
import com.odago.travollio.ui.screens.MainTabs

class MainActivity : ComponentActivity() {

    private val viewModel: TripViewModel by lazy {
        ViewModelProvider(this, TripViewModelFactory(application))[TripViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // True edge-to-edge so WindowInsets (IME, system bars) are delivered to Compose;
        // combined with windowSoftInputMode="adjustResize" the keyboard insets drive
        // imePadding() instead of panning the whole window ("text box flies up" bug).
        enableEdgeToEdge()
        setContent {
            // App-level dark/light preference (Settings → Appearance). Defaults to dark.
            var darkTheme by rememberSaveable {
                mutableStateOf(SettingsPrefs.isDarkTheme(application))
            }
            // Keep the Android system bar icons (status + nav) legible in the active mode.
            LaunchedEffect(darkTheme) {
                val ctl = WindowCompat.getInsetsController(window, window.decorView)
                ctl.isAppearanceLightStatusBars = !darkTheme
                ctl.isAppearanceLightNavigationBars = !darkTheme
            }
            TravollioTheme(darkTheme = darkTheme) {
                MainTabs(
                    viewModel = viewModel,
                    darkTheme = darkTheme,
                    onDarkThemeChange = { dark ->
                        darkTheme = dark
                        SettingsPrefs.setDarkTheme(this@MainActivity, dark)
                    }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }
                    .launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }
}
