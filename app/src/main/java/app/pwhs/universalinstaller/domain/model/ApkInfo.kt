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
    val certSha256: String = "",
    val vtResult: VtResult? = null,
    val obbFileNames: List<String> = emptyList(),
    val obbTotalBytes: Long = 0L,
    val splitEntries: List<SplitEntry> = emptyList(),
    /** Installed version on this device, null if not installed. Powers the downgrade chip. */
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    /** Permissions requested by the currently installed version on this device, if installed. */
    val installedPermissions: List<String> = emptyList(),
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
    /** Trackers detected in the APK bytecode matching Exodus Privacy database. */
    val trackers: List<TrackerInfo> = emptyList(),
    /** Whether tracker detection is currently running in the background. */
    val isScanningTrackers: Boolean = false,
) {
    val isRootRequested: Boolean
        get() = permissions.contains("android.permission.ACCESS_SUPERUSER")

    val isShizukuRequested: Boolean
        get() = permissions.contains("moe.shizuku.manager.permission.API_V23")

    val isAbiIncompatible: Boolean
        get() = supportedAbis.isNotEmpty() && !app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isCompatible(supportedAbis)

    val isUpdateInstalled: Boolean
        get() = installedVersionCode != null && installedVersionCode > 0L

    val addedPermissions: List<String>
        get() {
            if (!isUpdateInstalled) return emptyList()
            val installedSet = installedPermissions.toSet()
            return permissions.filter { it !in installedSet }
        }

    val removedPermissions: List<String>
        get() {
            if (!isUpdateInstalled) return emptyList()
            val newSet = permissions.toSet()
            return installedPermissions.filter { it !in newSet }
        }

    val selectedSplitsSizeBytes: Long
        get() = if (splitEntries.isEmpty()) fileSizeBytes
        else splitEntries.filter { it.selected }.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    val totalSplitsSizeBytes: Long
        get() = if (splitEntries.isEmpty()) fileSizeBytes
        else splitEntries.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    val savedSplitsSizeBytes: Long
        get() = (totalSplitsSizeBytes - selectedSplitsSizeBytes).coerceAtLeast(0L)
}
