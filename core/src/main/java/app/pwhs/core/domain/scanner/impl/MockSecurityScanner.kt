package app.pwhs.core.domain.scanner.impl

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockSecurityScanner(
    var simulatedVerdict: ScannerVerdict = ScannerVerdict.SAFE,
    var simulatedThreatScore: Int = 0,
    var simulatedError: ScanError? = null,
    var delayMillis: Long = 0L,
    var configured: Boolean = true,
    var cachedResult: SecurityScanResult? = null,
    override val providerId: ScannerProviderId = ScannerProviderId.MOCK,
) : ApkSecurityScanner {

    var scanCallCount = 0
        private set

    var lookupCallCount = 0
        private set

    override val capabilities: ScannerCapabilities = ScannerCapabilities(
        supportsHashLookup = true,
        supportsBinaryUpload = true,
        requiresNetwork = false,
        requiresApiKey = false,
        maxFileSizeBytes = 500L * 1024 * 1024,
        supportsStreamingProgress = true,
    )

    override suspend fun isConfigured(context: Context): Boolean = configured

    override suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?> {
        lookupCallCount++
        if (delayMillis > 0) delay(delayMillis)
        simulatedError?.let { return Result.failure(Exception(it.message)) }
        return Result.success(cachedResult)
    }

    override fun scanApk(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions,
    ): Flow<ScanStep> = flow {
        scanCallCount++
        emit(ScanStep.Hashing(0))
        if (delayMillis > 0) delay(delayMillis / 4)
        emit(ScanStep.Hashing(100))

        emit(ScanStep.LookupCache(metadata.sha256))
        if (delayMillis > 0) delay(delayMillis / 4)

        if (cachedResult != null && !options.forceRecheck) {
            emit(ScanStep.Completed(cachedResult!!))
            return@flow
        }

        simulatedError?.let { error ->
            emit(ScanStep.Failed(error))
            return@flow
        }

        emit(ScanStep.Uploading(progressPercent = 50, bytesUploaded = 500L, totalBytes = 1000L))
        if (delayMillis > 0) delay(delayMillis / 4)
        emit(ScanStep.Uploading(progressPercent = 100, bytesUploaded = 1000L, totalBytes = 1000L))

        emit(ScanStep.Analyzing(progressPercent = 50, stageDescription = "Analyzing simulated APK..."))
        if (delayMillis > 0) delay(delayMillis / 4)

        val result = SecurityScanResult(
            providerId = providerId,
            sha256 = metadata.sha256.ifBlank { "mock_sha256_${System.currentTimeMillis()}" },
            verdict = simulatedVerdict,
            riskScore = simulatedThreatScore,
            findings = listOf(
                ScannerFinding(
                    scanner = providerId.id,
                    ruleId = "MOCK_RULE_1",
                    title = "Mock Security Rule",
                    description = "Canned mock finding for test verification",
                    severity = if (simulatedVerdict == ScannerVerdict.MALICIOUS) ScannerSeverity.CRITICAL else ScannerSeverity.INFO,
                )
            ),
            engineDetections = listOf(
                EngineDetection(
                    engineName = "MockEngine",
                    category = if (simulatedVerdict == ScannerVerdict.MALICIOUS) "malicious" else "harmless",
                    result = if (simulatedVerdict == ScannerVerdict.MALICIOUS) "MockThreat" else null,
                )
            ),
            maliciousCount = if (simulatedVerdict == ScannerVerdict.MALICIOUS) 3 else 0,
            suspiciousCount = if (simulatedVerdict == ScannerVerdict.SUSPICIOUS) 2 else 0,
            harmlessCount = 65,
            undetectedCount = 0,
            totalEngines = 70,
            scannedAtMillis = System.currentTimeMillis(),
        )
        emit(ScanStep.Completed(result))
    }
}
