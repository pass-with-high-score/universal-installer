package app.pwhs.core.domain.scanner

import android.content.Context
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.scanner.impl.MockSecurityScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AdversarialOrchestratorStressTest {

    private class TestPreferences(
        var primary: ScannerProviderId = ScannerProviderId.VIRUSDETEC,
        var backup: ScannerProviderId = ScannerProviderId.VIRUSTOTAL,
        var autoFallback: Boolean = true,
        var scanningDisabled: Boolean = false,
        var offlineChecks: Boolean = false,
    ) : SecurityPreferencesDataSource {
        override suspend fun isScanningDisabled(context: Context): Boolean = scanningDisabled
        override suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId = primary
        override suspend fun getActiveBackupScanner(context: Context): ScannerProviderId = backup
        override suspend fun isAutoFallbackEnabled(context: Context): Boolean = autoFallback
        override suspend fun isOfflineChecksEnabled(context: Context): Boolean = offlineChecks
    }

    private val context = FakeContext()
    private val fakeUri = createTestUri("content://app.pwhs/adversarial.apk")
    private val metadata = ScanApkMetadata(sha256 = "adv_sha256_hash", appName = "AdversarialApp")

    // =========================================================================
    // 1. Cascading Failures
    // =========================================================================

    @Test
    fun testCascadingFailure_primaryFailsThenBackupFails_emitsFallbackAndBackupFailureCleanly() = runBlocking {
        val primaryError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, retryAfterSeconds = 30)
        val backupError = ScanError.ServerError(ScannerProviderId.VIRUSTOTAL, httpCode = 503, message = "Service Unavailable")

        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = primaryError,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            simulatedError = backupError,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)

        // Must emit FallbackTriggered first
        val fallbackStep = steps.filterIsInstance<ScanStep.FallbackTriggered>().firstOrNull()
        assertNotNull("FallbackTriggered must be emitted when primary fails", fallbackStep)
        assertEquals(ScannerProviderId.VIRUSTOTAL, fallbackStep?.fallbackProvider)
        assertEquals(primaryError, fallbackStep?.primaryError)

        // Must end cleanly with backup failure, NOT crash or hang
        val lastStep = steps.last()
        assertTrue("Last step must be ScanStep.Failed", lastStep is ScanStep.Failed)
        val failed = lastStep as ScanStep.Failed
        assertEquals(backupError, failed.error)
    }

    @Test
    fun testCascadingFailure_lookupHash_bothScannersFail_returnsPrimaryFailure() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, retryAfterSeconds = 60),
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            simulatedError = ScanError.ServerError(ScannerProviderId.VIRUSTOTAL, 500, "VT Internal Error"),
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(autoFallback = true),
        )

        val result = orchestrator.lookupHash(context, metadata.sha256, metadata)

        assertTrue("Lookup result must be a failure", result.isFailure)
        assertEquals(1, primary.lookupCallCount)
        assertEquals(1, backup.lookupCallCount)
    }

    // =========================================================================
    // 2. Zero-Key Scenarios
    // =========================================================================

    @Test
    fun testZeroKey_bothScannersUnconfigured_scanApk_emitsNoApiKeyWithoutFallbackLoop() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            configured = false,
            simulatedError = ScanError.NoApiKey(ScannerProviderId.VIRUSTOTAL, "No VT API key"),
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            configured = false,
            simulatedError = ScanError.NoApiKey(ScannerProviderId.VIRUSDETEC, "No VD credentials"),
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSTOTAL to primary,
                ScannerProviderId.VIRUSDETEC to backup,
            ),
            preferencesDataSource = TestPreferences(
                primary = ScannerProviderId.VIRUSTOTAL,
                backup = ScannerProviderId.VIRUSDETEC,
            ),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        // Backup is unconfigured, so fallback must NOT be attempted
        assertEquals(0, backup.scanCallCount)
        assertFalse(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Failed)
        val failed = steps.last() as ScanStep.Failed
        assertTrue(failed.error is ScanError.NoApiKey)
    }

    @Test
    fun testZeroKey_bothScannersUnconfigured_lookupHash_returnsIllegalStateException() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, configured = false)
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, configured = false)

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSTOTAL to primary,
                ScannerProviderId.VIRUSDETEC to backup,
            ),
            preferencesDataSource = TestPreferences(
                primary = ScannerProviderId.VIRUSTOTAL,
                backup = ScannerProviderId.VIRUSDETEC,
            ),
        )

        val result = orchestrator.lookupHash(context, metadata.sha256, metadata)
        assertTrue(result.isFailure)
        assertEquals(0, primary.lookupCallCount)
        assertEquals(0, backup.lookupCallCount)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testZeroKey_emptyScannersMap_scanAndLookup_failsGracefully() = runBlocking {
        val orchestrator = SecurityScannerOrchestrator(
            scanners = emptyMap(),
            preferencesDataSource = TestPreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()
        assertEquals(1, steps.size)
        assertTrue(steps.first() is ScanStep.Failed)
        val failed = steps.first() as ScanStep.Failed
        assertTrue(failed.error is ScanError.NoApiKey)

        val lookupResult = orchestrator.lookupHash(context, metadata.sha256, metadata)
        assertTrue(lookupResult.isFailure)
        assertTrue(lookupResult.exceptionOrNull() is IllegalStateException)
    }

    // =========================================================================
    // 3. Rate Limiting Simulation (429 with / without Retry-After)
    // =========================================================================

    @Test
    fun testRateLimit_withRetryAfterHeader_preservesRetryAfterAcrossFallback() = runBlocking {
        val rateLimitError = ScanError.RateLimited(
            provider = ScannerProviderId.VIRUSDETEC,
            retryAfterSeconds = 120L,
            message = "Too Many Requests",
        )
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = rateLimitError,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            simulatedVerdict = ScannerVerdict.SAFE,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(autoFallback = true),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        val fallback = steps.filterIsInstance<ScanStep.FallbackTriggered>().first()
        val error = fallback.primaryError as ScanError.RateLimited
        assertEquals(120L, error.retryAfterSeconds)
        assertEquals(ScannerProviderId.VIRUSTOTAL, fallback.fallbackProvider)

        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerProviderId.VIRUSTOTAL, completed.result.providerId)
    }

    @Test
    fun testRateLimit_withoutRetryAfter_preservesNullSecondsAcrossFallback() = runBlocking {
        val rateLimitError = ScanError.RateLimited(
            provider = ScannerProviderId.VIRUSDETEC,
            retryAfterSeconds = null,
        )
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = rateLimitError,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            simulatedVerdict = ScannerVerdict.SAFE,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(autoFallback = true),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        val fallback = steps.filterIsInstance<ScanStep.FallbackTriggered>().first()
        val error = fallback.primaryError as ScanError.RateLimited
        assertNull(error.retryAfterSeconds)
        assertTrue(steps.last() is ScanStep.Completed)
    }

    // =========================================================================
    // 4. Flow Cancellation & Coroutine Lifecycle
    // =========================================================================

    @Test
    fun testFlowCancellation_cancelsExecutionImmediately_noLeakOrCrash() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            delayMillis = 2000L,
            simulatedVerdict = ScannerVerdict.SAFE,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(),
        )

        val emittedSteps = mutableListOf<ScanStep>()
        val job = launch(Dispatchers.Default) {
            try {
                orchestrator.scan(context, fakeUri, "test.apk", metadata).collect { step ->
                    emittedSteps.add(step)
                }
            } catch (_: CancellationException) {
                // Expected cancellation
            }
        }

        // Give it a short moment to emit first hashing step, then cancel
        kotlinx.coroutines.delay(50)
        job.cancel()
        job.join()

        // Backup scanner should never be called
        assertEquals(0, backup.scanCallCount)
        // Should not have reached Completed
        assertFalse(emittedSteps.any { it is ScanStep.Completed })
    }

    @Test
    fun testFlowCancellation_duringBackupScan_cancelsBackupImmediately() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, retryAfterSeconds = 10),
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            delayMillis = 2000L,
            simulatedVerdict = ScannerVerdict.SAFE,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(autoFallback = true),
        )

        val emittedSteps = mutableListOf<ScanStep>()
        val job = launch(Dispatchers.Default) {
            try {
                orchestrator.scan(context, fakeUri, "test.apk", metadata).collect { step ->
                    emittedSteps.add(step)
                }
            } catch (_: CancellationException) {
                // Expected
            }
        }

        kotlinx.coroutines.delay(50)
        job.cancel()
        job.join()

        // Primary was called, fallback was triggered, but backup was cancelled before completing
        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertFalse("Backup must not finish after cancellation", emittedSteps.any { it is ScanStep.Completed })
    }

    // =========================================================================
    // 5. Concurrent Scan Invocations
    // =========================================================================

    @Test
    fun testConcurrentScans_50SimultaneousScans_threadSafeExecution() = runBlocking {
        val primaryCounter = AtomicInteger(0)
        val backupCounter = AtomicInteger(0)

        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedVerdict = ScannerVerdict.SAFE,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            simulatedVerdict = ScannerVerdict.SAFE,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = TestPreferences(autoFallback = true),
        )

        // Run 50 parallel scans
        coroutineScope {
            val deferreds = (1..50).map { index ->
                async(Dispatchers.Default) {
                    val scanMetadata = ScanApkMetadata(sha256 = "hash_$index", appName = "App$index")
                    val steps = orchestrator.scan(context, fakeUri, "app_$index.apk", scanMetadata).toList()
                    val last = steps.last()
                    assertTrue("Scan $index must complete", last is ScanStep.Completed)
                    (last as ScanStep.Completed).result
                }
            }
            val results = deferreds.awaitAll()
            assertEquals(50, results.size)
            assertTrue(results.all { it.verdict == ScannerVerdict.SAFE })
        }
    }

    @Test
    fun testConcurrentLookupHash_50SimultaneousLookups_threadSafe() = runBlocking {
        val cached = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = "shared_cached_hash",
            verdict = ScannerVerdict.SAFE,
        )
        val callCount = AtomicInteger(0)
        val threadSafeScanner = object : ApkSecurityScanner {
            override val providerId: ScannerProviderId = ScannerProviderId.VIRUSDETEC
            override val capabilities: ScannerCapabilities = ScannerCapabilities()
            override suspend fun isConfigured(context: Context): Boolean = true
            override suspend fun lookupHash(context: Context, sha256: String, metadata: ScanApkMetadata): Result<SecurityScanResult?> {
                callCount.incrementAndGet()
                return Result.success(cached)
            }
            override fun scanApk(context: Context, uri: Uri, fileName: String, metadata: ScanApkMetadata, options: ScanOptions) = kotlinx.coroutines.flow.emptyFlow<ScanStep>()
        }

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(ScannerProviderId.VIRUSDETEC to threadSafeScanner),
            preferencesDataSource = TestPreferences(),
        )

        coroutineScope {
            val jobs = (1..50).map { index ->
                async(Dispatchers.Default) {
                    val res = orchestrator.lookupHash(context, "hash_$index", ScanApkMetadata(sha256 = "hash_$index"))
                    assertTrue(res.isSuccess)
                    assertEquals(cached, res.getOrNull())
                }
            }
            jobs.awaitAll()
        }
        assertEquals(50, callCount.get())
    }

    // =========================================================================
    // 6. Offline Checks Augmentation Resilience
    // =========================================================================

    @Test
    fun testOfflineChecks_verdictPriorityRule() = runBlocking {
        val cloudScanner = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedVerdict = ScannerVerdict.SAFE,
            simulatedThreatScore = 0,
        )
        val offlineScanner = MockSecurityScanner(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            simulatedVerdict = ScannerVerdict.MALICIOUS,
            simulatedThreatScore = 90,
            cachedResult = SecurityScanResult(
                providerId = ScannerProviderId.OFFLINE_STATIC,
                sha256 = metadata.sha256,
                verdict = ScannerVerdict.MALICIOUS,
                riskScore = 90,
            ),
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to cloudScanner,
                ScannerProviderId.OFFLINE_STATIC to offlineScanner,
            ),
            preferencesDataSource = TestPreferences(offlineChecks = true),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()
        val completed = steps.last() as ScanStep.Completed

        // Cloud verdict was SAFE, but offline verdict was MALICIOUS -> combined MUST be MALICIOUS
        assertEquals(ScannerVerdict.MALICIOUS, completed.result.verdict)
        assertEquals(90, completed.result.riskScore)
    }
}
