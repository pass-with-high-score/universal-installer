package app.pwhs.universalinstaller.presentation.setting.security

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuShellExecutor
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.security.util.PinCryptoHelper
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.BiometricGate
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class SecurityUiState(
    val pinLockEnabled: Boolean = false,
    val pinLockInstall: Boolean = false,
    val pinLockUninstall: Boolean = false,
    val pinLockAppOpen: Boolean = false,
    val pinLockProtectSettings: Boolean = false,
    val hasPinSet: Boolean = false,
    val biometricLockInstall: Boolean = false,
    val biometricLockUninstall: Boolean = false,
    val biometricEnrolmentAvailable: Boolean = false,
    val blacklistCount: Int = 0,
    val isSettingsUnlocked: Boolean = false,
    val isSystemInstallerDisabled: Boolean = false,
    val isPrivilegeAvailable: Boolean = false,
)

class SecurityViewModel(
    private val application: Application,
) : ViewModel() {

    private val dataStore = application.dataStore

    private val isSettingsUnlockedState = MutableStateFlow(false)

    // Current cached hash and salt for synchronous verification
    @Volatile
    private var cachedHash: String = ""
    @Volatile
    private var cachedSalt: String = ""

    val uiState: StateFlow<SecurityUiState> = combine(
        dataStore.data,
        isSettingsUnlockedState,
    ) { prefs, isUnlocked ->
        val hash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
        val salt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
        cachedHash = hash
        cachedSalt = salt

        val hasPin = hash.isNotBlank() && salt.isNotBlank()
        val pinEnabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED] ?: false
        val pinInstall = prefs[PreferencesKeys.PIN_LOCK_INSTALL] ?: false
        val pinUninstall = prefs[PreferencesKeys.PIN_LOCK_UNINSTALL] ?: false
        val pinAppOpen = prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] ?: false
        val pinProtectSettings = prefs[PreferencesKeys.PIN_LOCK_PROTECT_SETTINGS] ?: false

        val bioInstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] ?: false
        val bioUninstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] ?: false
        val bioAvailable = BiometricGate.canAuthenticate(application)

        val blacklist = app.pwhs.universalinstaller.domain.manager.InstallBlacklist.read(prefs)

        val isRoot = runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false)
        val isShizuku = runCatching { ShizukuShellExecutor.isReady() }.getOrDefault(false)
        val isPrivilege = isRoot || isShizuku
        val isInstallerDisabled = SystemInstallerManager.isSystemPackageInstallerDisabled(application)

        SecurityUiState(
            pinLockEnabled = pinEnabled && hasPin,
            pinLockInstall = pinInstall && pinEnabled && hasPin,
            pinLockUninstall = pinUninstall && pinEnabled && hasPin,
            pinLockAppOpen = pinAppOpen && pinEnabled && hasPin,
            pinLockProtectSettings = pinProtectSettings && pinEnabled && hasPin,
            hasPinSet = hasPin,
            biometricLockInstall = bioInstall,
            biometricLockUninstall = bioUninstall,
            biometricEnrolmentAvailable = bioAvailable,
            blacklistCount = blacklist.size,
            isSettingsUnlocked = isUnlocked || !pinProtectSettings || !hasPin,
            isSystemInstallerDisabled = isInstallerDisabled,
            isPrivilegeAvailable = isPrivilege,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SecurityUiState(
            biometricEnrolmentAvailable = BiometricGate.canAuthenticate(application),
        ),
    )

    init {
        // Pre-populate cache synchronously at startup if available
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            cachedHash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
            cachedSalt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
        }
    }

    /**
     * Checks whether [inputPin] matches the stored PIN hash and salt.
     */
    fun verifyPin(inputPin: String): Boolean {
        var hash = cachedHash
        var salt = cachedSalt
        if (hash.isBlank() || salt.isBlank()) {
            runBlocking {
                val prefs = dataStore.data.first()
                hash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
                salt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
                cachedHash = hash
                cachedSalt = salt
            }
        }
        return PinCryptoHelper.verifyPin(inputPin, hash, salt)
    }

    /**
     * Sets a new PIN and activates PIN lock for installations by default.
     */
    fun setupPin(newPin: String) {
        val salt = PinCryptoHelper.generateSalt()
        val hash = PinCryptoHelper.hashPin(newPin, salt)
        cachedHash = hash
        cachedSalt = salt

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_HASH] = hash
                prefs[PreferencesKeys.PIN_LOCK_SALT] = salt
                prefs[PreferencesKeys.PIN_LOCK_ENABLED] = true
                prefs[PreferencesKeys.PIN_LOCK_INSTALL] = true
            }
            isSettingsUnlockedState.value = true
        }
    }

    /**
     * Updates to a new PIN.
     */
    fun changePin(newPin: String) {
        val salt = PinCryptoHelper.generateSalt()
        val hash = PinCryptoHelper.hashPin(newPin, salt)
        cachedHash = hash
        cachedSalt = salt

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_HASH] = hash
                prefs[PreferencesKeys.PIN_LOCK_SALT] = salt
            }
        }
    }

    /**
     * Disables PIN lock and clears stored PIN credentials.
     */
    fun disablePin() {
        cachedHash = ""
        cachedSalt = ""

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_ENABLED] = false
                prefs[PreferencesKeys.PIN_LOCK_INSTALL] = false
                prefs[PreferencesKeys.PIN_LOCK_UNINSTALL] = false
                prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] = false
                prefs[PreferencesKeys.PIN_LOCK_PROTECT_SETTINGS] = false
                prefs[PreferencesKeys.PIN_LOCK_HASH] = ""
                prefs[PreferencesKeys.PIN_LOCK_SALT] = ""
            }
            isSettingsUnlockedState.value = false
        }
    }

    fun setPinLockInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_INSTALL] = enabled
            }
        }
    }

    fun setPinLockUninstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_UNINSTALL] = enabled
            }
        }
    }

    fun setPinLockAppOpen(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] = enabled
            }
        }
    }

    fun setPinLockProtectSettings(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.PIN_LOCK_PROTECT_SETTINGS] = enabled
            }
        }
    }

    fun setBiometricLockInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] = enabled
            }
        }
    }

    fun setBiometricLockUninstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] = enabled
            }
        }
    }

    fun unlockSettings() {
        isSettingsUnlockedState.value = true
    }

    fun toggleSystemInstaller(disable: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false)
            val isShizuku = runCatching { ShizukuShellExecutor.isReady() }.getOrDefault(false)
            val success = SystemInstallerManager.setSystemInstallerEnabled(
                context = application,
                enabled = !disable,
                isRoot = isRoot,
                isShizuku = isShizuku,
            )
            if (success) {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.PIN_LOCK_DISABLE_SYSTEM_INSTALLER] = disable
                    if (disable) {
                        val hasElevated = prefs[PreferencesKeys.USE_SHIZUKU] == true ||
                            prefs[PreferencesKeys.USE_ROOT] == true ||
                            prefs[PreferencesKeys.USE_DHIZUKU] == true
                        if (!hasElevated) {
                            if (isShizuku) {
                                prefs[PreferencesKeys.USE_SHIZUKU] = true
                            } else if (isRoot) {
                                prefs[PreferencesKeys.USE_ROOT] = true
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun revokeOtherAppsInstallPermissions(onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false)
            val isShizuku = runCatching { ShizukuShellExecutor.isReady() }.getOrDefault(false)
            val count = SystemInstallerManager.revokeOtherAppsInstallPermission(
                context = application,
                isRoot = isRoot,
                isShizuku = isShizuku,
            )
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }
}
