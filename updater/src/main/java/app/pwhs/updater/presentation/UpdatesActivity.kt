package app.pwhs.updater.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.ui.theme.UniversalInstallerTheme
import app.pwhs.core.util.disableSceneTransition
import app.pwhs.updater.presentation.add.AddAppScreen
import app.pwhs.updater.presentation.component.UpdaterBottomBar
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

enum class UpdaterScreenRoute {
    UPDATES_LIST,
    ADD_APP,
}

class UpdatesActivity : ComponentActivity() {

    private val viewModel: UpdatesViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableSceneTransition()
        enableEdgeToEdge()

        setContent {
            var currentRoute by remember { mutableStateOf(UpdaterScreenRoute.UPDATES_LIST) }

            val themeFlow = remember {
                dataStore.data.map { prefs ->
                    val modeName = prefs[SharedPrefsKeys.THEME_MODE] ?: ThemeMode.System.name
                    val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.System)
                    val presetName = prefs[SharedPrefsKeys.THEME_PRESET] ?: AppThemePreset.Orange.name
                    val themePreset = runCatching { AppThemePreset.valueOf(presetName) }.getOrDefault(AppThemePreset.Orange)
                    val dynamicColor = prefs[SharedPrefsKeys.DYNAMIC_COLOR] ?: false
                    val amoledMode = prefs[SharedPrefsKeys.AMOLED_MODE] ?: false
                    ThemeConfig(themeMode, themePreset, dynamicColor, amoledMode)
                }
            }
            val themeConfig by themeFlow.collectAsState(initial = ThemeConfig())

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeConfig.mode) {
                ThemeMode.System -> systemDark
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

            BackHandler(enabled = currentRoute == UpdaterScreenRoute.ADD_APP) {
                currentRoute = UpdaterScreenRoute.UPDATES_LIST
            }

            UniversalInstallerTheme(
                darkTheme = darkTheme,
                dynamicColor = themeConfig.dynamicColor,
                amoledMode = themeConfig.amoledMode,
                themePreset = themeConfig.preset,
            ) {
                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "UpdaterScreenTransition",
                ) { route ->
                    when (route) {
                        UpdaterScreenRoute.UPDATES_LIST -> {
                            val uiState by viewModel.uiState.collectAsState()
                            UpdatesScreen(
                                viewModel = viewModel,
                                onNavigateToAddApp = {
                                    currentRoute = UpdaterScreenRoute.ADD_APP
                                },
                                onBackClick = null,
                                bottomBar = {
                                    UpdaterBottomBar(updateCount = uiState.updateCount)
                                },
                            )
                        }
                        UpdaterScreenRoute.ADD_APP -> {
                            AddAppScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    currentRoute = UpdaterScreenRoute.UPDATES_LIST
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        disableSceneTransition()
    }

    override fun finish() {
        super.finish()
        disableSceneTransition()
    }

    private data class ThemeConfig(
        val mode: ThemeMode = ThemeMode.System,
        val preset: AppThemePreset = AppThemePreset.Orange,
        val dynamicColor: Boolean = false,
        val amoledMode: Boolean = false,
    )
}
