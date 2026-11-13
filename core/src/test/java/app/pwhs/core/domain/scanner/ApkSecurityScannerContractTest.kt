package app.pwhs.core.domain.scanner

import android.content.Context
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * Tier 1 & 2 Tests for ApkSecurityScanner contract, capabilities, provider IDs, and hashing.
 */
class ApkSecurityScannerContractTest {

    @Test
    fun testScannerProviderId_valuesAndDisplayNames_validAndUnique() {
        val all = ScannerProviderId.entries
        assertEquals(4, all.size)

        val ids = all.map { it.id }
        assertEquals("All IDs must be unique", ids.size, ids.toSet().size)

        all.forEach { provider ->
            assertTrue("Provider ID must not be empty", provider.id.isNotBlank())
            assertTrue("Display name must not be empty", provider.displayName.isNotBlank())
            assertEquals("Provider ID must be lower case", provider.id.lowercase(), provider.id)
        }
    }

    @Test
    fun testScannerProviderId_fromId_handlesUnknownGracefully() {
        assertEquals(ScannerProviderId.VIRUSDETEC, ScannerProviderId.fromId("virusdetec"))
        assertEquals(ScannerProviderId.VIRUSTOTAL, ScannerProviderId.fromId("virustotal"))
        assertEquals(ScannerProviderId.VIRUSTOTAL, ScannerProviderId.fromId("VIRUSTOTAL"))
        assertEquals(ScannerProviderId.OFFLINE_STATIC, ScannerProviderId.fromId("offline_static"))
        assertEquals(ScannerProviderId.MOCK, ScannerProviderId.fromId("mock"))

        // Defaults to VIRUSDETEC when unknown or null
        assertEquals(ScannerProviderId.VIRUSDETEC, ScannerProviderId.fromId("unknown_engine"))
        assertEquals(ScannerProviderId.VIRUSDETEC, ScannerProviderId.fromId(null))

        // Custom fallback
        assertEquals(ScannerProviderId.VIRUSTOTAL, ScannerProviderId.fromId("unknown_engine", default = ScannerProviderId.VIRUSTOTAL))
    }

    @Test
    fun testScannerCapabilities_defaultsMatchSpecifications() {
        val defaultCaps = ScannerCapabilities()
        assertTrue(defaultCaps.supportsHashLookup)
        assertTrue(defaultCaps.supportsBinaryUpload)
        assertTrue(defaultCaps.requiresNetwork)
        assertFalse(defaultCaps.requiresApiKey)
        assertEquals(300L * 1024 * 1024, defaultCaps.maxFileSizeBytes)
        assertTrue(defaultCaps.supportsStreamingProgress)

        val offlineCaps = ScannerCapabilities(
            supportsHashLookup = false,
            supportsBinaryUpload = false,
            requiresNetwork = false,
            requiresApiKey = false,
            maxFileSizeBytes = Long.MAX_VALUE,
            supportsStreamingProgress = false,
        )
        assertFalse(offlineCaps.requiresNetwork)
        assertFalse(offlineCaps.supportsHashLookup)
    }

    @Test
    fun testApkSecurityScanner_contractExecution_happyPath() = runBlocking {
        val fakeScanner = FakeSecurityScanner(
            providerId = ScannerProviderId.MOCK,
            capabilities = ScannerCapabilities(),
            configured = true,
            cannedResult = SecurityScanResult(
                providerId = ScannerProviderId.MOCK,
                sha256 = "canned_sha256",
                verdict = ScannerVerdict.SAFE,
            )
        )

        // 1. Configured check
        assertTrue(fakeScanner.isConfigured(createDummyContext()))

        // 2. Hash lookup
        val lookupResult = fakeScanner.lookupHash(createDummyContext(), "canned_sha256", ScanApkMetadata())
        assertTrue(lookupResult.isSuccess)
        assertNotNull(lookupResult.getOrNull())
        assertEquals("canned_sha256", lookupResult.getOrNull()?.sha256)

        // 3. Scan APK flow
        val steps = fakeScanner.scanApk(
            context = createDummyContext(),
            uri = createDummyUri(),
            fileName = "app.apk",
            metadata = ScanApkMetadata(),
            options = ScanOptions(),
        ).toList()

        assertTrue(steps.size >= 3)
        assertTrue(steps[0] is ScanStep.Hashing)
        assertTrue(steps[1] is ScanStep.LookupCache)
        assertTrue(steps.last() is ScanStep.Completed)
    }

    @Test
    fun testApkSecurityScanner_isConfigured_returnsFalseWhenUnconfigured() = runBlocking {
        val unconfiguredScanner = FakeSecurityScanner(
            providerId = ScannerProviderId.VIRUSTOTAL,
            capabilities = ScannerCapabilities(requiresApiKey = true),
            configured = false,
        )
        assertFalse(unconfiguredScanner.isConfigured(createDummyContext()))
    }

    @Test
    fun testApkSecurityScanner_sha256Calculation_emptyStream_matchesKnownDigest() = runBlocking {
        val vtService = VirusTotalService()
        val emptyDigestVt = vtService.computeSha256(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigestVt)

        val vdService = VirusDetecService()
        val emptyDigestVd = vdService.computeSha256(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigestVd)
    }

    @Test
    fun testApkSecurityScanner_sha256Calculation_knownContent() = runBlocking {
        val content = "universal-installer-test-string".toByteArray(Charsets.UTF_8)
        val vtService = VirusTotalService()
        val digestVt = vtService.computeSha256(ByteArrayInputStream(content))
        assertTrue(digestVt.matches(Regex("^[0-9a-f]{64}$")))

        val vdService = VirusDetecService()
        val digestVd = vdService.computeSha256(ByteArrayInputStream(content))
        assertEquals(digestVt, digestVd)
    }

    @Test
    fun testApkSecurityScanner_coroutineCancellation_stopsFlowImmediately() = runBlocking {
        val fakeScanner = FakeSecurityScanner(
            providerId = ScannerProviderId.MOCK,
            capabilities = ScannerCapabilities(),
            configured = true,
        )

        val flow = fakeScanner.scanApk(
            context = createDummyContext(),
            uri = createDummyUri(),
            fileName = "app.apk",
            metadata = ScanApkMetadata(),
        )

        val collectedSteps = mutableListOf<ScanStep>()
        val job = this.launch {
            flow.collect { step ->
                collectedSteps.add(step)
            }
        }
        yield()
        job.cancel()
        job.join()

        assertTrue(collectedSteps.isNotEmpty())
        assertFalse("Should not reach Completed after cancellation", collectedSteps.any { it is ScanStep.Completed })
    }

    // Helper implementations for contract testing

    private fun createDummyContext(): Context = FakeContext()

    private fun createDummyUri(): Uri = createTestUri()

    private class FakeSecurityScanner(
        override val providerId: ScannerProviderId,
        override val capabilities: ScannerCapabilities,
        private val configured: Boolean,
        private val cannedResult: SecurityScanResult? = null,
    ) : ApkSecurityScanner {

        override suspend fun isConfigured(context: Context): Boolean = configured

        override suspend fun lookupHash(
            context: Context,
            sha256: String,
            metadata: ScanApkMetadata,
        ): Result<SecurityScanResult?> {
            return Result.success(cannedResult)
        }

        override fun scanApk(
            context: Context,
            uri: Uri,
            fileName: String,
            metadata: ScanApkMetadata,
            options: ScanOptions,
        ): Flow<ScanStep> = flow {
            emit(ScanStep.Hashing(progressPercent = 10))
            emit(ScanStep.LookupCache(metadata.sha256.ifEmpty { "dummy_sha" }))
            if (cannedResult != null) {
                emit(ScanStep.Completed(cannedResult))
            } else {
                emit(ScanStep.Failed(ScanError.NotFound(providerId, metadata.sha256)))
            }
        }
    }
}
