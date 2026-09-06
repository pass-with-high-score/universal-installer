package app.pwhs.core.domain

import android.graphics.Bitmap

/** Detailed metadata for an APK or bundle before installation. */
data class PackageMetadata(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Bitmap?,
    val isBundle: Boolean,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val permissions: List<String> = emptyList(),
)
