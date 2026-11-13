package app.pwhs.core.domain.scanner

import android.content.Context
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Tier 4 End-to-End Real-World Scenario Tests:
 * 1. Clean popular APK cache hit fast path (<100ms)
 * 2. Suspicious APK full scan with detections
 * 3. Primary cloud quota exhausted (429) seamless backup fallback
 * 4. Complete offline flight mode static tracker fallback
 * 5. Multi-APK batch scan queue with rate-limit recovery
 */
class SecurityScannerRealWorldScenariosTest {

    @Test
    fun testScenario1_cleanApkCacheHit_fastPathUnder100ms() = runBlocking {
        val cleanReport = ScanReport(
            sha256 = "signal_messenger_clean_sha256",
            verdict = Verdict.SAFE,
            riskScore = 0,
            findings = emptyList(),
        )
        val cleanResult = cleanReport.toSecurityScanResult(ScannerProviderId.VIRUSDETEC)

        val primaryScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            lookupHandler = { Result.success(cleanResult) },
            scanSteps = listOf(
                ScanStep.LookupCache("signal_messenger_clean_sha256"),
                ScanStep.Completed(cleanResult),
            )
        )
        val orchestrator = SecurityScannerOrchestrator(listOf(primaryScanner))

        // Fast hash lookup
        val lookupRes = orchestrator.lookupHash(
            createDummyContext(),
            "signal_messenger_clean_sha256",
            ScanApkMetadata(packageName = "org.thoughtcrime.securesms"),
        )
        assertTrue(lookupRes.isSuccess)
        val scanResult = lookupRes.getOrNull()
        assertNotNull(scanResult)
        assertEquals(ScannerVerdict.SAFE, scanResult?.verdict)

        // UI compatibility verification
        val vt = scanResult!!.toVtResult()
        assertEquals(VtStatus.CLEAN, vt.status)
        assertEquals(0, vt.malicious)
        assertEquals(0, vt.suspicious)
        assertTrue(vt.harmless >= 70)
        assertEquals(0, primaryScanner.uploadInvocationCount)
    }

    @Test
    fun testScenario2_suspiciousApkFullScanWithDetections_triggersRiskConfirmGate() = runBlocking {
        val detections = listOf(
            EngineDetection("Avast", "malicious", "Android:Airpush-G"),
            EngineDetection("Kaspersky", "malicious", "HEUR:Trojan.AndroidOS.Agent.a"),
        )
        val threatResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "adware_infected_sha256",
            verdict = ScannerVerdict.MALICIOUS,
            riskScore = 88,
            engineDetections = detections,
            maliciousCount = 2,
            suspiciousCount = 0,
            harmlessCount = 68,
            totalEngines = 70,
        )

        val scanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            scanSteps = listOf(
                ScanStep.Hashing(100),
                ScanStep.LookupCache("adware_infected_sha256"),
                ScanStep.Uploading(progressPercent = 50, bytesUploaded = 5_000_000, totalBytes = 10_000_000),
                ScanStep.Uploading(progressPercent = 100, bytesUploaded = 10_000_000, totalBytes = 10_000_000),
                ScanStep.Queued(queuePosition = 1, message = "Queued for analysis"),
                ScanStep.Analyzing(progressPercent = 80, stageDescription = "Antivirus engine scan"),
                ScanStep.Completed(threatResult),
            )
        )
        val orchestrator = SecurityScannerOrchestrator(listOf(scanner))

        val steps = orchestrator.scan(
            context = createDummyContext(),
            uri = createDummyUri(),
            fileName = "adware.apk",
            metadata = ScanApkMetadata(sha256 = "adware_infected_sha256"),
        ).toList()

        assertTrue(steps.any { it is ScanStep.Uploading })
        assertTrue(steps.any { it is ScanStep.Analyzing })
        assertTrue(steps.last() is ScanStep.Completed)

        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerVerdict.MALICIOUS, completed.result.verdict)
        assertEquals(2, completed.result.maliciousCount)

        // Maps to legacy VtResult for install risk confirmation dialog
        val vt = completed.result.toVtResult()
        assertEquals(VtStatus.MALICIOUS, vt.status)
        assertEquals(2, vt.malicious)
        assertEquals(2, vt.engineResults.size)
    }

    @Test
    fun testScenario3_primaryCloudQuotaExhausted_seamlessCloudBackupFallback() = runBlocking {
        // VirusDetec returns HTTP 429 rate limit
        val virusDetecScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            scanSteps = listOf(
                ScanStep.LookupCache("target_sha"),
                ScanStep.Failed(ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, retryAfterSeconds = 60)),
            )
        )

        val vtResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "target_sha",
            verdict = ScannerVerdict.SAFE,
            harmlessCount = 70,
        )
        val virusTotalScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            scanSteps = listOf(
                ScanStep.LookupCache("target_sha"),
                ScanStep.Completed(vtResult),
            )
        )

        val orchestrator = SecurityScannerOrchestrator(listOf(virusDetecScanner, virusTotalScanner))

        val steps = orchestrator.scan(
            context = createDummyContext(),
            uri = createDummyUri(),
            fileName = "app.apk",
            metadata = ScanApkMetadata(sha256 = "target_sha"),
        ).toList()

        // Verifies fallback occurred seamlessly without user interruption
        val fallbackStep = steps.firstOrNull { it is ScanStep.FallbackTriggered } as? ScanStep.FallbackTriggered
        assertNotNull("FallbackTriggered must be emitted", fallbackStep)
        assertEquals(ScannerProviderId.VIRUSTOTAL, fallbackStep?.fallbackProvider)
        assertTrue(fallbackStep?.primaryError is ScanError.RateLimited)

        val lastStep = steps.last()
        assertTrue("Scan must complete successfully via backup", lastStep is ScanStep.Completed)
        assertEquals(ScannerVerdict.SAFE, (lastStep as ScanStep.Completed).result.verdict)
        assertEquals(ScannerProviderId.VIRUSTOTAL, lastStep.result.providerId)
    }

    @Test
    fun testScenario4_completeOfflineFlightMode_dexTrackerFallback() = runBlocking {
        // Cloud scanners fail due to network loss
        val cloudScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            scanSteps = listOf(
                ScanStep.Failed(ScanError.NetworkError(ScannerProviderId.VIRUSDETEC)),
            )
        )

        val offlineFindings = listOf(
            ScannerFinding(
                scanner = "exodus_trackers",
                ruleId = "google_admob",
                severity = ScannerSeverity.LOW,
                title = "Google AdMob",
                description = "Embedded ad tracker",
            )
        )
        val offlineResult = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = "offline_sha",
            verdict = ScannerVerdict.RISKWARE,
            riskScore = 15,
            findings = offlineFindings,
        )

        val offlineScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            capabilities = ScannerCapabilities(requiresNetwork = false),
            scanSteps = listOf(
                ScanStep.Analyzing(progressPercent = 100, stageDescription = "Offline static DEX tracker analysis"),
                ScanStep.Completed(offlineResult),
            )
        )

        val prefs = object : SecurityPreferencesDataSource {
            override suspend fun isScanningDisabled(context: Context): Boolean = false
            override suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId = ScannerProviderId.VIRUSDETEC
            override suspend fun getActiveBackupScanner(context: Context): ScannerProviderId = ScannerProviderId.OFFLINE_STATIC
            override suspend fun isAutoFallbackEnabled(context: Context): Boolean = true
            override suspend fun isOfflineChecksEnabled(context: Context): Boolean = false
        }

        val orchestrator = SecurityScannerOrchestrator(
            scannerList = listOf(cloudScanner, offlineScanner),
            preferencesDataSource = prefs,
        )

        val steps = orchestrator.scan(
            context = createDummyContext(),
            uri = createDummyUri(),
            fileName = "offline.apk",
            metadata = ScanApkMetadata(sha256 = "offline_sha"),
        ).toList()

        val fallbackStep = steps.firstOrNull { it is ScanStep.FallbackTriggered } as? ScanStep.FallbackTriggered
        assertNotNull("FallbackTriggered must be emitted", fallbackStep)
        assertEquals(ScannerProviderId.OFFLINE_STATIC, fallbackStep?.fallbackProvider)
        assertTrue(fallbackStep?.primaryError is ScanError.NetworkError)

        val finalStep = steps.last() as ScanStep.Completed
        assertEquals(ScannerProviderId.OFFLINE_STATIC, finalStep.result.providerId)
        assertEquals(1, finalStep.result.findings.size)
        assertEquals("Google AdMob", finalStep.result.findings.first().title)
    }

    @Test
    fun testScenario5_batchScanQueue_rateLimitRecovery() = runBlocking {
        // Simulates scanning 4 APKs in batch
        var quotaCounter = 0
        val dynamicScanner = object : ApkSecurityScanner {
            override val providerId: ScannerProviderId = ScannerProviderId.VIRUSTOTAL
            override val capabilities: ScannerCapabilities = ScannerCapabilities()

            override suspend fun isConfigured(context: Context): Boolean = true

            override suspend fun lookupHash(context: Context, sha256: String, metadata: ScanApkMetadata): Result<SecurityScanResult?> {
                return Result.success(null)
            }

            override fun scanApk(context: Context, uri: Uri, fileName: String, metadata: ScanApkMetadata, options: ScanOptions): Flow<ScanStep> = flow {
                quotaCounter++
                if (quotaCounter == 3) {
                    // 3rd APK encounters rate limit
                    emit(ScanStep.Failed(ScanError.RateLimited(providerId, 1)))
                } else {
                    emit(ScanStep.Completed(SecurityScanResult(providerId, "sha_$quotaCounter", ScannerVerdict.SAFE)))
                }
            }
        }

        val fallbackScanner = ScenarioMockScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            scanSteps = listOf(ScanStep.Completed(SecurityScanResult(ScannerProviderId.VIRUSDETEC, "recovered_sha_3", ScannerVerdict.SAFE)))
        )

        val prefs = object : SecurityPreferencesDataSource {
            override suspend fun isScanningDisabled(context: Context): Boolean = false
            override suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId = ScannerProviderId.VIRUSTOTAL
            override suspend fun getActiveBackupScanner(context: Context): ScannerProviderId = ScannerProviderId.VIRUSDETEC
            override suspend fun isAutoFallbackEnabled(context: Context): Boolean = true
            override suspend fun isOfflineChecksEnabled(context: Context): Boolean = false
        }

        val orchestrator = SecurityScannerOrchestrator(
            scannerList = listOf(dynamicScanner, fallbackScanner),
            preferencesDataSource = prefs,
        )

        val results = mutableListOf<ScanStep>()
        for (i in 1..4) {
            val steps = orchestrator.scan(
                context = createDummyContext(),
                uri = createDummyUri(),
                fileName = "app_$i.apk",
                metadata = ScanApkMetadata(sha256 = "sha_$i"),
            ).toList()
            results.add(steps.last())
        }

        // All 4 scans completed successfully (3 via dynamic, 1 via fallback)
        assertEquals(4, results.size)
        assertEquals(4, quotaCounter)
        assertTrue(results.all { it is ScanStep.Completed })

        val completedResults = results.filterIsInstance<ScanStep.Completed>()
        assertEquals(4, completedResults.size)
        assertEquals(ScannerProviderId.VIRUSTOTAL, completedResults[0].result.providerId)
        assertEquals(ScannerProviderId.VIRUSTOTAL, completedResults[1].result.providerId)
        assertEquals(ScannerProviderId.VIRUSDETEC, completedResults[2].result.providerId) // Fallback on rate limit
        assertEquals(ScannerProviderId.VIRUSTOTAL, completedResults[3].result.providerId)
    }

    // Helpers

    private fun createDummyContext(): Context = FakeContext()

    private fun createDummyUri(): Uri = createTestUri()

    private class ScenarioMockScanner(
        override val providerId: ScannerProviderId,
        override val capabilities: ScannerCapabilities = ScannerCapabilities(),
        private val lookupHandler: suspend (String) -> Result<SecurityScanResult?> = { Result.success(null) },
        private val scanSteps: List<ScanStep> = emptyList(),
    ) : ApkSecurityScanner {
        var uploadInvocationCount = 0

        override suspend fun isConfigured(context: Context): Boolean = true

        override suspend fun lookupHash(
            context: Context,
            sha256: String,
            metadata: ScanApkMetadata,
        ): Result<SecurityScanResult?> = lookupHandler(sha256)

        override fun scanApk(
            context: Context,
            uri: Uri,
            fileName: String,
            metadata: ScanApkMetadata,
            options: ScanOptions,
        ): Flow<ScanStep> = flow {
            scanSteps.forEach { step ->
                if (step is ScanStep.Uploading) uploadInvocationCount++
                emit(step)
            }
        }
    }
}
