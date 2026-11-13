package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.domain.scanner.DefaultSecurityPreferencesDataSource
import app.pwhs.core.domain.scanner.ScanApkMetadata
import app.pwhs.core.domain.scanner.ScanOptions
import app.pwhs.core.domain.scanner.ScanStep
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.SecurityScannerOrchestrator
import app.pwhs.core.domain.scanner.impl.VirusDetecScanner
import app.pwhs.core.domain.scanner.toVtResult
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

object InstallVirusTotalHelper {

    suspend fun isDisabled(context: Context, orchestrator: SecurityScannerOrchestrator? = null): Boolean {
        return try {
            orchestrator?.isScanningDisabled(context)
                ?: DefaultSecurityPreferencesDataSource().isScanningDisabled(context)
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
        scannerOrchestrator: SecurityScannerOrchestrator,
        apkInfo: ApkInfo? = null,
        onAutoLookupStart: () -> Unit = {},
    ): Pair<String, VtResult?> = withContext(Dispatchers.IO) {
        if (isDisabled(context, scannerOrchestrator)) return@withContext "" to null
        return@withContext try {
            val sha256 = computeSha256(context, originalUri)
            if (sha256.isBlank()) {
                "" to VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
            } else {
                onAutoLookupStart()
                val metadata = ScanApkMetadata(
                    sha256 = sha256,
                    packageName = apkInfo?.packageName.orEmpty(),
                    appName = apkInfo?.appName.orEmpty(),
                    versionName = apkInfo?.versionName.orEmpty(),
                    versionCode = apkInfo?.versionCode ?: 0L,
                    minSdk = apkInfo?.minSdkVersion ?: 0,
                    targetSdk = apkInfo?.targetSdkVersion ?: 0,
                    certSha256 = apkInfo?.certSha256.orEmpty(),
                    fileSizeBytes = apkInfo?.fileSizeBytes ?: 0L,
                    permissions = apkInfo?.permissions.orEmpty(),
                )
                val lookupResult = scannerOrchestrator.lookupHash(context, sha256, metadata)
                if (lookupResult.isSuccess) {
                    val scanResult = lookupResult.getOrThrow()
                    if (scanResult != null) {
                        UserStatsTracker.recordVirusTotalScan(context)
                        sha256 to scanResult.toVtResult()
                    } else {
                        sha256 to VtResult(status = VtStatus.NOT_FOUND)
                    }
                } else {
                    Timber.w(lookupResult.exceptionOrNull(), "SecurityScanner lookup failed")
                    sha256 to null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SecurityScanner lookup error")
            "" to VtResult(status = VtStatus.ERROR, errorMessage = e.message.orEmpty())
        }
    }

    suspend fun launchHashLookupOnly(
        context: Context,
        originalUri: Uri,
        virusDetecService: VirusDetecService,
        apkInfo: ApkInfo? = null,
        onAutoLookupStart: () -> Unit = {},
    ): Pair<String, VtResult?> = launchHashLookupOnly(
        context = context,
        originalUri = originalUri,
        scannerOrchestrator = SecurityScannerOrchestrator(
            mapOf(ScannerProviderId.VIRUSDETEC to VirusDetecScanner(virusDetecService))
        ),
        apkInfo = apkInfo,
        onAutoLookupStart = onAutoLookupStart,
    )

    suspend fun scanVirusTotal(
        context: Context,
        uri: Uri,
        fileName: String,
        current: ApkInfo,
        scannerOrchestrator: SecurityScannerOrchestrator,
        virusTotalNotifier: VirusTotalNotifier,
        onUpdateSha256: (String) -> Unit,
        onProgress: (VtResult) -> Unit,
    ) {
        if (isDisabled(context, scannerOrchestrator)) return
        val sizeBytes = current.fileSizeBytes
        if (sizeBytes > 650L * 1024 * 1024) {
            onProgress(
                VtResult(
                    status = VtStatus.TOO_LARGE,
                    errorMessage = "${sizeBytes / (1024 * 1024)} MB",
                )
            )
            return
        }

        val scanNotifId = runCatching { virusTotalNotifier.notifyHashing(fileName) }.getOrDefault(0)
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

            val metadata = ScanApkMetadata(
                sha256 = sha256,
                packageName = current.packageName,
                appName = current.appName,
                versionName = current.versionName,
                versionCode = current.versionCode,
                minSdk = current.minSdkVersion,
                targetSdk = current.targetSdkVersion,
                certSha256 = current.certSha256,
                fileSizeBytes = current.fileSizeBytes,
                permissions = current.permissions,
            )
            val preferredLanguage = try {
                context.resources.configuration.locales[0]?.language ?: "vi"
            } catch (_: Exception) {
                "vi"
            }
            val options = ScanOptions(
                forceRecheck = false,
                allowUpload = true,
                preferredLanguage = preferredLanguage,
                enableFallback = true,
                runOfflineChecks = false,
            )

            scannerOrchestrator.scan(context, uri, fileName, metadata, options).collect { step ->
                val vtResult = step.toVtResult(sha256)
                when (step) {
                    is ScanStep.Hashing,
                    is ScanStep.LookupCache -> {
                        onProgress(vtResult)
                    }
                    is ScanStep.Uploading -> {
                        onProgress(vtResult)
                        runCatching { virusTotalNotifier.notifyUploading(scanNotifId, fileName, step.progressPercent) }
                    }
                    is ScanStep.Queued -> {
                        onProgress(vtResult)
                        runCatching { virusTotalNotifier.notifyQueued(scanNotifId, fileName) }
                    }
                    is ScanStep.Analyzing -> {
                        onProgress(vtResult)
                        runCatching { virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName) }
                    }
                    is ScanStep.FallbackTriggered -> {
                        onProgress(vtResult)
                        runCatching { virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName) }
                    }
                    is ScanStep.Completed -> {
                        scanFinished = true
                        UserStatsTracker.recordVirusTotalScan(context)
                        finishScan(context, virusTotalNotifier, scanNotifId, fileName, vtResult, sha256, onProgress)
                    }
                    is ScanStep.Failed -> {
                        scanFinished = true
                        finishScan(context, virusTotalNotifier, scanNotifId, fileName, vtResult, sha256, onProgress)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Security scan error")
            scanFinished = true
            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, e.message.orEmpty(), current.sha256, onProgress)
        } finally {
            if (!scanFinished) {
                runCatching { virusTotalNotifier.cancel(scanNotifId) }
            }
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
    ) = scanVirusTotal(
        context = context,
        uri = uri,
        fileName = fileName,
        current = current,
        scannerOrchestrator = SecurityScannerOrchestrator(
            mapOf(ScannerProviderId.VIRUSDETEC to VirusDetecScanner(virusDetecService))
        ),
        virusTotalNotifier = virusTotalNotifier,
        onUpdateSha256 = onUpdateSha256,
        onProgress = onProgress,
    )

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
        val (title, text) = runCatching { resultNotifCopy(context, result) }.getOrDefault("" to "")
        runCatching { notifier.notifyResult(scanNotifId, fileName, title, text, sha256) }
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
            VtStatus.RATE_LIMITED,
            VtStatus.NO_API_KEY,
            VtStatus.INVALID_API_KEY,
            VtStatus.TOO_LARGE,
            VtStatus.ERROR -> context.getString(R.string.vt_notif_result_error) to
                    (result.errorMessage.ifBlank { "" })
            else -> context.getString(R.string.vt_notif_result_done) to ""
        }
    }
}
