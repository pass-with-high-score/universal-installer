package app.pwhs.core.domain.scanner.impl

import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.scanner.ApkSecurityScanner
import app.pwhs.core.domain.scanner.ScanApkMetadata
import app.pwhs.core.domain.scanner.ScanError
import app.pwhs.core.domain.scanner.ScanOptions
import app.pwhs.core.domain.scanner.ScanStep
import app.pwhs.core.domain.scanner.ScannerCapabilities
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.SecurityScanResult
import app.pwhs.core.domain.scanner.toSecurityScanResult
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalQuotaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale

class VirusDetecScanner(
    private val service: VirusDetecService = VirusDetecService(),
) : ApkSecurityScanner {

    override val providerId: ScannerProviderId = ScannerProviderId.VIRUSDETEC

    override val capabilities: ScannerCapabilities = ScannerCapabilities(
        supportsHashLookup = true,
        supportsBinaryUpload = true,
        requiresNetwork = true,
        requiresApiKey = false,
        maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
        supportsStreamingProgress = true,
    )

    companion object {
        const val MAX_FILE_SIZE_BYTES = 300L * 1024 * 1024 // 300 MB
    }

    override suspend fun isConfigured(context: Context): Boolean = true

    private suspend fun isBuiltInQuotaAvailable(context: Context, sha256: String): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            if (prefs[SharedPrefsKeys.IS_PRO_USER] == true) return true
            val hashes = VirusTotalQuotaStore.parseAppShieldHashesToday(prefs)
            val normalized = sha256.trim().lowercase(Locale.US)
            if (normalized.isNotBlank() && normalized in hashes) true
            else hashes.size < VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun recordBuiltInQuotaUsed(context: Context, sha256: String) {
        if (!VirusTotalKeyManager.isProUser(context)) {
            VirusTotalQuotaStore.recordAppShieldHashUsed(context, sha256)
        }
    }

    override suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?> = withContext(Dispatchers.IO) {
        if (!isBuiltInQuotaAvailable(context, sha256)) {
            return@withContext Result.failure(
                ScanErrorException(
                    ScanError.RateLimited(
                        provider = providerId,
                        message = "Đã hết lượt quét AppShield miễn phí hôm nay (3/3)",
                    )
                )
            )
        }
        runCatching {
            val req = LookupRequest(
                sha256 = sha256,
                packageName = metadata.packageName,
                appName = metadata.appName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                minSdk = metadata.minSdk,
                targetSdk = metadata.targetSdk,
                certSha256 = metadata.certSha256,
                permissions = metadata.permissions,
                services = metadata.services,
                receivers = metadata.receivers,
            )
            val response = service.lookup(req).getOrThrow()
            val report = response.report ?: response.precheckReport
            if (report != null) {
                recordBuiltInQuotaUsed(context, sha256)
            }
            report?.toSecurityScanResult(providerId = ScannerProviderId.VIRUSDETEC)
        }.recoverCatching { t ->
            if (t is ScanErrorException) throw t
            throw mapExceptionToScanException(t)
        }
    }

    override fun scanApk(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions,
    ): Flow<ScanStep> = channelFlow {
        // 1. File size verification
        val fileSize = if (metadata.fileSizeBytes > 0) metadata.fileSizeBytes else queryFileSize(context, uri)
        if (fileSize > capabilities.maxFileSizeBytes) {
            send(ScanStep.Failed(ScanError.FileTooLarge(providerId, fileSize, capabilities.maxFileSizeBytes)))
            return@channelFlow
        }

        // 2. Hash calculation
        var sha256 = metadata.sha256
        if (sha256.isBlank()) {
            send(ScanStep.Hashing(0))
            val computed = runCatching {
                context.contentResolver.openInputStream(uri)?.use { service.computeSha256(it) }
            }.getOrNull()

            if (computed.isNullOrBlank()) {
                send(ScanStep.Failed(ScanError.Generic(providerId, "Could not compute SHA-256 for $fileName")))
                return@channelFlow
            }
            sha256 = computed
            send(ScanStep.Hashing(100))
        }

        // 3. Fast Cache Lookup
        send(ScanStep.LookupCache(sha256))
        val lookupResult = lookupHash(context, sha256, metadata.copy(sha256 = sha256))
        if (lookupResult.isSuccess) {
            val cached = lookupResult.getOrNull()
            if (cached != null && !options.forceRecheck) {
                send(ScanStep.Completed(cached))
                return@channelFlow
            }
        } else {
            val err = lookupResult.exceptionOrNull()
            val scanError = if (err is ScanErrorException) err.error else mapExceptionToScanError(err)
            if (scanError.isEligibleForFallback()) {
                send(ScanStep.Failed(scanError))
                return@channelFlow
            }
        }

        // 4. Upload Check
        if (!options.allowUpload) {
            send(ScanStep.Failed(ScanError.NotFound(providerId, sha256)))
            return@channelFlow
        }

        // 5. Binary Upload and SSE Processing
        val tempFile = createTempUploadFile(context, uri, fileName)
        if (tempFile == null || !tempFile.exists()) {
            send(ScanStep.Failed(ScanError.Generic(providerId, "Could not prepare file for upload")))
            return@channelFlow
        }

        try {
            send(ScanStep.Uploading(progressPercent = 0, bytesUploaded = 0L, totalBytes = tempFile.length()))
            val ticketRes = service.createUploadTicket(sha256, tempFile.length(), options.preferredLanguage)
            if (ticketRes.isFailure) {
                send(ScanStep.Failed(mapExceptionToScanError(ticketRes.exceptionOrNull())))
                return@channelFlow
            }
            val ticket = ticketRes.getOrThrow()

            val uploadRes = service.uploadBinary(ticket.uploadUrl, tempFile) { pct ->
                val sent = (tempFile.length() * pct / 100).coerceAtMost(tempFile.length())
                trySend(ScanStep.Uploading(progressPercent = pct, bytesUploaded = sent, totalBytes = tempFile.length()))
            }
            if (uploadRes.isFailure) {
                send(ScanStep.Failed(mapExceptionToScanError(uploadRes.exceptionOrNull())))
                return@channelFlow
            }

            send(ScanStep.Queued(queuePosition = null, message = "Scan queued", analysisId = ticket.jobId))
            val startRes = service.startScan(ticket.jobId, options.preferredLanguage)
            if (startRes.isFailure) {
                send(ScanStep.Failed(mapExceptionToScanError(startRes.exceptionOrNull())))
                return@channelFlow
            }

            service.watchScanStream(ticket.jobId, options.preferredLanguage).collect { update ->
                when (update.status) {
                    "COMPLETED" -> {
                        recordBuiltInQuotaUsed(context, sha256)
                        val finalResult = update.report?.toSecurityScanResult(providerId) ?: SecurityScanResult(
                            providerId = ScannerProviderId.VIRUSDETEC,
                            sha256 = sha256,
                            verdict = app.pwhs.core.domain.scanner.ScannerVerdict.SAFE,
                        )
                        send(ScanStep.Completed(finalResult))
                    }
                    "FAILED" -> {
                        send(ScanStep.Failed(mapErrorCodeToScanError(update.errorCode, update.message)))
                    }
                    else -> {
                        send(ScanStep.Analyzing(progressPercent = update.progressPercent, stageDescription = update.message))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusDetecScanner scan execution failed")
            send(ScanStep.Failed(mapExceptionToScanError(e)))
        } finally {
            runCatching { tempFile.delete() }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapExceptionToScanException(t: Throwable?): Throwable {
        val err = mapExceptionToScanError(t)
        return ScanErrorException(err)
    }

    private fun mapExceptionToScanError(t: Throwable?): ScanError {
        val msg = t?.message.orEmpty()
        return when {
            t is java.net.SocketTimeoutException || t is io.ktor.client.plugins.HttpRequestTimeoutException ->
                ScanError.Timeout(providerId, 60_000L, "Connection timed out")
            t is java.net.ConnectException || t is java.net.UnknownHostException || msg == VirusDetecService.NETWORK_ERROR_TAG ->
                ScanError.NetworkError(providerId, t, "Network unavailable")
            msg.contains("429") -> ScanError.RateLimited(providerId, message = "AppShield rate limit reached")
            msg.contains("401") || msg.contains("403") -> ScanError.InvalidApiKey(providerId, "AppShield client unauthorized")
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ->
                ScanError.ServerError(providerId, 500, "AppShield server temporarily unavailable")
            else -> ScanError.Generic(providerId, msg.ifBlank { "Scan failed" }, t)
        }
    }

    private fun mapErrorCodeToScanError(code: String?, message: String?): ScanError {
        return when (code) {
            "RATE_LIMITED" -> ScanError.RateLimited(providerId, message = message ?: "Rate limit reached")
            "FILE_TOO_LARGE" -> ScanError.FileTooLarge(providerId, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_BYTES)
            else -> ScanError.Generic(providerId, message ?: code ?: "Scan processing failed")
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L
    }

    private fun createTempUploadFile(context: Context, uri: Uri, fileName: String): File? {
        return runCatching {
            val ext = fileName.substringAfterLast('.', "apk").ifBlank { "apk" }
            val f = File(context.cacheDir, "vd_upload_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
            f
        }.getOrNull()
    }

    class ScanErrorException(val error: ScanError) : Exception(error.message, error.cause)
}
