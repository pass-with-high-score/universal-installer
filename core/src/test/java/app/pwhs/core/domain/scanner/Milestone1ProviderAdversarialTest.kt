package app.pwhs.core.domain.scanner

import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.scanner.impl.VirusTotalScanner
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import app.pwhs.core.network.VtRemoteConfigSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Adversarial empirical tests by Challenger 2 for VirusTotalKeyManager and VirusTotalService.
 * Challenges key pool parsing, trial rotation, and direct/large upload URL routing thresholds.
 */
class Milestone1ProviderAdversarialTest {

    private val sampleKeyA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val sampleKeyB = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
    private val sampleKeyC = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private val fakeContext = FakeContext()
    private val fakeUri: Uri = createTestUri("content://app.pwhs/sample.apk")

    @Before
    fun setUp() {
        VirusTotalKeyManager.resetForTesting()
        VirusTotalKeyManager.install(object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$sampleKeyA,$sampleKeyB"
            override fun getDailyLimit(): Int = 10
        })
    }

    @After
    fun tearDown() {
        VirusTotalKeyManager.resetForTesting()
    }

    @Test
    fun testKeyPoolParsing_malformedJsonSyntaxAndDirtyStrings() {
        // Truncated / broken JSON
        val brokenJson = """{"keys": ["$sampleKeyA", "$sampleKeyB"""
        val parsedBroken = VirusTotalKeyManager.parseKeyPool(brokenJson)
        assertEquals(2, parsedBroken.size)
        assertTrue(parsedBroken.contains(sampleKeyA))
        assertTrue(parsedBroken.contains(sampleKeyB))

        // JSON object instead of array
        val objectJson = """{"key1": "$sampleKeyA", "status": "active", "key2": "$sampleKeyB"}"""
        val parsedObject = VirusTotalKeyManager.parseKeyPool(objectJson)
        assertEquals(2, parsedObject.size)

        // Dirty HTML and log output with delimiters
        val dirtyLog = """
            <html><body>
            <div id="api-keys">
                <span>Key: $sampleKeyA</span>;
                <span>Backup: $sampleKeyB</span>\n
                <span>Duplicate: ${sampleKeyA.uppercase()}</span>
            </div>
            </body></html>
        """.trimIndent()
        val parsedLog = VirusTotalKeyManager.parseKeyPool(dirtyLog)
        assertEquals(2, parsedLog.size)
        assertEquals(listOf(sampleKeyA, sampleKeyB), parsedLog)

        // Empty and blank strings
        assertTrue(VirusTotalKeyManager.parseKeyPool("").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("   \t\n  ").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("[]").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("{}").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("null").isEmpty())
    }

    @Test
    fun testKeyPoolParsing_invalidLengthsAndNonHexChars() {
        val shortKey = sampleKeyA.substring(0, 63)
        assertEquals(63, shortKey.length)
        assertTrue(VirusTotalKeyManager.parseKeyPool(shortKey).isEmpty())

        val nonHexKey = sampleKeyA.substring(0, 63) + "g"
        assertTrue(VirusTotalKeyManager.parseKeyPool(nonHexKey).isEmpty())

        val mixedCorrupt = "key1=$shortKey, key2=$sampleKeyC, key3=$nonHexKey"
        val parsedMixed = VirusTotalKeyManager.parseKeyPool(mixedCorrupt)
        assertEquals(1, parsedMixed.size)
        assertEquals(sampleKeyC, parsedMixed[0])
    }

    @Test
    fun testKeyManager_trialKeyRateLimitRotation() = runBlocking {
        var callCount = 0
        var firstKeyCalled: String? = null
        val serviceSpy = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                callCount++
                if (firstKeyCalled == null) {
                    firstKeyCalled = apiKey
                    return VtResult(status = VtStatus.RATE_LIMITED, errorMessage = "60")
                }
                return VtResult(status = VtStatus.CLEAN, harmless = 70)
            }
        }

        val result = VirusTotalKeyManager.checkFile(fakeContext, serviceSpy, "test_sha256")
        assertEquals(VtStatus.CLEAN, result.status)
        assertEquals(70, result.harmless)
        assertEquals(2, callCount)
    }

    @Test
    fun testVirusTotalService_directUploadRouting_atOrBelow32MB() = runBlocking {
        val interceptedUrls = mutableListOf<String>()
        val interceptedMethods = mutableListOf<String>()

        val customClient = HttpClient(OkHttp) {
            engine {
                addInterceptor { chain ->
                    val req = chain.request()
                    interceptedUrls.add(req.url.toString())
                    interceptedMethods.add(req.method)

                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"data":{"id":"upload_direct_id"}}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
        }

        val vtService = VirusTotalService(client = customClient)

        val smallFile = File.createTempFile("vt_test_small", ".apk")
        try {
            smallFile.writeBytes(ByteArray(1024))
            val result = vtService.uploadFile(sampleKeyA, smallFile)

            assertTrue(result.isSuccess)
            assertEquals("upload_direct_id", result.getOrNull())
            assertEquals(1, interceptedUrls.size)
            assertEquals("https://www.virustotal.com/api/v3/files", interceptedUrls[0])
            assertEquals("POST", interceptedMethods[0])
        } finally {
            smallFile.delete()
        }
    }

    @Test
    fun testVirusTotalService_largeUploadRouting_above32MB() = runBlocking {
        val interceptedUrls = mutableListOf<String>()
        val interceptedMethods = mutableListOf<String>()

        val customClient = HttpClient(OkHttp) {
            engine {
                addInterceptor { chain ->
                    val req = chain.request()
                    interceptedUrls.add(req.url.toString())
                    interceptedMethods.add(req.method)

                    val body = if (req.url.toString().endsWith("/upload_url")) {
                        """{"data":"https://upload.virustotal.com/custom_upload_url_abc"}"""
                    } else {
                        """{"data":{"id":"upload_large_id"}}"""
                    }

                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
        }

        val vtService = VirusTotalService(client = customClient)

        val largeFile = File.createTempFile("vt_test_large", ".apk")
        try {
            val raf = RandomAccessFile(largeFile, "rw")
            raf.setLength(VirusTotalService.SIZE_LIMIT_DIRECT + 1L)
            raf.close()

            assertEquals(VirusTotalService.SIZE_LIMIT_DIRECT + 1L, largeFile.length())

            val result = vtService.uploadFile(sampleKeyA, largeFile)
            assertTrue(result.isSuccess)
            assertEquals("upload_large_id", result.getOrNull())

            assertEquals(2, interceptedUrls.size)
            assertEquals("https://www.virustotal.com/api/v3/files/upload_url", interceptedUrls[0])
            assertEquals("GET", interceptedMethods[0])
            assertEquals("https://upload.virustotal.com/custom_upload_url_abc", interceptedUrls[1])
            assertEquals("POST", interceptedMethods[1])
        } finally {
            largeFile.delete()
        }
    }

    @Test
    fun testVirusTotalScanner_sizeLimitEnforcement_atAndAbove650MB() = runBlocking {
        val scanner = VirusTotalScanner(service = VirusTotalService())

        val metaAt650MB = ScanApkMetadata(
            sha256 = "hash_650mb",
            fileSizeBytes = VirusTotalService.SIZE_LIMIT_LARGE,
        )
        val stepsAtLimit = scanner.scanApk(
            context = fakeContext,
            uri = fakeUri,
            fileName = "sample.apk",
            metadata = metaAt650MB,
            options = ScanOptions(allowUpload = false),
        ).toList()

        val notTooLarge = stepsAtLimit.none { it is ScanStep.Failed && it.error is ScanError.FileTooLarge }
        assertTrue("Scan at exactly 650MB must not fail with FileTooLarge", notTooLarge)

        val metaAbove650MB = ScanApkMetadata(
            sha256 = "hash_too_large",
            fileSizeBytes = VirusTotalService.SIZE_LIMIT_LARGE + 1L,
        )
        val stepsAboveLimit = scanner.scanApk(
            context = fakeContext,
            uri = fakeUri,
            fileName = "sample.apk",
            metadata = metaAbove650MB,
        ).toList()

        assertEquals(1, stepsAboveLimit.size)
        assertTrue(stepsAboveLimit[0] is ScanStep.Failed)
        val failed = stepsAboveLimit[0] as ScanStep.Failed
        assertTrue(failed.error is ScanError.FileTooLarge)
        val error = failed.error as ScanError.FileTooLarge
        assertEquals(ScannerProviderId.VIRUSTOTAL, error.provider)
        assertEquals(VirusTotalService.SIZE_LIMIT_LARGE + 1L, error.fileSizeBytes)
        assertEquals(VirusTotalService.SIZE_LIMIT_LARGE, error.maxSizeBytes)
    }
}
