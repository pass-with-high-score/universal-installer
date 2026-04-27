package app.pwhs.universalinstaller.util

import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Applies a background blur behind the window on Android 12+ (API 31+).
 * Falls back to no-op on older versions. Inspired by InstallerX-Revived's WindowBlurEffect.
 */
@Composable
fun WindowBlurEffect(
    blurRadius: Int = 30,
    enabled: Boolean = true,
) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val view = LocalView.current
    DisposableEffect(blurRadius) {
        val window: Window? = (view.context as? android.app.Activity)?.window
        window?.let {
            it.setBackgroundBlurRadius(blurRadius)
        }
        onDispose {
            window?.setBackgroundBlurRadius(0)
        }
    }
}
