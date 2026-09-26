package app.pwhs.universalinstaller.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import timber.log.Timber

/**
 * Helper to launch URLs in Chrome Custom Tabs with automatic fallback to standard ACTION_VIEW.
 */
object CustomTabsHelper {

    fun openUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            Timber.w(e, "CustomTabs launch failed for $url, falling back to standard ACTION_VIEW")
            runCatching {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
}
