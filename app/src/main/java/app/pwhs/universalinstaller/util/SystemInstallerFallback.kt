package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.net.toUri
import app.pwhs.core.security.GhostMapPregate
import timber.log.Timber

/**
 * Builds an install intent aimed at somebody *other than us*.
 *
 * The "Install via System Installer" button used to fire a bare `ACTION_VIEW` for the APK mime
 * type. That works right up until the user takes us up on the offer to become the default
 * installer — from then on the mime resolves straight back to `DialogInstallActivity`, so the
 * dialog closed and nothing happened at all (#110). The way out of a preferred-activity
 * registration is an explicit component, which is what this resolves.
 *
 * `ACTION_INSTALL_PACKAGE` is tried after `ACTION_VIEW` because some OEM installers only
 * advertise the older action, and the point of this button is to reach whatever the device has.
 */
object SystemInstallerFallback {

    private const val MIME = "application/vnd.android.package-archive"

    @Suppress("DEPRECATION")
    private val ACTIONS = listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)

    /**
     * @return an intent with an explicit component set, or null when this device has no other
     *   installer to hand the file to — in which case there is nothing to offer the user.
     */
    fun resolve(context: Context, apkUri: Uri): Intent? {
        val check = GhostMapPregate.validateUri(context, apkUri)
        if (check is GhostMapPregate.ValidationResult.Invalid) {
            Timber.w("Refusing to resolve system installer fallback for GhostMap malicious APK: ${check.reason}")
            return null
        }
        for (action in ACTIONS) {
            val target = candidates(context, action)
                // Prefer the platform installer over, say, a file manager that also claims APKs:
                // the button promises the system one.
                .minByOrNull { if (it.isSystem) 0 else 1 }
                ?: continue
            Timber.d("Fallback install via %s (%s)", target.component.flattenToShortString(), action)
            return Intent(action).apply {
                component = target.component
                setDataAndType(apkUri, MIME)
                // Forwards the read grant we hold on the source URI to whoever we hand it to;
                // without it the installer gets a URI it cannot open.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        }
        return null
    }

    private class Candidate(val component: ComponentName, val isSystem: Boolean)

    private fun candidates(context: Context, action: String): List<Candidate> {
        val probe = Intent(action).setDataAndType("content://probe/base.apk".toUri(), MIME)
        return query(context, probe)
            .asSequence()
            .filter { it.activityInfo != null }
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { it.activityInfo.exported && it.activityInfo.enabled }
            .map {
                Candidate(
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    isSystem = it.activityInfo.applicationInfo.flags and
                        ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .toList()
    }

    /**
     * `MATCH_DEFAULT_ONLY` asks the question the user is asking — "who would normally open this"
     * — but an OEM installer that omits `CATEGORY_DEFAULT` would be invisible to it, so a plain
     * query is the fallback. Both are fine to start explicitly afterwards.
     */
    private fun query(context: Context, intent: Intent): List<ResolveInfo> {
        val pm = context.packageManager
        val preferred = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.onFailure { Timber.w(it, "queryIntentActivities(MATCH_DEFAULT_ONLY) failed") }
            .getOrDefault(emptyList())
        if (preferred.isNotEmpty()) return preferred
        return runCatching { pm.queryIntentActivities(intent, 0) }
            .onFailure { Timber.w(it, "queryIntentActivities failed") }
            .getOrDefault(emptyList())
    }
}
