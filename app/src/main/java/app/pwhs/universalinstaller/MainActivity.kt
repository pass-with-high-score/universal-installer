package app.pwhs.universalinstaller

import android.Manifest
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.universalinstaller.app.UniversalInstallerApp
import app.pwhs.universalinstaller.presentation.onboarding.OnboardingScreen
import app.pwhs.universalinstaller.presentation.setting.ThemeMode
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.presentation.splash.SplashScreen
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

private enum class AppRoute { Splash, Onboarding, Main }

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — uninstall flow works either way, notifications just won't show */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        handleViewIntent(intent)
        setContent {
            // `remember` caches the Flow across recompositions so `map {}` isn't re-invoked
            // every frame (Detekt/Android Lint: "Flow operator functions should not be invoked
            // within composition").
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

            // Re-apply edge-to-edge whenever the effective theme flips so the status-bar /
            // navigation-bar icon colors (light vs dark glyphs) match the new background.
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

            var currentRoute by remember { mutableStateOf(AppRoute.Splash) }

            UniversalInstallerTheme(darkTheme = darkTheme) {
                when (currentRoute) {
                    AppRoute.Splash -> SplashScreen(
                        onNavigateToOnboarding = { currentRoute = AppRoute.Onboarding },
                        onNavigateToMain = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Onboarding -> OnboardingScreen(
                        onFinish = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Main -> UniversalInstallerApp()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Pick up VIEW / INSTALL_PACKAGE intents (Chrome downloads, file managers, Gmail,
     * Telegram). The manifest filter accepts `application/octet-stream` to catch Chrome's
     * download URIs — that means we can receive unrelated binaries too, so the install
     * screen's existing extension check is the real gatekeeper; here we just hand off.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE -> {
                val uri: Uri = intent.data ?: return
                IntentHandoff.post(uri)
            }
            Intent.ACTION_SEND -> {
                // Share single: either intent.data or EXTRA_STREAM.
                val uri = intent.data ?: @Suppress("DEPRECATION")
                    (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri) ?: return
                IntentHandoff.post(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        ?.filterNotNull()
                        ?: return
                when {
                    uris.isEmpty() -> return
                    uris.size == 1 -> IntentHandoff.post(uris.first())
                    else -> IntentHandoff.postBatch(uris)
                }
            }
        }
    }
}
