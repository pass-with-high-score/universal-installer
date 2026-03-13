package com.nqmgaming.universalinstaller.domain.repository

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val sourceDir: String,
    val splitSourceDirs: List<String> = emptyList(),
    val isSystemApp: Boolean
)

interface AppManagerRepository {
    suspend fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp>
    suspend fun extractApp(app: InstalledApp): Result<String>
}
