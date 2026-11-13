package app.pwhs.core.domain.scanner

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.scanner.impl.MockSecurityScanner
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityScannerOrchestratorTest {

    private class FakePreferences(
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
    private val fakeUri = createTestUri("content://app.pwhs/test.apk")
    private val metadata = ScanApkMetadata(sha256 = "test_sha256_123", appName = "TestApp")

    @Test
    fun scan_primarySucceeds_emitsStepsAndDoesNotInvokeFallback() = runBlocking {
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(0, backup.scanCallCount)
        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerVerdict.SAFE, completed.result.verdict)
        assertEquals(ScannerProviderId.VIRUSDETEC, completed.result.providerId)
    }

    @Test
    fun scan_primaryRateLimited_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "Rate limit reached"),
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)

        val fallbackStep = steps.filterIsInstance<ScanStep.FallbackTriggered>().firstOrNull()
        assertNotNull(fallbackStep)
        assertEquals(ScannerProviderId.VIRUSTOTAL, fallbackStep?.fallbackProvider)
        assertTrue(fallbackStep?.primaryError is ScanError.RateLimited)

        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerProviderId.VIRUSTOTAL, completed.result.providerId)
    }

    @Test
    fun scan_primaryAuthFailure_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.InvalidApiKey(ScannerProviderId.VIRUSDETEC, "Unauthorized"),
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Completed)
    }

    @Test
    fun scan_primaryServerError_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.ServerError(ScannerProviderId.VIRUSDETEC, 500, "Server error"),
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Completed)
    }

    @Test
    fun scan_primaryFileTooLarge_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.FileTooLarge(ScannerProviderId.VIRUSDETEC, 350L * 1024 * 1024, 300L * 1024 * 1024),
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Completed)
    }

    @Test
    fun scan_autoFallbackDisabled_emitsFailureDirectly() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "Rate limit reached"),
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
            preferencesDataSource = FakePreferences(autoFallback = false),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(0, backup.scanCallCount)
        assertFalse(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Failed)
        val failed = steps.last() as ScanStep.Failed
        assertTrue(failed.error is ScanError.RateLimited)
    }

    @Test
    fun scan_optionsFallbackDisabled_emitsFailureDirectly() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "Rate limit reached"),
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
            preferencesDataSource = FakePreferences(autoFallback = true),
        )

        val steps = orchestrator.scan(
            context,
            fakeUri,
            "test.apk",
            metadata,
            ScanOptions(enableFallback = false),
        ).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(0, backup.scanCallCount)
        assertFalse(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Failed)
    }

    @Test
    fun scan_primaryNotConfigured_routesDirectlyToBackup() = runBlocking {
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            configured = false,
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
            preferencesDataSource = FakePreferences(),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(0, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerProviderId.VIRUSTOTAL, completed.result.providerId)
    }

    @Test
    fun lookupHash_primarySucceeds_returnsResult() = runBlocking {
        val primaryCached = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = metadata.sha256,
            verdict = ScannerVerdict.SAFE,
        )
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            cachedResult = primaryCached,
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = FakePreferences(),
        )

        val result = orchestrator.lookupHash(context, metadata.sha256, metadata)

        assertTrue(result.isSuccess)
        assertEquals(primaryCached, result.getOrNull())
        assertEquals(1, primary.lookupCallCount)
        assertEquals(0, backup.lookupCallCount)
    }

    @Test
    fun lookupHash_primaryFails_fallsBackToBackup() = runBlocking {
        val backupCached = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = metadata.sha256,
            verdict = ScannerVerdict.SAFE,
        )
        val primary = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSDETEC,
            simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "429"),
        )
        val backup = MockSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            cachedResult = backupCached,
        )

        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(
                ScannerProviderId.VIRUSDETEC to primary,
                ScannerProviderId.VIRUSTOTAL to backup,
            ),
            preferencesDataSource = FakePreferences(autoFallback = true),
        )

        val result = orchestrator.lookupHash(context, metadata.sha256, metadata)

        assertTrue(result.isSuccess)
        assertEquals(backupCached, result.getOrNull())
        assertEquals(1, primary.lookupCallCount)
        assertEquals(1, backup.lookupCallCount)
    }

    @Test
    fun scan_scanningGloballyDisabled_emitsNothing() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC)
        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(ScannerProviderId.VIRUSDETEC to primary),
            preferencesDataSource = FakePreferences(scanningDisabled = true),
        )

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertTrue(steps.isEmpty())
        assertEquals(0, primary.scanCallCount)
    }

    @Test
    fun scan_dualFailure_primaryAndBackupBothFail_emitsBackupError() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "429"))
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, simulatedError = ScanError.ServerError(ScannerProviderId.VIRUSTOTAL, 500, "VT 500"))
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences())

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        val last = steps.last() as ScanStep.Failed
        assertTrue(last.error is ScanError.ServerError)
        assertEquals(ScannerProviderId.VIRUSTOTAL, (last.error as ScanError.ServerError).provider)
    }

    @Test
    fun scan_nonEligibleError_notFound_emitsDirectlyWithoutBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, simulatedError = ScanError.NotFound(ScannerProviderId.VIRUSDETEC, "sha123"))
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, simulatedVerdict = ScannerVerdict.SAFE)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences())

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(0, backup.scanCallCount)
        assertFalse(steps.any { it is ScanStep.FallbackTriggered })
        val last = steps.last() as ScanStep.Failed
        assertTrue(last.error is ScanError.NotFound)
    }

    @Test
    fun scan_primaryInFlightNoApiKey_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, simulatedError = ScanError.NoApiKey(ScannerProviderId.VIRUSDETEC, "Key expired"))
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, simulatedVerdict = ScannerVerdict.SAFE)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences())

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        assertEquals(ScannerProviderId.VIRUSTOTAL, (steps.last() as ScanStep.Completed).result.providerId)
    }

    @Test
    fun scan_primaryNetworkError_triggersFallbackToBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, simulatedError = ScanError.NetworkError(ScannerProviderId.VIRUSDETEC, message = "Network down"))
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, simulatedVerdict = ScannerVerdict.SAFE)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences())

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(1, primary.scanCallCount)
        assertEquals(1, backup.scanCallCount)
        assertTrue(steps.any { it is ScanStep.FallbackTriggered })
        assertEquals(ScannerProviderId.VIRUSTOTAL, (steps.last() as ScanStep.Completed).result.providerId)
    }

    @Test
    fun scan_primaryNotConfigured_autoFallbackDisabled_emitsFailureAndDoesNotCallBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, configured = false)
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, configured = true)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences(autoFallback = false))

        val steps = orchestrator.scan(context, fakeUri, "test.apk", metadata).toList()

        assertEquals(0, primary.scanCallCount)
        assertEquals(0, backup.scanCallCount)
        assertFalse(steps.any { it is ScanStep.FallbackTriggered })
        assertTrue(steps.last() is ScanStep.Failed)
    }

    @Test
    fun lookupHash_primaryNotConfigured_autoFallbackDisabled_returnsFailureWithoutCallingBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, configured = false)
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, configured = true)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences(autoFallback = false))

        val res = orchestrator.lookupHash(context, metadata.sha256, metadata)

        assertTrue(res.isFailure)
        assertEquals(0, primary.lookupCallCount)
        assertEquals(0, backup.lookupCallCount)
    }

    @Test
    fun lookupHash_primaryFails_autoFallbackDisabled_doesNotCallBackup() = runBlocking {
        val primary = MockSecurityScanner(providerId = ScannerProviderId.VIRUSDETEC, simulatedError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC, message = "429"))
        val backup = MockSecurityScanner(providerId = ScannerProviderId.VIRUSTOTAL, configured = true)
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.VIRUSDETEC to primary, ScannerProviderId.VIRUSTOTAL to backup), FakePreferences(autoFallback = false))

        val res = orchestrator.lookupHash(context, metadata.sha256, metadata)

        assertTrue(res.isFailure)
        assertEquals(1, primary.lookupCallCount)
        assertEquals(0, backup.lookupCallCount)
    }
}
