package app.pwhs.universalinstaller.base

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.universalinstaller.presentation.setting.ThemeMode
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

abstract class BaseActivity : ComponentActivity() {

    protected data class AppThemeState(
        val mode: ThemeMode,
        val dynamicColor: Boolean,
        val amoledMode: Boolean
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove standard sliding window transitions to mimic Compose navigation speed
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    protected fun setContentWithTheme(content: @Composable () -> Unit) {
        enableEdgeToEdge()
        setContent {
            val initialState = remember {
                kotlinx.coroutines.runBlocking {
                    val prefs = dataStore.data.first()
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                    val dynamicColor = prefs[booleanPreferencesKey("dynamic_color")] ?: true
                    val amoledMode = prefs[booleanPreferencesKey("amoled_mode")] ?: false
                    AppThemeState(mode, dynamicColor, amoledMode)
                }
            }
            
            val themeStateFlow = remember {
                dataStore.data.map { prefs ->
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                    val dynamicColor = prefs[booleanPreferencesKey("dynamic_color")] ?: true
                    val amoledMode = prefs[booleanPreferencesKey("amoled_mode")] ?: false
                    AppThemeState(mode, dynamicColor, amoledMode)
                }
            }
            val themeState by themeStateFlow.collectAsState(initial = initialState)

            val darkTheme = when (themeState.mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
                onDispose {}
            }

            UniversalInstallerTheme(
                darkTheme = darkTheme,
                dynamicColor = themeState.dynamicColor,
                amoledMode = themeState.amoledMode
            ) {
                content()
            }
        }
    }
}
