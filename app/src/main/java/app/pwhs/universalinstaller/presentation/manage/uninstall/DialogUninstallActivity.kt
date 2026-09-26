package app.pwhs.universalinstaller.presentation.manage.uninstall

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.lifecycleScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.domain.provider.PrivilegedExecutor
import app.pwhs.universalinstaller.domain.provider.PrivilegedProvider
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.manage.util.ManageUninstallHelper
import app.pwhs.universalinstaller.presentation.manage.util.UninstallOptions
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import timber.log.Timber

/**
 * Dialog-style activity handling uninstall intents (ACTION_UNINSTALL_PACKAGE and ACTION_DELETE).
 * Provides a modern bottom sheet with options to preserve application data/cache,
 * uninstall for all users, and delete system apps.
 */
class DialogUninstallActivity : ComponentActivity() {

    private val packageUninstaller: PackageUninstaller by inject()
    private val uninstallLogDao: UninstallLogDao by inject()
    private val privilegedProvider: PrivilegedProvider by inject()
    private val backendFactory: InstallerBackendFactory by inject()

    companion object {
        private const val EXTRA_UNINSTALL_ALL_USERS_FALLBACK = "android.intent.extra.UNINSTALL_ALL_USERS"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val targetPackage = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: intent.data?.pathSegments?.lastOrNull()

        if (targetPackage.isNullOrBlank()) {
            Timber.w("DialogUninstallActivity launched without target package name")
            finish()
            return
        }

        val pm = packageManager
        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(targetPackage, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(targetPackage, 0)
            }
        }.getOrNull()

        if (appInfo == null) {
            Toast.makeText(this, getString(R.string.uninstall_no_apps_found), Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val appName = pm.getApplicationLabel(appInfo).toString()
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(targetPackage, 0)
            }
        }.getOrNull()
        val versionName = packageInfo?.versionName.orEmpty()
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val returnResult = intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)
        val allUsersInitial = intent.getBooleanExtra(EXTRA_UNINSTALL_ALL_USERS_FALLBACK, false)

        setContent {
            val prefs by dataStore.data.collectAsState(initial = null)
            val themeModeName = prefs?.get(PreferencesKeys.THEME_MODE) ?: ThemeMode.System.name
            val themeMode = ThemeMode.entries.find { it.name == themeModeName } ?: ThemeMode.System
            val dynamicColor = prefs?.get(PreferencesKeys.DYNAMIC_COLOR) ?: false
            val amoledMode = prefs?.get(PreferencesKeys.AMOLED_MODE) ?: false
            val presetName = prefs?.get(PreferencesKeys.THEME_PRESET) ?: AppThemePreset.Orange.name
            val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange

            val defaultKeepData = prefs?.get(PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA) ?: false
            val defaultAllUsers = prefs?.get(PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS) ?: allUsersInitial
            val defaultDeleteSystemApp = prefs?.get(PreferencesKeys.PRIVILEGED_UNINSTALL_DELETE_SYSTEM_APP) ?: isSystemApp
            val useShizukuPref = prefs?.get(PreferencesKeys.USE_SHIZUKU) ?: false
            val useRootPref = prefs?.get(PreferencesKeys.USE_ROOT) ?: false

            val isPrivileged by produceState(initialValue = useShizukuPref || useRootPref) {
                value = useShizukuPref || useRootPref || (privilegedProvider.resolveExecutor() != null)
            }

            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }

            UniversalInstallerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                amoledMode = amoledMode,
                themePreset = themePreset,
            ) {
                DialogUninstallSheet(
                    appName = appName,
                    packageName = targetPackage,
                    versionName = versionName,
                    isSystemApp = isSystemApp,
                    defaultKeepData = defaultKeepData,
                    defaultAllUsers = defaultAllUsers,
                    defaultDeleteSystemApp = defaultDeleteSystemApp,
                    isPrivileged = isPrivileged,
                    onCancel = {
                        if (returnResult) setResult(RESULT_CANCELED)
                        finish()
                    },
                    onConfirmUninstall = { keepData, allUsers, deleteSystemApp ->
                        executeUninstall(
                            packageName = targetPackage,
                            appName = appName,
                            isSystemApp = isSystemApp,
                            deleteSystemApp = deleteSystemApp,
                            keepData = keepData,
                            allUsers = allUsers,
                            useShizuku = useShizukuPref,
                            useRoot = useRootPref,
                            returnResult = returnResult,
                        )
                    },
                )
            }
        }
    }

    private fun executeUninstall(
        packageName: String,
        appName: String,
        isSystemApp: Boolean,
        deleteSystemApp: Boolean,
        keepData: Boolean,
        allUsers: Boolean,
        useShizuku: Boolean,
        useRoot: Boolean,
        returnResult: Boolean,
    ) {
        lifecycleScope.launch {
            val executor = privilegedProvider.resolveExecutor()
            val effectiveUseShizuku = useShizuku || executor == PrivilegedExecutor.Shizuku
            val effectiveUseRoot = useRoot || executor == PrivilegedExecutor.Root

            val success = if (isSystemApp && executor != null) {
                if (!deleteSystemApp) {
                    Toast.makeText(
                        this@DialogUninstallActivity,
                        getString(R.string.uninstall_notif_single_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (returnResult) setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }
                ManageUninstallHelper.performSystemUninstall(
                    packageName = packageName,
                    appName = appName,
                    method = SystemAppMethod.UninstallForUser0,
                    executor = executor,
                    backendFactory = backendFactory,
                    packageUninstaller = packageUninstaller,
                    uninstallLogDao = uninstallLogDao,
                )
            } else {
                val opts = UninstallOptions(
                    useShizuku = effectiveUseShizuku,
                    useRoot = effectiveUseRoot,
                    keepData = keepData,
                    allUsers = allUsers,
                )
                ManageUninstallHelper.performUninstall(
                    packageName = packageName,
                    appName = appName,
                    opts = opts,
                    packageUninstaller = packageUninstaller,
                    uninstallLogDao = uninstallLogDao,
                    backendFactory = backendFactory,
                )
            }

            if (success) {
                Toast.makeText(
                    this@DialogUninstallActivity,
                    getString(R.string.uninstall_notif_single_success),
                    Toast.LENGTH_SHORT,
                ).show()
                if (returnResult) setResult(RESULT_OK)
            } else {
                Toast.makeText(
                    this@DialogUninstallActivity,
                    getString(R.string.uninstall_notif_single_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                if (returnResult) setResult(RESULT_CANCELED)
            }
            finish()
        }
    }
}
