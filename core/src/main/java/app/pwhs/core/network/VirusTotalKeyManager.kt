package app.pwhs.core.network

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of the user's VirusTotal quota and unlock tier for UI display.
 */
data class VtQuotaStatus(
    val hasPersonalKey: Boolean = false,
    val isProUser: Boolean = false,
    val isFallbackEnabled: Boolean = false,
    val usedToday: Int = 0,
    val dailyLimit: Int = VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT,
) {
    val isUnlimited: Boolean get() = hasPersonalKey || isProUser
    val remainingToday: Int get() = (dailyLimit - usedToday).coerceAtLeast(0)
    val isQuotaExhausted: Boolean get() = !isUnlimited && (!isFallbackEnabled || remainingToday <= 0)
}

/**
 * Abstraction over Firebase Remote Config for providing fallback VirusTotal API keys.
 *
 * The `play` flavor installs a Firebase-backed source; the `opensource` flavor leaves
 * [NoOpVtRemoteConfigSource] in place so no Google Play Services dependency is required.
 */
interface VtRemoteConfigSource {
    suspend fun ensureFetched()
    fun isFallbackEnabled(): Boolean
    fun getRawKeys(): String
    fun getDailyLimit(): Int
}

object NoOpVtRemoteConfigSource : VtRemoteConfigSource {
    override suspend fun ensureFetched() = Unit
    override fun isFallbackEnabled(): Boolean = false
    override fun getRawKeys(): String = ""
    override fun getDailyLimit(): Int = 0
}

/**
 * Manages VirusTotal API key resolution, Remote Config fallback key rotation, rate-limit
 * cooldowns, and per-device daily trial quota when the user has not configured a personal key.
 */
object VirusTotalKeyManager {

    const val RC_KEY_FALLBACK_ENABLED = "virustotal_fallback_enabled"
    const val RC_KEY_FALLBACK_KEYS = "virustotal_fallback_keys"
    const val RC_KEY_TRIAL_DAILY_LIMIT = "virustotal_trial_daily_limit"
    const val DEFAULT_TRIAL_DAILY_LIMIT = 3

    private val VT_KEY_REGEX = Regex("[0-9a-fA-F]{64}")
    private const val RATE_LIMIT_COOLDOWN_MS = 65_000L
    private const val INVALID_KEY_COOLDOWN_MS = 3_600_000L
    private const val MAX_ROTATION_ATTEMPTS = 4

    @Volatile
    private var remoteSource: VtRemoteConfigSource = NoOpVtRemoteConfigSource

    /** Monotonic elapsedRealtime deadline until which a rate-limited or invalid key rests. */
    private val keyCooldownUntilMs = ConcurrentHashMap<String, Long>()

    fun install(source: VtRemoteConfigSource) {
        remoteSource = source
    }

    /**
     * Extracts all valid 64-character hex VirusTotal API keys from a raw Remote Config string.
     * Supports JSON arrays (`["k1", "k2"]`), comma/newline/space-separated lists, or single keys.
     */
    fun parseKeyPool(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return VT_KEY_REGEX.findAll(raw)
            .map { it.value.lowercase(Locale.US) }
            .distinct()
            .toList()
    }

    suspend fun readUserApiKey(context: Context): String {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY]?.trim().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun isProUser(context: Context): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reactive stream of [VtQuotaStatus] for displaying daily usage (`usedToday / dailyLimit`)
     * and Unlimited status (Custom Key or Pro) across Phone and TV UIs.
     */
    fun observeQuotaStatus(context: Context): Flow<VtQuotaStatus> {
        return context.dataStore.data.map { prefs ->
            val hasPersonalKey = !prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY]?.trim().isNullOrBlank()
            val isPro = prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
            val today = todayString()
            val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY]
            val usedToday = if (storedDay == today) {
                prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
                    .orEmpty()
                    .split(',')
                    .count { it.isNotBlank() }
            } else {
                0
            }
            val poolNonEmpty = parseKeyPool(remoteSource.getRawKeys()).isNotEmpty()
            val fallbackActive = remoteSource.isFallbackEnabled() && poolNonEmpty
            val limit = remoteSource.getDailyLimit().takeIf { it > 0 } ?: DEFAULT_TRIAL_DAILY_LIMIT
            VtQuotaStatus(
                hasPersonalKey = hasPersonalKey,
                isProUser = isPro,
                isFallbackEnabled = fallbackActive,
                usedToday = usedToday,
                dailyLimit = limit,
            )
        }
    }

    /**
     * Returns true if VirusTotal can be called right now — either because the user has their own
     * personal API key, is a Pro user, or because Remote Config fallback keys are active and the
     * device still has trial quota available for today (or [sha256] was already authorized today).
     */
    suspend fun canUseVirusTotal(context: Context, sha256: String = ""): Boolean {
        if (readUserApiKey(context).isNotBlank()) return true
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return false
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return false
        if (isProUser(context)) return true
        return isTrialQuotaAvailable(context, sha256, remoteSource.getDailyLimit())
    }

    /**
     * Returns true when opening an APK should automatically look up its SHA-256 on VirusTotal
     * without waiting for a manual button click:
     * - Unlimited users (Pro subscribers or users with a personal API key), OR
     * - Free users opening a file whose SHA-256 was already scanned today (costs 0 extra quota).
     */
    suspend fun shouldAutoLookupHash(context: Context, sha256: String): Boolean {
        if (readUserApiKey(context).isNotBlank()) return true
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return false
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return false
        if (isProUser(context)) return true
        return isHashAlreadyScannedToday(context, sha256)
    }

    /**
     * Looks up a file hash on VirusTotal using the user's personal key if present, or rotating
     * through random Remote Config fallback keys when eligible.
     */
    suspend fun checkFile(
        context: Context,
        service: VirusTotalService,
        sha256: String,
    ): VtResult {
        if (sha256.isBlank()) {
            return VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
        }
        val userKey = readUserApiKey(context)
        if (userKey.isNotBlank()) {
            return service.checkFile(userKey, sha256)
        }

        val candidates = resolveTrialCandidateKeys(context, sha256)
        if (candidates.isEmpty()) {
            return VtResult(status = VtStatus.NO_API_KEY)
        }

        var lastResult = VtResult(status = VtStatus.NO_API_KEY)
        for (candidateKey in candidates) {
            val result = service.checkFile(candidateKey, sha256)
            lastResult = result
            when (result.status) {
                VtStatus.RATE_LIMITED -> {
                    markCooldown(candidateKey, RATE_LIMIT_COOLDOWN_MS)
                    Timber.w("Trial VT key rate-limited on checkFile; rotating to next key")
                }
                VtStatus.INVALID_API_KEY -> {
                    markCooldown(candidateKey, INVALID_KEY_COOLDOWN_MS)
                    Timber.w("Trial VT key invalid on checkFile; rotating to next key")
                }
                else -> {
                    if (result.status != VtStatus.ERROR && result.status != VtStatus.NOT_FOUND) {
                        recordTrialHashUsed(context, sha256)
                    }
                    return result
                }
            }
        }
        return lastResult
    }

    /**
     * Uploads an APK and polls its analysis on VirusTotal using the user's personal key if present,
     * or rotating through random Remote Config fallback keys when eligible.
     */
    suspend fun uploadAndPoll(
        context: Context,
        service: VirusTotalService,
        sha256: String,
        file: File,
        onUploadProgress: (Int) -> Unit,
        onQueued: (String) -> Unit = {},
        onPollProgress: (VtStatus, String) -> Unit = { _, _ -> },
    ): VtResult {
        val userKey = readUserApiKey(context)
        if (userKey.isNotBlank()) {
            return executeUploadAndPollWithSingleKey(
                apiKey = userKey,
                service = service,
                file = file,
                onUploadProgress = onUploadProgress,
                onQueued = onQueued,
                onPollProgress = onPollProgress,
            )
        }

        val candidates = resolveTrialCandidateKeys(context, sha256)
        if (candidates.isEmpty()) {
            return VtResult(status = VtStatus.NO_API_KEY)
        }

        var workingKey: String? = null
        var analysisId: String? = null
        var lastFailureResult: VtResult? = null

        for (candidateKey in candidates) {
            onUploadProgress(0)
            val uploadResult = service.uploadFile(candidateKey, file, onUploadProgress)
            val id = uploadResult.getOrNull()
            if (id != null) {
                workingKey = candidateKey
                analysisId = id
                break
            }
            val err = uploadResult.exceptionOrNull()
            if (err is VirusTotalService.VtHttpException) {
                when (err.vtStatus) {
                    VtStatus.RATE_LIMITED -> {
                        markCooldown(candidateKey, RATE_LIMIT_COOLDOWN_MS)
                        Timber.w("Trial VT key rate-limited on uploadFile; rotating to next key")
                        lastFailureResult = VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                        continue
                    }
                    VtStatus.INVALID_API_KEY -> {
                        markCooldown(candidateKey, INVALID_KEY_COOLDOWN_MS)
                        Timber.w("Trial VT key invalid on uploadFile; rotating to next key")
                        lastFailureResult = VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                        continue
                    }
                    else -> {
                        return VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                    }
                }
            } else {
                return VtResult(status = VtStatus.ERROR, errorMessage = err?.message ?: "Upload failed")
            }
        }

        val activeKey = workingKey ?: return (lastFailureResult ?: VtResult(status = VtStatus.NO_API_KEY))
        val activeAnalysisId = analysisId ?: return (lastFailureResult ?: VtResult(status = VtStatus.NO_API_KEY))

        recordTrialHashUsed(context, sha256)
        onQueued(activeAnalysisId)

        val pollResult = service.pollAnalysis(activeKey, activeAnalysisId) { status ->
            onPollProgress(status, activeAnalysisId)
        }
        if (pollResult.status == VtStatus.RATE_LIMITED) {
            markCooldown(activeKey, RATE_LIMIT_COOLDOWN_MS)
            val backupKey = candidates.firstOrNull { it != activeKey && !isOnCooldown(it) }
            if (backupKey != null) {
                val retryPoll = service.pollAnalysis(backupKey, activeAnalysisId) { status ->
                    onPollProgress(status, activeAnalysisId)
                }
                if (retryPoll.status != VtStatus.RATE_LIMITED) {
                    return retryPoll
                }
            }
        }
        return pollResult
    }

    private suspend fun executeUploadAndPollWithSingleKey(
        apiKey: String,
        service: VirusTotalService,
        file: File,
        onUploadProgress: (Int) -> Unit,
        onQueued: (String) -> Unit,
        onPollProgress: (VtStatus, String) -> Unit,
    ): VtResult {
        val uploadResult = service.uploadFile(apiKey, file, onUploadProgress)
        val analysisId = uploadResult.getOrElse { e ->
            return if (e is VirusTotalService.VtHttpException) {
                VtResult(status = e.vtStatus, errorMessage = e.message.orEmpty())
            } else {
                VtResult(status = VtStatus.ERROR, errorMessage = e.message ?: "Upload failed")
            }
        }
        onQueued(analysisId)
        return service.pollAnalysis(apiKey, analysisId) { status ->
            onPollProgress(status, analysisId)
        }
    }

    private suspend fun resolveTrialCandidateKeys(context: Context, sha256: String): List<String> {
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return emptyList()
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return emptyList()
        val dailyLimit = remoteSource.getDailyLimit()
        if (!isProUser(context) && !isTrialQuotaAvailable(context, sha256, dailyLimit)) return emptyList()

        val now = SystemClock.elapsedRealtime()
        val readyKeys = pool.filter { key ->
            val until = keyCooldownUntilMs[key] ?: 0L
            now >= until
        }.shuffled()

        val ordered = if (readyKeys.isNotEmpty()) {
            readyKeys
        } else {
            // All keys are on cooldown; pick the ones expiring soonest as last resort
            pool.sortedBy { keyCooldownUntilMs[it] ?: 0L }
        }
        return ordered.take(MAX_ROTATION_ATTEMPTS.coerceAtMost(pool.size))
    }

    private fun markCooldown(key: String, durationMs: Long) {
        keyCooldownUntilMs[key] = SystemClock.elapsedRealtime() + durationMs
    }

    private fun isOnCooldown(key: String): Boolean {
        val until = keyCooldownUntilMs[key] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    private suspend fun isHashAlreadyScannedToday(
        context: Context,
        sha256: String,
    ): Boolean {
        val normalizedHash = sha256.trim().lowercase(Locale.US)
        if (normalizedHash.isBlank()) return false
        return try {
            val prefs = context.dataStore.data.first()
            val today = todayString()
            val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY]
            if (storedDay != today) return false
            val hashes = prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
                .orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
            normalizedHash in hashes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isTrialQuotaAvailable(
        context: Context,
        sha256: String,
        dailyLimit: Int,
    ): Boolean {
        if (dailyLimit <= 0) return false
        return try {
            val prefs = context.dataStore.data.first()
            val today = todayString()
            val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY]
            val hashes = if (storedDay == today) {
                prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
                    .orEmpty()
                    .split(',')
                    .filter { it.isNotBlank() }
                    .toSet()
            } else {
                emptySet()
            }
            val normalizedHash = sha256.trim().lowercase(Locale.US)
            if (normalizedHash.isNotBlank() && normalizedHash in hashes) {
                true
            } else {
                hashes.size < dailyLimit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun recordTrialHashUsed(context: Context, sha256: String) {
        val normalizedHash = sha256.trim().lowercase(Locale.US)
        if (normalizedHash.isBlank()) return
        try {
            val today = todayString()
            context.dataStore.edit { prefs ->
                val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY]
                val currentSet = if (storedDay == today) {
                    prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
                        .orEmpty()
                        .split(',')
                        .filter { it.isNotBlank() }
                        .toMutableSet()
                } else {
                    mutableSetOf()
                }
                currentSet.add(normalizedHash)
                prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY] = today
                prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES] = currentSet.joinToString(",")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist VT trial hash usage")
        }
    }

    private fun todayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
