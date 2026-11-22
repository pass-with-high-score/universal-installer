package app.pwhs.universalinstaller.domain.pro

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.DeviceIdProvider
import app.pwhs.core.network.TrialStatusResponse
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalQuotaStore
import app.pwhs.universalinstaller.domain.stats.MilestoneId
import kotlinx.coroutines.flow.first
import timber.log.Timber

object ProTrialManager {

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
        VirusTotalKeyManager.ensureRemoteConfigFetched()

        val newUserEnabled = VirusTotalKeyManager.isNewUserProTrialEnabled()
        val milestoneEnabled = VirusTotalKeyManager.isMilestoneProTrialEnabled()
        val service = createService(context)

        // 1. Always sync authoritative server state first (even if Remote Config disabled new grants)
        val serverStatus = service.getTrialStatus().getOrNull()
        if (serverStatus != null) {
            applyServerStatus(context, serverStatus)
        }

        if (!newUserEnabled && !milestoneEnabled) {
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
        VirusTotalKeyManager.ensureRemoteConfigFetched()
        if (!VirusTotalKeyManager.isMilestoneProTrialEnabled()) return
        val hours = VirusTotalKeyManager.getMilestoneProTrialHours().coerceIn(1, 72)
        if (hours <= 0) return

        val service = createService(context)
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val localClaimed = prefs?.get(SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES).orEmpty()

        for (milestone in newlyUnlocked) {
            if (milestone.name in localClaimed) continue
            service.claimTrial(
                claimType = "milestone",
                durationHours = hours,
                milestoneId = milestone.name,
            ).onSuccess { claimRes ->
                applyServerStatus(context, claimRes)
                Timber.i("ProTrialManager: Backend claim milestone=${milestone.name} granted=${claimRes.granted}, expiresAt=${claimRes.trialExpiresAt}")
            }.onFailure { err ->
                Timber.w(err, "ProTrialManager: Backend milestone trial claim failed for ${milestone.name}")
            }
        }
    }
}
