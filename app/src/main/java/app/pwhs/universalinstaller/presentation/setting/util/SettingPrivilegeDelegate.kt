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
import kotlinx.coroutines.flow.first
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

    private var pendingCombinedMode = false

    private val defaultRoleDelegate = SettingDefaultRoleDelegate(
        application = application,
        scope = scope,
        backendFactory = backendFactory,
        shizukuState = { _shizukuState.value },
        rootState = { _rootState.value },
        updateShizukuState = { updateShizukuState() },
        requestShizukuPermission = { requestShizukuPermission() },
        emitEvent = emitEvent,
    )

    val isDefaultInstaller: StateFlow<Boolean> = defaultRoleDelegate.isDefaultInstaller
    val isDefaultUninstaller: StateFlow<Boolean> = defaultRoleDelegate.isDefaultUninstaller

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
                        val keepDhizuku = pendingCombinedMode || (prefs[PreferencesKeys.USE_DHIZUKU] ?: false)
                        prefs[PreferencesKeys.USE_ROOT] = false
                        prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
                        prefs[PreferencesKeys.USE_MICROG] = false
                        prefs[PreferencesKeys.USE_SHIZUKU] = true
                        prefs[PreferencesKeys.USE_DHIZUKU] = keepDhizuku
                        pendingCombinedMode = false
                    }
                }
            } else {
                pendingCombinedMode = false
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
        scope.launch {
            useDhizuku.collect { enabled ->
                refreshDhizukuState()
                if (enabled) {
                    if (_dhizukuState.value != DhizukuState.READY) {
                        kotlinx.coroutines.delay(400)
                        refreshDhizukuState()
                    }
                    if (_dhizukuState.value != DhizukuState.READY) {
                        kotlinx.coroutines.delay(800)
                        refreshDhizukuState()
                    }
                }
            }
        }
        defaultRoleDelegate.updateDefaultInstallerStatus()
        defaultRoleDelegate.updateDefaultUninstallerStatus()
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
            InstallMode.SHIZUKU_DHIZUKU -> {
                setUseShizukuAndDhizuku()
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
        pendingCombinedMode = false
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
        pendingCombinedMode = false
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
                if (granted) commitDhizukuMode(keepShizuku = false) else emitEvent(R.string.setting_dhizuku_denied)
            }
            DhizukuState.READY -> commitDhizukuMode(keepShizuku = false)
        }
    }

    private fun commitDhizukuMode(keepShizuku: Boolean = false) = scope.launch {
        dataStore.edit { p ->
            p[PreferencesKeys.USE_ROOT] = false
            p[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = false
            p[PreferencesKeys.USE_MICROG] = false
            p[PreferencesKeys.USE_DHIZUKU] = true
            p[PreferencesKeys.USE_SHIZUKU] = keepShizuku
        }
    }

    fun setUseShizukuAndDhizuku() {
        pendingCombinedMode = true
        val dState = DhizukuCompat.state(application)
        _dhizukuState.value = dState
        updateShizukuState()
        val sState = _shizukuState.value

        if (dState == DhizukuState.UNSUPPORTED && sState == ShizukuState.UNSUPPORTED) {
            emitEvent(R.string.setting_shizuku_unsupported)
            return
        }
        if (dState == DhizukuState.PROFILE_OWNER_UNSUPPORTED && sState != ShizukuState.READY) {
            emitEvent(R.string.setting_dhizuku_profile_owner_unsupported)
        }

        if (dState == DhizukuState.NOT_AUTHORIZED) {
            DhizukuCompat.requestPermission(application) { granted ->
                _dhizukuState.value = if (granted) DhizukuState.READY else DhizukuState.NOT_AUTHORIZED
                if (granted) {
                    commitDhizukuMode(keepShizuku = true)
                } else {
                    emitEvent(R.string.setting_dhizuku_denied)
                }
            }
        }

        if (sState == ShizukuState.NO_PERMISSION) {
            requestShizukuPermission()
        }

        if (dState == DhizukuState.READY || sState == ShizukuState.READY) {
            commitDhizukuMode(keepShizuku = true)
        } else if (dState == DhizukuState.NOT_RUNNING && sState == ShizukuState.NOT_RUNNING) {
            emitEvent(R.string.setting_shizuku_dhizuku_not_running)
        }
    }

    fun setCustomAuthorizerCommand(command: String) = scope.launch {
        dataStore.edit { p ->
            p[PreferencesKeys.CUSTOM_AUTHORIZER_COMMAND] = command
        }
    }

    fun refreshDhizukuState() {
        scope.launch(Dispatchers.IO) {
            val state = if (useDhizuku.value) {
                DhizukuCompat.state(application)
            } else {
                DhizukuCompat.stateUnbound(application)
            }
            _dhizukuState.value = state
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

    fun toggleDefaultInstaller(enabled: Boolean) = defaultRoleDelegate.toggleDefaultInstaller(enabled)
    fun updateDefaultInstallerStatus() = defaultRoleDelegate.updateDefaultInstallerStatus()
    fun toggleDefaultUninstaller(enabled: Boolean) = defaultRoleDelegate.toggleDefaultUninstaller(enabled)
    fun updateDefaultUninstallerStatus() = defaultRoleDelegate.updateDefaultUninstallerStatus()
}
