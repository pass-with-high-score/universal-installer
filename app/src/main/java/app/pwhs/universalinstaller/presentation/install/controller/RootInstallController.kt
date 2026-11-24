package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.domain.model.SessionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.libsu.libsu
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation
import timber.log.Timber
import java.util.UUID

/**
 * Root-backed installer built on ackpine's libsu plugin. Only compiled into the `full`
 * flavor — `store` uses the no-op factory instead.
 *
 * Flags mirror [ShizukuInstallController] so users carry the same mental model between
 * the two privileged backends; separate DataStore keys keep the two toggleable independently.
 */
class RootInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    override val telemetryMethod = "root"

    /**
     * Install via the root shell (`pm install-create/write/commit`) instead of ackpine's libsu
     * plugin. The plugin binds a libsu RootProxyService and forwards the PackageInstaller
     * binder through it; on several devices (e.g. MIUI/Samsung) that binding doesn't take, so
     * ackpine silently falls back to the system PackageInstaller and the OS shows its install
     * confirmation dialog (issue #82). Committing the session straight from the uid-0 shell
     * is what InstallerX does and installs silently. We keep the same session/progress/history
     * bookkeeping as the ackpine path so the UI is unchanged.
     */
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
            // ROOT_ALL_USERS → omit `--user` so pm installs in the default/all-users scope;
            // otherwise scope to the current user (uid / 100000).
            val userId = if (prefs?.get(PreferencesKeys.ROOT_ALL_USERS) == true) -1
                else android.os.Process.myUid() / 100000

            val result = RootTargetedInstaller.install(
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
                    Timber.e(e, "Root shell install failed")
                    // `pm` failures come back as a shell exit code, not an ackpine
                    // InstallFailure, so there's no failure kind to attach here.
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
            libsu {
                if (targetUserId != null && targetUserId >= 0) {
                    targetUser = ru.solrudev.ackpine.privileged.TargetUser(targetUserId)
                }
                bypassLowTargetSdkBlock = prefs[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] ?: false
                allowTest = prefs[PreferencesKeys.ROOT_ALLOW_TEST] ?: false
                // Default ON to match the UI (`?: true` in Settings/dialog). ackpine only sets
                // INSTALL_REPLACE_EXISTING when true; false makes an upgrade of an existing package
                // fail with INSTALL_FAILED_ALREADY_EXISTS. Explicit user choice still honored.
                replaceExisting = prefs[PreferencesKeys.ROOT_REPLACE_EXISTING] ?: true
                requestDowngrade = allowDowngrade || (prefs[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] ?: false)
                grantAllRequestedPermissions = prefs[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] ?: false
                allUsers = prefs[PreferencesKeys.ROOT_ALL_USERS] ?: false
                if (prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] == true) {
                    val overrides = prefs[PreferencesKeys.INSTALLER_OVERRIDES]
                    val override = if (packageName.isNotBlank()) {
                        InstallerOverrides.get(overrides, packageName)
                    } else null
                    
                    installerPackageName = override
                        ?: prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME]
                        ?.trim()
                        ?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                        ?: DEFAULT_INSTALLER_PACKAGE_NAME
                }
            }
        }
    }
}
