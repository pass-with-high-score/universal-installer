package com.nqmgaming.universalinstaller.presentation.install.controller

import android.net.Uri
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.getSession
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.progress
import ru.solrudev.ackpine.session.state
import timber.log.Timber
import java.util.UUID

abstract class BaseInstallController(
    protected val packageInstaller: PackageInstaller,
    protected val sessionDataRepository: SessionDataRepository,
) {
    private val activeSessions = mutableMapOf<UUID, ProgressSession<InstallFailure>>()
    private val sessionUris = mutableMapOf<UUID, List<Uri>>()

    protected abstract suspend fun createSession(
        uris: List<Uri>,
        name: String,
    ): ProgressSession<InstallFailure>

    fun install(
        uris: List<Uri>,
        sessionData: SessionData,
        scope: CoroutineScope,
    ) {
        scope.launch {
            val session = createSession(uris, sessionData.name)
            activeSessions[session.id] = session
            sessionUris[session.id] = uris
            val data = sessionData.copy(id = session.id)
            sessionDataRepository.addSessionData(data)
            awaitSession(session, scope)
        }
    }

    fun cancel(id: UUID, scope: CoroutineScope) {
        scope.launch {
            activeSessions[id]?.cancel()
            activeSessions.remove(id)
            sessionUris.remove(id)
            sessionDataRepository.removeSessionData(id)
        }
    }

    fun retry(id: UUID, scope: CoroutineScope) {
        val uris = sessionUris[id] ?: return
        val oldSession = sessionDataRepository.sessions.value.find { it.id == id } ?: return

        activeSessions.remove(id)
        sessionUris.remove(id)
        sessionDataRepository.removeSessionData(id)

        install(
            uris = uris,
            sessionData = SessionData(
                id = UUID.randomUUID(), // placeholder, replaced by install()
                name = oldSession.name,
                appName = oldSession.appName,
                iconPath = oldSession.iconPath,
            ),
            scope = scope,
        )
    }

    fun restoreSessionsFromSavedState(scope: CoroutineScope) {
        scope.launch {
            val sessions = sessionDataRepository.sessions.value
            for (data in sessions) {
                val session = packageInstaller.getSession(data.id) ?: continue
                activeSessions[session.id] = session
                awaitSession(session, scope)
            }
        }
    }

    private fun awaitSession(session: ProgressSession<InstallFailure>, scope: CoroutineScope) {
        scope.launch {
            session.progress
                .onEach { progress ->
                    sessionDataRepository.updateSessionProgress(session.id, progress)
                }
                .launchIn(this)
            session.state
                .filterIsInstance<Session.State.Committed>()
                .onEach {
                    sessionDataRepository.updateSessionIsCancellable(session.id, isCancellable = false)
                }
                .launchIn(this)
            try {
                when (val result = session.await()) {
                    Session.State.Succeeded -> {
                        sessionDataRepository.removeSessionData(session.id)
                        activeSessions.remove(session.id)
                        sessionUris.remove(session.id)
                    }
                    is Session.State.Failed -> handleError(result.failure.message, session.id)
                }
            } catch (e: CancellationException) {
                sessionDataRepository.removeSessionData(session.id)
                activeSessions.remove(session.id)
                sessionUris.remove(session.id)
                throw e
            } catch (e: Exception) {
                handleError(e.message, session.id)
                Timber.e(e, "Session error")
            }
        }
    }

    private fun handleError(message: String?, sessionId: UUID) {
        val err = if (message != null) {
            ResolvableString.transientResource(R.string.session_error_with_reason, message)
        } else {
            ResolvableString.transientResource(R.string.session_error)
        }
        sessionDataRepository.setError(sessionId, err)
    }
}
