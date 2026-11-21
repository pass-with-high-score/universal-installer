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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.presentation.adaptive.LocalAdaptiveLayoutInfo
import app.pwhs.core.presentation.adaptive.ProvideAdaptiveLayoutInfo
import app.pwhs.core.ui.theme.UniversalInstallerTheme
import app.pwhs.core.util.disableSceneTransition
import app.pwhs.updater.presentation.add.AddAppScreen
import app.pwhs.updater.presentation.component.UpdaterBottomBar
import app.pwhs.updater.presentation.component.UpdaterNavigationRail
import app.pwhs.updater.presentation.deeplink.UpdaterDeepLinkAction
import app.pwhs.updater.presentation.deeplink.UpdaterDeepLinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

enum class UpdaterScreenRoute {
    UPDATES_LIST,
    ADD_APP,
}

class UpdatesActivity : ComponentActivity() {

    private val viewModel: UpdatesViewModel by viewModel()
    private val pendingDeepLink = MutableStateFlow<UpdaterDeepLinkAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableSceneTransition()
        enableEdgeToEdge()
        pendingDeepLink.value = UpdaterDeepLinkParser.parse(intent)

        setContent {
            var currentRoute by rememberSaveable { mutableStateOf(UpdaterScreenRoute.UPDATES_LIST) }
            var prefillAddApp by remember { mutableStateOf<UpdaterDeepLinkAction.PreFillAddApp?>(null) }
            var pendingDetailPkg by rememberSaveable { mutableStateOf<String?>(null) }
            val incomingDeepLink by pendingDeepLink.collectAsState()

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
                ProvideAdaptiveLayoutInfo(activity = this@UpdatesActivity) {
                    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
                    val uiState by viewModel.uiState.collectAsState()

                    LaunchedEffect(incomingDeepLink) {
                        val action = incomingDeepLink ?: return@LaunchedEffect
                        when (action) {
                            UpdaterDeepLinkAction.OpenList -> {
                                currentRoute = UpdaterScreenRoute.UPDATES_LIST
                            }
                            UpdaterDeepLinkAction.CheckAllUpdates -> {
                                currentRoute = UpdaterScreenRoute.UPDATES_LIST
                                viewModel.checkAllUpdates()
                            }
                            is UpdaterDeepLinkAction.OpenAppDetail -> {
                                currentRoute = UpdaterScreenRoute.UPDATES_LIST
                                pendingDetailPkg = action.packageName
                            }
                            is UpdaterDeepLinkAction.PreFillAddApp -> {
                                prefillAddApp = action
                                currentRoute = UpdaterScreenRoute.ADD_APP
                            }
                        }
                        pendingDeepLink.value = null
                    }

                    LaunchedEffect(pendingDetailPkg, uiState.trackedApps) {
                        val targetPkg = pendingDetailPkg ?: return@LaunchedEffect
                        val matched = uiState.trackedApps.firstOrNull { it.packageName == targetPkg }
                        if (matched != null) {
                            viewModel.selectAppForDetail(matched)
                            pendingDetailPkg = null
                        }
                    }

                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "UpdaterScreenTransition",
                    ) { route ->
                        when (route) {
                            UpdaterScreenRoute.UPDATES_LIST -> {
                                if (adaptiveInfo.useNavigationRail) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background),
                                    ) {
                                        UpdaterNavigationRail(updateCount = uiState.updateCount)
                                        VerticalDivider(
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                        ) {
                                            UpdatesScreen(
                                                viewModel = viewModel,
                                                onNavigateToAddApp = {
                                                    currentRoute = UpdaterScreenRoute.ADD_APP
                                                },
                                                onBackClick = null,
                                                bottomBar = {},
                                            )
                                        }
                                    }
                                } else {
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
                            }
                            UpdaterScreenRoute.ADD_APP -> {
                                val prefill = prefillAddApp
                                AddAppScreen(
                                    viewModel = viewModel,
                                    initialUrl = prefill?.url.orEmpty(),
                                    initialCategory = prefill?.category.orEmpty(),
                                    initialIncludePrereleases = prefill?.includePrereleases == true,
                                    initialTargetPackageName = prefill?.targetPackageName,
                                    onBackClick = {
                                        prefillAddApp = null
                                        currentRoute = UpdaterScreenRoute.UPDATES_LIST
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInstalledVersions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = UpdaterDeepLinkParser.parse(intent)
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
