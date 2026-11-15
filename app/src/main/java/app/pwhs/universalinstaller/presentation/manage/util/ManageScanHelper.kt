package app.pwhs.universalinstaller.presentation.manage.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.scanner.ScanApkMetadata
import app.pwhs.core.domain.scanner.ScanOptions
import app.pwhs.core.domain.scanner.ScanStep
import app.pwhs.core.domain.scanner.SecurityScannerOrchestrator
import app.pwhs.core.domain.scanner.toVtResult
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import app.pwhs.universalinstaller.presentation.install.util.InstallVirusTotalHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object ManageScanHelper {

    suspend fun scanInstalledApp(
        context: Context,
        app: InstalledApp,
        scannerOrchestrator: SecurityScannerOrchestrator,
        notifier: VirusTotalNotifier,
        onUpdateSha256: (String) -> Unit = {},
        onProgress: (VtResult) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (InstallVirusTotalHelper.isDisabled(context, scannerOrchestrator)) {
            onProgress(VtResult(status = VtStatus.NO_API_KEY, errorMessage = "Scanning disabled"))
            return@withContext
        }

        val scanNotifId: Int = runCatching { notifier.notifyHashing(app.appName) }.getOrDefault(0)
        var scanFinished = false

        try {
            onProgress(VtResult(status = VtStatus.SCANNING))

            val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
            val baseApkFile = File(appInfo.sourceDir)
            if (!baseApkFile.exists()) {
                scanFinished = true
                onProgress(VtResult(status = VtStatus.ERROR, errorMessage = "APK file not found"))
                return@withContext
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            baseApkFile.inputStream().use { input ->
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = input.read(buffer)
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            onUpdateSha256(sha256)

            val metadata = ScanApkMetadata(
                sha256 = sha256,
                packageName = app.packageName,
                appName = app.appName,
                versionName = app.versionName,
                fileSizeBytes = baseApkFile.length(),
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

            val uri = Uri.fromFile(baseApkFile)

            scannerOrchestrator.scan(context, uri, "${app.appName}.apk", metadata, options).collect { step ->
                val vtResult = step.toVtResult(sha256)
                when (step) {
                    is ScanStep.Hashing,
                    is ScanStep.LookupCache -> {
                        onProgress(vtResult)
                    }
                    is ScanStep.Uploading -> {
                        onProgress(vtResult)
                        runCatching { notifier.notifyUploading(scanNotifId, app.appName, step.progressPercent) }
                    }
                    is ScanStep.Queued -> {
                        onProgress(vtResult)
                        runCatching { notifier.notifyQueued(scanNotifId, app.appName) }
                    }
                    is ScanStep.Analyzing,
                    is ScanStep.FallbackTriggered -> {
                        onProgress(vtResult)
                        runCatching { notifier.notifyAnalyzing(scanNotifId, app.appName) }
                    }
                    is ScanStep.Completed -> {
                        scanFinished = true
                        UserStatsTracker.recordVirusTotalScan(context)
                        val (title, text) = runCatching { InstallVirusTotalHelper.resultNotifCopy(context, vtResult) }.getOrDefault("" to "")
                        runCatching { notifier.notifyResult(scanNotifId, app.appName, title, text, sha256) }
                        onProgress(vtResult)
                    }
                    is ScanStep.Failed -> {
                        scanFinished = true
                        val (title, text) = runCatching { InstallVirusTotalHelper.resultNotifCopy(context, vtResult) }.getOrDefault("" to "")
                        runCatching { notifier.notifyResult(scanNotifId, app.appName, title, text, sha256) }
                        onProgress(vtResult)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Security scan error for ${app.packageName}")
            scanFinished = true
            onProgress(VtResult(status = VtStatus.ERROR, errorMessage = e.message.orEmpty()))
        } finally {
            if (!scanFinished) {
                runCatching { notifier.cancel(scanNotifId) }
            }
        }
    }
}
