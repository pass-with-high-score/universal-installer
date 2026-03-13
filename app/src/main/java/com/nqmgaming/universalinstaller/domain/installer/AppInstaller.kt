package com.nqmgaming.universalinstaller.domain.installer

import android.net.Uri
import kotlinx.coroutines.flow.Flow

enum class InstallMethod {
    STANDARD, SHIZUKU, ROOT
}

data class InstallOptions(
    val packageName: String? = null,
    val method: InstallMethod = InstallMethod.STANDARD
)

interface AppInstaller {
    /**
     * Start the installation process for the given package URIs.
     * @param uris The list of URIs to install (e.g. one for APK, or multiple for APKS splits)
     * @param options Configurable options for installation (Shizuku, Root, etc)
     */
    suspend fun install(uris: List<Uri>, options: InstallOptions = InstallOptions()): Flow<InstallState>
}

sealed class InstallState {
    object Idle : InstallState()
    object Processing : InstallState()
    data class Progress(val percentage: Int) : InstallState()
    object Success : InstallState()
    data class Error(val message: String, val cause: Throwable? = null) : InstallState()
}
