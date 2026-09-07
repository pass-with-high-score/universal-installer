package app.pwhs.tv.presentation.install

import android.net.Uri
import app.pwhs.core.domain.PackageMetadata

/**
 * UI State for the TV Dialog Installer.
 */
sealed interface TvInstallState {
    /** Staging file or reading metadata */
    data object Loading : TvInstallState

    /** Failed to parse or read APK */
    data class ParseError(val message: String) : TvInstallState

    /** Package metadata parsed and ready for installation */
    data class Ready(
        val uri: Uri,
        val metadata: PackageMetadata,
        val isBundle: Boolean,
        val sizeBytes: Long,
        val installedVersion: String? = null,
        val showPermissions: Boolean = false
    ) : TvInstallState

    /** Actively writing/installing the session */
    data class Installing(
        val appName: String,
        val progress: Float?
    ) : TvInstallState

    /** Installation completed successfully */
    data class Success(
        val appName: String,
        val packageName: String
    ) : TvInstallState

    /** Installation failed */
    data class Failure(
        val appName: String,
        val error: String
    ) : TvInstallState
}
