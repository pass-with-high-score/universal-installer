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

data class AttachedObb(
    val uri: android.net.Uri,
    val fileName: String,
    val sizeBytes: Long,
)

/** Single entry in a batch install — one APK parsed + resolved + user-toggleable. */
data class BatchApkEntry(
    val uri: android.net.Uri,
    val fileName: String,
    val apkInfo: ApkInfo,
    /** URIs resolved from ackpine's SplitPackage — what the installer session receives. */
    val splitUris: List<android.net.Uri>,
    val selected: Boolean = true,
    val parseError: String? = null,
    /**
     * Soft warning — e.g. another entry in the batch already targets the same package name.
     * Unlike [parseError], the entry is still installable; the label just flags a likely conflict
     * (downgrade, signature mismatch) so the user can deselect before committing.
     */
    val conflictLabel: String? = null,
)

sealed interface BatchInstallState {
    data object Idle : BatchInstallState
    data class Parsing(val processed: Int, val total: Int) : BatchInstallState
    data class Ready(val entries: List<BatchApkEntry>) : BatchInstallState
}

sealed interface ObbCopyState {
    data object Idle : ObbCopyState
    data class Running(
        val appName: String,
        val packageName: String,
        val bytesCopied: Long,
        val totalBytes: Long,
    ) : ObbCopyState {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
    }
    data class Done(val appName: String, val fileCount: Int) : ObbCopyState
    data class Error(val appName: String, val message: String) : ObbCopyState
    /** No Shizuku + no stored SAF grant → user needs to pick `Android/obb/<pkg>/`. */
    data class NeedSafGrant(val appName: String, val packageName: String) : ObbCopyState
}

data class InstallUiState(
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList(),
    val isLoading: Boolean = false,
    val pendingApkInfo: ApkInfo? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val scanState: ScanState = ScanState.Idle,
    val obbCopyState: ObbCopyState = ObbCopyState.Idle,
    val attachedObbFiles: List<AttachedObb> = emptyList(),
    val batchState: BatchInstallState = BatchInstallState.Idle,
)
