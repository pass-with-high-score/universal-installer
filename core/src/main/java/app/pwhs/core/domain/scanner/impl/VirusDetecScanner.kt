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
import app.pwhs.core.network.DeviceIdProvider
import app.pwhs.core.network.VirusDetecApiException
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
            val trialExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
            if (trialExp > VirusTotalQuotaStore.currentTrueTimeMillis()) return true
            val hashes = VirusTotalQuotaStore.parseAppShieldHashesToday(prefs)
            val normalized = sha256.trim().lowercase(Locale.US)
            if (normalized.isNotBlank() && normalized in hashes) true
            else hashes.size < VirusTotalKeyManager.DEFAULT_APPSHIELD_DAILY_LIMIT
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
        if (service.deviceIdProvider == null) {
            service.deviceIdProvider = { DeviceIdProvider.getDeviceId(context) }
        }
        VirusTotalQuotaStore.syncWithServer(service)
        if (!isBuiltInQuotaAvailable(context, sha256)) {
            return@withContext Result.failure(
                ScanErrorException(
                    ScanError.RateLimited(
                        provider = providerId,
                        message = "Daily free AppShield scan limit reached (3/3)",
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
            val isPrecheck = response.report == null && response.precheckReport != null
            val report = response.report ?: response.precheckReport
            if (response.report != null) {
                recordBuiltInQuotaUsed(context, sha256)
            }
            report?.toSecurityScanResult(providerId = ScannerProviderId.VIRUSDETEC)
                ?.copy(isPrecheckOnly = isPrecheck)
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
        if (service.deviceIdProvider == null) {
            service.deviceIdProvider = { DeviceIdProvider.getDeviceId(context) }
        }
        VirusTotalQuotaStore.syncWithServer(service)
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
            if (cached != null && !cached.isPrecheckOnly && !options.forceRecheck) {
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
        val directFile = if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
            uri.path?.let(::File)?.takeIf { it.isFile && it.canRead() }
        } else {
            null
        }
        val tempFile = directFile ?: createTempUploadFile(context, uri, fileName)
        val shouldDeleteTemp = directFile == null
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
            if (shouldDeleteTemp) {
                runCatching { tempFile.delete() }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapExceptionToScanException(t: Throwable?): Throwable {
        val err = mapExceptionToScanError(t)
        return ScanErrorException(err)
    }

    private fun mapExceptionToScanError(t: Throwable?): ScanError {
        if (t is VirusDetecApiException) {
            return when (t.errorCode) {
                "RATE_LIMITED", "ERR_RATE_LIMITED" -> ScanError.RateLimited(
                    providerId,
                    retryAfterSeconds = t.rateLimit?.reset,
                    message = t.message,
                )
                "FILE_TOO_LARGE", "ERR_FILE_TOO_LARGE" -> ScanError.FileTooLarge(
                    providerId,
                    MAX_FILE_SIZE_BYTES,
                    MAX_FILE_SIZE_BYTES,
                    message = t.message,
                )
                "INVALID_API_KEY", "ERR_INVALID_API_KEY" -> ScanError.InvalidApiKey(providerId, t.message)
                "TIMEOUT", "SCAN_TIMEOUT", "ERR_SCAN_TIMEOUT" -> ScanError.Timeout(providerId, 60_000L, t.message)
                else -> when (t.httpCode) {
                    429 -> ScanError.RateLimited(providerId, message = t.message)
                    401, 403 -> ScanError.InvalidApiKey(providerId, t.message)
                    in 500..599 -> ScanError.ServerError(providerId, t.httpCode, t.message)
                    else -> ScanError.Generic(providerId, t.message, t)
                }
            }
        }
        val msg = t?.message.orEmpty()
        return when {
            t is java.net.SocketTimeoutException || t is io.ktor.client.plugins.HttpRequestTimeoutException ->
                ScanError.Timeout(providerId, 60_000L, "Connection timed out")
            t is java.net.ConnectException || t is java.net.UnknownHostException || msg == VirusDetecService.NETWORK_ERROR_TAG ->
                ScanError.NetworkError(providerId, t, "Network unavailable")
            msg.contains("Binary upload failed") -> ScanError.ServerError(providerId, 502, msg)
            msg.contains("429") -> ScanError.RateLimited(providerId, message = "AppShield rate limit reached")
            msg.contains("401") || msg.contains("403") -> ScanError.InvalidApiKey(providerId, "AppShield client unauthorized")
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ->
                ScanError.ServerError(providerId, 500, "AppShield server temporarily unavailable")
            else -> ScanError.Generic(providerId, msg.ifBlank { "Scan failed" }, t)
        }
    }

    private fun mapErrorCodeToScanError(code: String?, message: String?): ScanError {
        return when (code) {
            "RATE_LIMITED", "ERR_RATE_LIMITED" -> ScanError.RateLimited(providerId, message = message ?: "Rate limit reached")
            "FILE_TOO_LARGE", "ERR_FILE_TOO_LARGE" -> ScanError.FileTooLarge(providerId, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_BYTES)
            "TIMEOUT", "SCAN_TIMEOUT", "ERR_SCAN_TIMEOUT" -> ScanError.Timeout(providerId, 60_000L, message ?: "Scan timeout")
            "INVALID_API_KEY", "ERR_INVALID_API_KEY" -> ScanError.InvalidApiKey(providerId, message ?: "Invalid API key")
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
                f.outputStream().use { output -> input.copyTo(output, bufferSize = 128 * 1024) }
            }
            f
        }.getOrNull()
    }

    class ScanErrorException(val error: ScanError) : Exception(error.message, error.cause)
}
