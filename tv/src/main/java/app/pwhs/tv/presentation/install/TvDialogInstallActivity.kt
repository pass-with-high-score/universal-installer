package app.pwhs.tv.presentation.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.tv.presentation.receive.ExternalApkIntake
import app.pwhs.tv.ui.theme.UniversalInstallerTheme
import app.pwhs.tv.util.LocaleHelper
import kotlinx.coroutines.flow.map

class TvDialogInstallActivity : ComponentActivity() {

    private val viewModel: TvDialogInstallViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = ExternalApkIntake.urisFrom(intent).firstOrNull()
        if (uri == null) {
            finish()
            return
        }
        ExternalApkIntake.markConsumed(intent)
        viewModel.loadApk(uri)

        setContent {
            val themeModeName by dataStore.data
                .map { it[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name }
                .collectAsState(initial = ThemeMode.System.name)

            val themeMode = remember(themeModeName) {
                ThemeMode.entries.find { it.name == themeModeName } ?: ThemeMode.System
            }

            val themePresetName by dataStore.data
                .map { it[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name }
                .collectAsState(initial = AppThemePreset.Orange.name)

            val themePreset = remember(themePresetName) {
                AppThemePreset.entries.find { it.name == themePresetName } ?: AppThemePreset.Orange
            }

            UniversalInstallerTheme(themeMode = themeMode, themePreset = themePreset) {
                val state by viewModel.state.collectAsState()

                BackHandler {
                    if (state is TvInstallState.Ready && (state as TvInstallState.Ready).showPermissions) {
                        viewModel.togglePermissions()
                    } else {
                        finish()
                    }
                }

                TvDialogInstallContent(
                    state = state,
                    onInstall = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                            openUnknownSources()
                        } else {
                            viewModel.install()
                        }
                    },
                    onTogglePermissions = viewModel::togglePermissions,
                    onDismiss = { finish() },
                    onOpenApp = { packageName ->
                        openApp(packageName)
                        finish()
                    },
                    onRetry = viewModel::retry
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = ExternalApkIntake.urisFrom(intent).firstOrNull()
        if (uri != null) {
            ExternalApkIntake.markConsumed(intent)
            viewModel.loadApk(uri)
        }
    }

    private fun openUnknownSources() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openApp(packageName: String) {
        runCatching {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
            launchIntent?.let { startActivity(it) }
        }
    }
}
