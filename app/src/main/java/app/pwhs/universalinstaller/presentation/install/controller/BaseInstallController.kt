package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.presentation.install.InstallErrorHelper
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
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
    protected val context: Context,
    protected val packageInstaller: PackageInstaller,
    protected val sessionDataRepository: SessionDataRepository,
    protected val historyDao: InstallHistoryDao,
) {
    private val activeSessions = mutableMapOf<UUID, ProgressSession<InstallFailure>>()
    private val sessionUris = mutableMapOf<UUID, List<Uri>>()
    private val originalFileUris = mutableMapOf<UUID, Uri>()
    private val deleteFlags = mutableMapOf<UUID, Boolean>()
    private val successHooks = mutableMapOf<UUID, suspend () -> Unit>()

    protected abstract suspend fun createSession(
        uris: List<Uri>,
        name: String,
    ): ProgressSession<InstallFailure>

    fun install(
        uris: List<Uri>,
        sessionData: SessionData,
        scope: CoroutineScope,
        context: Context? = null,
        originalUri: Uri? = null,
        deleteAfterInstall: Boolean = false,
        onSuccess: (suspend () -> Unit)? = null,
    ) {
        scope.launch {
            val session = createSession(uris, sessionData.name)
            activeSessions[session.id] = session
            sessionUris[session.id] = uris
            if (originalUri != null) originalFileUris[session.id] = originalUri
            deleteFlags[session.id] = deleteAfterInstall
            if (onSuccess != null) successHooks[session.id] = onSuccess
            val data = sessionData.copy(id = session.id)
            sessionDataRepository.addSessionData(data)
            awaitSession(session, scope, context)
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
                id = UUID.randomUUID(),
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

    private fun awaitSession(session: ProgressSession<InstallFailure>, scope: CoroutineScope, context: Context? = null) {
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
                val sessionData = sessionDataRepository.sessions.value.find { it.id == session.id }
                when (val result = session.await()) {
                    Session.State.Succeeded -> {
                        saveHistory(sessionData, success = true)
                        // Hook runs BEFORE source deletion so the hook can still read the original
                        // zip (e.g. to extract OBB entries). Errors are caller-reported; we don't
                        // roll back the APK install here.
                        val hook = successHooks.remove(session.id)
                        if (hook != null) {
                            runCatching { hook() }.onFailure {
                                Timber.e(it, "Install success hook failed")
                            }
                        }
                        deleteSourceFileIfNeeded(session.id, context)
                        sessionDataRepository.removeSessionData(session.id)
                        activeSessions.remove(session.id)
                        sessionUris.remove(session.id)
                        originalFileUris.remove(session.id)
                        deleteFlags.remove(session.id)
                    }
                    is Session.State.Failed -> {
                        if (context == null) return@launch
                        val errorInfo = InstallErrorHelper.getErrorInfo(context, result.failure)
                        val fullMessage = "${errorInfo.title}\n${errorInfo.guidance}"
                        saveHistory(sessionData, success = false, errorMessage = errorInfo.title)
                        handleError(fullMessage, session.id)
                    }
                }
            } catch (e: CancellationException) {
                sessionDataRepository.removeSessionData(session.id)
                activeSessions.remove(session.id)
                sessionUris.remove(session.id)
                originalFileUris.remove(session.id)
                deleteFlags.remove(session.id)
                successHooks.remove(session.id)
                throw e
            } catch (e: Exception) {
                handleError(e.message, session.id)
                Timber.e(e, "Session error")
            }
        }
    }

    private suspend fun saveHistory(
        sessionData: SessionData?,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        if (sessionData == null) return
        try {
            historyDao.insert(
                InstallHistoryEntity(
                    appName = sessionData.appName.ifEmpty { sessionData.name },
                    packageName = "",
                    fileName = sessionData.name,
                    iconPath = sessionData.iconPath,
                    success = success,
                    errorMessage = errorMessage,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save install history")
        }
    }

    private fun deleteSourceFileIfNeeded(sessionId: UUID, context: Context?) {
        if (deleteFlags[sessionId] != true || context == null) return
        val uri = originalFileUris[sessionId] ?: return
        try {
            // Take write permission granted by OpenDocument picker
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { /* permission may already be held or not available */ }
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
            Timber.d("Deleted source file: $uri")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete source file: $uri")
        }
    }

    private fun handleError(message: String?, sessionId: UUID) {
        val err = ResolvableString.raw(message ?: "Installation failed")
        sessionDataRepository.setError(sessionId, err)
    }
}
