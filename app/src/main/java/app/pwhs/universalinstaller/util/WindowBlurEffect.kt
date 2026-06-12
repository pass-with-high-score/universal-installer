package app.pwhs.universalinstaller.util

import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Applies a background blur behind the window on Android 12+ (API 31+).
 * Falls back to no-op on older versions.
 */
@Composable
fun WindowBlurEffect(
    blurRadius: Int = 30,
    enabled: Boolean = true,
) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val view = LocalView.current
    DisposableEffect(blurRadius) {
        val window = findWindow(view)
        
        window?.let { w ->
            // setBackgroundBlurRadius blurs the area behind the window.
            w.setBackgroundBlurRadius(blurRadius)
            
            // setBlurBehindRadius blurs everything behind the window on the system level.
            w.attributes.blurBehindRadius = blurRadius
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            
            // Fix "square blur": ensure the window outline is rounded.
            // 28dp radius matches MaterialTheme.shapes.extraLarge
            val density = view.resources.displayMetrics.density
            val radius = 28 * density
            
            w.decorView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            w.decorView.clipToOutline = true
        }
        
        onDispose {
            window?.let { w ->
                w.setBackgroundBlurRadius(0)
                w.attributes.blurBehindRadius = 0
                w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.decorView.clipToOutline = false
                w.decorView.outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        }
    }
}

private fun findWindow(view: android.view.View): Window? {
    var context = view.context
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context.window
        context = context.baseContext
    }
    // Fallback for cases where context is not an Activity (e.g. inside some Dialogs)
    return view.rootView?.let { rootView ->
        // In many Dialog implementations, the decorView (rootView) is the one holding the window
        // But there is no public API to get Window from DecorView directly.
        // However, if we are here, we might just be out of luck without reflection.
        null
    }
}
