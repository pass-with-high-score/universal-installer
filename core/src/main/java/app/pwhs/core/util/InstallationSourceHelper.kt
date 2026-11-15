package app.pwhs.core.util

import android.content.Context
import android.os.Build

object InstallationSourceHelper {
    private val KNOWN_STORE_INSTALLERS = setOf(
        "com.android.vending",             // Google Play Store
        "com.amazon.venezia",              // Amazon Appstore
        "com.sec.android.app.samsungapps", // Samsung Galaxy Store
        "com.huawei.appmarket",            // Huawei AppGallery
        "com.xiaomi.mipicks",              // Xiaomi GetApps
        "com.heytap.market",               // OPPO / Realme Market
        "com.oppo.market",                 // OPPO App Market
        "com.vivo.appstore",               // Vivo V-Appstore
        "org.fdroid.fdroid",               // F-Droid Client
    )

    /**
     * Determines whether this application was installed via an app store
     * or belongs to a store distribution build flavor.
     */
    fun isInstalledFromStore(context: Context, isStoreFlavor: Boolean = false): Boolean {
        if (isStoreFlavor) return true

        val pm = context.packageManager
        val installerPkg = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()

        return !installerPkg.isNullOrBlank() && installerPkg in KNOWN_STORE_INSTALLERS
    }
}
