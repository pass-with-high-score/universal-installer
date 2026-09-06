package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.security.components.PinDialog
import app.pwhs.universalinstaller.presentation.setting.security.components.PinDialogMode
import app.pwhs.universalinstaller.presentation.setting.security.util.PinCryptoHelper
import app.pwhs.universalinstaller.util.BiometricGate
import kotlinx.coroutines.flow.map

class SecurityGateController(
    val isPinRequired: Boolean,
    val isBiometricRequired: Boolean,
    private val onTriggerGate: (onSuccess: () -> Unit) -> Unit,
) {
    fun authenticate(onSuccess: () -> Unit) {
        onTriggerGate(onSuccess)
    }
}

private data class GateConfig(
    val isPinRequired: Boolean,
    val isBiometricRequired: Boolean,
    val pinHash: String,
    val pinSalt: String,
)

/**
 * Reusable security gate for installation flows.
 * Enforces PIN authentication (if enabled) followed by Biometric authentication (if enabled).
 */
@Composable
fun rememberInstallSecurityGate(
    context: Context,
    onCancel: () -> Unit = {},
): SecurityGateController {
    val prefsFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            val pinEnabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED] ?: false
            val pinInstall = prefs[PreferencesKeys.PIN_LOCK_INSTALL] ?: false
            val pinHash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
            val pinSalt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
            val bioInstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] ?: false

            val hasPin = pinEnabled && pinInstall && pinHash.isNotBlank() && pinSalt.isNotBlank()
            GateConfig(
                isPinRequired = hasPin,
                isBiometricRequired = bioInstall,
                pinHash = pinHash,
                pinSalt = pinSalt,
            )
        }
    }
    val config by prefsFlow.collectAsState(initial = GateConfig(false, false, "", ""))

    var showPinDialog by remember { mutableStateOf(false) }
    var pendingSuccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun runBiometric(action: () -> Unit) {
        val activity = context as? FragmentActivity
        if (activity != null && config.isBiometricRequired) {
            BiometricGate.authenticate(
                activity = activity,
                enabled = true,
                title = context.getString(R.string.biometric_install_title),
                subtitle = context.getString(R.string.biometric_install_sub),
                onSuccess = action,
                onCancel = onCancel,
            )
        } else {
            action()
        }
    }

    if (showPinDialog) {
        PinDialog(
            mode = PinDialogMode.Verify(
                titleRes = R.string.pin_dialog_title_verify,
                descRes = R.string.pin_dialog_desc_install,
            ),
            onDismiss = {
                showPinDialog = false
                pendingSuccessAction = null
                onCancel()
            },
            onVerifyPin = { pin ->
                PinCryptoHelper.verifyPin(pin, config.pinHash, config.pinSalt)
            },
            onSuccess = {
                showPinDialog = false
                val action = pendingSuccessAction
                pendingSuccessAction = null
                if (action != null) {
                    runBiometric(action)
                }
            },
        )
    }

    return remember(config) {
        SecurityGateController(
            isPinRequired = config.isPinRequired,
            isBiometricRequired = config.isBiometricRequired,
            onTriggerGate = { onSuccess ->
                if (config.isPinRequired) {
                    pendingSuccessAction = onSuccess
                    showPinDialog = true
                } else {
                    runBiometric(onSuccess)
                }
            },
        )
    }
}

/**
 * Security gate for uninstallation flows.
 * Enforces PIN authentication (if enabled) followed by Biometric authentication (if enabled).
 */
@Composable
fun rememberUninstallSecurityGate(
    context: Context,
): (packageName: String, appName: String, performUninstall: (String) -> Unit) -> Unit {
    val prefsFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            val pinEnabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED] ?: false
            val pinUninstall = prefs[PreferencesKeys.PIN_LOCK_UNINSTALL] ?: false
            val pinHash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
            val pinSalt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
            val bioUninstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] ?: false

            val hasPin = pinEnabled && pinUninstall && pinHash.isNotBlank() && pinSalt.isNotBlank()
            GateConfig(
                isPinRequired = hasPin,
                isBiometricRequired = bioUninstall,
                pinHash = pinHash,
                pinSalt = pinSalt,
            )
        }
    }
    val config by prefsFlow.collectAsState(initial = GateConfig(false, false, "", ""))

    var showPinDialog by remember { mutableStateOf(false) }
    var pendingPkg by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingAppName by remember { mutableStateOf("") }

    fun runBiometric(pkg: String, name: String, action: (String) -> Unit) {
        val activity = context as? FragmentActivity
        if (activity != null && config.isBiometricRequired) {
            BiometricGate.authenticate(
                activity = activity,
                enabled = true,
                title = context.getString(R.string.biometric_uninstall_title),
                subtitle = context.getString(R.string.biometric_uninstall_sub, name),
                onSuccess = { action(pkg) },
            )
        } else {
            action(pkg)
        }
    }

    if (showPinDialog) {
        PinDialog(
            mode = PinDialogMode.Verify(
                titleRes = R.string.pin_dialog_title_verify,
                descRes = R.string.pin_dialog_desc_uninstall,
            ),
            onDismiss = {
                showPinDialog = false
                pendingPkg = null
                pendingAction = null
            },
            onVerifyPin = { pin ->
                PinCryptoHelper.verifyPin(pin, config.pinHash, config.pinSalt)
            },
            onSuccess = {
                showPinDialog = false
                val pkg = pendingPkg
                val action = pendingAction
                val name = pendingAppName
                pendingPkg = null
                pendingAction = null
                if (pkg != null && action != null) {
                    runBiometric(pkg, name, action)
                }
            },
        )
    }

    return remember(config) {
        { pkg, appName, action ->
            if (config.isPinRequired) {
                pendingPkg = pkg
                pendingAppName = appName
                pendingAction = action
                showPinDialog = true
            } else {
                runBiometric(pkg, appName, action)
            }
        }
    }
}

