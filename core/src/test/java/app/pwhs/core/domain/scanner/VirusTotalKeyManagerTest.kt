package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.network.NoOpVtRemoteConfigSource
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalService
import app.pwhs.core.network.VtQuotaStatus
import app.pwhs.core.network.VtRemoteConfigSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 & 2 Tests for VirusTotalKeyManager: API key parsing, pool rotation, cooldowns, and quotas.
 */
class VirusTotalKeyManagerTest {

    private val validKey1 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    private val validKey2 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
    private val validKey3 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Before
    fun setUp() {
        // Reset remote source and cooldowns to clean state before each test
        VirusTotalKeyManager.resetForTesting()
    }

    @After
    fun tearDown() {
        VirusTotalKeyManager.resetForTesting()
    }

    @Test
    fun testParseKeyPool_jsonArrayFormat() {
        val json = """["$validKey1", "$validKey2"]"""
        val keys = VirusTotalKeyManager.parseKeyPool(json)
        assertEquals(2, keys.size)
        assertEquals(validKey1, keys[0])
        assertEquals(validKey2, keys[1])
    }

    @Test
    fun testParseKeyPool_csvAndWhitespace() {
        val csv = " $validKey1 , \n $validKey2 , \t $validKey3 "
        val keys = VirusTotalKeyManager.parseKeyPool(csv)
        assertEquals(3, keys.size)
        assertTrue(keys.contains(validKey1))
        assertTrue(keys.contains(validKey2))
        assertTrue(keys.contains(validKey3))
    }

    @Test
    fun testParseKeyPool_newlineSeparated() {
        val newlineSeparated = "$validKey1\n$validKey2\n$validKey3"
        val keys = VirusTotalKeyManager.parseKeyPool(newlineSeparated)
        assertEquals(3, keys.size)
    }

    @Test
    fun testParseKeyPool_filtersInvalidKeys() {
        // 63 chars (too short), 65 chars (too long), non-hex characters, embedded strings
        val invalidTooShort = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcde"
        val invalidTooLong = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1"
        val invalidIndependent65 = "f".repeat(65)
        val invalidEmbedded = "prefix" + validKey3 + "suffix"
        val invalidNonHex = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdeg" // 'g' is non-hex

        val mixed = "$validKey1, $invalidTooShort, $invalidTooLong, $invalidIndependent65, $invalidEmbedded, $invalidNonHex, $validKey2"
        val keys = VirusTotalKeyManager.parseKeyPool(mixed)

        assertEquals(2, keys.size)
        assertEquals(listOf(validKey1, validKey2), keys)

        // Strict boundary edge checks
        assertEquals(emptyList<String>(), VirusTotalKeyManager.parseKeyPool("f".repeat(65)))
        assertEquals(emptyList<String>(), VirusTotalKeyManager.parseKeyPool("prefix" + "a".repeat(64) + "suffix"))
        assertEquals(emptyList<String>(), VirusTotalKeyManager.parseKeyPool("a".repeat(63)))
    }

    @Test
    fun testParseKeyPool_normalizesToLowerCaseAndDeduplicates() {
        val upperKey = validKey1.uppercase()
        val mixedCase = """["$validKey1", "$upperKey", "$validKey1"]"""
        val keys = VirusTotalKeyManager.parseKeyPool(mixedCase)

        assertEquals(1, keys.size)
        assertEquals(validKey1.lowercase(), keys[0])
    }

    @Test
    fun testParseKeyPool_emptyAndBlankString_returnsEmptyList() {
        assertTrue(VirusTotalKeyManager.parseKeyPool("").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("   ").isEmpty())
        assertTrue(VirusTotalKeyManager.parseKeyPool("[]").isEmpty())
    }

    @Test
    fun testVtQuotaStatus_isUnlimited() {
        val personalKeyStatus = VtQuotaStatus(hasPersonalKey = true, isProUser = false, isFallbackEnabled = false, usedToday = 10, dailyLimit = 3)
        assertTrue("Personal key should be unlimited", personalKeyStatus.isUnlimited)
        assertFalse(personalKeyStatus.isQuotaExhausted)

        val proStatus = VtQuotaStatus(hasPersonalKey = false, isProUser = true, isFallbackEnabled = false, usedToday = 10, dailyLimit = 3)
        assertTrue("Pro user should be unlimited", proStatus.isUnlimited)
        assertFalse(proStatus.isQuotaExhausted)
    }

    @Test
    fun testVtQuotaStatus_trialQuotaCalculations() {
        val trialActive = VtQuotaStatus(hasPersonalKey = false, isProUser = false, usedToday = 1, dailyLimit = 3)
        assertFalse(trialActive.isUnlimited)
        assertEquals(2, trialActive.remainingToday)
        assertFalse(trialActive.isQuotaExhausted)

        val trialExhausted = VtQuotaStatus(hasPersonalKey = false, isProUser = false, usedToday = 3, dailyLimit = 3)
        assertEquals(0, trialExhausted.remainingToday)
        assertTrue(trialExhausted.isQuotaExhausted)

        val trialOverLimit = VtQuotaStatus(hasPersonalKey = false, isProUser = false, usedToday = 5, dailyLimit = 3)
        assertEquals(0, trialOverLimit.remainingToday)
        assertTrue(trialOverLimit.isQuotaExhausted)
    }

    @Test
    fun testRemoteConfigSource_customImplementation() {
        val customSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() {}
            override fun getDailyLimit(): Int = 5
        }

        VirusTotalKeyManager.install(customSource)
        assertEquals(5, customSource.getDailyLimit())
    }

    @Test
    fun testCanUseVirusTotal_withoutPersonalKey_returnsFalse() = runBlocking {
        VirusTotalKeyManager.userApiKeyOverride = null
        assertFalse(VirusTotalKeyManager.canUseVirusTotal(FakeContext()))
    }

    @Test
    fun testCanUseVirusTotal_withPersonalKey_returnsTrue() = runBlocking {
        VirusTotalKeyManager.userApiKeyOverride = validKey1
        assertTrue(VirusTotalKeyManager.canUseVirusTotal(FakeContext()))
    }

    @Test
    fun testCheckFile_withoutPersonalKey_returnsNoApiKey() = runBlocking {
        VirusTotalKeyManager.userApiKeyOverride = null
        val dummyService = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                throw AssertionError("Should not make network calls when personal key is missing")
            }
        }
        val result = VirusTotalKeyManager.checkFile(FakeContext(), dummyService, "test_sha")
        assertEquals(VtStatus.NO_API_KEY, result.status)
    }

    @Test
    fun testCheckFile_withPersonalKey_delegatesToService() = runBlocking {
        VirusTotalKeyManager.userApiKeyOverride = validKey1
        var calledKey: String? = null
        val dummyService = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                calledKey = apiKey
                return VtResult(status = VtStatus.CLEAN, harmless = 70)
            }
        }
        val result = VirusTotalKeyManager.checkFile(FakeContext(), dummyService, "test_sha")
        assertEquals(VtStatus.CLEAN, result.status)
        assertEquals(70, result.harmless)
        assertEquals(validKey1, calledKey)
    }
}
