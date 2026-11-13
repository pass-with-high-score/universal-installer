package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.network.VirusDetecService

/**
 * Granular error classification for scanner failures.
 */
sealed class ScanError(
    open val provider: ScannerProviderId,
    open val message: String,
    open val cause: Throwable? = null,
) {
    /**
     * Determines whether this error should automatically trigger fallback to the backup scanner.
     */
    open fun isEligibleForFallback(): Boolean = true

    data class NoApiKey(
        override val provider: ScannerProviderId,
        override val message: String = "No API key configured for ${provider.displayName}",
    ) : ScanError(provider, message)

    data class InvalidApiKey(
        override val provider: ScannerProviderId,
        override val message: String = "Invalid or revoked API key for ${provider.displayName}",
    ) : ScanError(provider, message)

    data class RateLimited(
        override val provider: ScannerProviderId,
        val retryAfterSeconds: Long? = null,
        override val message: String = "Rate limit exceeded for ${provider.displayName}",
    ) : ScanError(provider, message)

    data class FileTooLarge(
        override val provider: ScannerProviderId,
        val fileSizeBytes: Long,
        val maxSizeBytes: Long,
        override val message: String = "File size (${fileSizeBytes / (1024 * 1024)} MB) exceeds maximum allowed (${maxSizeBytes / (1024 * 1024)} MB)",
    ) : ScanError(provider, message)

    data class NetworkError(
        override val provider: ScannerProviderId,
        override val cause: Throwable? = null,
        override val message: String = VirusDetecService.NETWORK_ERROR_TAG,
    ) : ScanError(provider, message, cause)

    data class ServerError(
        override val provider: ScannerProviderId,
        val httpCode: Int? = null,
        override val message: String = "Server error ($httpCode) from ${provider.displayName}",
    ) : ScanError(provider, message)

    data class Timeout(
        override val provider: ScannerProviderId,
        val timeoutMillis: Long,
        override val message: String = "Scan timed out after ${timeoutMillis / 1000}s",
    ) : ScanError(provider, message)

    data class NotFound(
        override val provider: ScannerProviderId,
        val sha256: String,
        override val message: String = "File hash not found in ${provider.displayName} database",
    ) : ScanError(provider, message) {
        override fun isEligibleForFallback(): Boolean = false
    }

    data class Cancelled(
        override val provider: ScannerProviderId,
        override val message: String = "Scan cancelled by user",
    ) : ScanError(provider, message) {
        override fun isEligibleForFallback(): Boolean = false
    }

    data class Generic(
        override val provider: ScannerProviderId,
        override val message: String,
        override val cause: Throwable? = null,
    ) : ScanError(provider, message, cause)
}

/**
 * Reactive step updates emitted throughout the scan lifecycle.
 */
sealed class ScanStep {
    /**
     * Calculating SHA-256 hash of the APK binary.
     */
    data class Hashing(val progressPercent: Int? = null) : ScanStep()

    /**
     * Querying cloud database for pre-existing report by hash.
     */
    data class LookupCache(val sha256: String) : ScanStep()

    /**
     * Uploading APK binary to the scanning service.
     */
    data class Uploading(
        val progressPercent: Int,
        val bytesUploaded: Long = 0L,
        val totalBytes: Long = 0L,
    ) : ScanStep()

    /**
     * APK upload completed, waiting in analysis queue.
     */
    data class Queued(
        val queuePosition: Int? = null,
        val message: String = "",
        val analysisId: String = "",
    ) : ScanStep()

    /**
     * Active security analysis underway (static heuristics, signatures, call graph).
     */
    data class Analyzing(
        val progressPercent: Int? = null,
        val stageDescription: String? = null,
    ) : ScanStep()

    /**
     * Automatic fallback triggered due to primary failure.
     */
    data class FallbackTriggered(
        val primaryError: ScanError,
        val fallbackProvider: ScannerProviderId,
    ) : ScanStep()

    /**
     * Scan completed successfully with verified result.
     */
    data class Completed(val result: SecurityScanResult) : ScanStep()

    /**
     * Scan failed and no further fallback is available.
     */
    data class Failed(val error: ScanError) : ScanStep()
}

/**
 * Maps a [ScanStep] to legacy [VtResult] for direct UI backward compatibility.
 */
fun ScanStep.toVtResult(sha256: String = ""): VtResult = when (this) {
    is ScanStep.Hashing -> VtResult(status = VtStatus.SCANNING, analysisId = sha256)
    is ScanStep.LookupCache -> VtResult(status = VtStatus.SCANNING, analysisId = sha256)
    is ScanStep.Uploading -> VtResult(
        status = VtStatus.UPLOADING,
        uploadProgress = progressPercent,
        analysisId = sha256,
    )
    is ScanStep.Queued -> VtResult(
        status = VtStatus.QUEUED,
        errorMessage = message,
        analysisId = analysisId.ifBlank { sha256 },
    )
    is ScanStep.Analyzing -> VtResult(
        status = VtStatus.ANALYZING,
        uploadProgress = progressPercent ?: 100,
        errorMessage = stageDescription.orEmpty(),
        analysisId = sha256,
    )
    is ScanStep.FallbackTriggered -> VtResult(
        status = VtStatus.SCANNING,
        errorMessage = "Fallback to ${fallbackProvider.displayName}...",
        analysisId = sha256,
    )
    is ScanStep.Completed -> result.toVtResult()
    is ScanStep.Failed -> error.toVtResult(sha256)
}

/**
 * Maps a [ScanError] to legacy [VtResult].
 */
fun ScanError.toVtResult(sha256: String = ""): VtResult = when (this) {
    is ScanError.NoApiKey -> VtResult(
        status = VtStatus.NO_API_KEY,
        errorMessage = message,
        analysisId = sha256,
    )
    is ScanError.InvalidApiKey -> VtResult(
        status = VtStatus.INVALID_API_KEY,
        errorMessage = message,
        analysisId = sha256,
    )
    is ScanError.RateLimited -> VtResult(
        status = VtStatus.RATE_LIMITED,
        errorMessage = retryAfterSeconds?.toString().orEmpty().ifBlank { message },
        analysisId = sha256,
    )
    is ScanError.FileTooLarge -> VtResult(
        status = VtStatus.TOO_LARGE,
        errorMessage = "${fileSizeBytes / (1024 * 1024)} MB",
        analysisId = sha256,
    )
    is ScanError.NotFound -> VtResult(
        status = VtStatus.NOT_FOUND,
        errorMessage = message,
        analysisId = sha256,
    )
    is ScanError.NetworkError -> VtResult(
        status = VtStatus.ERROR,
        errorMessage = VirusDetecService.NETWORK_ERROR_TAG,
        analysisId = sha256,
    )
    is ScanError.Cancelled -> VtResult(
        status = VtStatus.ERROR,
        errorMessage = "Cancelled",
        analysisId = sha256,
    )
    else -> VtResult(
        status = VtStatus.ERROR,
        errorMessage = message,
        analysisId = sha256,
    )
}
