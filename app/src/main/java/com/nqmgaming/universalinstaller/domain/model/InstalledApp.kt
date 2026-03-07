package com.nqmgaming.universalinstaller.domain.model

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
)
