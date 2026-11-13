package app.pwhs.core.domain.scanner.impl

import android.content.Context
import android.net.Uri
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.scanner.ApkSecurityScanner
import app.pwhs.core.domain.scanner.EngineDetection
import app.pwhs.core.domain.scanner.ScanApkMetadata
import app.pwhs.core.domain.scanner.ScanError
import app.pwhs.core.domain.scanner.ScanOptions
import app.pwhs.core.domain.scanner.ScanStep
import app.pwhs.core.domain.scanner.ScannerCapabilities
import app.pwhs.core.domain.scanner.ScannerFinding
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.ScannerSeverity
import app.pwhs.core.domain.scanner.ScannerVerdict
import app.pwhs.core.domain.scanner.SecurityScanResult
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class VirusTotalScanner(
    private val service: VirusTotalService = VirusTotalService(),
    private val keyManager: VirusTotalKeyManager = VirusTotalKeyManager,
) : ApkSecurityScanner {

    override val providerId: ScannerProviderId = ScannerProviderId.VIRUSTOTAL

    override val capabilities: ScannerCapabilities = ScannerCapabilities(
        supportsHashLookup = true,
        supportsBinaryUpload = true,
        requiresNetwork = true,
        requiresApiKey = false,
        maxFileSizeBytes = VirusTotalService.SIZE_LIMIT_LARGE,
        supportsStreamingProgress = true,
    )

    override suspend fun isConfigured(context: Context): Boolean {
        return keyManager.canUseVirusTotal(context)
    }

    override suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?> = withContext(Dispatchers.IO) {
        if (sha256.isBlank()) return@withContext Result.failure(IllegalArgumentException("SHA-256 cannot be blank"))
        return@withContext try {
            val vtResult = keyManager.checkFile(context, service, sha256)
            when (vtResult.status) {
                VtStatus.NOT_FOUND -> Result.success(null)
                VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS -> {
                    Result.success(toSecurityScanResult(vtResult, sha256))
                }
                VtStatus.RATE_LIMITED -> {
                    Result.failure(ScanErrorException(ScanError.RateLimited(providerId, vtResult.errorMessage.toLongOrNull(), "VirusTotal rate limit exceeded")))
                }
                VtStatus.NO_API_KEY -> {
                    Result.failure(ScanErrorException(ScanError.NoApiKey(providerId, "VirusTotal API key missing or daily quota exhausted")))
                }
                VtStatus.INVALID_API_KEY -> {
                    Result.failure(ScanErrorException(ScanError.InvalidApiKey(providerId, "VirusTotal API key is invalid")))
                }
                else -> {
                    if (vtResult.errorMessage == VirusTotalService.NETWORK_ERROR_TAG) {
                        Result.failure(ScanErrorException(ScanError.NetworkError(providerId, message = "Network error during VirusTotal lookup")))
                    } else {
                        Result.failure(ScanErrorException(ScanError.ServerError(providerId, 500, vtResult.errorMessage.ifBlank { "VirusTotal lookup error" })))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun scanApk(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions,
    ): Flow<ScanStep> = channelFlow {
        val sizeBytes = if (metadata.fileSizeBytes > 0) metadata.fileSizeBytes else queryFileSize(context, uri)
        if (sizeBytes > VirusTotalService.SIZE_LIMIT_LARGE) {
            send(ScanStep.Failed(ScanError.FileTooLarge(providerId, sizeBytes, VirusTotalService.SIZE_LIMIT_LARGE)))
            return@channelFlow
        }

        val sha256 = if (metadata.sha256.isNotBlank()) {
            metadata.sha256
        } else {
            send(ScanStep.Hashing(0))
            val hash = try {
                context.contentResolver.openInputStream(uri)?.use { service.computeSha256(it) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(ScanStep.Failed(ScanError.Generic(providerId, "Failed to compute SHA-256", e)))
                return@channelFlow
            }
            send(ScanStep.Hashing(100))
            hash
        }

        if (sha256.isBlank()) {
            send(ScanStep.Failed(ScanError.Generic(providerId, "Could not hash APK file")))
            return@channelFlow
        }

        if (!options.forceRecheck) {
            send(ScanStep.LookupCache(sha256))
            val checkResult = keyManager.checkFile(context, service, sha256)
            when (checkResult.status) {
                VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS -> {
                    send(ScanStep.Completed(toSecurityScanResult(checkResult, sha256)))
                    return@channelFlow
                }
                VtStatus.RATE_LIMITED -> {
                    send(ScanStep.Failed(ScanError.RateLimited(providerId, checkResult.errorMessage.toLongOrNull(), "VirusTotal rate limited")))
                    return@channelFlow
                }
                VtStatus.INVALID_API_KEY -> {
                    send(ScanStep.Failed(ScanError.InvalidApiKey(providerId, "VirusTotal API key invalid")))
                    return@channelFlow
                }
                VtStatus.NO_API_KEY -> {
                    send(ScanStep.Failed(ScanError.NoApiKey(providerId, "VirusTotal API key missing or quota exhausted")))
                    return@channelFlow
                }
                VtStatus.NOT_FOUND -> {
                    // Proceed to upload
                }
                else -> {
                    if (checkResult.errorMessage == VirusTotalService.NETWORK_ERROR_TAG) {
                        send(ScanStep.Failed(ScanError.NetworkError(providerId, message = "Network error during VirusTotal lookup")))
                        return@channelFlow
                    }
                }
            }
        }

        if (!options.allowUpload) {
            send(ScanStep.Failed(ScanError.NotFound(providerId, sha256)))
            return@channelFlow
        }

        val tempFile = createTempApkFile(context, uri, fileName)
        if (tempFile == null || !tempFile.exists()) {
            send(ScanStep.Failed(ScanError.Generic(providerId, "Could not prepare file for upload")))
            return@channelFlow
        }

        try {
            send(ScanStep.Uploading(progressPercent = 0, bytesUploaded = 0L, totalBytes = tempFile.length()))

            val uploadAndPollResult = keyManager.uploadAndPoll(
                context = context,
                service = service,
                sha256 = sha256,
                file = tempFile,
                onUploadProgress = { pct ->
                    val sent = (tempFile.length() * pct / 100).coerceAtMost(tempFile.length())
                    trySend(ScanStep.Uploading(progressPercent = pct, bytesUploaded = sent, totalBytes = tempFile.length()))
                },
                onQueued = { analysisId ->
                    trySend(ScanStep.Queued(queuePosition = null, message = "Queued", analysisId = analysisId))
                },
                onPollProgress = { status, analysisId ->
                    when (status) {
                        VtStatus.QUEUED -> trySend(ScanStep.Queued(queuePosition = null, message = "Queued", analysisId = analysisId))
                        VtStatus.ANALYZING -> trySend(ScanStep.Analyzing(progressPercent = null, stageDescription = "Engines running"))
                        else -> Unit
                    }
                }
            )

            when (uploadAndPollResult.status) {
                VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS -> {
                    send(ScanStep.Completed(toSecurityScanResult(uploadAndPollResult, sha256)))
                }
                VtStatus.RATE_LIMITED -> {
                    send(ScanStep.Failed(ScanError.RateLimited(providerId, uploadAndPollResult.errorMessage.toLongOrNull(), "VirusTotal rate limit exceeded during analysis")))
                }
                VtStatus.INVALID_API_KEY -> {
                    send(ScanStep.Failed(ScanError.InvalidApiKey(providerId, "VirusTotal API key is invalid")))
                }
                VtStatus.NO_API_KEY -> {
                    send(ScanStep.Failed(ScanError.NoApiKey(providerId, "VirusTotal API key not configured or quota exhausted")))
                }
                VtStatus.TOO_LARGE -> {
                    send(ScanStep.Failed(ScanError.FileTooLarge(providerId, tempFile.length(), VirusTotalService.SIZE_LIMIT_LARGE)))
                }
                else -> {
                    send(ScanStep.Failed(ScanError.ServerError(providerId, 500, uploadAndPollResult.errorMessage.ifBlank { "VirusTotal upload failed" })))
                }
            }
        } finally {
            tempFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L
    }

    private suspend fun createTempApkFile(context: Context, uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = fileName.substringAfterLast('.', "apk").ifBlank { "apk" }
            val f = File(context.cacheDir, "vt_scan_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            f
        }.getOrNull()
    }

    private fun toSecurityScanResult(vtResult: VtResult, sha256: String): SecurityScanResult {
        val verdict = when {
            vtResult.malicious > 0 -> ScannerVerdict.MALICIOUS
            vtResult.suspicious > 0 -> ScannerVerdict.SUSPICIOUS
            vtResult.status == VtStatus.CLEAN || vtResult.harmless > 0 -> ScannerVerdict.SAFE
            else -> ScannerVerdict.UNKNOWN
        }

        val detections = vtResult.engineResults.map {
            EngineDetection(
                engineName = it.engineName,
                category = it.category,
                result = it.result,
            )
        }

        val findings = vtResult.engineResults
            .filter { it.category in setOf("malicious", "suspicious") }
            .map {
                ScannerFinding(
                    scanner = "virustotal",
                    ruleId = "vt_${it.engineName.lowercase()}",
                    title = it.engineName,
                    description = it.result ?: it.category,
                    severity = if (it.category == "malicious") ScannerSeverity.HIGH else ScannerSeverity.MEDIUM,
                )
            }

        val total = vtResult.malicious + vtResult.suspicious + vtResult.harmless + vtResult.undetected

        return SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = sha256,
            verdict = verdict,
            riskScore = if (vtResult.malicious > 0) 100 else if (vtResult.suspicious > 0) 50 else 0,
            findings = findings,
            engineDetections = detections,
            maliciousCount = vtResult.malicious,
            suspiciousCount = vtResult.suspicious,
            harmlessCount = vtResult.harmless,
            undetectedCount = vtResult.undetected,
            totalEngines = total.takeIf { it > 0 } ?: 70,
            permalink = if (sha256.isNotBlank()) "https://www.virustotal.com/gui/file/$sha256" else null,
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    class ScanErrorException(val error: ScanError) : Exception(error.message, error.cause)
}
