package app.pwhs.universalinstaller.domain.model

import android.graphics.drawable.Drawable

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
)
