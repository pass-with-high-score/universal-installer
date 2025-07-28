package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.exceptions.ConflictingBaseApkException
import ru.solrudev.ackpine.exceptions.ConflictingPackageNameException
import ru.solrudev.ackpine.exceptions.ConflictingSplitNameException
import ru.solrudev.ackpine.exceptions.ConflictingVersionCodeException
import ru.solrudev.ackpine.exceptions.NoBaseApkException
import ru.solrudev.ackpine.exceptions.SplitPackageException
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.installer.getSession
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.session.progress
import ru.solrudev.ackpine.session.state
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.util.UUID

class InstallViewModel(
    private val packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
) : ViewModel() {
    val error = MutableStateFlow(ResolvableString.empty())
    var session: ProgressSession<InstallFailure>? = null
    val uiState = combine(
        error,
        sessionDataRepository.sessions,
        sessionDataRepository.sessionsProgress,
        ::InstallUiState
    )
        .onStart { awaitSessionsFromSavedState() }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    @OptIn(DelicateAckpineApi::class)
    fun installPackage(splitPackage: SplitPackage.Provider, fileName: String) =
        viewModelScope.launch {
            session?.cancel()
            val uris = getApkUris(splitPackage)
            if (uris.isEmpty()) {
                return@launch
            }
            session = packageInstaller.createSession(uris) {
                name = fileName
                confirmation = Confirmation.IMMEDIATE
            }
            val sessionData = SessionData(session?.id!!, fileName)
            sessionDataRepository.addSessionData(sessionData)
            awaitSession(session!!)
        }

    private suspend inline fun getApkUris(splitPackage: SplitPackage.Provider): List<Uri> {
        try {
            return splitPackage
                .get()
                .toList()
                .map { it.apk.uri }
        } catch (exception: SplitPackageException) {
            Timber.e("Error getting APK URIs: $exception")
            error.value = when (exception) {
                is NoBaseApkException -> ResolvableString.transientResource(R.string.error_no_base_apk)
                is ConflictingBaseApkException -> ResolvableString.transientResource(R.string.error_conflicting_base_apk)
                is ConflictingSplitNameException -> ResolvableString.transientResource(
                    R.string.error_conflicting_split_name,
                    exception.name
                )

                is ConflictingPackageNameException -> ResolvableString.transientResource(
                    R.string.error_conflicting_package_name,
                    exception.expected, exception.actual, exception.name
                )

                is ConflictingVersionCodeException -> ResolvableString.transientResource(
                    R.string.error_conflicting_version_code,
                    exception.expected, exception.actual, exception.name
                )
            }
            return emptyList()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            error.value = ResolvableString.raw(exception.message.orEmpty())
            Timber.tag("InstallViewModel").e(exception)
            return emptyList()
        }
    }

    private fun awaitSession(session: ProgressSession<InstallFailure>) = viewModelScope.launch {
        session.progress
            .onEach { progress ->
                sessionDataRepository.updateSessionProgress(
                    session.id,
                    progress
                )
            }
            .launchIn(this)
        session.state
            .filterIsInstance<Session.State.Committed>()
            .onEach {
                sessionDataRepository.updateSessionIsCancellable(
                    session.id,
                    isCancellable = false
                )
            }
            .launchIn(this)
        try {
            when (val result = session.await()) {
                Session.State.Succeeded -> sessionDataRepository.removeSessionData(session.id)
                is Session.State.Failed -> handleSessionError(result.failure.message, session.id)
            }
        } catch (exception: CancellationException) {
            sessionDataRepository.removeSessionData(session.id)
            throw exception
        } catch (exception: Exception) {
            handleSessionError(exception.message, session.id)
            Timber.tag("InstallViewModel").e(exception)
        }
    }

    private fun handleSessionError(message: String?, sessionId: UUID) {
        val error = if (message != null) {
            ResolvableString.transientResource(R.string.session_error_with_reason, message)
        } else {
            ResolvableString.transientResource(R.string.session_error)
        }
        sessionDataRepository.setError(sessionId, error)
    }

    private fun awaitSessionsFromSavedState() = viewModelScope.launch {
        val sessions = sessionDataRepository.sessions.value
        if (sessions.isNotEmpty()) {
            sessions
                .map { sessionData ->
                    async { packageInstaller.getSession(sessionData.id) }
                }
                .awaitAll()
                .filterNotNull()
                .forEach(::awaitSession)
        }
    }
}