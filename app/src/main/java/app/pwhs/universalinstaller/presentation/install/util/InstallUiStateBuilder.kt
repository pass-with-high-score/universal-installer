package app.pwhs.universalinstaller.presentation.install.util

import android.net.Uri
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.BatchInstallState
import app.pwhs.universalinstaller.presentation.install.DialogStage
import app.pwhs.universalinstaller.presentation.install.DownloadState
import app.pwhs.universalinstaller.presentation.install.InstallUiState
import app.pwhs.universalinstaller.presentation.install.ObbCopyState
import app.pwhs.universalinstaller.presentation.install.ScanState
import app.pwhs.universalinstaller.presentation.install.WatchSendState
import app.pwhs.universalinstaller.presentation.sync.SyncState

object InstallUiStateBuilder {

    fun build(flows: Array<Any?>): InstallUiState {
        @Suppress("UNCHECKED_CAST")
        return InstallUiState(
            sessions = flows[0] as List<SessionData>,
            sessionsProgress = flows[1] as List<SessionProgress>,
            isLoading = flows[2] as Boolean,
            pendingApkInfo = flows[3] as ApkInfo?,
            downloadState = flows[4] as DownloadState,
            scanState = flows[5] as ScanState,
            obbCopyState = flows[6] as ObbCopyState,
            attachedObbFiles = flows[7] as List<AttachedObb>,
            batchState = flows[8] as BatchInstallState,
            dialogStage = flows[9] as DialogStage,
            mergeSplits = flows[10] as Boolean,
            installerProfiles = ProfileManager.parseProfiles(flows[11] as String?),
            appProfileMapping = ProfileManager.parseMapping(flows[12] as String?),
            syncState = flows[13] as SyncState,
            selectedProfileId = flows[14] as String?,
            allUsers = flows[15] as Boolean,
            selectedUserId = flows[16] as Int?,
            isApk = flows[17] as Boolean,
            batchDetailUri = flows[18] as Uri?,
            dialogDownloadProgress = flows[19] as app.pwhs.core.network.DownloadProgress?,
            watchSendState = flows[20] as WatchSendState,
            abiOverride = flows[21] as String?,
        )
    }
}
