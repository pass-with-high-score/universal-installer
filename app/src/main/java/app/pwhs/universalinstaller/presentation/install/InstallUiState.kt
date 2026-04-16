package app.pwhs.universalinstaller.presentation.install

import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress

data class InstallUiState(
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList(),
    val isLoading: Boolean = false,
    val pendingApkInfo: ApkInfo? = null,
)
