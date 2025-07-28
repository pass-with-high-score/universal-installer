package com.nqmgaming.universalinstaller.presentation.install

import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.SessionProgress
import ru.solrudev.ackpine.resources.ResolvableString

data class InstallUiState(
    val error: ResolvableString = ResolvableString.empty(),
    val sessions: List<SessionData> = emptyList(),
    val sessionsProgress: List<SessionProgress> = emptyList()
)