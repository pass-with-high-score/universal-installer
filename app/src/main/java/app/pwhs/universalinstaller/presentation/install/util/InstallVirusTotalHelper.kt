package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object InstallVirusTotalHelper {

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
    ): Pair<String, VtResult> = withContext(Dispatchers.IO) {
        val apiKey = readVirusTotalApiKey(context)
        runCatching {
            val sha256 = context.contentResolver.openInputStream(originalUri)?.use { input ->
                virusTotalService.computeSha256(input)
            } ?: ""

            if (sha256.isBlank()) {
                "" to VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
            } else if (apiKey.isBlank()) {
                sha256 to VtResult(status = VtStatus.NO_API_KEY)
            } else {
                sha256 to virusTotalService.checkFile(apiKey, sha256)
            }
        }.getOrElse { e ->
            Timber.e(e, "VirusTotal hash lookup error")
            "" to VtResult(status = VtStatus.ERROR, errorMessage = e.message ?: "Unknown error")
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
        val apiKey = readVirusTotalApiKey(context)
        val sizeBytes = current.fileSizeBytes

        if (apiKey.isNotBlank() && sizeBytes > VirusTotalService.SIZE_LIMIT_LARGE) {
            onProgress(
                VtResult(
                    status = VtStatus.TOO_LARGE,
                    errorMessage = "${sizeBytes / (1024 * 1024)} MB",
                )
            )
            return
        }

        if (apiKey.isBlank()) {
            onProgress(VtResult(status = VtStatus.NO_API_KEY))
            return
        }

        val isDirectUpload = current.vtResult?.status == VtStatus.NOT_FOUND && current.sha256.isNotBlank()

        val scanNotifId = if (isDirectUpload) {
            virusTotalNotifier.notifyUploadingStart(fileName)
        } else {
            virusTotalNotifier.notifyHashing(fileName)
        }

        if (isDirectUpload) {
            onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
        } else {
            onProgress(VtResult(status = VtStatus.SCANNING))
        }

        val sha256 = current.sha256.ifBlank {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        virusTotalService.computeSha256(input)
                    } ?: ""
                }
            }.getOrDefault("")
        }

        if (sha256.isBlank()) {
            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not hash file", sha256, onProgress)
            return
        }

        onUpdateSha256(sha256)

        if (!isDirectUpload) {
            val hashResult = virusTotalService.checkFile(apiKey, sha256)
            if (hashResult.status != VtStatus.NOT_FOUND) {
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
            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not read file for upload", sha256, onProgress)
            return
        }

        try {
            onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
            virusTotalNotifier.notifyUploading(scanNotifId, fileName, 0)

            val uploadResult = virusTotalService.uploadFile(apiKey, tempFile) { pct ->
                onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = pct))
                virusTotalNotifier.notifyUploading(scanNotifId, fileName, pct)
            }
            val analysisId = uploadResult.getOrElse { e ->
                if (e is VirusTotalService.VtHttpException) {
                    finishScan(
                        context,
                        virusTotalNotifier,
                        scanNotifId,
                        fileName,
                        VtResult(status = e.vtStatus, errorMessage = e.message.orEmpty()),
                        sha256,
                        onProgress,
                    )
                } else {
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, e.message ?: "Upload failed", sha256, onProgress)
                }
                return
            }

            onProgress(VtResult(status = VtStatus.QUEUED, analysisId = analysisId))
            virusTotalNotifier.notifyQueued(scanNotifId, fileName)

            val finalResult = virusTotalService.pollAnalysis(apiKey, analysisId) { status ->
                onProgress(VtResult(status = status, analysisId = analysisId))
                when (status) {
                    VtStatus.ANALYZING -> virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName)
                    VtStatus.QUEUED -> virusTotalNotifier.notifyQueued(scanNotifId, fileName)
                    else -> {}
                }
            }
            finishScan(context, virusTotalNotifier, scanNotifId, fileName, finalResult, sha256, onProgress)
        } finally {
            runCatching { tempFile.delete() }
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
