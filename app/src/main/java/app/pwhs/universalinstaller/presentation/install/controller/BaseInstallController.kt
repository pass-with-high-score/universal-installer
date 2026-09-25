package app.pwhs.universalinstaller.presentation.install.controller

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.presentation.install.InstallErrorHelper
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.review.ReviewGate
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.File
import java.util.UUID

abstract class BaseInstallController(
    protected val context: Context,
    protected val packageInstaller: PackageInstaller,
    protected val sessionDataRepository: SessionDataRepository,
    protected val historyDao: InstallHistoryDao,
) {
    /**
     * Name this backend goes by in telemetry — see [TelemetryEvents.PARAM_METHOD].
     *
     * Spelled out per subclass rather than derived from the class name because R8 renames
     * these classes, and a metric that changes shape between debug and release is worthless.
     */
    protected abstract val telemetryMethod: String

    /**
     * Whether this installer backend relies on the system package installer service and
     * requires the `REQUEST_INSTALL_PACKAGES` permission on Android 8.0+.
     */
    open val requiresInstallPermission: Boolean get() = false

    private val sessionStartTimes = mutableMapOf<UUID, Long>()
    private val sessionFileTypes = mutableMapOf<UUID, String>()
    private val sessionFileSizes = mutableMapOf<UUID, Long>()

    /**
     * @param method overridden only where one controller drives more than one backend.
     */
    protected fun reportInstallStarted(
        apkCount: Int,
        method: String = telemetryMethod,
        id: UUID? = null,
        fileType: String = "apk",
        fileSizeBytes: Long = 0L,
    ) {
        if (id != null) {
            sessionStartTimes[id] = System.currentTimeMillis()
            sessionFileTypes[id] = fileType
            sessionFileSizes[id] = fileSizeBytes
        }
        Telemetry.setUserProperty(TelemetryEvents.PROPERTY_INSTALL_METHOD, method)
        app.pwhs.core.telemetry.AnalyticsHelper.logInstallStarted(
            fileType = fileType,
            installMode = method,
            isSplit = apkCount > 1,
            fileSizeBytes = fileSizeBytes,
            fileCount = apkCount,
        )
    }

    protected fun reportInstallResult(
        result: String,
        method: String = telemetryMethod,
        failureKey: String? = null,
        errorCode: String? = null,
        errorType: String? = null,
        errorReason: String? = null,
        id: UUID? = null,
    ) {
        val durationMs = id?.let { sessionStartTimes.remove(it) }?.let { System.currentTimeMillis() - it } ?: 0L
        val fileType = id?.let { sessionFileTypes.remove(it) } ?: "apk"
        sessionFileSizes.remove(id)

        val status = when (result) {
            TelemetryEvents.RESULT_SUCCESS -> app.pwhs.core.telemetry.TelemetryEvents.RESULT_SUCCESS
            TelemetryEvents.RESULT_CANCELLED -> app.pwhs.core.telemetry.TelemetryEvents.RESULT_CANCELLED
            else -> app.pwhs.core.telemetry.TelemetryEvents.RESULT_FAILURE
        }
        val resolvedErrorCode = errorCode ?: failureKey
        val resolvedErrorType = errorType ?: failureKey
        val resolvedErrorReason = errorReason ?: resolvedErrorCode

        app.pwhs.core.telemetry.AnalyticsHelper.logInstallResult(
            fileType = fileType,
            status = status,
            errorCode = resolvedErrorCode,
            errorType = resolvedErrorType,
            errorReason = resolvedErrorReason,
            installMode = method,
            durationMs = durationMs,
        )
    }

    private val activeSessions = mutableMapOf<UUID, ProgressSession<InstallFailure>>()
    private val sessionUris = mutableMapOf<UUID, List<Uri>>()
    private val originalFileUris = mutableMapOf<UUID, Uri>()
    private val deleteFlags = mutableMapOf<UUID, Boolean>()
    private val successHooks = mutableMapOf<UUID, suspend () -> Unit>()

    /**
     * @param allowDowngrade one-shot consent from the "downgrade detected" dialog. ORed with the
     *   persisted flag rather than replacing it, and deliberately *not* written back to prefs —
     *   accepting one downgrade must not silently flip the Settings toggle for every later install.
     */
    protected abstract suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
        allowDowngrade: Boolean,
        targetUserId: Int? = null,
    ): ProgressSession<InstallFailure>

    open fun install(
        uris: List<Uri>,
        sessionData: SessionData,
        scope: CoroutineScope,
        context: Context? = null,
        originalUri: Uri? = null,
        deleteAfterInstall: Boolean = false,
        allowDowngrade: Boolean = false,
        onSuccess: (suspend () -> Unit)? = null,
        onSessionCreated: ((UUID) -> Unit)? = null,
    ) {
        scope.launch {
            val session = createSession(uris, sessionData.name, sessionData.packageName, allowDowngrade, sessionData.targetUserId)
            activeSessions[session.id] = session
            sessionUris[session.id] = uris
            if (originalUri != null) originalFileUris[session.id] = originalUri
            deleteFlags[session.id] = deleteAfterInstall
            if (onSuccess != null) successHooks[session.id] = onSuccess
            val data = sessionData.copy(
                id = session.id,
                uris = uris,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                allowDowngrade = allowDowngrade,
                targetUserId = sessionData.targetUserId,
            )
            sessionDataRepository.addSessionData(data)
            // Hand the real ackpine session ID back to the caller. The dialog flow keys its
            // Installing/Success/Failed watchers off this — using the caller-passed id won't
            // match because addSessionData stores the data under session.id, not sessionData.id.
            onSessionCreated?.invoke(session.id)
            val fileType = originalUri?.lastPathSegment?.substringAfterLast('.', "") ?: "apk"
            val fileSizeBytes = originalUri?.let { uri ->
                runCatching { context?.contentResolver?.openFileDescriptor(uri, "r")?.statSize }.getOrNull() ?: 0L
            } ?: 0L
            reportInstallStarted(uris.size, id = session.id, fileType = fileType, fileSizeBytes = fileSizeBytes)
            awaitSession(session, scope, context)
        }
    }

    fun cancel(id: UUID, scope: CoroutineScope) {
        scope.launch {
            try {
                activeSessions[id]?.cancel()
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling session")
            } finally {
                activeSessions.remove(id)
                sessionUris.remove(id)
                originalFileUris.remove(id)
                deleteFlags.remove(id)
                successHooks.remove(id)
                sessionDataRepository.removeSessionData(id)
            }
        }
    }

    /**
     * Drop a session the user is done with, without trying to cancel it.
     *
     * [cancel] is for a session still running; calling it on one that already failed asks ackpine
     * to cancel a completed session, which is meaningless. This exists so a failed install can be
     * cleared from the list — it previously had Retry and no way out at all (issue #93).
     */
    fun dismiss(id: UUID) {
        activeSessions.remove(id)
        sessionUris.remove(id)
        originalFileUris.remove(id)
        deleteFlags.remove(id)
        successHooks.remove(id)
        sessionDataRepository.removeSessionData(id)
    }

    /**
     * @param context must be non-null for the retry to be able to report a second failure —
     *   awaitSession bails out of its Failed branch without one, which would leave the user with
     *   a card that silently reverts to idle.
     */
    /**
     * @param onSessionCreated the retry runs as a *new* ackpine session, so a caller watching one
     *   id — the install dialog does — has to be told the new one or it watches a session that
     *   will never report again.
     */
    fun retry(
        id: UUID,
        scope: CoroutineScope,
        context: Context? = null,
        onSessionCreated: ((UUID) -> Unit)? = null,
    ) {
        // Read from the repository, not from sessionUris: those maps are per controller instance
        // and empty for a session another instance created or that was restored after a restart.
        val old = sessionDataRepository.sessions.value.find { it.id == id } ?: return
        val uris = old.uris.ifEmpty { sessionUris[id].orEmpty() }
        if (uris.isEmpty()) {
            Timber.w("Retry for $id has no source uris — nothing to reinstall")
            return
        }

        activeSessions.remove(id)
        sessionUris.remove(id)
        sessionDataRepository.removeSessionData(id)

        install(
            uris = uris,
            sessionData = SessionData(
                id = UUID.randomUUID(),
                name = old.name,
                appName = old.appName,
                packageName = old.packageName,
                iconPath = old.iconPath,
            ),
            scope = scope,
            context = context,
            originalUri = old.originalUri,
            deleteAfterInstall = old.deleteAfterInstall,
            allowDowngrade = old.allowDowngrade,
            onSessionCreated = onSessionCreated,
        )
    }

    fun restoreSessionsFromSavedState(scope: CoroutineScope) {
        scope.launch {
            val sessions = sessionDataRepository.sessions.value
            for (data in sessions) {
                if (activeSessions.containsKey(data.id)) continue
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
            val sessionData = sessionDataRepository.sessions.value.find { it.id == session.id }
            try {
                when (val result = session.await()) {
                    Session.State.Succeeded -> {
                        reportInstallResult(TelemetryEvents.RESULT_SUCCESS, id = session.id)
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
                        autoOpenAppIfNeeded(sessionData?.packageName, context)
                        sessionDataRepository.removeSessionData(session.id)
                        activeSessions.remove(session.id)
                        sessionUris.remove(session.id)
                        originalFileUris.remove(session.id)
                        deleteFlags.remove(session.id)
                    }
                    is Session.State.Failed -> {
                        // Reported before the null-context bail-out below, so failures on a
                        // session restored after a process death still show up in the numbers.
                        val errorCode = InstallErrorHelper.extractErrorCode(result.failure)
                        val errorType = InstallErrorHelper.classifyErrorType(result.failure, errorCode)
                        reportInstallResult(
                            TelemetryEvents.RESULT_FAILURE,
                            failureKey = InstallErrorHelper.failureKey(result.failure),
                            errorCode = errorCode,
                            errorType = errorType,
                            errorReason = errorCode,
                            id = session.id,
                        )
                        if (context == null) return@launch
                        val errorInfo = InstallErrorHelper.getErrorInfo(context, result.failure)
                        saveHistory(
                            sessionData,
                            success = false,
                            errorMessage = "${errorInfo.title}\n${errorInfo.guidance}",
                        )
                        // Device-specific workarounds go to the live error only — history keeps
                        // the plain diagnosis.
                        val shown = InstallErrorHelper.withDeviceHint(context, errorInfo, result.failure)
                        handleError("${shown.title}\n${shown.guidance}", session.id)
                    }
                }
            } catch (e: CancellationException) {
                reportInstallResult(TelemetryEvents.RESULT_CANCELLED, id = session.id)
                sessionDataRepository.removeSessionData(session.id)
                activeSessions.remove(session.id)
                sessionUris.remove(session.id)
                originalFileUris.remove(session.id)
                deleteFlags.remove(session.id)
                successHooks.remove(session.id)
                throw e
            } catch (e: Exception) {
                val isFrp = InstallErrorHelper.isFrpException(e)
                val errorCode = if (isFrp) "INSTALL_FAILED_SECURITY_FRP" else "INSTALL_FAILED_INTERNAL_ERROR"
                val errorType = if (isFrp) "security_frp" else "internal_error"
                val errorReason = if (isFrp) "security_frp" else e.javaClass.simpleName
                reportInstallResult(
                    TelemetryEvents.RESULT_FAILURE,
                    errorCode = errorCode,
                    errorType = errorType,
                    errorReason = errorReason,
                    id = session.id,
                )
                val userMessage = if (isFrp && context != null) {
                    val title = context.getString(R.string.install_error_security_frp_title)
                    val guidance = context.getString(R.string.install_error_security_frp_guidance)
                    "$title\n$guidance"
                } else {
                    e.message
                }
                saveHistory(sessionData, success = false, errorMessage = userMessage)
                handleError(userMessage, session.id)
                Timber.e(e, "Session error")
            }
        }
    }

    protected suspend fun saveHistory(
        sessionData: SessionData?,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        if (sessionData == null) return
        try {
            val rowId = historyDao.insert(
                InstallHistoryEntity(
                    appName = sessionData.appName.ifEmpty { sessionData.name },
                    packageName = sessionData.packageName,
                    fileName = sessionData.name,
                    versionName = sessionData.versionName,
                    oldVersionName = sessionData.oldVersionName,
                    fileSizeBytes = sessionData.fileSizeBytes,
                    iconPath = sessionData.iconPath,
                    success = success,
                    errorMessage = errorMessage,
                    sessionId = sessionData.id.toString(),
                    installerMode = sessionData.installerMode,
                    operationType = sessionData.operationType,
                    filePath = sessionData.filePath,
                )
            )
            // The same install can be observed by its headless notification owner and by an
            // activity restoring the session. The unique session ID makes the history write the
            // one atomic completion claim they share, so its downstream count stays one too.
            if (success && rowId != -1L) ReviewGate.recordSuccessfulInstall(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save install history")
        }
    }

    /**
     * Delete the file the user installed from. Shared with controllers that bypass the ackpine
     * session path (e.g. the root shell installer) and so don't go through the per-session
     * [deleteFlags] map.
     *
     * Returns false when the file is still on disk afterwards — [deleteSourceFileAndWarn] turns
     * that into something the user can see. Silently swallowing it is what made issue #100 look
     * like the setting did nothing at all.
     */
    protected fun deleteSourceFile(context: Context, uri: Uri): Boolean =
        app.pwhs.universalinstaller.presentation.install.util.SourceFileDeleter.deleteSourceFile(context, uri)

    protected suspend fun deleteSourceFileAndWarn(context: Context, uri: Uri) =
        app.pwhs.universalinstaller.presentation.install.util.SourceFileDeleter.deleteSourceFileAndWarn(context, uri)

    private suspend fun deleteSourceFileIfNeeded(sessionId: UUID, context: Context?) {
        if (deleteFlags[sessionId] != true || context == null) return
        val uri = originalFileUris[sessionId] ?: return
        deleteSourceFileAndWarn(context, uri)
    }

    private fun handleError(message: String?, sessionId: UUID) {
        val err = ResolvableString.raw(message ?: "Installation failed")
        sessionDataRepository.setError(sessionId, err)
    }

    protected suspend fun autoOpenAppIfNeeded(packageName: String?, context: Context?) {
        if (packageName.isNullOrBlank()) return
        val targetContext = context ?: this.context
        val prefs = runCatching { targetContext.dataStore.data.first() }.getOrNull()
        val autoOpen = prefs?.get(PreferencesKeys.AUTO_OPEN_AFTER_INSTALL) ?: false
        if (!autoOpen) return
        withContext(Dispatchers.Main) {
            val intent = targetContext.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { targetContext.startActivity(intent) }
                    .onFailure { Timber.e(it, "Failed to auto-open app $packageName") }
            }
        }
    }
}
