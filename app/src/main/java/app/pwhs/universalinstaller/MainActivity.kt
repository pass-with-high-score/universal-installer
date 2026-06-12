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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.universalinstaller.presentation.onboarding.OnboardingScreen
import app.pwhs.universalinstaller.presentation.setting.ThemeMode
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.presentation.splash.SplashActivity
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import app.pwhs.universalinstaller.presentation.install.InstallActivity
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import app.pwhs.universalinstaller.presentation.manage.ManageActivity
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

private enum class AppRoute { Onboarding, Main }

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
            val themeModeFlow = remember {
                dataStore.data.map { prefs ->
                    val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                    ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
                }
            }
            val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.System)

            val darkTheme = when (themeMode) {
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

            UniversalInstallerTheme(darkTheme = darkTheme) {
                when (currentRoute) {
                    AppRoute.Onboarding -> OnboardingScreen(
                        onFinish = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Main -> {
                        LaunchedEffect(Unit) {
                            val uri = intent?.data
                            // Handle internal deep-links (sync, uninstall).
                            // Regular launches default to InstallActivity.
                            val targetActivity = if (uri?.scheme == "universalinstaller") {
                                when (uri.host) {
                                    "sync" -> SyncActivity::class.java
                                    "uninstall" -> ManageActivity::class.java
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

        val uninstallIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "universalinstaller://uninstall".toUri()
        }
        val uninstallShortcut = ShortcutInfoCompat.Builder(this, "shortcut_uninstall")
            .setShortLabel(getString(R.string.screen_title_uninstall))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_delete))
            .setIntent(uninstallIntent)
            .build()

        ShortcutManagerCompat.addDynamicShortcuts(this, listOf(syncShortcut, uninstallShortcut))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
