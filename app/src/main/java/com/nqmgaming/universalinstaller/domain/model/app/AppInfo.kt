package com.nqmgaming.universalinstaller.domain.model.app

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable? = null,
    val size: Long = 0L,
    val supportedAbis: List<String> = emptyList(),
    val isApks: Boolean = false
) {
    val sizeInMb: Double
        get() = size / (1024.0 * 1024.0)
}
