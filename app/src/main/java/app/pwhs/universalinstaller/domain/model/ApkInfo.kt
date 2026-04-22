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
    val supportedLanguages: List<String> = emptyList(),
    val sha256: String = "",
    val vtResult: VtResult? = null,
    val obbFileNames: List<String> = emptyList(),
    val obbTotalBytes: Long = 0L,
    val splitEntries: List<SplitEntry> = emptyList(),
)
