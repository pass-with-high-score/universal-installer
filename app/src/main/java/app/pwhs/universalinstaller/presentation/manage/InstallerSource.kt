package app.pwhs.universalinstaller.presentation.manage

import android.content.Intent
import androidx.core.net.toUri

/**
 * Friendly description + deep-link for an app's known installer source. Returned only for
 * stores we can route the user back to; sideload / unknown installers fall through to null
 * because we have no canonical "view this app" URL for them.
 */
data class InstallerInfo(
    val displayName: String,
    val intent: Intent,
)

/**
 * Map an installer package name → store name + listing intent. Returns null for sideload
 * and unrecognised installers; callers gate the "Open in store" action on this.
 *
 * Aurora Store is treated as a Play Store proxy — same `play.google.com` URL, but we do
 * NOT pin `setPackage("com.aurora.store")` because Aurora itself accepts the Play URL via
 * its intent filter, and bypassing the chooser when both Aurora + Play are installed
 * picks whichever the user already set as default.
 */
fun resolveInstallerInfo(installerPackage: String?, packageName: String): InstallerInfo? {
    return when (installerPackage) {
        "com.android.vending" -> InstallerInfo(
            displayName = "Play Store",
            intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri(),
            ).setPackage("com.android.vending"),
        )
        "org.fdroid.fdroid",
        "org.fdroid.basic" -> InstallerInfo(
            displayName = "F-Droid",
            // F-Droid registers an intent filter on f-droid.org/packages — the deep-link
            // works as long as F-Droid is installed; if uninstalled later we'd just open
            // the URL in a browser, which is acceptable graceful degradation.
            intent = Intent(
                Intent.ACTION_VIEW,
                "https://f-droid.org/packages/$packageName/".toUri(),
            ).setPackage(installerPackage),
        )
        "com.aurora.store" -> InstallerInfo(
            displayName = "Aurora Store",
            intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri(),
            ),
        )
        else -> null
    }
}
