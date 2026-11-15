package app.pwhs.universalinstaller.domain.pro

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalQuotaStore
import app.pwhs.universalinstaller.domain.stats.MilestoneId
import timber.log.Timber

object ProTrialManager {

    suspend fun checkAndGrantNewUserTrial(context: Context) {
        if (!VirusTotalKeyManager.isNewUserProTrialEnabled()) return
        val days = VirusTotalKeyManager.getNewUserProTrialDays()
        if (days <= 0) return

        runCatching {
            context.dataStore.edit { prefs ->
                if (prefs[SharedPrefsKeys.NEW_USER_TRIAL_CLAIMED] != true) {
                    prefs[SharedPrefsKeys.NEW_USER_TRIAL_CLAIMED] = true
                    val now = VirusTotalQuotaStore.currentTrueTimeMillis()
                    val currentExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
                    val base = maxOf(now, currentExp)
                    val newExp = base + (days * 24L * 3600L * 1000L)
                    prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] = newExp
                    Timber.i("ProTrialManager: Granted $days days new user trial until $newExp")
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to grant new user Pro trial")
        }
    }

    suspend fun grantMilestoneTrial(context: Context, newlyUnlocked: List<MilestoneId>) {
        if (newlyUnlocked.isEmpty()) return
        if (!VirusTotalKeyManager.isMilestoneProTrialEnabled()) return
        val hours = VirusTotalKeyManager.getMilestoneProTrialHours()
        if (hours <= 0) return

        runCatching {
            context.dataStore.edit { prefs ->
                val claimed = prefs[SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES].orEmpty().toMutableSet()
                var addedHours = 0L
                for (milestone in newlyUnlocked) {
                    if (milestone.name !in claimed) {
                        claimed += milestone.name
                        addedHours += hours
                    }
                }
                if (addedHours > 0) {
                    prefs[SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES] = claimed
                    val now = VirusTotalQuotaStore.currentTrueTimeMillis()
                    val currentExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
                    val base = maxOf(now, currentExp)
                    val newExp = base + (addedHours * 3600L * 1000L)
                    prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] = newExp
                    Timber.i("ProTrialManager: Granted $addedHours hours trial for milestones until $newExp")
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to grant milestone Pro trial")
        }
    }
}
