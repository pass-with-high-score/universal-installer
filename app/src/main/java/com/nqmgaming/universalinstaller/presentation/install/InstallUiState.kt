package com.nqmgaming.universalinstaller.presentation.install

import com.nqmgaming.universalinstaller.domain.model.ApkInfo
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.SessionProgress

data class InstallUiState(
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList(),
    val isLoading: Boolean = false,
    val pendingApkInfo: ApkInfo? = null,
)
