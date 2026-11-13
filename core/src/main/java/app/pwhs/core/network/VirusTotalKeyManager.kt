package app.pwhs.core.network

import android.content.Context
import android.os.SystemClock
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object VirusTotalKeyManager {

    const val RC_KEY_FALLBACK_ENABLED = "virustotal_fallback_enabled"
    const val RC_KEY_FALLBACK_KEYS = "virustotal_fallback_keys"
    const val RC_KEY_TRIAL_DAILY_LIMIT = "virustotal_trial_daily_limit"
    const val DEFAULT_TRIAL_DAILY_LIMIT = 3

    private val VT_KEY_REGEX = Regex("""\b[0-9a-fA-F]{64}\b""")
    internal const val RATE_LIMIT_COOLDOWN_MS = 65_000L
    internal const val INVALID_KEY_COOLDOWN_MS = 3_600_000L
    private const val MAX_ROTATION_ATTEMPTS = 4

    @Volatile
    private var remoteSource: VtRemoteConfigSource = NoOpVtRemoteConfigSource

    private val keyCooldownUntilMs = ConcurrentHashMap<String, Long>()

    internal var clock: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun install(source: VtRemoteConfigSource) {
        remoteSource = source
    }

    internal fun clearCooldowns() {
        keyCooldownUntilMs.clear()
    }

    internal fun resetForTesting() {
        keyCooldownUntilMs.clear()
        remoteSource = NoOpVtRemoteConfigSource
        clock = {
            try {
                SystemClock.elapsedRealtime()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

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

    fun observeQuotaStatus(context: Context): Flow<VtQuotaStatus> {
        return context.dataStore.data.map { prefs ->
            val hasPersonalKey = !prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY]?.trim().isNullOrBlank()
            val isPro = prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
            val usedToday = VirusTotalQuotaStore.parseScannedHashesToday(prefs).size
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

    suspend fun canUseVirusTotal(context: Context, sha256: String = ""): Boolean {
        if (readUserApiKey(context).isNotBlank()) return true
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return false
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return false
        if (isProUser(context)) return true
        return VirusTotalQuotaStore.isTrialQuotaAvailable(context, sha256, remoteSource.getDailyLimit())
    }

    suspend fun shouldAutoLookupHash(context: Context, sha256: String): Boolean {
        if (readUserApiKey(context).isNotBlank()) return true
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return false
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return false
        if (isProUser(context)) return true
        return VirusTotalQuotaStore.isHashAlreadyScannedToday(context, sha256)
    }

    suspend fun checkFile(context: Context, service: VirusTotalService, sha256: String): VtResult {
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
                        VirusTotalQuotaStore.recordTrialHashUsed(context, sha256)
                    }
                    return result
                }
            }
        }
        return lastResult
    }

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
            return executeUploadAndPollWithSingleKey(userKey, service, file, onUploadProgress, onQueued, onPollProgress)
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
                        lastFailureResult = VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                        continue
                    }
                    VtStatus.INVALID_API_KEY -> {
                        markCooldown(candidateKey, INVALID_KEY_COOLDOWN_MS)
                        lastFailureResult = VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                        continue
                    }
                    else -> return VtResult(status = err.vtStatus, errorMessage = err.message.orEmpty())
                }
            } else {
                return VtResult(status = VtStatus.ERROR, errorMessage = err?.message ?: "Upload failed")
            }
        }

        val activeKey = workingKey ?: return (lastFailureResult ?: VtResult(status = VtStatus.NO_API_KEY))
        val activeAnalysisId = analysisId ?: return (lastFailureResult ?: VtResult(status = VtStatus.NO_API_KEY))

        VirusTotalQuotaStore.recordTrialHashUsed(context, sha256)
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
        onUploadProgress(0)
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

    internal suspend fun resolveTrialCandidateKeys(context: Context, sha256: String = ""): List<String> {
        remoteSource.ensureFetched()
        if (!remoteSource.isFallbackEnabled()) return emptyList()
        val pool = parseKeyPool(remoteSource.getRawKeys())
        if (pool.isEmpty()) return emptyList()
        val dailyLimit = remoteSource.getDailyLimit()
        if (!isProUser(context) && !VirusTotalQuotaStore.isTrialQuotaAvailable(context, sha256, dailyLimit)) {
            return emptyList()
        }

        val now = clock()
        val readyKeys = pool.filter { key -> (keyCooldownUntilMs[key] ?: 0L) <= now }.shuffled()
        if (readyKeys.isEmpty()) {
            Timber.w("All keys in VirusTotal fallback pool are currently on cooldown")
            return emptyList()
        }
        return readyKeys.take(MAX_ROTATION_ATTEMPTS.coerceAtMost(readyKeys.size))
    }

    internal fun markCooldown(key: String, durationMs: Long) {
        keyCooldownUntilMs[key] = clock() + durationMs
    }

    internal fun isOnCooldown(key: String): Boolean {
        val until = keyCooldownUntilMs[key] ?: return false
        return clock() < until
    }

    internal fun getKeyCooldownUntilMs(key: String): Long? = keyCooldownUntilMs[key]
}
