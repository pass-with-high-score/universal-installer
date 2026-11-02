package app.pwhs.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Helper to discover Shizuku-compatible manager applications (Shizuku, Shevery, Porter,
 * or future forks) and build dynamic root starter scripts.
 */
object ShizukuStarterHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHEVERY_PACKAGE = "com.hamondev.shevery"
    const val PORTER_PACKAGE = "eu.darken.porter"
    const val SHEVERY_PERMISSION = "com.hamondev.shevery.permission.PRIVILEGED_API"

    const val ACTION_REQUEST_PERMISSION = "moe.shizuku.manager.action.REQUEST_PERMISSION"

    val KNOWN_MANAGERS = listOf(
        SHIZUKU_PACKAGE,
        SHEVERY_PACKAGE
    )

    val KNOWN_STARTER_PACKAGES = listOf(
        SHIZUKU_PACKAGE,
        SHEVERY_PACKAGE,
        PORTER_PACKAGE
    )

    /**
     * Finds the package name of an installed Shizuku manager (or fork like Shevery).
     * 1. Dynamic intent-based discovery for activities handling [ACTION_REQUEST_PERMISSION].
     * 2. Fallback to [KNOWN_MANAGERS].
     */
    fun findInstalledManagerPackage(context: Context): String? {
        val pm = context.packageManager
        // 1. Dynamic intent-based discovery
        runCatching {
            val intent = Intent(ACTION_REQUEST_PERMISSION)
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val pkg = resolveInfos.firstOrNull()?.activityInfo?.packageName
            if (!pkg.isNullOrBlank()) return pkg
        }

        // 2. Fallback to known packages
        for (pkg in KNOWN_MANAGERS) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.isSuccess
            if (installed) return pkg
        }

        return null
    }

    /**
     * Checks if any Shizuku-compatible manager is installed.
     */
    fun isManagerInstalled(context: Context): Boolean = findInstalledManagerPackage(context) != null

    /**
     * Checks if the detected manager is Shevery.
     */
    fun isShevery(context: Context): Boolean = findInstalledManagerPackage(context) == SHEVERY_PACKAGE

    /**
     * Returns a launch intent for the installed manager, or null if none is installed.
     */
    fun getManagerLaunchIntent(context: Context): Intent? {
        val pkg = findInstalledManagerPackage(context) ?: return null
        return context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Builds a shell command to start the Shizuku server via root.
     * Iterates dynamically over [installedPackage] (or discovered from [context] if provided)
     * and [KNOWN_STARTER_PACKAGES].
     */
    fun buildStarterScript(context: Context? = null, installedPackage: String? = null): String {
        val detected = installedPackage ?: context?.let { findInstalledManagerPackage(it) }
        val packages = listOfNotNull(detected)
            .plus(KNOWN_STARTER_PACKAGES)
            .distinct()
            .joinToString(" ")

        return """
            STARTED=0
            for PKG in $packages; do
                if [ -f "/data/user_de/0/${'$'}PKG/bin/shizuku_starter" ]; then
                    /data/user_de/0/${'$'}PKG/bin/shizuku_starter
                    STARTED=1
                    break
                elif [ -f "/sdcard/Android/data/${'$'}PKG/start.sh" ]; then
                    sh "/sdcard/Android/data/${'$'}PKG/start.sh"
                    STARTED=1
                    break
                fi
            done

            if [ ${'$'}STARTED -eq 0 ]; then
                for PKG in $packages; do
                    APK=${'$'}(pm path "${'$'}PKG" 2>/dev/null | head -n 1 | cut -d: -f2)
                    if [ -n "${'$'}APK" ]; then
                        LIB=${'$'}(find ${'$'}(dirname "${'$'}APK")/lib -name "libshizuku.so" 2>/dev/null | head -n 1)
                        if [ -f "${'$'}LIB" ]; then
                            cp "${'$'}LIB" /data/local/tmp/shizuku_starter
                            chmod 755 /data/local/tmp/shizuku_starter
                            /data/local/tmp/shizuku_starter
                            STARTED=1
                            break
                        fi
                    fi
                done
            fi

            if [ ${'$'}STARTED -eq 0 ]; then
                if [ -x /data/local/tmp/shizuku_starter ]; then
                    /data/local/tmp/shizuku_starter
                fi
            fi
        """.trimIndent()
    }
}
