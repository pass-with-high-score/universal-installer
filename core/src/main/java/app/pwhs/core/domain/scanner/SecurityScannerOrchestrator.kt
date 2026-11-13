package app.pwhs.core.domain.scanner

import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Coordinates scanner execution, user preferences, and automatic fallback between
 * primary and backup scanning providers.
 */
class SecurityScannerOrchestrator(
    private val scanners: Map<ScannerProviderId, ApkSecurityScanner>,
    private val preferencesDataSource: SecurityPreferencesDataSource? = null,
) {
    private val prefs: SecurityPreferencesDataSource = preferencesDataSource ?: DefaultSecurityPreferencesDataSource()

    /**
     * Secondary constructor accepting scanner collection.
     */
    constructor(
        scannerList: List<ApkSecurityScanner>,
        preferencesDataSource: SecurityPreferencesDataSource? = null,
    ) : this(
        scanners = scannerList.associateBy { it.providerId },
        preferencesDataSource = preferencesDataSource,
    )

    /**
     * Returns all registered scanner provider IDs.
     */
    fun getAvailableScanners(): List<ScannerProviderId> = scanners.keys.toList()

    /**
     * Retrieves a specific scanner by its provider ID.
     */
    fun getScanner(id: ScannerProviderId): ApkSecurityScanner? = scanners[id]

    /**
     * Checks if scanning is completely disabled by user preference.
     */
    suspend fun isScanningDisabled(context: Context): Boolean = prefs.isScanningDisabled(context)

    /**
     * Returns the selected primary scanner provider ID.
     */
    suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId = prefs.getActivePrimaryScanner(context)

    /**
     * Returns the selected backup scanner provider ID.
     */
    suspend fun getActiveBackupScanner(context: Context): ScannerProviderId = prefs.getActiveBackupScanner(context)

    /**
     * Checks if automatic fallback from primary to backup scanner is enabled.
     */
    suspend fun isAutoFallbackEnabled(context: Context): Boolean = prefs.isAutoFallbackEnabled(context)

    /**
     * Checks if offline static analysis should be performed alongside cloud scans.
     */
    suspend fun isOfflineChecksEnabled(context: Context): Boolean = prefs.isOfflineChecksEnabled(context)

    /**
     * Fast-path hash lookup with automatic fallback if primary scanner fails.
     */
    suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?> {
        if (isScanningDisabled(context)) return Result.success(null)

        val primaryId = getActivePrimaryScanner(context)
        val backupId = getActiveBackupScanner(context)
        val autoFallback = isAutoFallbackEnabled(context)

        val primaryScanner = scanners[primaryId] ?: scanners.values.firstOrNull()
        val backupScanner = scanners[backupId] ?: scanners.values.firstOrNull { it.providerId != primaryScanner?.providerId }

        // 1. Attempt primary scanner lookup if configured
        if (primaryScanner != null && primaryScanner.isConfigured(context)) {
            val primaryResult = primaryScanner.lookupHash(context, sha256, metadata)
            if (primaryResult.isSuccess) {
                return primaryResult
            }

            Timber.w(primaryResult.exceptionOrNull(), "Primary scanner ($primaryId) lookup failed")

            // 2. Fallback to backup scanner if eligible
            if (autoFallback && backupScanner != null && backupScanner.providerId != primaryScanner.providerId && backupScanner.isConfigured(context)) {
                Timber.i("Falling back to backup scanner ($backupId) for hash lookup")
                val backupResult = backupScanner.lookupHash(context, sha256, metadata)
                if (backupResult.isSuccess) {
                    return backupResult
                }
            }

            return primaryResult
        }

        // 3. Primary not configured, try backup directly
        if (autoFallback && backupScanner != null && backupScanner.isConfigured(context)) {
            Timber.i("Primary scanner not configured, using backup scanner ($backupId) for lookup")
            return backupScanner.lookupHash(context, sha256, metadata)
        }

        return Result.failure(IllegalStateException("No operational security scanner configured"))
    }

    /**
     * Performs full scan execution with reactive progress and automatic fallback.
     */
    fun scan(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions = ScanOptions(),
    ): Flow<ScanStep> = flow {
        if (isScanningDisabled(context)) {
            return@flow
        }

        val primaryId = getActivePrimaryScanner(context)
        val backupId = getActiveBackupScanner(context)
        val autoFallback = isAutoFallbackEnabled(context) && options.enableFallback

        var activeScanner = scanners[primaryId] ?: scanners.values.firstOrNull()
        val backupScanner = scanners[backupId] ?: scanners.values.firstOrNull { it.providerId != activeScanner?.providerId }

        // If primary is not configured but backup is, start directly with backup
        if (autoFallback &&
            (activeScanner == null || !activeScanner.isConfigured(context)) &&
            backupScanner != null && backupScanner.isConfigured(context)
        ) {
            Timber.i("Primary scanner ($primaryId) not configured, routing directly to backup ($backupId)")
            activeScanner = backupScanner
        }

        if (activeScanner == null || !activeScanner.isConfigured(context)) {
            emit(
                ScanStep.Failed(
                    ScanError.NoApiKey(
                        provider = primaryId,
                        message = "No configured security scanner available",
                    )
                )
            )
            return@flow
        }

        var shouldFallback = false
        var fallbackError: ScanError? = null

        // 1. Execute Primary / Active Scanner
        activeScanner.scanApk(context, uri, fileName, metadata, options).collect { step ->
            when (step) {
                is ScanStep.Failed -> {
                    if (autoFallback &&
                        step.error.isEligibleForFallback() &&
                        backupScanner != null &&
                        backupScanner.providerId != activeScanner.providerId &&
                        backupScanner.isConfigured(context)
                    ) {
                        shouldFallback = true
                        fallbackError = step.error
                    } else {
                        emit(step)
                    }
                }
                is ScanStep.Completed -> {
                    val finalResult = augmentWithOfflineChecksIfNeeded(context, step.result, metadata, options)
                    emit(ScanStep.Completed(finalResult))
                }
                else -> emit(step)
            }
        }

        // 2. Execute Backup Scanner if fallback was triggered
        if (shouldFallback && backupScanner != null && fallbackError != null) {
            Timber.i("Triggering fallback scan with ${backupScanner.providerId}")
            emit(
                ScanStep.FallbackTriggered(
                    primaryError = fallbackError,
                    fallbackProvider = backupScanner.providerId,
                )
            )

            backupScanner.scanApk(context, uri, fileName, metadata, options).collect { backupStep ->
                when (backupStep) {
                    is ScanStep.Completed -> {
                        val finalResult = augmentWithOfflineChecksIfNeeded(context, backupStep.result, metadata, options)
                        emit(ScanStep.Completed(finalResult))
                    }
                    else -> emit(backupStep)
                }
            }
        }
    }

    private suspend fun augmentWithOfflineChecksIfNeeded(
        context: Context,
        cloudResult: SecurityScanResult,
        metadata: ScanApkMetadata,
        options: ScanOptions,
    ): SecurityScanResult {
        if (!options.runOfflineChecks && !isOfflineChecksEnabled(context)) {
            return cloudResult
        }
        val offlineScanner = scanners[ScannerProviderId.OFFLINE_STATIC] ?: return cloudResult
        val offlineLookup = runCatching { offlineScanner.lookupHash(context, cloudResult.sha256, metadata) }.getOrNull()
        val offlineResult = offlineLookup?.getOrNull() ?: return cloudResult

        val combinedFindings = cloudResult.findings + offlineResult.findings
        val combinedVerdict = when {
            cloudResult.verdict == ScannerVerdict.MALICIOUS || offlineResult.verdict == ScannerVerdict.MALICIOUS -> ScannerVerdict.MALICIOUS
            cloudResult.verdict == ScannerVerdict.SUSPICIOUS || offlineResult.verdict == ScannerVerdict.SUSPICIOUS -> ScannerVerdict.SUSPICIOUS
            cloudResult.verdict == ScannerVerdict.RISKWARE || offlineResult.verdict == ScannerVerdict.RISKWARE -> ScannerVerdict.RISKWARE
            else -> cloudResult.verdict
        }

        return cloudResult.copy(
            verdict = combinedVerdict,
            findings = combinedFindings,
            riskScore = maxOf(cloudResult.riskScore, offlineResult.riskScore),
        )
    }
}
