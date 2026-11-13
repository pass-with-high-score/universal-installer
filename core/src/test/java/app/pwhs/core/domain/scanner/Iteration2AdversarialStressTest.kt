package app.pwhs.core.domain.scanner

import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import app.pwhs.core.network.VtRemoteConfigSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Adversarial stress harness by Challenger 1 for Iteration 2 remediations.
 * Strictly verifies regex boundary constraints, cooldown boundaries, key exhaustion,
 * and Category E malformed SSE / JSON handling.
 */
class Iteration2AdversarialStressTest {

    private val validKeyA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val validKeyB = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
    private val validKeyC = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private val fakeContext = FakeContext()

    @Before
    fun setUp() {
        VirusTotalKeyManager.resetForTesting()
    }

    @After
    fun tearDown() {
        VirusTotalKeyManager.resetForTesting()
    }

    // =========================================================================
    // 1. Regex Word-Boundary Stress Tests
    // =========================================================================

    @Test
    fun testRegex_strictlyRejectsIndependent65PlusHexStrings() {
        val key65 = "f".repeat(65)
        val key66 = "f".repeat(66)
        val key70 = "0123456789abcdef".repeat(4) + "123456"
        val key128 = "a".repeat(128)

        assertTrue("65-char hex must be rejected", VirusTotalKeyManager.parseKeyPool(key65).isEmpty())
        assertTrue("66-char hex must be rejected", VirusTotalKeyManager.parseKeyPool(key66).isEmpty())
        assertTrue("70-char hex must be rejected", VirusTotalKeyManager.parseKeyPool(key70).isEmpty())
        assertTrue("128-char hex must be rejected", VirusTotalKeyManager.parseKeyPool(key128).isEmpty())
    }

    @Test
    fun testRegex_strictlyRejectsEmbeddedWordKeys() {
        // Alphanumeric prefix / suffix (forms contiguous word chars \w)
        val prefixLetter = "prefix$validKeyA"
        val suffixLetter = "${validKeyA}suffix"
        val prefixDigit = "1$validKeyA"
        val suffixDigit = "${validKeyA}2"
        val embeddedWord = "abc${validKeyA}def"

        assertTrue(VirusTotalKeyManager.parseKeyPool(prefixLetter).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(suffixLetter).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(prefixDigit).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(suffixDigit).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(embeddedWord).isEmpty())

        // Underscore prefix / suffix (_ is in \w, so \b boundary fails)
        val underscorePrefix = "_$validKeyA"
        val underscoreSuffix = "${validKeyA}_"
        val underscoreBoth = "_${validKeyA}_"

        assertTrue(VirusTotalKeyManager.parseKeyPool(underscorePrefix).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(underscoreSuffix).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(underscoreBoth).isEmpty())
    }

    @Test
    fun testRegex_strictlyRejectsKeysGluedToNonHexWordChars() {
        // 'g' is \w but not hex [0-9a-fA-F]
        val gluedGPrefix = "g$validKeyA"
        val gluedGSuffix = "${validKeyA}g"
        val gluedZ = "z${validKeyA}z"

        assertTrue(VirusTotalKeyManager.parseKeyPool(gluedGPrefix).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(gluedGSuffix).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool(gluedZ).isEmpty())
    }

    @Test
    fun testRegex_strictlyRejectsShortKeys() {
        assertTrue(VirusTotalKeyManager.parseKeyPool("a".repeat(63)).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("0".repeat(32)).isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("").isEmpty())
    }

    @Test
    fun testRegex_mixedRawStringWithMultipleAttacks_extractsOnlyValidKeys() {
        val mixedRaw = listOf(
            "f".repeat(65),
            validKeyA,
            "pre" + validKeyB + "suf",
            "1" + validKeyB,
            validKeyB + "2",
            validKeyB, // duplicate
            validKeyC.uppercase(),
            "g" + validKeyC,
            "_" + validKeyC + "_",
            "0".repeat(63),
        ).joinToString(separator = ", \n\t ")

        val parsed = VirusTotalKeyManager.parseKeyPool(mixedRaw)
        assertEquals(3, parsed.size)
        assertEquals(validKeyA, parsed[0])
        assertEquals(validKeyB, parsed[1])
        assertEquals(validKeyC, parsed[2]) // normalized lowercase
    }

    // =========================================================================
    // 2. Cooldown Calculation & State Machine Stress Tests
    // =========================================================================

    @Test
    fun testCooldown_rateLimitBoundary_preciseToSingleMillisecond() {
        val t0 = 10_000_000L
        var virtualTime = t0
        VirusTotalKeyManager.clock = { virtualTime }

        VirusTotalKeyManager.markCooldown(validKeyA, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)
        assertEquals(t0 + 65_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(validKeyA))

        // t0: active
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKeyA))

        // t0 + 64_999ms: strictly active
        virtualTime = t0 + 64_999L
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKeyA))

        // t0 + 65_000ms: strictly expired
        virtualTime = t0 + 65_000L
        assertFalse(VirusTotalKeyManager.isOnCooldown(validKeyA))

        // t0 + 65_001ms: expired
        virtualTime = t0 + 65_001L
        assertFalse(VirusTotalKeyManager.isOnCooldown(validKeyA))
    }

    @Test
    fun testCooldown_invalidKeyBoundary_preciseToSingleMillisecond() {
        val t0 = 50_000_000L
        var virtualTime = t0
        VirusTotalKeyManager.clock = { virtualTime }

        VirusTotalKeyManager.markCooldown(validKeyA, VirusTotalKeyManager.INVALID_KEY_COOLDOWN_MS)
        assertEquals(t0 + 3_600_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(validKeyA))

        // t0: active
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKeyA))

        // t0 + 3_599_999ms: strictly active
        virtualTime = t0 + 3_599_999L
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKeyA))

        // t0 + 3_600_000ms: strictly expired
        virtualTime = t0 + 3_600_000L
        assertFalse(VirusTotalKeyManager.isOnCooldown(validKeyA))
    }

    @Test
    fun testKeyRotation_skipsCooledKeysAndRestoresWhenExpired() = runBlocking {
        val t0 = 100_000_000L
        var virtualTime = t0
        VirusTotalKeyManager.clock = { virtualTime }

        val remoteSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$validKeyA, $validKeyB, $validKeyC"
            override fun getDailyLimit(): Int = 20
        }
        VirusTotalKeyManager.install(remoteSource)

        // Put Key A on 65s cooldown and Key B on 1h cooldown
        VirusTotalKeyManager.markCooldown(validKeyA, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)
        VirusTotalKeyManager.markCooldown(validKeyB, VirusTotalKeyManager.INVALID_KEY_COOLDOWN_MS)

        // Only Key C is ready
        val initialCandidates = VirusTotalKeyManager.resolveTrialCandidateKeys(fakeContext, "sha1")
        assertEquals(1, initialCandidates.size)
        assertEquals(validKeyC, initialCandidates[0])

        // Advance past 65s: Key A becomes ready, Key B still cooled
        virtualTime = t0 + 65_000L
        val after65s = VirusTotalKeyManager.resolveTrialCandidateKeys(fakeContext, "sha1")
        assertEquals(2, after65s.size)
        assertTrue(after65s.contains(validKeyA))
        assertTrue(after65s.contains(validKeyC))
        assertFalse(after65s.contains(validKeyB))

        // Advance past 1 hour: all 3 keys ready
        virtualTime = t0 + 3_600_000L
        val after1h = VirusTotalKeyManager.resolveTrialCandidateKeys(fakeContext, "sha1")
        assertEquals(3, after1h.size)
        assertTrue(after1h.contains(validKeyA))
        assertTrue(after1h.contains(validKeyB))
        assertTrue(after1h.contains(validKeyC))
    }

    @Test
    fun testKeyExhaustion_allKeysOnCooldown_shortCircuitsWithoutNetworkCalls() = runBlocking {
        var virtualTime = 200_000_000L
        VirusTotalKeyManager.clock = { virtualTime }

        val remoteSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$validKeyA, $validKeyB"
            override fun getDailyLimit(): Int = 10
        }
        VirusTotalKeyManager.install(remoteSource)

        VirusTotalKeyManager.markCooldown(validKeyA, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)
        VirusTotalKeyManager.markCooldown(validKeyB, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)

        // resolveTrialCandidateKeys returns empty
        val candidates = VirusTotalKeyManager.resolveTrialCandidateKeys(fakeContext, "sha_exhausted")
        assertTrue(candidates.isEmpty())

        // checkFile short-circuits to NO_API_KEY with zero network calls
        var checkFileNetworkCalls = 0
        val serviceMock = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                checkFileNetworkCalls++
                return VtResult(status = VtStatus.CLEAN)
            }
        }

        val checkResult = VirusTotalKeyManager.checkFile(fakeContext, serviceMock, "sha_exhausted")
        assertEquals(VtStatus.NO_API_KEY, checkResult.status)
        assertEquals(0, checkFileNetworkCalls)

        // uploadAndPoll short-circuits to NO_API_KEY with zero network calls
        var uploadNetworkCalls = 0
        val uploadServiceMock = object : VirusTotalService() {
            override suspend fun uploadFile(apiKey: String, file: File, onProgress: suspend (Int) -> Unit): Result<String> {
                uploadNetworkCalls++
                return Result.success("analysis_123")
            }
        }
        val dummyFile = File.createTempFile("exhausted_test", ".apk")
        try {
            val uploadResult = VirusTotalKeyManager.uploadAndPoll(
                context = fakeContext,
                service = uploadServiceMock,
                sha256 = "sha_exhausted",
                file = dummyFile,
                onUploadProgress = {},
            )
            assertEquals(VtStatus.NO_API_KEY, uploadResult.status)
            assertEquals(0, uploadNetworkCalls)
        } finally {
            dummyFile.delete()
        }
    }

    @Test
    fun testInFlightCheckFile_rateLimitAndInvalidKey_marksCorrectCooldowns() = runBlocking {
        var virtualTime = 300_000_000L
        VirusTotalKeyManager.clock = { virtualTime }

        val remoteSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$validKeyA, $validKeyB, $validKeyC"
            override fun getDailyLimit(): Int = 10
        }
        VirusTotalKeyManager.install(remoteSource)

        // First rotated key returns 429, second returns 401, third returns CLEAN
        val calledKeys = mutableListOf<String>()
        val service = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                calledKeys.add(apiKey)
                return when (calledKeys.size) {
                    1 -> VtResult(status = VtStatus.RATE_LIMITED, errorMessage = "Rate limited")
                    2 -> VtResult(status = VtStatus.INVALID_API_KEY, errorMessage = "Invalid key")
                    else -> VtResult(status = VtStatus.CLEAN, harmless = 50)
                }
            }
        }

        val result = VirusTotalKeyManager.checkFile(fakeContext, service, "in_flight_sha")
        assertEquals(VtStatus.CLEAN, result.status)
        assertEquals(3, calledKeys.size)

        val firstKey = calledKeys[0]
        val secondKey = calledKeys[1]
        val thirdKey = calledKeys[2]

        // Verify first key cooled 65s, second key cooled 1h, third key not cooled
        assertTrue(VirusTotalKeyManager.isOnCooldown(firstKey))
        assertEquals(virtualTime + 65_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(firstKey))

        assertTrue(VirusTotalKeyManager.isOnCooldown(secondKey))
        assertEquals(virtualTime + 3_600_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(secondKey))

        assertFalse(VirusTotalKeyManager.isOnCooldown(thirdKey))
    }

    // =========================================================================
    // 3. Category E Edge Cases in VirusDetecService
    // =========================================================================

    @Test
    fun testParseSseLine_adversarialCorruptedLines_handledSafely() {
        val service = VirusDetecService()

        // Missing prefix
        assertNull(service.parseSseLine("not-data-prefix: {}"))
        assertNull(service.parseSseLine(": ping"))
        assertNull(service.parseSseLine("event: update"))

        // Malformed / incomplete JSON payloads
        assertNull(service.parseSseLine("data: {"))
        assertNull(service.parseSseLine("data: {\"job_id\": \"123\", \"status\":"))
        assertNull(service.parseSseLine("data: [1, 2, 3]"))
        assertNull(service.parseSseLine("data: \"just a plain string\""))
        assertNull(service.parseSseLine("data: 99999"))
        assertNull(service.parseSseLine("data: true"))
        assertNull(service.parseSseLine("data: null"))
        assertNull(service.parseSseLine("data: {}"))
        assertNull(service.parseSseLine("data:    "))

        // Valid update amidst noise
        val validLine = """data: {"job_id":"job_999","status":"ANALYZING","progress_percent":80,"message":"Bytecode check"}"""
        val update = service.parseSseLine(validLine)
        assertNotNull(update)
        assertEquals("job_999", update?.jobId)
        assertEquals("ANALYZING", update?.status)
        assertEquals(80, update?.progressPercent)
        assertEquals("Bytecode check", update?.message)
    }

    @Test
    fun testParseScanReport_extremeMalformedJson_defaultsSafely() {
        val service = VirusDetecService()

        // Completely empty JSON
        val emptyReport = service.parseScanReport(JSONObject())
        assertEquals("", emptyReport.sha256)
        assertEquals(Verdict.SAFE, emptyReport.verdict)
        assertEquals(0, emptyReport.riskScore)
        assertTrue(emptyReport.findings.isEmpty())
        assertTrue(emptyReport.dexHashes.isEmpty())

        // Corrupted findings array containing primitives / nulls
        val corruptFindingsJson = JSONObject().apply {
            put("sha256", "corrupted_findings_sha")
            put("verdict", "MALICIOUS")
            put("risk_score", 95)
            put("findings", JSONArray().apply {
                put("not an object")
                put(12345)
                put(JSONObject().apply {
                    put("scanner", "static_engine")
                    put("rule_id", "rule_malware")
                    put("severity", "CRITICAL")
                    put("score_impact", 50)
                    put("title", "Trojan Dropper Detected")
                    put("description", "Contains known dropper DEX bytecode")
                })
                put(JSONObject()) // empty finding object
            })
        }

        val report = service.parseScanReport(corruptFindingsJson)
        assertEquals("corrupted_findings_sha", report.sha256)
        assertEquals(Verdict.MALICIOUS, report.verdict)
        assertEquals(95, report.riskScore)
        // Corrupted primitives skipped, empty object handled gracefully
        assertEquals(2, report.findings.size)
        assertEquals("Trojan Dropper Detected", report.findings[0].title)
        assertEquals(SeverityLevel.CRITICAL, report.findings[0].severity)
        assertEquals(50, report.findings[0].scoreImpact)
        assertEquals("", report.findings[1].title)
        assertEquals(SeverityLevel.INFO, report.findings[1].severity)
    }

    @Test
    fun testParseLookupResponse_emptyOrMissingReports_handlesGracefully() {
        val service = VirusDetecService()

        val emptyLookup = service.parseLookupResponse(JSONObject())
        assertFalse(emptyLookup.cached)
        assertFalse(emptyLookup.needUpload)
        assertNull(emptyLookup.report)
        assertNull(emptyLookup.precheckReport)

        // Lookup with cached true and full report
        val populatedJson = JSONObject().apply {
            put("cached", true)
            put("need_upload", false)
            put("report", JSONObject().apply {
                put("sha256", "cached_sha_123")
                put("verdict", "SAFE")
                put("risk_score", 0)
            })
        }
        val populatedLookup = service.parseLookupResponse(populatedJson)
        assertTrue(populatedLookup.cached)
        assertFalse(populatedLookup.needUpload)
        assertNotNull(populatedLookup.report)
        assertEquals("cached_sha_123", populatedLookup.report?.sha256)
        assertEquals(Verdict.SAFE, populatedLookup.report?.verdict)
    }
}
