package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.model.toVtResult
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
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

    suspend fun isDisabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[PreferencesKeys.DISABLE_VIRUSTOTAL] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    suspend fun computeSha256(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read >= 0) {
                    ensureActive()
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun launchHashLookupOnly(
        context: Context,
        originalUri: Uri,
        virusDetecService: VirusDetecService,
        apkInfo: ApkInfo? = null,
        onAutoLookupStart: () -> Unit = {},
    ): Pair<String, VtResult?> = withContext(Dispatchers.IO) {
        if (isDisabled(context)) return@withContext "" to null
        return@withContext try {
            val sha256 = computeSha256(context, originalUri)
            if (sha256.isBlank()) {
                "" to VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
            } else {
                onAutoLookupStart()
                val req = LookupRequest(
                    sha256 = sha256,
                    packageName = apkInfo?.packageName.orEmpty(),
                    appName = apkInfo?.appName.orEmpty(),
                    versionName = apkInfo?.versionName.orEmpty(),
                    versionCode = apkInfo?.versionCode ?: 0L,
                    minSdk = apkInfo?.minSdkVersion ?: 0,
                    targetSdk = apkInfo?.targetSdkVersion ?: 0,
                    certSha256 = apkInfo?.certSha256.orEmpty(),
                    permissions = apkInfo?.permissions.orEmpty(),
                )
                val lookupResult = virusDetecService.lookup(req)
                if (lookupResult.isSuccess) {
                    val resp = lookupResult.getOrThrow()
                    val report = resp.report ?: resp.precheckReport
                    if (report != null) {
                        app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                        sha256 to report.toVtResult()
                    } else if (resp.needUpload) {
                        sha256 to VtResult(status = VtStatus.NOT_FOUND)
                    } else {
                        sha256 to null
                    }
                } else {
                    Timber.w(lookupResult.exceptionOrNull(), "VirusDetec lookup failed")
                    sha256 to null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusDetec lookup error")
            "" to VtResult(status = VtStatus.ERROR, errorMessage = e.message.orEmpty())
        }
    }

    suspend fun scanVirusTotal(
        context: Context,
        uri: Uri,
        fileName: String,
        current: ApkInfo,
        virusDetecService: VirusDetecService,
        virusTotalNotifier: VirusTotalNotifier,
        onUpdateSha256: (String) -> Unit,
        onProgress: (VtResult) -> Unit,
    ) {
        if (isDisabled(context)) return
        val sizeBytes = current.fileSizeBytes
        if (sizeBytes > 300L * 1024 * 1024) {
            onProgress(
                VtResult(
                    status = VtStatus.TOO_LARGE,
                    errorMessage = "${sizeBytes / (1024 * 1024)} MB",
                )
            )
            return
        }

        val scanNotifId = virusTotalNotifier.notifyHashing(fileName)
        var scanFinished = false

        try {
            onProgress(VtResult(status = VtStatus.SCANNING))

            val sha256 = current.sha256.ifBlank {
                computeSha256(context, uri)
            }
            if (sha256.isBlank()) {
                scanFinished = true
                finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not hash file", sha256, onProgress)
                return
            }
            onUpdateSha256(sha256)

            // 1. Try fast precheck/cache lookup first
            val req = LookupRequest(
                sha256 = sha256,
                packageName = current.packageName,
                appName = current.appName,
                versionName = current.versionName,
                versionCode = current.versionCode,
                minSdk = current.minSdkVersion,
                targetSdk = current.targetSdkVersion,
                certSha256 = current.certSha256,
                permissions = current.permissions,
            )
            val lookupResult = virusDetecService.lookup(req)
            if (lookupResult.isSuccess) {
                val resp = lookupResult.getOrThrow()
                val report = resp.report ?: resp.precheckReport
                if (report != null) {
                    scanFinished = true
                    app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                    finishScan(context, virusTotalNotifier, scanNotifId, fileName, report.toVtResult(), sha256, onProgress)
                    return
                }
            }

            // 2. File needs upload -> copy to temp file and upload ticket
            val tempFile = runCatching {
                withContext(Dispatchers.IO) {
                    val ext = fileName.substringAfterLast('.', "apk").ifBlank { "apk" }
                    val f = File(context.cacheDir, "sec_${System.currentTimeMillis()}.$ext")
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

                val ticketRes = virusDetecService.createUploadTicket(sha256, tempFile.length())
                if (ticketRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, ticketRes.exceptionOrNull()?.message ?: "Upload ticket failed", sha256, onProgress)
                    return
                }
                val ticket = ticketRes.getOrThrow()

                val uploadRes = virusDetecService.uploadBinary(ticket.uploadUrl, tempFile) { pct ->
                    onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = pct))
                    virusTotalNotifier.notifyUploading(scanNotifId, fileName, pct)
                }
                if (uploadRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Upload failed", sha256, onProgress)
                    return
                }

                val startRes = virusDetecService.startScan(ticket.jobId)
                if (startRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Start scan failed", sha256, onProgress)
                    return
                }

                onProgress(VtResult(status = VtStatus.ANALYZING, uploadProgress = 100))
                virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName)

                virusDetecService.watchScanStream(ticket.jobId).collect { update ->
                    when (update.status) {
                        "COMPLETED" -> {
                            val finalResult = update.report?.toVtResult() ?: VtResult(status = VtStatus.CLEAN)
                            scanFinished = true
                            app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                            finishScan(context, virusTotalNotifier, scanNotifId, fileName, finalResult, sha256, onProgress)
                        }
                        "FAILED" -> {
                            scanFinished = true
                            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, update.errorCode ?: "Scan failed", sha256, onProgress)
                        }
                        else -> {
                            onProgress(
                                VtResult(
                                    status = VtStatus.ANALYZING,
                                    uploadProgress = update.progressPercent,
                                    errorMessage = update.message,
                                )
                            )
                        }
                    }
                }
            } finally {
                runCatching { tempFile.delete() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusDetec scan error")
            scanFinished = true
            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, e.message.orEmpty(), current.sha256, onProgress)
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
