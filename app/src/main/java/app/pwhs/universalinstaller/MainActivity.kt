package app.pwhs.universalinstaller

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.presentation.onboarding.OnboardingScreen
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.core.util.DeviceCompat
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.splash.SplashActivity
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import app.pwhs.universalinstaller.presentation.install.InstallActivity
import app.pwhs.universalinstaller.presentation.sync.SendToTvActivity
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import app.pwhs.universalinstaller.presentation.manage.ManageActivity
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

private enum class AppRoute { Onboarding, Main }

private data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val amoledMode: Boolean = false,
    val themePreset: AppThemePreset = AppThemePreset.Orange,
)

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Shortcuts (universalinstaller://sync, ://uninstall) cold-launch this Activity
        // directly, bypassing SplashActivity. installSplashScreen keeps the system splash
        // window in place until the first Compose frame so we don't flash the Material
        // default grey on slower devices.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupShortcuts()

        // SplashActivity owns the cold-start splash and tells us whether onboarding
        // still needs to run via EXTRA_SHOW_ONBOARDING. Deep links (shortcuts) target
        // this Activity directly without that extra and skip onboarding entirely.
        val isDeepLink = intent?.data?.scheme == "universalinstaller"
        val showOnboarding = !isDeepLink &&
            intent?.getBooleanExtra(SplashActivity.EXTRA_SHOW_ONBOARDING, false) == true
        setContent {
            val themeStateFlow = remember {
                dataStore.data.map { prefs ->
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    val themeMode = ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                    val dynamicColor = prefs[booleanPreferencesKey("dynamic_color")] ?: false
                    val amoledMode = prefs[booleanPreferencesKey("amoled_mode")] ?: false
                    val presetName = prefs[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name
                    val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                    ThemeConfig(themeMode, dynamicColor, amoledMode, themePreset)
                }
            }
            val themeState by themeStateFlow.collectAsState(initial = ThemeConfig())

            val darkTheme = when (themeState.themeMode) {
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

            var currentRoute by remember {
                mutableStateOf(if (showOnboarding) AppRoute.Onboarding else AppRoute.Main)
            }

            var onboardingStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

            UniversalInstallerTheme(
                darkTheme = darkTheme,
                dynamicColor = themeState.dynamicColor,
                amoledMode = themeState.amoledMode,
                themePreset = themeState.themePreset,
            ) {
                when (currentRoute) {
                    AppRoute.Onboarding -> {
                        val backupViewModel: app.pwhs.universalinstaller.presentation.setting.backup.BackupViewModel = org.koin.androidx.compose.koinViewModel()
                        var openRestorePicker by remember { mutableStateOf<(() -> Unit)?>(null) }

                        OnboardingScreen(
                            onFinish = {
                                val durationSec = (System.currentTimeMillis() - onboardingStartTime) / 1000L
                                app.pwhs.core.telemetry.AnalyticsHelper.logOnboardingComplete(durationSec = durationSec)
                                currentRoute = AppRoute.Main
                            },
                            // Only the build that has reporting asks about it. On `opensource`
                            // there is no sink, so the page would be a question about nothing.
                            showAnalyticsConsent = Telemetry.isCollecting,
                            onRestoreBackup = { openRestorePicker?.invoke() },
                        )

                        app.pwhs.universalinstaller.presentation.setting.backup.BackupSheetsHost(
                            viewModel = backupViewModel,
                            onPickerReady = { openRestorePicker = it },
                        )
                    }
                    AppRoute.Main -> {
                        LaunchedEffect(Unit) {
                            val uri = intent?.data
                            val isWebOrFile = uri != null && (uri.scheme in listOf("http", "https", "content", "file"))
                            val isShare = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE
                            if (isWebOrFile || isShare) {
                                val dialogIntent = Intent(this@MainActivity, app.pwhs.universalinstaller.presentation.install.DialogInstallActivity::class.java).apply {
                                    action = intent?.action ?: Intent.ACTION_VIEW
                                    data = intent?.data
                                    intent?.clipData?.let { clipData = it }
                                    intent?.extras?.let { putExtras(it) }
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(dialogIntent)
                                this@MainActivity.disableSceneTransition()
                                finish()
                                this@MainActivity.disableSceneTransition()
                                return@LaunchedEffect
                            }

                            // Handle internal deep-links (sync, tv, uninstall).
                            // Regular launches default to InstallActivity.
                            val targetActivity = if (uri?.scheme == "universalinstaller") {
                                when (uri.host) {
                                    "sync" -> SyncActivity::class.java
                                    "send_tv", "tv" -> SendToTvActivity::class.java
                                    "uninstall" -> ManageActivity::class.java
                                    "updates" -> runCatching { Class.forName("app.pwhs.updater.presentation.UpdatesActivity") }.getOrNull()
                                        ?: InstallActivity::class.java
                                    else -> InstallActivity::class.java
                                }
                            } else {
                                InstallActivity::class.java
                            }

                            startActivity(
                                Intent(this@MainActivity, targetActivity).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                                },
                            )
                            this@MainActivity.disableSceneTransition()
                            finish()
                            this@MainActivity.disableSceneTransition()
                        }
                    }
                }
            }
        }
    }

    private fun setupShortcuts() {
        val syncIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "universalinstaller://sync".toUri()
        }
        val syncShortcut = ShortcutInfoCompat.Builder(this, "shortcut_sync")
            .setShortLabel(getString(R.string.setting_section_sync))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_sync))
            .setIntent(syncIntent)
            .build()

        val tvIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "universalinstaller://send_tv".toUri()
        }
        val tvShortcut = ShortcutInfoCompat.Builder(this, "shortcut_send_tv")
            .setShortLabel(getString(R.string.tv_sync_screen_title))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_tv))
            .setIntent(tvIntent)
            .build()

        val uninstallIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "universalinstaller://uninstall".toUri()
        }
        val uninstallShortcut = ShortcutInfoCompat.Builder(this, "shortcut_uninstall")
            .setShortLabel(getString(R.string.screen_title_uninstall))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_delete))
            .setIntent(uninstallIntent)
            .build()

        ShortcutManagerCompat.addDynamicShortcuts(this, listOf(syncShortcut, tvShortcut, uninstallShortcut))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
