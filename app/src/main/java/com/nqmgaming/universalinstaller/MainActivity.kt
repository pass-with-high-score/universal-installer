package com.nqmgaming.universalinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nqmgaming.universalinstaller.app.UniversalInstallerApp
import com.nqmgaming.universalinstaller.presentation.setting.ThemeMode
import com.nqmgaming.universalinstaller.presentation.setting.dataStore
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeModeFlow = dataStore.data.map { prefs ->
                val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
            }
            val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.System)

            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            UniversalInstallerTheme(darkTheme = darkTheme) {
                UniversalInstallerApp()
            }
        }
    }
}
