package app.pwhs.core.network

import android.content.Context
import androidx.annotation.VisibleForTesting
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale

object VirusTotalKeyManager {

    const val RC_KEY_APPSHIELD_DAILY_LIMIT = "appshield_daily_free_limit"
    const val RC_KEY_NEW_USER_PRO_TRIAL_ENABLED = "new_user_pro_trial_enabled"
    const val RC_KEY_NEW_USER_PRO_TRIAL_DAYS = "new_user_pro_trial_days"
    const val RC_KEY_MILESTONE_PRO_TRIAL_ENABLED = "milestone_pro_trial_enabled"
    const val RC_KEY_MILESTONE_PRO_TRIAL_HOURS = "milestone_pro_trial_hours"
    const val DEFAULT_APPSHIELD_DAILY_LIMIT = 3
    const val DEFAULT_TRIAL_DAILY_LIMIT = DEFAULT_APPSHIELD_DAILY_LIMIT

    fun isNewUserProTrialEnabled(): Boolean = remoteSource.isNewUserProTrialEnabled()
    fun getNewUserProTrialDays(): Int = remoteSource.getNewUserProTrialDays()
    fun isMilestoneProTrialEnabled(): Boolean = remoteSource.isMilestoneProTrialEnabled()
    fun getMilestoneProTrialHours(): Int = remoteSource.getMilestoneProTrialHours()
    suspend fun ensureRemoteConfigFetched() = remoteSource.ensureFetched()

    private val VT_KEY_REGEX = Regex("""\b[0-9a-fA-F]{64}\b""")

    @Volatile
    private var remoteSource: VtRemoteConfigSource = NoOpVtRemoteConfigSource

    @set:VisibleForTesting
    internal var userApiKeyOverride: String? = null

    fun install(source: VtRemoteConfigSource) {
        remoteSource = source
    }

    internal fun resetForTesting() {
        remoteSource = NoOpVtRemoteConfigSource
        userApiKeyOverride = null
    }

    fun parseKeyPool(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return VT_KEY_REGEX.findAll(raw)
            .map { it.value.lowercase(Locale.US) }
            .distinct()
            .toList()
    }

    suspend fun readUserApiKey(context: Context): String {
        userApiKeyOverride?.let { return it }
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
            if (prefs[SharedPrefsKeys.IS_PRO_USER] == true) return true
            val trialExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
            trialExp > VirusTotalQuotaStore.currentTrueTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun observeQuotaStatus(context: Context): Flow<VtQuotaStatus> {
        return context.dataStore.data.map { prefs ->
            val hasPersonalKey = !prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY]?.trim().isNullOrBlank()
            val isPurchasedPro = prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
            val trialExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
            val isPro = isPurchasedPro || (trialExp > VirusTotalQuotaStore.currentTrueTimeMillis())
            val usedToday = VirusTotalQuotaStore.parseAppShieldHashesToday(prefs).size
            val limit = remoteSource.getDailyLimit().takeIf { it > 0 } ?: DEFAULT_TRIAL_DAILY_LIMIT
            VtQuotaStatus(
                hasPersonalKey = hasPersonalKey,
                isProUser = isPro,
                isFallbackEnabled = false,
                usedToday = usedToday,
                dailyLimit = limit,
            )
        }
    }

    suspend fun canUseVirusTotal(context: Context, sha256: String = ""): Boolean {
        return readUserApiKey(context).isNotBlank()
    }

    suspend fun shouldAutoLookupHash(context: Context, sha256: String): Boolean {
        return readUserApiKey(context).isNotBlank()
    }

    suspend fun checkFile(context: Context, service: VirusTotalService, sha256: String): VtResult {
        if (sha256.isBlank()) {
            return VtResult(status = VtStatus.ERROR, errorMessage = "Could not hash file")
        }
        val userKey = readUserApiKey(context)
        if (userKey.isBlank()) {
            return VtResult(status = VtStatus.NO_API_KEY)
        }
        return service.checkFile(userKey, sha256)
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
        if (userKey.isBlank()) {
            return VtResult(status = VtStatus.NO_API_KEY)
        }
        return executeUploadAndPollWithSingleKey(userKey, service, file, onUploadProgress, onQueued, onPollProgress)
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
}
