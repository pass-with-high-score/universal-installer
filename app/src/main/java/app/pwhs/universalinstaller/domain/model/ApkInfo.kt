package app.pwhs.universalinstaller.domain.model

import android.graphics.drawable.Drawable
import android.net.Uri

enum class SplitType { Base, Libs, Locale, ScreenDensity, Feature, Other }

data class SplitEntry(
    val name: String,
    val type: SplitType,
    val uri: Uri,
    val sizeBytes: Long,
    val selected: Boolean = true,
)

data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val fileSizeBytes: Long,
    val permissions: List<String>,
    val splitCount: Int = 1,
    val fileFormat: String = "APK",
    val supportedAbis: List<String> = emptyList(),
    val sha256: String = "",
    val vtResult: VtResult? = null,
    val obbFileNames: List<String> = emptyList(),
    val obbTotalBytes: Long = 0L,
    val splitEntries: List<SplitEntry> = emptyList(),
    /** Installed version on this device, null if not installed. Powers the downgrade chip. */
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    /**
     * Whether the installed copy was signed with a different key, so Android will refuse this file
     * as an update. `null` means we could not determine it — treat as "no problem known", never as
     * a mismatch. See [app.pwhs.universalinstaller.util.SignatureCheck].
     */
    val signatureMismatch: Boolean? = null,
    /** The user put this package on the never-install list; the Install button is disabled. */
    val isBlocked: Boolean = false,
    /** Whether the APK declares Android Auto compatibility (services or metadata). */
    val isAndroidAutoSupported: Boolean = false,
    /** Whether the APK declares `uses-feature android.hardware.type.watch`. */
    val isWearOsSupported: Boolean = false,
) {
    val isRootRequested: Boolean
        get() = permissions.contains("android.permission.ACCESS_SUPERUSER")

    val isShizukuRequested: Boolean
        get() = permissions.contains("moe.shizuku.manager.permission.API_V23")
}
