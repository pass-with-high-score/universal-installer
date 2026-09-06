package app.pwhs.universalinstaller.presentation.setting.security.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.security.util.AppLockManager
import app.pwhs.universalinstaller.presentation.setting.security.util.PinCryptoHelper
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Gate composable that locks the app and requires PIN verification if "Lock App on Open" is active
 * and the app hasn't been unlocked in the current session.
 */
@Composable
fun AppLockGate() {
    val context = LocalContext.current
    val isAppUnlocked by AppLockManager.isAppUnlocked.collectAsState()

    val lockConfigFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            val enabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED] ?: false
            val appOpen = prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] ?: false
            val hash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
            val salt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
            (enabled && appOpen && hash.isNotBlank() && salt.isNotBlank()) to (hash to salt)
        }
    }
    val initialLockConfig = remember(context) {
        runCatching {
            runBlocking {
                val prefs = context.dataStore.data.first()
                val enabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED] ?: false
                val appOpen = prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] ?: false
                val hash = prefs[PreferencesKeys.PIN_LOCK_HASH].orEmpty()
                val salt = prefs[PreferencesKeys.PIN_LOCK_SALT].orEmpty()
                (enabled && appOpen && hash.isNotBlank() && salt.isNotBlank()) to (hash to salt)
            }
        }.getOrDefault(false to ("" to ""))
    }
    val lockConfig by lockConfigFlow.collectAsState(initial = initialLockConfig)
    val (isLockRequired, credentials) = lockConfig

    if (isLockRequired && !isAppUnlocked) {
        BackHandler {
            val activity = context as? Activity
            activity?.finishAffinity()
        }

        PinDialog(
            mode = PinDialogMode.Verify(
                titleRes = R.string.pin_dialog_title_verify,
                descRes = R.string.pin_dialog_desc_app_open,
            ),
            onDismiss = {
                val activity = context as? Activity
                activity?.finishAffinity()
            },
            onVerifyPin = { pin ->
                val (hash, salt) = credentials
                PinCryptoHelper.verifyPin(pin, hash, salt)
            },
            onSuccess = {
                AppLockManager.unlock()
            },
        )
    }
}
