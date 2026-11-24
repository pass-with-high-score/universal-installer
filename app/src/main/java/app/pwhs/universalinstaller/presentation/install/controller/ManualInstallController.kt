package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
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
 * Drives installs that target a specific Android user id (e.g. a work profile).
 *
 * Ackpine's Shizuku/Root backends only expose an `allUsers` toggle — they can't
 * pin an install to one specific secondary user. So we go around ackpine for
 * this case and shell out via either:
 *  - [ManualTargetedInstaller] — Shizuku binder path, works on any device with
 *    Shizuku running and granted.
 *  - [InstallerBackendFactory.installTargetedViaRoot] — libsu `pm install --user`
 *    path, works on devices with Root (KernelSU, Magisk, …) when Shizuku isn't set up.
 *
 * The caller (InstallViewModel) decides which backend to ask for by probing what's
 * available and passing the appropriate [TargetedBackend] value.
 */
class ManualInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
    private val backendFactory: InstallerBackendFactory,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    enum class TargetedBackend { SHIZUKU, ROOT }

    // Never reported bare: installTargeted appends the backend, since this controller drives
    // both of them.
    override val telemetryMethod = "manual"

    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
        allowDowngrade: Boolean,
        targetUserId: Int?,
    ): ProgressSession<InstallFailure> {
        // This is not used as we override the main install() method.
        throw UnsupportedOperationException("ManualInstallController uses targetedInstall instead")
    }

    /**
     * Note: this path does NOT honor most installer profile flags (replaceExisting,
     * allowTest, requestDowngrade, installerPackageName, …). The Shizuku impl applies
     * only `MODE_FULL_INSTALL`; the Root impl applies only `-r`. If a future profile
     * needs to influence the targeted path, plumb the relevant flags through here.
     */
    fun installTargeted(
        uris: List<Uri>,
        sessionData: SessionData,
        userId: Int,
        backend: TargetedBackend,
        scope: CoroutineScope,
        originalUri: Uri? = null,
        deleteAfterInstall: Boolean = false,
        onSessionCreated: ((UUID) -> Unit)? = null,
    ) {
        val sessionId = UUID.randomUUID()
        val data = sessionData.copy(id = sessionId)
        val method = "${telemetryMethod}_${backend.name.lowercase()}"

        scope.launch {
            sessionDataRepository.addSessionData(data)
            onSessionCreated?.invoke(sessionId)
            reportInstallStarted(uris.size, method)

            val onProgress: (Float) -> Unit = { fraction ->
                val progress = Progress((fraction * 100).toInt().coerceIn(0, 100), 100)
                sessionDataRepository.updateSessionProgress(sessionId, progress)
            }

            val result: Result<Unit> = when (backend) {
                TargetedBackend.SHIZUKU -> if (app.pwhs.universalinstaller.presentation.install.util.AbiOverrideUtil.sanitize(data.abiOverride) != null) {
                    ShizukuShellInstaller.install(
                        context = application,
                        uris = uris,
                        userId = userId,
                        packageName = data.packageName,
                        allowDowngrade = data.allowDowngrade,
                        abiOverride = data.abiOverride,
                        onProgress = onProgress,
                    )
                } else {
                    ManualTargetedInstaller.install(
                        context = application,
                        uris = uris,
                        userId = userId,
                        onProgress = onProgress,
                    )
                }
                TargetedBackend.ROOT -> backendFactory.installTargetedViaRoot(
                    context = application,
                    uris = uris,
                    userId = userId,
                    packageName = data.packageName,
                    abiOverride = data.abiOverride,
                    onProgress = onProgress,
                )
            }

            result.fold(
                onSuccess = {
                    sessionDataRepository.updateSessionProgress(sessionId, Progress(100, 100))
                    kotlinx.coroutines.delay(500)
                    reportInstallResult(TelemetryEvents.RESULT_SUCCESS, method)
                    saveHistory(data, success = true)
                    if (deleteAfterInstall && originalUri != null) {
                        deleteSourceFileAndWarn(application, originalUri)
                    }
                    autoOpenAppIfNeeded(data.packageName, application)
                    sessionDataRepository.removeSessionData(sessionId)
                },
                onFailure = { e ->
                    Timber.e(e, "Manual targeted install failed (backend=$backend)")
                    val errorMsg = ResolvableString.raw(e.message ?: "Installation failed")
                    // This path never produces an ackpine InstallFailure — it shells out — so
                    // there is no failure kind to report beyond "it failed".
                    reportInstallResult(TelemetryEvents.RESULT_FAILURE, method)
                    saveHistory(data, success = false, errorMessage = e.message)
                    sessionDataRepository.setError(sessionId, errorMsg)
                }
            )
        }
    }
}
