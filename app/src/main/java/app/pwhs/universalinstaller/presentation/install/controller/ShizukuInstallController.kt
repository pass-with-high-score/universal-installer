package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides
import app.pwhs.universalinstaller.presentation.install.util.AbiOverrideUtil
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.shizuku.shizuku
import timber.log.Timber
import java.util.UUID

class ShizukuInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    override val telemetryMethod = "shizuku"

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
        val abiOverride = AbiOverrideUtil.sanitize(sessionData.abiOverride)
        if (abiOverride == null) {
            super.install(
                uris = uris,
                sessionData = sessionData,
                scope = scope,
                context = context,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                allowDowngrade = allowDowngrade,
                onSuccess = onSuccess,
                onSessionCreated = onSessionCreated,
            )
            return
        }

        val sessionId = UUID.randomUUID()
        val data = sessionData.copy(
            id = sessionId,
            uris = uris,
            originalUri = originalUri,
            deleteAfterInstall = deleteAfterInstall,
            allowDowngrade = allowDowngrade,
            abiOverride = abiOverride,
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
            val userId = when {
                sessionData.targetUserId != null && sessionData.targetUserId >= 0 -> sessionData.targetUserId
                prefs?.get(PreferencesKeys.SHIZUKU_ALL_USERS) == true -> -1
                else -> android.os.Process.myUid() / 100000
            }

            val result = ShizukuShellInstaller.install(
                context = application,
                uris = uris,
                userId = userId,
                packageName = sessionData.packageName,
                allowDowngrade = allowDowngrade,
                abiOverride = abiOverride,
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
                    Timber.e(e, "Shizuku shell install with --abi %s failed", abiOverride)
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

    @OptIn(DelicateAckpineApi::class)
    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
        allowDowngrade: Boolean,
        targetUserId: Int?,
    ): ProgressSession<InstallFailure> {
        val prefs = application.dataStore.data.first()
        return packageInstaller.createSession(uris) {
            this.name = name
            confirmation = Confirmation.IMMEDIATE
            shizuku {
                if (targetUserId != null && targetUserId >= 0) {
                    targetUser = ru.solrudev.ackpine.privileged.TargetUser(targetUserId)
                }
                bypassLowTargetSdkBlock = prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] ?: false
                allowTest = prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST] ?: false
                // Default ON to match the UI (Settings/dialog show this as `?: true`). ackpine only
                // ORs INSTALL_REPLACE_EXISTING into the session when this is true; with it false a
                // privileged upgrade of an existing package fails with INSTALL_FAILED_ALREADY_EXISTS
                // (the "package conflict" error). An explicit user choice is still honored — `?:`
                // only fills the never-set case.
                replaceExisting = prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] ?: true
                requestDowngrade = allowDowngrade || (prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] ?: false)
                grantAllRequestedPermissions = prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] ?: false
                allUsers = prefs[PreferencesKeys.SHIZUKU_ALL_USERS] ?: false
                // Spoof installer package (shows in PackageManager.getInstallerPackageName).
                // Applied only when user opted in; otherwise ackpine defaults to this app.
                // Under-the-hood API requires Android 9+ — silently ignored on older devices.
                if (prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] == true) {
                    val overrides = prefs[PreferencesKeys.INSTALLER_OVERRIDES]
                    val override = if (packageName.isNotBlank()) {
                        InstallerOverrides.get(overrides, packageName)
                    } else null
                    
                    installerPackageName = override
                        ?: prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME]
                        ?.trim()
                        ?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                        ?: DEFAULT_INSTALLER_PACKAGE_NAME
                }
            }
        }
    }
}
