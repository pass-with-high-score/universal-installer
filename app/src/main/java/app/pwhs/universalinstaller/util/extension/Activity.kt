package app.pwhs.universalinstaller.util.extension

import android.app.Activity
import app.pwhs.core.util.disableSceneTransition as coreDisableSceneTransition

/**
 * Disables the standard activity transition animations.
 *
 * For API 34+, it uses the new [Activity.overrideActivityTransition] API.
 * For older versions, it falls back to the deprecated [Activity.overridePendingTransition].
 */
fun Activity.disableSceneTransition() {
    coreDisableSceneTransition()
}
