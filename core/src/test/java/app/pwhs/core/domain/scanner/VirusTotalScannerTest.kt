package app.pwhs.core.domain.scanner

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.model.VtEngineResult
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.scanner.impl.VirusTotalScanner
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import app.pwhs.core.network.VtRemoteConfigSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VirusTotalScannerTest {

    private class FakeVtService : VirusTotalService() {
        var stubbedCheckFileResult: VtResult = VtResult(status = VtStatus.CLEAN, harmless = 70)

        override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
            return stubbedCheckFileResult
        }
    }

    private class FakeRemoteSource(
        private val fallbackEnabled: Boolean = true,
        private val keys: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        private val dailyLimit: Int = 10,
    ) : VtRemoteConfigSource {
        override suspend fun ensureFetched() = Unit
        override fun isFallbackEnabled(): Boolean = fallbackEnabled
        override fun getRawKeys(): String = keys
        override fun getDailyLimit(): Int = dailyLimit
    }

    private val context = FakeContext()
    private val fakeUri = createTestUri("content://app.pwhs/test.apk")
    private val fakeService = FakeVtService()

    @Before
    fun setUp() {
        VirusTotalKeyManager.resetForTesting()
        VirusTotalKeyManager.install(FakeRemoteSource())
    }

    @After
    fun tearDown() {
        VirusTotalKeyManager.resetForTesting()
    }

    @Test
    fun capabilities_areConfiguredCorrectly() {
        val scanner = VirusTotalScanner(service = fakeService)

        assertEquals(ScannerProviderId.VIRUSTOTAL, scanner.providerId)
        assertTrue(scanner.capabilities.supportsHashLookup)
        assertTrue(scanner.capabilities.supportsBinaryUpload)
        assertTrue(scanner.capabilities.requiresNetwork)
        assertEquals(650L * 1024 * 1024, scanner.capabilities.maxFileSizeBytes)
    }

    @Test
    fun lookupHash_blankSha256_returnsFailure() = runBlocking {
        val scanner = VirusTotalScanner(service = fakeService)
        val metadata = ScanApkMetadata(sha256 = "")

        val result = scanner.lookupHash(context, "", metadata)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun lookupHash_cleanSample_returnsSafeVerdictWithEngines() = runBlocking {
        fakeService.stubbedCheckFileResult = VtResult(
            status = VtStatus.CLEAN,
            harmless = 68,
            malicious = 0,
            suspicious = 0,
            undetected = 2,
            engineResults = listOf(
                VtEngineResult("Avast", "harmless", null),
                VtEngineResult("Kaspersky", "harmless", null),
            ),
        )

        val scanner = VirusTotalScanner(service = fakeService)
        val metadata = ScanApkMetadata(sha256 = "clean_hash_123")

        val result = scanner.lookupHash(context, "clean_hash_123", metadata)

        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()
        assertNotNull(scanResult)
        assertEquals(ScannerVerdict.SAFE, scanResult?.verdict)
        assertEquals(ScannerProviderId.VIRUSTOTAL, scanResult?.providerId)
        assertEquals(68, scanResult?.harmlessCount)
        assertEquals(2, scanResult?.engineDetections?.size)
    }

    @Test
    fun lookupHash_notFound_returnsNull() = runBlocking {
        fakeService.stubbedCheckFileResult = VtResult(status = VtStatus.NOT_FOUND)

        val scanner = VirusTotalScanner(service = fakeService)
        val metadata = ScanApkMetadata(sha256 = "not_found_hash")

        val result = scanner.lookupHash(context, "not_found_hash", metadata)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun lookupHash_rateLimited_returnsRateLimitedError() = runBlocking {
        fakeService.stubbedCheckFileResult = VtResult(status = VtStatus.RATE_LIMITED, errorMessage = "60")

        val scanner = VirusTotalScanner(service = fakeService)
        val metadata = ScanApkMetadata(sha256 = "rate_limited_hash")

        val result = scanner.lookupHash(context, "rate_limited_hash", metadata)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is VirusTotalScanner.ScanErrorException)
        assertTrue((ex as VirusTotalScanner.ScanErrorException).error is ScanError.RateLimited)
    }

    @Test
    fun scanApk_fileTooLarge_emitsFailedImmediately() = runBlocking {
        val scanner = VirusTotalScanner(service = fakeService)
        val metadata = ScanApkMetadata(
            sha256 = "huge_apk",
            fileSizeBytes = 700L * 1024 * 1024, // 700MB > 650MB limit
        )

        val steps = scanner.scanApk(context, fakeUri, "huge.apk", metadata).toList()

        assertEquals(1, steps.size)
        assertTrue(steps[0] is ScanStep.Failed)
        val failed = steps[0] as ScanStep.Failed
        assertTrue(failed.error is ScanError.FileTooLarge)
        val err = failed.error as ScanError.FileTooLarge
        assertEquals(ScannerProviderId.VIRUSTOTAL, err.provider)
        assertEquals(700L * 1024 * 1024, err.fileSizeBytes)
        assertEquals(VirusTotalService.SIZE_LIMIT_LARGE, err.maxSizeBytes)
    }
}
