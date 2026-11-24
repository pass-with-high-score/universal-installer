package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import ru.solrudev.ackpine.session.ProgressSession
import timber.log.Timber
import java.util.UUID

/**
 * Installer controller powered by a custom shell/authorizer command template.
 *
 * Runs `pm install-create/write/commit` commands through [CustomTargetedInstaller],
 * updating UI progress, install history, and telemetry identically to [RootInstallController].
 */
class CustomInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    override val telemetryMethod = "custom"

    override fun install(
        uris: List<Uri>,
        sessionData: SessionData,
        scope: CoroutineScope,
        context: Context?,
        originalUri: Uri?,
        deleteAfterInstall: Boolean,
        allowDowngrade: Boolean,
        onSuccess: (suspend () -> Unit)?,
        onSessionCreated: ((UUID) -> Unit)?,
    ) {
        val sessionId = UUID.randomUUID()
        val data = sessionData.copy(
            id = sessionId,
            uris = uris,
            originalUri = originalUri,
            deleteAfterInstall = deleteAfterInstall,
            allowDowngrade = allowDowngrade,
        )
        scope.launch {
            sessionDataRepository.addSessionData(data)
            onSessionCreated?.invoke(sessionId)
            reportInstallStarted(uris.size)

            val onProgress: (Float) -> Unit = { fraction ->
                sessionDataRepository.updateSessionProgress(
                    sessionId,
                    Progress((fraction * 100).toInt().coerceIn(0, 100), 100),
                )
            }

            val prefs = runCatching { application.dataStore.data.first() }.getOrNull()
            val userId = if (prefs?.get(PreferencesKeys.ROOT_ALL_USERS) == true) -1
            else android.os.Process.myUid() / 100000

            val result = CustomTargetedInstaller.install(
                context = application,
                uris = uris,
                userId = userId,
                packageName = sessionData.packageName,
                allowDowngrade = allowDowngrade,
                abiOverride = sessionData.abiOverride,
                onProgress = onProgress,
            )

            result.fold(
                onSuccess = {
                    sessionDataRepository.updateSessionProgress(sessionId, Progress(100, 100))
                    reportInstallResult(TelemetryEvents.RESULT_SUCCESS)
                    saveHistory(data, success = true)
                    runCatching { onSuccess?.invoke() }
                        .onFailure { Timber.e(it, "Install success hook failed") }
                    if (deleteAfterInstall && originalUri != null) {
                        deleteSourceFileAndWarn(context ?: application, originalUri)
                    }
                    autoOpenAppIfNeeded(data.packageName, context ?: application)
                    sessionDataRepository.removeSessionData(sessionId)
                },
                onFailure = { e ->
                    Timber.e(e, "Custom shell install failed")
                    reportInstallResult(TelemetryEvents.RESULT_FAILURE)
                    saveHistory(data, success = false, errorMessage = e.message)
                    sessionDataRepository.setError(
                        sessionId,
                        ResolvableString.raw(e.message ?: "Installation failed"),
                    )
                },
            )
        }
    }

    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
        allowDowngrade: Boolean,
        targetUserId: Int?,
    ): ProgressSession<InstallFailure> {
        throw UnsupportedOperationException("CustomInstallController drives installs via CustomTargetedInstaller")
    }
}
