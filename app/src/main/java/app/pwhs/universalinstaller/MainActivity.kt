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
import app.pwhs.universalinstaller.presentation.splash.SplashScreen
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import app.pwhs.universalinstaller.presentation.install.InstallActivity
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import app.pwhs.universalinstaller.presentation.uninstall.UninstallActivity


private enum class AppRoute { Splash, Onboarding, Main }

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    /** True when the launch intent is a file-open action (VIEW / INSTALL / SEND). */
    private fun isFileOpenIntent(intent: Intent?): Boolean {
        return intent?.action in setOf(
            Intent.ACTION_VIEW,
            Intent.ACTION_INSTALL_PACKAGE,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
        ) && intent?.data?.scheme != "universalinstaller"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupShortcuts()

        // Determine if we should skip splash: when launched from a file-open intent,
        // the user expects to see the install preview immediately.
        val skipSplash = isFileOpenIntent(intent) || intent?.data?.scheme == "universalinstaller"
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

            var currentRoute by remember {
                mutableStateOf(if (skipSplash) AppRoute.Main else AppRoute.Splash)
            }

            val intentScheme = intent?.data?.scheme

            // Post the intent URI AFTER the composable tree is set up, so InstallScreen
            // is already composed (when skipSplash == true) and can process it immediately.
            LaunchedEffect(Unit) {
                handleViewIntent(intent)
            }

            UniversalInstallerTheme(darkTheme = darkTheme) {
                when (currentRoute) {
                    AppRoute.Splash -> SplashScreen(
                        onNavigateToOnboarding = { currentRoute = AppRoute.Onboarding },
                        onNavigateToMain = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Onboarding -> OnboardingScreen(
                        onFinish = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Main -> {
                        LaunchedEffect(Unit) {
                            val incoming = intent
                            val uri = incoming?.data
                            val isDeepLink = uri?.scheme == "universalinstaller"
                            val targetActivity = if (isDeepLink) {
                                when (uri.host) {
                                    "sync" -> SyncActivity::class.java
                                    "uninstall" -> UninstallActivity::class.java
                                    else -> InstallActivity::class.java
                                }
                            } else {
                                InstallActivity::class.java
                            }
                            val launch = Intent(this@MainActivity, targetActivity).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            // Forward content URIs received via VIEW/INSTALL/SEND so the install
                            // task gets its own read-permission grant. Without this, the new
                            // task can't openInputStream() the URI and the parser falls back to
                            // packageName="Unknown" — the v1.4.0 install regression.
                            if (!isDeepLink) {
                                forwardIncomingUris(incoming, launch)
                            }
                            startActivity(launch)
                            finish()
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
        handleViewIntent(intent)
    }

    /**
     * Re-grant any content URIs we received on [source] to [target] so the install activity's
     * task can read them. Required because we launch [target] with `NEW_TASK | CLEAR_TASK` and
     * `finish()` ourselves — the original grant only covered MainActivity's task.
     *
     * Single URI → `Intent.setData()`. Multiple URIs (`SEND_MULTIPLE`) → `ClipData`. In both
     * cases we OR in `FLAG_GRANT_READ_URI_PERMISSION` so the system re-issues the grant.
     */
    private fun forwardIncomingUris(source: Intent?, target: Intent) {
        if (source == null) return
        val uris = collectGrantableUris(source)
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            target.data = uris.first()
        } else {
            val clip = ClipData.newRawUri("", uris.first())
            for (i in 1 until uris.size) {
                clip.addItem(ClipData.Item(uris[i]))
            }
            target.clipData = clip
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Suppress("DEPRECATION")
    private fun collectGrantableUris(source: Intent): List<Uri> {
        val out = mutableListOf<Uri>()
        source.data?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let(out::add)
        when (source.action) {
            Intent.ACTION_SEND ->
                (source.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let(out::add)
            Intent.ACTION_SEND_MULTIPLE ->
                source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.filterNotNull()
                    ?.let(out::addAll)
        }
        return out.distinct()
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
                if (uri.scheme == "universalinstaller") return
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
