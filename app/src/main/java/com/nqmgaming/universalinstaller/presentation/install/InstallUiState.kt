package com.nqmgaming.universalinstaller.presentation.install

import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.SessionProgress
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo
import ru.solrudev.ackpine.resources.ResolvableString

data class ExistingAppInfo(
    val versionName: String,
    val versionCode: Long
)

data class InstallUiState(
    val error: String? = null,
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList(),
    val parsedAppInfo: AppInfo? = null,
    val existingAppInfo: ExistingAppInfo? = null
)