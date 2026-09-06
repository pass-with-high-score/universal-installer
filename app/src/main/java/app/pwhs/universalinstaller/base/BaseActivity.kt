package app.pwhs.universalinstaller.base

import android.content.Context
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

abstract class BaseActivity : FragmentActivity() {

    protected data class AppThemeState(
        val mode: ThemeMode,
        val dynamicColor: Boolean,
        val amoledMode: Boolean,
        val themePreset: AppThemePreset
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove standard sliding window transitions to mimic Compose navigation speed
        disableSceneTransition()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        disableSceneTransition()
    }

    override fun finish() {
        super.finish()
        disableSceneTransition()
    }

    protected fun setContentWithTheme(content: @Composable () -> Unit) {
        enableEdgeToEdge()
        setContent {
            val initialState = remember {
                kotlinx.coroutines.runBlocking {
                    val prefs = dataStore.data.first()
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                    val dynamicColor = prefs[booleanPreferencesKey("dynamic_color")] ?: false
                    val amoledMode = prefs[booleanPreferencesKey("amoled_mode")] ?: false
                    val presetName = prefs[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name
                    val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                    AppThemeState(mode, dynamicColor, amoledMode, themePreset)
                }
            }
            
            val themeStateFlow = remember {
                dataStore.data.map { prefs ->
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                    val dynamicColor = prefs[booleanPreferencesKey("dynamic_color")] ?: false
                    val amoledMode = prefs[booleanPreferencesKey("amoled_mode")] ?: false
                    val presetName = prefs[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name
                    val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                    AppThemeState(mode, dynamicColor, amoledMode, themePreset)
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
                amoledMode = themeState.amoledMode,
                themePreset = themeState.themePreset
            ) {
                if (this@BaseActivity !is app.pwhs.universalinstaller.presentation.splash.SplashActivity) {
                    app.pwhs.universalinstaller.presentation.setting.security.components.AppLockGate()
                }
                content()
            }
        }
    }
}
