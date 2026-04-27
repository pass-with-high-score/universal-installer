package app.pwhs.universalinstaller.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around BiometricPrompt for one-shot "user must authenticate before X"
 * gates. Falls open (calls [onSuccess]) when [enabled] is false or the device has no
 * enrolled biometric — the gate is best-effort UX, not a security boundary, and silently
 * blocking destructive actions on a device without a fingerprint sensor would just frustrate
 * the user.
 *
 * Allowed authenticators include device credential (PIN/pattern/password) so users without
 * fingerprint hardware can still gate behind their lock screen.
 */
object BiometricGate {

    /** True when the device has at least one biometric or device-credential enrolled. */
    fun canAuthenticate(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return mgr.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Prompt the user to authenticate. If [enabled] is false → calls [onSuccess] immediately
     * (no prompt). If the device can't authenticate → also calls [onSuccess] immediately so
     * the action proceeds. On user cancel/negative → [onCancel]. On hard error → [onCancel]
     * with the system-supplied message available via [onError] if you want to surface it.
     */
    fun authenticate(
        activity: FragmentActivity,
        enabled: Boolean,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onCancel: () -> Unit = {},
        onError: ((String) -> Unit)? = null,
    ) {
        if (!enabled || !canAuthenticate(activity)) {
            onSuccess()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Treat user-driven dismissals as plain cancel (no toast). Hard errors get
                // surfaced via onError so the screen can show feedback if it cares.
                val isUserCancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (!isUserCancel) onError?.invoke(errString.toString())
                onCancel()
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }
}
