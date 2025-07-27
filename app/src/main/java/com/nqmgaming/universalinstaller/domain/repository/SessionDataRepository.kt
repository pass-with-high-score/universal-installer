package com.nqmgaming.universalinstaller.domain.repository

import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.SessionProgress
import kotlinx.coroutines.flow.StateFlow
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import java.util.UUID

interface SessionDataRepository {
    val sessions: StateFlow<List<SessionData>>
    val sessionsProgress: StateFlow<List<SessionProgress>>
    fun addSessionData(sessionData: SessionData)
    fun removeSessionData(id: UUID)
    fun updateSessionProgress(id: UUID, progress: Progress)
    fun updateSessionIsCancellable(id: UUID, isCancellable: Boolean)
    fun setError(id: UUID, error: ResolvableString)

}