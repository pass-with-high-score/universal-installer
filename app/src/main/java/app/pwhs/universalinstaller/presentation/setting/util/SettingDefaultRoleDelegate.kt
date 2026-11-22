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
    private val startShizukuService: () -> Unit,
    private val onRootStateUpdated: (RootState) -> Unit,
    private val emitEvent: (Int) -> Unit,
) {
    private val dataStore = application.dataStore
    @Volatile
    private var pendingShizukuAction: (() -> Unit)? = null

    fun onShizukuPermissionGranted() {
        val action = pendingShizukuAction ?: return
        pendingShizukuAction = null
        action()
    }

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
            val shouldProbeRoot = useRoot ||
                (!shizukuReady && currentShizukuState != ShizukuState.NO_PERMISSION && backendFactory.rootSupportCompiledIn && currentRootState == RootState.UNKNOWN)
            val rootReady = when {
                currentRootState == RootState.READY -> true
                shouldProbeRoot -> {
                    val requested = backendFactory.requestRoot()
                    onRootStateUpdated(requested)
                    requested == RootState.READY
                }
                else -> false
            }

            val method = if (useRoot && rootReady) "root" else if (shizukuReady) "shizuku" else if (rootReady) "root" else "none"

            if (!shizukuReady && !rootReady) {
                reportDefaultInstaller("none", enabled, TelemetryEvents.RESULT_BLOCKED)
                when {
                    currentShizukuState == ShizukuState.NO_PERMISSION && !useRoot -> {
                        pendingShizukuAction = { toggleDefaultInstaller(enabled) }
                        requestShizukuPermission()
                    }
                    currentShizukuState == ShizukuState.NOT_RUNNING && !useRoot -> startShizukuService()
                    else -> emitEvent(R.string.setting_default_installer_needs_backend)
                }
                return@launch
            }

            val useRootForCall = (useRoot && rootReady) || (!shizukuReady && rootReady)
            val keepUninstaller = !enabled && resolveIsDefaultUninstaller()
            val component = defaultInstallerComponent()
            val result = if (useRootForCall) {
                backendFactory.setDefaultInstallerViaRoot(application, component, enabled)
            } else {
                app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultInstaller(component, enabled)
            }
            result
                .onSuccess {
                    if (keepUninstaller) {
                        val uninstallerComponent = defaultUninstallerComponent()
                        if (useRootForCall) {
                            backendFactory.setDefaultUninstallerViaRoot(application, uninstallerComponent, true)
                        } else {
                            app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultUninstaller(uninstallerComponent, true)
                        }
                    }
                    reportDefaultInstaller(method, enabled, TelemetryEvents.RESULT_SUCCESS)
                    refreshBothRoleStatuses()
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

    private fun resolveIsDefaultInstaller(): Boolean {
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
        return resolved?.activityInfo?.packageName == application.packageName
    }

    fun updateDefaultInstallerStatus() {
        scope.launch(Dispatchers.IO) {
            _isDefaultInstaller.value = resolveIsDefaultInstaller()
        }
    }

    private fun refreshBothRoleStatuses() {
        _isDefaultInstaller.value = resolveIsDefaultInstaller()
        _isDefaultUninstaller.value = resolveIsDefaultUninstaller()
    }

    fun toggleDefaultUninstaller(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            updateShizukuState()
            val prefs = dataStore.data.first()
            val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
            val currentShizukuState = shizukuState()
            val currentRootState = rootState()
            val shizukuReady = currentShizukuState == ShizukuState.READY
            val shouldProbeRoot = useRoot ||
                (!shizukuReady && currentShizukuState != ShizukuState.NO_PERMISSION && backendFactory.rootSupportCompiledIn && currentRootState == RootState.UNKNOWN)
            val rootReady = when {
                currentRootState == RootState.READY -> true
                shouldProbeRoot -> {
                    val requested = backendFactory.requestRoot()
                    onRootStateUpdated(requested)
                    requested == RootState.READY
                }
                else -> false
            }

            if (!shizukuReady && !rootReady) {
                when {
                    currentShizukuState == ShizukuState.NO_PERMISSION && !useRoot -> {
                        pendingShizukuAction = { toggleDefaultUninstaller(enabled) }
                        requestShizukuPermission()
                    }
                    currentShizukuState == ShizukuState.NOT_RUNNING && !useRoot -> startShizukuService()
                    else -> emitEvent(R.string.setting_default_uninstaller_needs_backend)
                }
                return@launch
            }

            val useRootForCall = (useRoot && rootReady) || (!shizukuReady && rootReady)
            val keepInstaller = !enabled && resolveIsDefaultInstaller()
            val usingSystemLevel = useRootForCall ||
                runCatching { rikka.shizuku.Shizuku.getUid() == 0 }.getOrDefault(false)
            val component = defaultUninstallerComponent()
            val result = if (useRootForCall) {
                backendFactory.setDefaultUninstallerViaRoot(application, component, enabled)
            } else {
                app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultUninstaller(component, enabled)
            }
            result
                .onSuccess {
                    if (keepInstaller) {
                        val installerComponent = defaultInstallerComponent()
                        if (useRootForCall) {
                            backendFactory.setDefaultInstallerViaRoot(application, installerComponent, true)
                        } else {
                            app.pwhs.universalinstaller.util.ShizukuDefaultInstaller.setDefaultInstaller(installerComponent, true)
                        }
                    }
                    refreshBothRoleStatuses()
                    val eventRes = when {
                        !enabled -> R.string.setting_default_uninstaller_disabled
                        _isDefaultUninstaller.value -> R.string.setting_default_uninstaller_enabled
                        !usingSystemLevel -> R.string.setting_default_uninstaller_requires_root
                        else -> R.string.setting_default_uninstaller_failed
                    }
                    emitEvent(eventRes)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle default uninstaller")
                    refreshBothRoleStatuses()
                    emitEvent(R.string.setting_default_uninstaller_failed)
                }
        }
    }

    private fun defaultUninstallerComponent(): ComponentName =
        ComponentName(application, "app.pwhs.universalinstaller.presentation.manage.uninstall.DialogUninstallActivity")

    private fun resolveIsDefaultUninstaller(): Boolean {
        val uri = android.net.Uri.parse("package:app.pwhs.universalinstaller.test")
        val pDelete = Intent(Intent.ACTION_DELETE).apply { addCategory(Intent.CATEGORY_DEFAULT); data = uri }
        val pUninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { addCategory(Intent.CATEGORY_DEFAULT); data = uri }
        val pm = application.packageManager
        val rDelete = runCatching { pm.resolveActivity(pDelete, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
        val rUninstall = runCatching { pm.resolveActivity(pUninstall, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
        val pkg = application.packageName
        return rDelete?.activityInfo?.packageName == pkg || rUninstall?.activityInfo?.packageName == pkg
    }

    fun updateDefaultUninstallerStatus() {
        scope.launch(Dispatchers.IO) {
            _isDefaultUninstaller.value = resolveIsDefaultUninstaller()
        }
    }
}
