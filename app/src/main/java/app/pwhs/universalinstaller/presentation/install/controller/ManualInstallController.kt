package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import ru.solrudev.ackpine.session.ProgressSession
import timber.log.Timber
import java.util.UUID

/**
 * A controller that handles targeted user installs manually using hidden APIs.
 * This bypasses Ackpine's standard flow to support specific User IDs (e.g. Work Profiles).
 */
class ManualInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
    ): ProgressSession<InstallFailure> {
        // This is not used as we override the main install() method.
        throw UnsupportedOperationException("ManualInstallController uses targetedInstall instead")
    }

    fun installTargeted(
        uris: List<Uri>,
        sessionData: SessionData,
        userId: Int,
        scope: CoroutineScope,
        onSessionCreated: ((UUID) -> Unit)? = null,
    ) {
        val sessionId = UUID.randomUUID()
        val data = sessionData.copy(id = sessionId)
        
        scope.launch {
            sessionDataRepository.addSessionData(data)
            onSessionCreated?.invoke(sessionId)
            
            val result = ManualTargetedInstaller.install(
                context = application,
                uris = uris,
                userId = userId,
                onProgress = { progressValue ->
                    // Progress is 0.0 to 1.0
                    val progress = Progress((progressValue * 100).toInt(), 100)
                    sessionDataRepository.updateSessionProgress(sessionId, progress)
                }
            )

            result.fold(
                onSuccess = {
                    sessionDataRepository.updateSessionProgress(sessionId, Progress(100, 100))
                    // Wait a bit to show 100%
                    kotlinx.coroutines.delay(500)
                    saveHistory(data, success = true)
                    sessionDataRepository.removeSessionData(sessionId)
                },
                onFailure = { e ->
                    Timber.e(e, "Manual targeted install failed")
                    val errorMsg = ResolvableString.raw(e.message ?: "Installation failed")
                    saveHistory(data, success = false, errorMessage = e.message)
                    sessionDataRepository.setError(sessionId, errorMsg)
                }
            )
        }
    }
}
