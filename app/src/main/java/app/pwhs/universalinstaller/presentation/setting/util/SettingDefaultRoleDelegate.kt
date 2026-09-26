package app.pwhs.universalinstaller.presentation.setting.util

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingDefaultRoleDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val backendFactory: InstallerBackendFactory,
    private val shizukuState: () -> ShizukuState,
    private val rootState: () -> RootState,
    private val updateShizukuState: () -> Unit,
    private val requestShizukuPermission: () -> Unit,
    private val emitEvent: (Int) -> Unit,
) {
    private val dataStore = application.dataStore

    private val _isDefaultInstaller = MutableStateFlow(false)
    val isDefaultInstaller: StateFlow<Boolean> = _isDefaultInstaller.asStateFlow()

    private val _isDefaultUninstaller = MutableStateFlow(false)
    val isDefaultUninstaller: StateFlow<Boolean> = _isDefaultUninstaller.asStateFlow()

    fun toggleDefaultInstaller(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            updateShizukuState()
            val prefs = dataStore.data.first()
            val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
            val currentShizukuState = shizukuState()
            val currentRootState = rootState()
            val shizukuReady = currentShizukuState == ShizukuState.READY
            val rootReady = if (useRoot) {
                if (currentRootState == RootState.READY) true
                else backendFactory.requestRoot() == RootState.READY
            } else currentRootState == RootState.READY

            val method = if (useRoot && rootReady) "root" else if (shizukuReady) "shizuku" else "none"

            if (!shizukuReady && !rootReady) {
                reportDefaultInstaller("none", enabled, TelemetryEvents.RESULT_BLOCKED)
                when {
                    currentShizukuState == ShizukuState.NO_PERMISSION && !useRoot -> requestShizukuPermission()
                    currentShizukuState == ShizukuState.NOT_RUNNING && !useRoot -> emitEvent(R.string.setting_shizuku_start_service_hint)
                    else -> emitEvent(R.string.setting_default_installer_needs_backend)
                }
                return@launch
            }

            val component = defaultInstallerComponent()
            val result = if (useRoot && rootReady) {
                backendFactory.setDefaultInstallerViaRoot(application, component, enabled)
            } else {
                app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultInstaller(component, enabled)
            }
            result
                .onSuccess {
                    reportDefaultInstaller(method, enabled, TelemetryEvents.RESULT_SUCCESS)
                    updateDefaultInstallerStatus()
                    emitEvent(
                        if (enabled) R.string.setting_default_installer_enabled
                        else R.string.setting_default_installer_disabled,
                    )
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle default installer")
                    reportDefaultInstaller(method, enabled, TelemetryEvents.RESULT_FAILURE)
                    emitEvent(R.string.setting_default_installer_failed)
                }
        }
    }

    private fun reportDefaultInstaller(method: String, enabled: Boolean, result: String) {
        Telemetry.event(
            TelemetryEvents.DEFAULT_INSTALLER_SET,
            TelemetryEvents.PARAM_METHOD to method,
            TelemetryEvents.PARAM_ENABLED to enabled,
            TelemetryEvents.PARAM_RESULT to result,
        )
        val action = if (result == TelemetryEvents.RESULT_SUCCESS) {
            app.pwhs.core.telemetry.TelemetryEvents.DEFAULT_INSTALLER_SET_SUCCESS
        } else {
            app.pwhs.core.telemetry.TelemetryEvents.DEFAULT_INSTALLER_CANCELLED
        }
        app.pwhs.core.telemetry.AnalyticsHelper.logDefaultInstallerAction(action)
        app.pwhs.core.telemetry.AnalyticsHelper.updateIsDefaultInstaller(enabled && result == TelemetryEvents.RESULT_SUCCESS)
    }

    private fun defaultInstallerComponent(): ComponentName =
        ComponentName(
            application,
            "app.pwhs.universalinstaller.presentation.install.DialogInstallActivity",
        )

    fun updateDefaultInstallerStatus() {
        scope.launch(Dispatchers.IO) {
            val probe = Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(
                    android.net.Uri.parse("content://storage/emulated/0/test.apk"),
                    "application/vnd.android.package-archive",
                )
            }
            val resolved = try {
                application.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (t: Throwable) {
                Timber.w(t, "resolveActivity failed")
                null
            }
            _isDefaultInstaller.value = resolved?.activityInfo?.packageName == application.packageName
        }
    }

    fun toggleDefaultUninstaller(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            updateShizukuState()
            val prefs = dataStore.data.first()
            val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
            val currentShizukuState = shizukuState()
            val currentRootState = rootState()
            val shizukuReady = currentShizukuState == ShizukuState.READY
            val rootReady = if (useRoot) {
                if (currentRootState == RootState.READY) true
                else backendFactory.requestRoot() == RootState.READY
            } else currentRootState == RootState.READY

            if (!shizukuReady && !rootReady) {
                when {
                    currentShizukuState == ShizukuState.NO_PERMISSION && !useRoot -> requestShizukuPermission()
                    currentShizukuState == ShizukuState.NOT_RUNNING && !useRoot -> emitEvent(R.string.setting_shizuku_start_service_hint)
                    else -> emitEvent(R.string.setting_default_uninstaller_needs_backend)
                }
                return@launch
            }

            val component = defaultUninstallerComponent()
            val result = if (useRoot && rootReady) {
                backendFactory.setDefaultUninstallerViaRoot(application, component, enabled)
            } else {
                app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultUninstaller(component, enabled)
            }
            result
                .onSuccess {
                    updateDefaultUninstallerStatus()
                    emitEvent(
                        if (enabled) R.string.setting_default_uninstaller_enabled
                        else R.string.setting_default_uninstaller_disabled,
                    )
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle default uninstaller")
                    emitEvent(R.string.setting_default_uninstaller_failed)
                }
        }
    }

    private fun defaultUninstallerComponent(): ComponentName =
        ComponentName(application, "app.pwhs.universalinstaller.presentation.manage.uninstall.DialogUninstallActivity")

    fun updateDefaultUninstallerStatus() {
        scope.launch(Dispatchers.IO) {
            val uri = android.net.Uri.parse("package:app.pwhs.universalinstaller.test")
            val pDelete = Intent(Intent.ACTION_DELETE).apply { addCategory(Intent.CATEGORY_DEFAULT); data = uri }
            val pUninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { addCategory(Intent.CATEGORY_DEFAULT); data = uri }
            val pm = application.packageManager
            val rDelete = runCatching { pm.resolveActivity(pDelete, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
            val rUninstall = runCatching { pm.resolveActivity(pUninstall, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
            val pkg = application.packageName
            _isDefaultUninstaller.value = rDelete?.activityInfo?.packageName == pkg || rUninstall?.activityInfo?.packageName == pkg
        }
    }
}
