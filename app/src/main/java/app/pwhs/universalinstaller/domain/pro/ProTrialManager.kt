package app.pwhs.universalinstaller.domain.pro

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.DeviceIdProvider
import app.pwhs.core.network.TrialStatusResponse
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalQuotaStore
import app.pwhs.universalinstaller.domain.stats.MilestoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

data class MilestoneRewardTier(
    val tierId: String,
    val requiredUnlocked: Int,
    val hoursMultiplier: Int,
) {
    fun durationHours(baseHours: Int): Int =
        (baseHours.coerceAtLeast(1) * hoursMultiplier).coerceIn(1, 72)
}

object ProTrialManager {

    val REWARD_TIERS: List<MilestoneRewardTier> = listOf(
        MilestoneRewardTier(tierId = "TIER_2", requiredUnlocked = 2, hoursMultiplier = 1),
        MilestoneRewardTier(tierId = "TIER_5", requiredUnlocked = 5, hoursMultiplier = 1),
        MilestoneRewardTier(tierId = "TIER_7", requiredUnlocked = 7, hoursMultiplier = 2),
        MilestoneRewardTier(tierId = "TIER_10", requiredUnlocked = 10, hoursMultiplier = 2),
        MilestoneRewardTier(tierId = "TIER_15", requiredUnlocked = 15, hoursMultiplier = 3),
    )

    private const val SYNC_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = AtomicBoolean(false)

    @Volatile
    private var lastSyncElapsedMs: Long = 0L

    fun triggerTrialSyncAsync(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastSyncElapsedMs > 0L && (now - lastSyncElapsedMs) < SYNC_COOLDOWN_MS) return
        if (!isSyncing.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        syncScope.launch {
            try {
                checkAndGrantNewUserTrialInternal(appContext)
            } finally {
                isSyncing.set(false)
            }
        }
    }

    private fun createService(context: Context): VirusDetecService {
        return VirusDetecService(
            deviceIdProvider = { DeviceIdProvider.getDeviceId(context) }
        )
    }

    private suspend fun applyServerStatus(context: Context, status: TrialStatusResponse) {
        if (status.serverTime > 0L) {
            VirusTotalQuotaStore.syncServerTime(status.serverTime)
        }
        val now = if (status.serverTime > 0L) status.serverTime else VirusTotalQuotaStore.currentTrueTimeMillis()
        val isActive = status.isTrialActive && status.trialExpiresAt > now
        context.dataStore.edit { prefs ->
            prefs[SharedPrefsKeys.NEW_USER_TRIAL_CLAIMED] = status.newUserTrialClaimed
            if (status.claimedMilestones.isNotEmpty()) {
                prefs[SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES] = status.claimedMilestones
            } else {
                prefs.remove(SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES)
            }
            if (isActive) {
                prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] = status.trialExpiresAt
            } else {
                prefs.remove(SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP)
            }
        }
    }

    suspend fun checkAndGrantNewUserTrial(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastSyncElapsedMs > 0L && (now - lastSyncElapsedMs) < SYNC_COOLDOWN_MS) return
        if (!isSyncing.compareAndSet(false, true)) return
        try {
            checkAndGrantNewUserTrialInternal(context.applicationContext)
        } finally {
            isSyncing.set(false)
        }
    }

    private suspend fun checkAndGrantNewUserTrialInternal(context: Context) {
        VirusTotalKeyManager.ensureRemoteConfigFetched()

        val newUserEnabled = VirusTotalKeyManager.isNewUserProTrialEnabled()
        val milestoneEnabled = VirusTotalKeyManager.isMilestoneProTrialEnabled()

        if (!newUserEnabled && !milestoneEnabled) {
            lastSyncElapsedMs = SystemClock.elapsedRealtime()
            runCatching {
                context.dataStore.edit { prefs ->
                    if ((prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L) > 0L) {
                        prefs.remove(SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP)
                        Timber.i("ProTrialManager: Cleared active trial because Remote Config disabled trials")
                    }
                }
            }
            return
        }

        val service = createService(context)

        // 1. Always sync authoritative server state first
        val serverStatus = service.getTrialStatus().getOrNull()
        if (serverStatus != null) {
            lastSyncElapsedMs = SystemClock.elapsedRealtime()
            applyServerStatus(context, serverStatus)
        }

        if (serverStatus != null) {
            if (serverStatus.newUserTrialClaimed || !newUserEnabled) {
                return
            }
        } else if (!newUserEnabled) {
            return
        }

        val days = VirusTotalKeyManager.getNewUserProTrialDays()
        if (days <= 0) return
        val durationHours = (days * 24).coerceIn(1, 168)

        // 2. Claim new user trial via authoritative backend endpoint
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        if (prefs?.get(SharedPrefsKeys.NEW_USER_TRIAL_CLAIMED) == true) return

        service.claimTrial(
            claimType = "new_user",
            durationHours = durationHours,
        ).onSuccess { claimRes ->
            applyServerStatus(context, claimRes)
            Timber.i("ProTrialManager: Backend claim new_user granted=${claimRes.granted}, expiresAt=${claimRes.trialExpiresAt}")
        }.onFailure { err ->
            Timber.w(err, "ProTrialManager: Backend new_user trial claim failed (skipping offline grant to prevent Clear Data abuse)")
        }
    }

    suspend fun grantMilestoneTrial(context: Context, newlyUnlocked: List<MilestoneId>) {
        if (newlyUnlocked.isEmpty()) return
        // Legacy entry point retained for callers that don't pass total unlocked count.
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val unlockedCount = prefs?.get(
            androidx.datastore.preferences.core.stringSetPreferencesKey("stats_announced_milestones")
        )?.size ?: newlyUnlocked.size
        checkAndGrantMilestoneTierTrials(context, unlockedCount)
    }

    suspend fun checkAndGrantMilestoneTierTrials(context: Context, unlockedCount: Int) {
        if (unlockedCount < REWARD_TIERS.first().requiredUnlocked) return
        VirusTotalKeyManager.ensureRemoteConfigFetched()
        if (!VirusTotalKeyManager.isMilestoneProTrialEnabled()) return
        val baseHours = VirusTotalKeyManager.getMilestoneProTrialHours().coerceIn(1, 72)
        if (baseHours <= 0) return

        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val localClaimed = prefs?.get(SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES).orEmpty()
        val eligibleTiers = REWARD_TIERS.filter {
            unlockedCount >= it.requiredUnlocked && it.tierId !in localClaimed
        }
        if (eligibleTiers.isEmpty()) return

        val service = createService(context)
        for (tier in eligibleTiers) {
            val hours = tier.durationHours(baseHours)
            service.claimTrial(
                claimType = "milestone",
                durationHours = hours,
                milestoneId = tier.tierId,
            ).onSuccess { claimRes ->
                applyServerStatus(context, claimRes)
                Timber.i(
                    "ProTrialManager: Backend claim tier=${tier.tierId} (+${hours}h) granted=${claimRes.granted}, expiresAt=${claimRes.trialExpiresAt}"
                )
            }.onFailure { err ->
                Timber.w(err, "ProTrialManager: Backend tier trial claim failed for ${tier.tierId}")
            }
        }
    }

    suspend fun claimRewardTier(
        context: Context,
        tier: MilestoneRewardTier,
        unlockedCount: Int,
    ): Boolean {
        if (unlockedCount < tier.requiredUnlocked) return false
        VirusTotalKeyManager.ensureRemoteConfigFetched()
        if (!VirusTotalKeyManager.isMilestoneProTrialEnabled()) return false
        val baseHours = VirusTotalKeyManager.getMilestoneProTrialHours().coerceIn(1, 72)
        if (baseHours <= 0) return false

        val hours = tier.durationHours(baseHours)
        val service = createService(context)
        return service.claimTrial(
            claimType = "milestone",
            durationHours = hours,
            milestoneId = tier.tierId,
        ).fold(
            onSuccess = { claimRes ->
                applyServerStatus(context, claimRes)
                Timber.i(
                    "ProTrialManager: Manual claim tier=${tier.tierId} (+${hours}h) granted=${claimRes.granted}"
                )
                claimRes.granted || tier.tierId in claimRes.claimedMilestones
            },
            onFailure = { err ->
                Timber.w(err, "ProTrialManager: Manual tier trial claim failed for ${tier.tierId}")
                false
            },
        )
    }
}
