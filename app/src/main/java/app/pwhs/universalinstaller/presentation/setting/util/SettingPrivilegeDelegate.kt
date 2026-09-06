package app.pwhs.universalinstaller.presentation.setting.util

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import timber.log.Timber

private const val SHIZUKU_PERMISSION_REQ_CODE = 0xA17

class SettingPrivilegeDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val backendFactory: InstallerBackendFactory,
    private val emitEvent: (Int) -> Unit,
) {
    private val dataStore = application.dataStore

    private val _shizukuState = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    private val _dhizukuState = MutableStateFlow(DhizukuState.NOT_INSTALLED)
    val dhizukuState: StateFlow<DhizukuState> = _dhizukuState.asStateFlow()

    val useDhizuku: StateFlow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.USE_DHIZUKU] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val useMicroG: StateFlow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.USE_MICROG] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val useCustomAuthorizer: StateFlow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.USE_CUSTOM_AUTHORIZER] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val customAuthorizerCommand: StateFlow<String> = dataStore.data
        .map { it[PreferencesKeys.CUSTOM_AUTHORIZER_COMMAND] ?: PreferencesKeys.DEFAULT_CUSTOM_AUTHORIZER_COMMAND }
        .stateIn(scope, SharingStarted.Eagerly, PreferencesKeys.DEFAULT_CUSTOM_AUTHORIZER_COMMAND)

    private val _rootState = MutableStateFlow(
        if (backendFactory.rootSupportCompiledIn) RootState.UNKNOWN else RootState.UNAVAILABLE,
    )
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    private val _isDefaultInstaller = MutableStateFlow(false)
    val isDefaultInstaller: StateFlow<Boolean> = _isDefaultInstaller.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("Shizuku binder received")
        app.pwhs.core.telemetry.AnalyticsHelper.logShizukuStatusChanged(app.pwhs.core.telemetry.TelemetryEvents.SHIZUKU_CONNECTED)
        updateShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("Shizuku binder dead")
        app.pwhs.core.telemetry.AnalyticsHelper.logShizukuStatusChanged(app.pwhs.core.telemetry.TelemetryEvents.SHIZUKU_SERVICE_DEAD)
        updateShizukuState()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_PERMISSION_REQ_CODE) return@OnRequestPermissionResultListener
            updateShizukuState()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                app.pwhs.core.telemetry.AnalyticsHelper.logShizukuStatusChanged(app.pwhs.core.telemetry.TelemetryEvents.SHIZUKU_CONNECTED)
                scope.launch {
                    dataStore.edit { prefs ->
                        prefs[PreferencesKeys.USE_ROOT] = false
                        prefs[PreferencesKeys.USE_DHIZUKU] = false
                        prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                        prefs[PreferencesKeys.USE_MICROG] = false
                        prefs[PreferencesKeys.USE_SHIZUKU] = true
                    }
                }
            } else {
                app.pwhs.core.telemetry.AnalyticsHelper.logShizukuStatusChanged(app.pwhs.core.telemetry.TelemetryEvents.SHIZUKU_PERMISSION_DENIED)
                emitEvent(R.string.setting_shizuku_permission_denied)
            }
        }

    init {
        updateShizukuState()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        if (backendFactory.rootSupportCompiledIn) {
            scope.launch {
                _rootState.value = backendFactory.probeRootState()
            }
        }
        updateDefaultInstallerStatus()
    }

    fun cleanUp() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun updateShizukuState() {
        _shizukuState.value = when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.getVersion() < 11 -> ShizukuState.UNSUPPORTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    }

    fun setInstallMode(mode: InstallMode) {
        when (mode) {
            InstallMode.DEFAULT -> scope.launch {
                if (SystemInstallerManager.isSystemPackageInstallerDisabled(application)) {
                    emitEvent(R.string.setting_system_installer_frozen_cannot_select)
                    return@launch
                }
                dataStore.edit { p ->
                    p[PreferencesKeys.USE_SHIZUKU] = false
                    p[PreferencesKeys.USE_ROOT] = false
                    p[PreferencesKeys.USE_DHIZUKU] = false
                    p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                    p[PreferencesKeys.USE_MICROG] = false
                }
            }
            InstallMode.SHIZUKU -> {
                setUseShizuku(true)
            }
            InstallMode.DHIZUKU -> {
                setUseDhizuku(true)
            }
            InstallMode.ROOT -> scope.launch {
                val state = backendFactory.requestRoot()
                _rootState.value = state
                if (state == RootState.READY) {
                    dataStore.edit { p ->
                        p[PreferencesKeys.USE_SHIZUKU] = false
                        p[PreferencesKeys.USE_DHIZUKU] = false
                        p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                        p[PreferencesKeys.USE_MICROG] = false
                        p[PreferencesKeys.USE_ROOT] = true
                    }
                }
            }
            InstallMode.CUSTOM -> scope.launch {
                dataStore.edit { p ->
                    p[PreferencesKeys.USE_SHIZUKU] = false
                    p[PreferencesKeys.USE_ROOT] = false
                    p[PreferencesKeys.USE_DHIZUKU] = false
                    p[PreferencesKeys.USE_MICROG] = false
                    p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = true
                }
            }
            InstallMode.MICROG -> scope.launch {
                if (app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(application)) {
                    dataStore.edit { p ->
                        p[PreferencesKeys.USE_SHIZUKU] = false
                        p[PreferencesKeys.USE_ROOT] = false
                        p[PreferencesKeys.USE_DHIZUKU] = false
                        p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                        p[PreferencesKeys.USE_MICROG] = true
                    }
                } else {
                    emitEvent(R.string.setting_microg_not_available)
                }
            }
        }
    }

    fun setUseShizuku(enabled: Boolean) {
        if (!enabled) {
            scope.launch {
                dataStore.edit { prefs -> prefs[PreferencesKeys.USE_SHIZUKU] = false }
            }
            return
        }
        updateShizukuState()
        when (_shizukuState.value) {
            ShizukuState.READY -> scope.launch {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.USE_ROOT] = false
                    prefs[PreferencesKeys.USE_DHIZUKU] = false
                    prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                    prefs[PreferencesKeys.USE_MICROG] = false
                    prefs[PreferencesKeys.USE_SHIZUKU] = true
                }
            }
            ShizukuState.NO_PERMISSION -> requestShizukuPermission()
            ShizukuState.NOT_RUNNING -> emitEvent(R.string.setting_shizuku_start_service_hint)
            ShizukuState.NOT_INSTALLED -> emitEvent(R.string.setting_shizuku_install_hint)
            ShizukuState.UNSUPPORTED -> emitEvent(R.string.setting_shizuku_unsupported)
        }
    }

    private fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ_CODE)
        } catch (t: Throwable) {
            Timber.w(t, "Shizuku.requestPermission threw")
            emitEvent(R.string.setting_shizuku_start_service_hint)
        }
    }

    fun setUseRoot(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                val state = backendFactory.requestRoot()
                _rootState.value = state
                if (state == RootState.READY) {
                    dataStore.edit { prefs ->
                        prefs[PreferencesKeys.USE_SHIZUKU] = false
                        prefs[PreferencesKeys.USE_DHIZUKU] = false
                        prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                        prefs[PreferencesKeys.USE_MICROG] = false
                        prefs[PreferencesKeys.USE_ROOT] = true
                    }
                }
            } else {
                dataStore.edit { prefs -> prefs[PreferencesKeys.USE_ROOT] = false }
            }
        }
    }

    fun retryRoot() {
        scope.launch {
            _rootState.value = RootState.UNKNOWN
        }
    }

    fun setUseDhizuku(enabled: Boolean) {
        if (!enabled) {
            scope.launch {
                dataStore.edit { prefs -> prefs[PreferencesKeys.USE_DHIZUKU] = false }
            }
            return
        }
        val state = DhizukuCompat.state(application)
        _dhizukuState.value = state
        when (state) {
            DhizukuState.UNSUPPORTED -> emitEvent(R.string.setting_dhizuku_unsupported)
            DhizukuState.NOT_INSTALLED -> emitEvent(R.string.setting_dhizuku_not_installed)
            DhizukuState.NOT_RUNNING -> emitEvent(R.string.setting_dhizuku_not_running)
            DhizukuState.PROFILE_OWNER_UNSUPPORTED -> emitEvent(R.string.setting_dhizuku_profile_owner_unsupported)
            DhizukuState.NOT_AUTHORIZED -> DhizukuCompat.requestPermission(application) { granted ->
                _dhizukuState.value = if (granted) DhizukuState.READY else DhizukuState.NOT_AUTHORIZED
                if (granted) commitDhizukuMode() else emitEvent(R.string.setting_dhizuku_denied)
            }
            DhizukuState.READY -> commitDhizukuMode()
        }
    }

    private fun commitDhizukuMode() = scope.launch {
        dataStore.edit { p ->
            p[PreferencesKeys.USE_SHIZUKU] = false
            p[PreferencesKeys.USE_ROOT] = false
            p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
            p[PreferencesKeys.USE_MICROG] = false
            p[PreferencesKeys.USE_DHIZUKU] = true
        }
    }

    fun setCustomAuthorizerCommand(command: String) = scope.launch {
        dataStore.edit { p ->
            p[PreferencesKeys.CUSTOM_AUTHORIZER_COMMAND] = command
        }
    }

    fun refreshDhizukuState() {
        _dhizukuState.value = if (useDhizuku.value) {
            DhizukuCompat.state(application)
        } else {
            DhizukuCompat.stateUnbound(application)
        }
    }

    fun setPrivilegedOption(option: SettingViewModel.PrivilegedOption, enabled: Boolean) {
        scope.launch {
            dataStore.edit { p ->
                p[option.shizukuKey] = enabled
                p[option.rootKey] = enabled
                option.dhizukuKey?.let { p[it] = enabled }
            }
        }
    }

    fun setInstallerPackageName(packageName: String) {
        scope.launch {
            dataStore.edit { p ->
                p[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = packageName
                p[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = packageName
            }
        }
    }

    fun toggleDefaultInstaller(enabled: Boolean) {
        updateShizukuState()
        val shizukuReady = _shizukuState.value == ShizukuState.READY
        val rootReady = _rootState.value == RootState.READY

        if (!shizukuReady && !rootReady) {
            reportDefaultInstaller("none", enabled, TelemetryEvents.RESULT_BLOCKED)
            when (_shizukuState.value) {
                ShizukuState.NO_PERMISSION -> requestShizukuPermission()
                ShizukuState.NOT_RUNNING -> emitEvent(R.string.setting_shizuku_start_service_hint)
                else -> emitEvent(R.string.setting_default_installer_needs_backend)
            }
            return
        }

        val component = defaultInstallerComponent()
        val method = if (shizukuReady) "shizuku" else "root"
        scope.launch(Dispatchers.IO) {
            val result = if (shizukuReady) {
                app.pwhs.universalinstaller.util.ShizukuDefaultInstaller
                    .setDefaultInstaller(component, enabled)
            } else {
                backendFactory.setDefaultInstallerViaRoot(application, component, enabled)
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
}
