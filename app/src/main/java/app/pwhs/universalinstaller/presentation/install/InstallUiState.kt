package app.pwhs.universalinstaller.presentation.install

import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(
        val url: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : DownloadState {
        val progressPercent: Int?
            get() = if (totalBytes > 0) ((bytesRead * 100L) / totalBytes).toInt().coerceIn(0, 100) else null
    }

    data class Error(val message: String) : DownloadState
}

sealed interface ScanState {
    data object Idle : ScanState
    data object PermissionNeeded : ScanState
    data object Scanning : ScanState
    data class Ready(val files: List<FoundPackageFile>) : ScanState
}

data class InstallUiState(
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList(),
    val isLoading: Boolean = false,
    val pendingApkInfo: ApkInfo? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val scanState: ScanState = ScanState.Idle,
)
