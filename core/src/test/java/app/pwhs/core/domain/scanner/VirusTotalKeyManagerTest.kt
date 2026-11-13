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
        val trialActive = VtQuotaStatus(hasPersonalKey = false, isProUser = false, isFallbackEnabled = true, usedToday = 1, dailyLimit = 3)
        assertFalse(trialActive.isUnlimited)
        assertEquals(2, trialActive.remainingToday)
        assertFalse(trialActive.isQuotaExhausted)

        val trialExhausted = VtQuotaStatus(hasPersonalKey = false, isProUser = false, isFallbackEnabled = true, usedToday = 3, dailyLimit = 3)
        assertEquals(0, trialExhausted.remainingToday)
        assertTrue(trialExhausted.isQuotaExhausted)

        val trialOverLimit = VtQuotaStatus(hasPersonalKey = false, isProUser = false, isFallbackEnabled = true, usedToday = 5, dailyLimit = 3)
        assertEquals(0, trialOverLimit.remainingToday)
        assertTrue(trialOverLimit.isQuotaExhausted)
    }

    @Test
    fun testRemoteConfigSource_customImplementation() {
        val customSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() {}
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = validKey1
            override fun getDailyLimit(): Int = 5
        }

        VirusTotalKeyManager.install(customSource)
        val pool = VirusTotalKeyManager.parseKeyPool(customSource.getRawKeys())
        assertEquals(1, pool.size)
        assertEquals(validKey1, pool[0])
        assertEquals(5, customSource.getDailyLimit())
    }

    @Test
    fun testCooldown_rateLimit_65SecondsCooldown() {
        var currentTime = 1_000_000L
        VirusTotalKeyManager.clock = { currentTime }

        VirusTotalKeyManager.markCooldown(validKey1, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKey1))
        assertEquals(currentTime + 65_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(validKey1))

        // Before 65s expires
        currentTime += 64_999L
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKey1))

        // At exactly 65s
        currentTime += 1L
        assertFalse(VirusTotalKeyManager.isOnCooldown(validKey1))
    }

    @Test
    fun testCooldown_invalidKey_oneHourCooldown() {
        var currentTime = 5_000_000L
        VirusTotalKeyManager.clock = { currentTime }

        VirusTotalKeyManager.markCooldown(validKey1, VirusTotalKeyManager.INVALID_KEY_COOLDOWN_MS)
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKey1))
        assertEquals(currentTime + 3_600_000L, VirusTotalKeyManager.getKeyCooldownUntilMs(validKey1))

        // At 59 minutes
        currentTime += 59 * 60 * 1000L
        assertTrue(VirusTotalKeyManager.isOnCooldown(validKey1))

        // At 60 minutes
        currentTime += 60 * 1000L
        assertFalse(VirusTotalKeyManager.isOnCooldown(validKey1))
    }

    @Test
    fun testKeyRotation_bypassesKeysOnCooldown() = runBlocking {
        var currentTime = 10_000_000L
        VirusTotalKeyManager.clock = { currentTime }

        val remoteSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$validKey1, $validKey2"
            override fun getDailyLimit(): Int = 10
        }
        VirusTotalKeyManager.install(remoteSource)

        // Put key 1 on cooldown
        VirusTotalKeyManager.markCooldown(validKey1, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)

        val candidates = VirusTotalKeyManager.resolveTrialCandidateKeys(FakeContext(), "test_sha")
        assertEquals(1, candidates.size)
        assertEquals(validKey2, candidates[0])

        // When cooldown expires, both keys are available
        currentTime += 65_000L
        val refreshedCandidates = VirusTotalKeyManager.resolveTrialCandidateKeys(FakeContext(), "test_sha")
        assertEquals(2, refreshedCandidates.size)
        assertTrue(refreshedCandidates.contains(validKey1))
        assertTrue(refreshedCandidates.contains(validKey2))
    }

    @Test
    fun testKeyRotation_allKeysOnCooldown_exhaustsQuotaAndReturnsNoApiKey() = runBlocking {
        val currentTime = 20_000_000L
        VirusTotalKeyManager.clock = { currentTime }

        val remoteSource = object : VtRemoteConfigSource {
            override suspend fun ensureFetched() = Unit
            override fun isFallbackEnabled(): Boolean = true
            override fun getRawKeys(): String = "$validKey1, $validKey2"
            override fun getDailyLimit(): Int = 10
        }
        VirusTotalKeyManager.install(remoteSource)

        // Mark all keys on cooldown
        VirusTotalKeyManager.markCooldown(validKey1, VirusTotalKeyManager.RATE_LIMIT_COOLDOWN_MS)
        VirusTotalKeyManager.markCooldown(validKey2, VirusTotalKeyManager.INVALID_KEY_COOLDOWN_MS)

        val candidates = VirusTotalKeyManager.resolveTrialCandidateKeys(FakeContext(), "test_sha")
        assertTrue("No candidate keys should be resolved when all are on cooldown", candidates.isEmpty())

        val dummyService = object : VirusTotalService() {
            override suspend fun checkFile(apiKey: String, sha256: String): VtResult {
                throw AssertionError("Should not make network calls when all keys are on cooldown")
            }
        }
        val result = VirusTotalKeyManager.checkFile(FakeContext(), dummyService, "test_sha")
        assertEquals(VtStatus.NO_API_KEY, result.status)
    }
}
