package app.pwhs.universalinstaller.util.extension

import android.app.Activity
import android.os.Build

/**
 * Disables the standard activity transition animations.
 *
 * For API 34+, it uses the new [Activity.overrideActivityTransition] API.
 * For older versions, it falls back to the deprecated [Activity.overridePendingTransition].
 */
fun Activity.disableSceneTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
