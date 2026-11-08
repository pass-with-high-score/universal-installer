package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object InstallVirusTotalHelper {

    /**
     * Whether the user has turned the whole VirusTotal integration off in Settings. Every
     * entry point into VirusTotal checks this first; when it is true nothing should touch the
     * VirusTotal API or surface any VirusTotal UI.
     */
    suspend fun isDisabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[PreferencesKeys.DISABLE_VIRUSTOTAL] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fail closed: if the preference can't be read, assume VirusTotal is disabled so a
            // flaky read can never let a lookup/upload through behind the user's back.
            true
        }
    }

    /**
     * Local SHA-256 of the file, with no VirusTotal interaction. Used even when VirusTotal is
     * disabled — the hash is shown in the APK info (with its copy action) regardless of the
     * feature toggle.
     */
    suspend fun computeSha256(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read >= 0) {
                    // A dismissed APK must stop its read loop; blocking reads can't be
                    // interrupted, so check cancellation between chunks.
                    ensureActive()
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (e: CancellationException) {
            // Never swallow cancellation into an empty hash — propagate so the job dies.
            throw e
        } catch (_: Exception) {
            // Ordinary read failure: empty hash, caller decides what to show.
            ""
        }
    }

    suspend fun readVirusTotalApiKey(context: Context): String {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[stringPreferencesKey("virustotal_api_key")] ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun launchHashLookupOnly(
        context: Context,
        originalUri: Uri,
        virusTotalService: VirusTotalService,
        onAutoLookupStart: () -> Unit = {},
    ): Pair<String, VtResult?> = withContext(Dispatchers.IO) {
        if (isDisabled(context)) return@withContext "" to VtResult(status = VtStatus.NO_API_KEY)
        return@withContext try {
            val sha256 = context.contentResolver.openInputStream(originalUri)?.use { input ->
                virusTotalService.computeSha256(input)
            } ?: ""

            if (sha256.isBlank()) {
                "" to VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
            } else if (VirusTotalKeyManager.shouldAutoLookupHash(context, sha256)) {
                onAutoLookupStart()
                val vtResult = VirusTotalKeyManager.checkFile(context, virusTotalService, sha256)
                if (vtResult.status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)) {
                    app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                }
                sha256 to vtResult
            } else if (!VirusTotalKeyManager.canUseVirusTotal(context, sha256)) {
                sha256 to VtResult(status = VtStatus.NO_API_KEY)
            } else {
                sha256 to null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusTotal hash lookup error")
            val message = when {
                VirusTotalService.isNetworkError(e) -> VirusTotalService.NETWORK_ERROR_TAG
                !e.message.isNullOrBlank() -> e.message.orEmpty()
                else -> ""
            }
            "" to VtResult(status = VtStatus.ERROR, errorMessage = message)
        }
    }

    suspend fun scanVirusTotal(
        context: Context,
        uri: Uri,
        fileName: String,
        current: ApkInfo,
        virusTotalService: VirusTotalService,
        virusTotalNotifier: VirusTotalNotifier,
        onUpdateSha256: (String) -> Unit,
        onProgress: (VtResult) -> Unit,
    ) {
        if (isDisabled(context)) return
        val canUseVt = VirusTotalKeyManager.canUseVirusTotal(context, current.sha256)
        val sizeBytes = current.fileSizeBytes

        if (canUseVt && sizeBytes > VirusTotalService.SIZE_LIMIT_LARGE) {
            onProgress(
                VtResult(
                    status = VtStatus.TOO_LARGE,
                    errorMessage = "${sizeBytes / (1024 * 1024)} MB",
                )
            )
            return
        }

        if (!canUseVt) {
            onProgress(VtResult(status = VtStatus.NO_API_KEY))
            return
        }

        val isDirectUpload = current.vtResult?.status == VtStatus.NOT_FOUND && current.sha256.isNotBlank()

        val scanNotifId = if (isDirectUpload) {
            virusTotalNotifier.notifyUploadingStart(fileName)
        } else {
            virusTotalNotifier.notifyHashing(fileName)
        }

        var scanFinished = false
        try {
            if (isDirectUpload) {
                onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
            } else {
                onProgress(VtResult(status = VtStatus.SCANNING))
            }

            val sha256 = current.sha256.ifBlank {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            virusTotalService.computeSha256(input)
                        } ?: ""
                    }
                } catch (e: CancellationException) {
                    // Rethrow so the scan job dies and the notification is properly cancelled
                    // below instead of being reported as "Could not hash file".
                    throw e
                } catch (_: Exception) {
                    ""
                }
            }

            if (sha256.isBlank()) {
                scanFinished = true
                finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not hash file", sha256, onProgress)
                return
            }

            onUpdateSha256(sha256)

            if (!VirusTotalKeyManager.canUseVirusTotal(context, sha256)) {
                onProgress(VtResult(status = VtStatus.NO_API_KEY))
                return
            }

            if (!isDirectUpload) {
                val hashResult = VirusTotalKeyManager.checkFile(context, virusTotalService, sha256)
                if (hashResult.status != VtStatus.NOT_FOUND) {
                    scanFinished = true
                    finishScan(context, virusTotalNotifier, scanNotifId, fileName, hashResult, sha256, onProgress)
                    return
                }
                onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
                virusTotalNotifier.notifyUploading(scanNotifId, fileName, 0)
            }

            val tempFile = runCatching {
                withContext(Dispatchers.IO) {
                    val ext = fileName.substringAfterLast('.', "apk").ifBlank { "apk" }
                    val f = File(context.cacheDir, "vt_${System.currentTimeMillis()}_upload.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                    f
                }
            }.getOrNull()

            if (tempFile == null || !tempFile.exists()) {
                scanFinished = true
                finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not read file for upload", sha256, onProgress)
                return
            }

            try {
                onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
                virusTotalNotifier.notifyUploading(scanNotifId, fileName, 0)

                val finalResult = VirusTotalKeyManager.uploadAndPoll(
                    context = context,
                    service = virusTotalService,
                    sha256 = sha256,
                    file = tempFile,
                    onUploadProgress = { pct ->
                        onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = pct))
                        virusTotalNotifier.notifyUploading(scanNotifId, fileName, pct)
                    },
                    onQueued = { analysisId ->
                        onProgress(VtResult(status = VtStatus.QUEUED, analysisId = analysisId))
                        virusTotalNotifier.notifyQueued(scanNotifId, fileName)
                    },
                    onPollProgress = { status, analysisId ->
                        onProgress(VtResult(status = status, analysisId = analysisId))
                        when (status) {
                            VtStatus.ANALYZING -> virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName)
                            VtStatus.QUEUED -> virusTotalNotifier.notifyQueued(scanNotifId, fileName)
                            else -> {}
                        }
                    },
                )
                scanFinished = true
                finishScan(context, virusTotalNotifier, scanNotifId, fileName, finalResult, sha256, onProgress)
            } finally {
                runCatching { tempFile.delete() }
            }
        } finally {
            if (!scanFinished) {
                virusTotalNotifier.cancel(scanNotifId)
            }
        }
    }

    private fun finishScan(
        context: Context,
        notifier: VirusTotalNotifier,
        scanNotifId: Int,
        fileName: String,
        result: VtResult,
        sha256: String,
        onProgress: (VtResult) -> Unit,
    ) {
        onProgress(result)
        val (title, text) = resultNotifCopy(context, result)
        notifier.notifyResult(scanNotifId, fileName, title, text, sha256)
    }

    private fun finishScanWithError(
        context: Context,
        notifier: VirusTotalNotifier,
        scanNotifId: Int,
        fileName: String,
        message: String,
        sha256: String,
        onProgress: (VtResult) -> Unit,
    ) {
        finishScan(
            context,
            notifier,
            scanNotifId,
            fileName,
            VtResult(status = VtStatus.ERROR, errorMessage = message),
            sha256,
            onProgress,
        )
    }

    fun resultNotifCopy(context: Context, result: VtResult): Pair<String, String> {
        return when (result.status) {
            VtStatus.CLEAN -> context.getString(R.string.vt_notif_result_clean) to
                    context.getString(R.string.apk_info_vt_clean)
            VtStatus.MALICIOUS -> context.getString(R.string.vt_notif_result_malicious) to
                    context.getString(R.string.apk_info_vt_malicious, result.malicious)
            VtStatus.SUSPICIOUS -> context.getString(R.string.vt_notif_result_suspicious) to
                    context.getString(R.string.apk_info_vt_suspicious, result.suspicious)
            VtStatus.NOT_FOUND -> context.getString(R.string.vt_notif_result_done) to
                    context.getString(R.string.apk_info_vt_not_found)
            VtStatus.ERROR -> context.getString(R.string.vt_notif_result_error) to
                    (result.errorMessage.ifBlank { "" })
            else -> context.getString(R.string.vt_notif_result_done) to ""
        }
    }
}
