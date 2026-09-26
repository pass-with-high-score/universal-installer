package app.pwhs.universalinstaller.presentation.setting.util

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallBackend
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.util.MicroGCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingPriorityDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val backendFactory: InstallerBackendFactory,
    private val shizukuState: () -> ShizukuState,
    private val updateShizukuState: () -> Unit,
    private val requestShizukuPermission: () -> Unit,
    private val updateDhizukuState: (DhizukuState) -> Unit,
    private val updateRootState: (RootState) -> Unit,
    private val emitEvent: (Int) -> Unit,
) {
    private val dataStore = application.dataStore

    val backendPriority: StateFlow<List<InstallBackend>> = dataStore.data
        .map { prefs ->
            InstallBackend.parsePriorityList(prefs[PreferencesKeys.INSTALL_BACKEND_PRIORITY])
        }
        .stateIn(scope, SharingStarted.Eagerly, InstallBackend.DEFAULT_ORDER)

    fun setBackendPriority(list: List<InstallBackend>) = scope.launch {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.INSTALL_BACKEND_PRIORITY] = InstallBackend.serializePriorityList(list)
        }
    }

    fun moveBackendPriority(fromIndex: Int, toIndex: Int) {
        val current = backendPriority.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setBackendPriority(current)
        }
    }

    fun resetBackendPriority() {
        setBackendPriority(InstallBackend.DEFAULT_ORDER)
    }

    fun promoteToTop(backend: InstallBackend) {
        val current = backendPriority.value.toMutableList()
        val index = current.indexOf(backend)
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(0, item)
            setBackendPriority(current)
        }
    }

    fun setBackendEnabled(backend: InstallBackend, enabled: Boolean) {
        scope.launch {
            when (backend) {
                InstallBackend.SHIZUKU -> {
                    if (!enabled) {
                        dataStore.edit { it[PreferencesKeys.USE_SHIZUKU] = false }
                        return@launch
                    }
                    updateShizukuState()
                    when (shizukuState()) {
                        ShizukuState.READY -> dataStore.edit { it[PreferencesKeys.USE_SHIZUKU] = true }
                        ShizukuState.NO_PERMISSION -> requestShizukuPermission()
                        ShizukuState.NOT_RUNNING -> {
                            dataStore.edit { it[PreferencesKeys.USE_SHIZUKU] = true }
                            emitEvent(R.string.setting_shizuku_start_service_hint)
                        }
                        ShizukuState.NOT_INSTALLED -> emitEvent(R.string.setting_shizuku_install_hint)
                        ShizukuState.UNSUPPORTED -> emitEvent(R.string.setting_shizuku_unsupported)
                    }
                }
                InstallBackend.DHIZUKU -> {
                    if (!enabled) {
                        dataStore.edit { it[PreferencesKeys.USE_DHIZUKU] = false }
                        return@launch
                    }
                    val state = DhizukuCompat.state(application)
                    updateDhizukuState(state)
                    when (state) {
                        DhizukuState.READY -> dataStore.edit { it[PreferencesKeys.USE_DHIZUKU] = true }
                        DhizukuState.NOT_AUTHORIZED -> DhizukuCompat.requestPermission(application) { granted ->
                            val newState = if (granted) DhizukuState.READY else DhizukuState.NOT_AUTHORIZED
                            updateDhizukuState(newState)
                            if (granted) {
                                scope.launch { dataStore.edit { it[PreferencesKeys.USE_DHIZUKU] = true } }
                            } else {
                                emitEvent(R.string.setting_dhizuku_denied)
                            }
                        }
                        DhizukuState.NOT_RUNNING -> {
                            dataStore.edit { it[PreferencesKeys.USE_DHIZUKU] = true }
                            emitEvent(R.string.setting_dhizuku_not_running)
                        }
                        DhizukuState.PROFILE_OWNER_UNSUPPORTED -> emitEvent(R.string.setting_dhizuku_profile_owner_unsupported)
                        DhizukuState.UNSUPPORTED -> emitEvent(R.string.setting_dhizuku_unsupported)
                        DhizukuState.NOT_INSTALLED -> emitEvent(R.string.setting_dhizuku_not_installed)
                    }
                }
                InstallBackend.ROOT -> {
                    if (!enabled) {
                        dataStore.edit { it[PreferencesKeys.USE_ROOT] = false }
                        return@launch
                    }
                    val state = backendFactory.requestRoot()
                    updateRootState(state)
                    if (state == RootState.READY) {
                        dataStore.edit { it[PreferencesKeys.USE_ROOT] = true }
                    } else {
                        emitEvent(R.string.installer_engine_root_request)
                    }
                }
                InstallBackend.CUSTOM -> {
                    dataStore.edit { it[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = enabled }
                }
                InstallBackend.MICROG -> {
                    if (enabled) {
                        if (MicroGCompat.isAvailable(application)) {
                            dataStore.edit { it[PreferencesKeys.USE_MICROG] = true }
                        } else {
                            emitEvent(R.string.setting_microg_not_available)
                        }
                    } else {
                        dataStore.edit { it[PreferencesKeys.USE_MICROG] = false }
                    }
                }
                InstallBackend.DEFAULT -> {
                    if (SystemInstallerManager.isSystemPackageInstallerDisabled(application)) {
                        emitEvent(R.string.setting_system_installer_frozen_cannot_select)
                    }
                }
            }
        }
    }
}
