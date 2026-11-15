package app.pwhs.core.network

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object VirusTotalQuotaStore {

    @Volatile
    private var serverTimeOffsetMillis: Long? = null

    /**
     * Synchronizes monotonic clock with verified server timestamp from `/api/v1/time`.
     * Preserved across app runtime using [SystemClock.elapsedRealtime],
     * which prevents manipulation by changing device settings date/time.
     */
    fun syncServerTime(serverTimestampMillis: Long) {
        if (serverTimestampMillis > 0) {
            val monotonicBootTime = SystemClock.elapsedRealtime()
            serverTimeOffsetMillis = serverTimestampMillis - monotonicBootTime
        }
    }

    /**
     * Asynchronously attempts to sync server time using [VirusDetecService].
     * Non-blocking and fails gracefully if offline.
     */
    suspend fun syncWithServer(service: VirusDetecService) {
        service.getServerTime().onSuccess { timeRes ->
            syncServerTime(timeRes.timestamp)
        }.onFailure {
            Timber.d(it, "Could not sync server time for quota store, using monotonic rollback check")
        }
    }

    /**
     * Calculates the trusted timestamp in milliseconds:
     * - Uses [SystemClock.elapsedRealtime] + offset if server time was synced.
     * - Falls back to [System.currentTimeMillis] otherwise.
     */
    fun currentTrueTimeMillis(): Long {
        val offset = serverTimeOffsetMillis
        return if (offset != null) {
            SystemClock.elapsedRealtime() + offset
        } else {
            System.currentTimeMillis()
        }
    }

    /**
     * Checks whether date has been rolled backward by checking against the stored monotonic max timestamp.
     */
    fun isClockManipulated(prefs: Preferences): Boolean {
        val lastMax = prefs[SharedPrefsKeys.QUOTA_LAST_MAX_RECORDED_TIMESTAMP] ?: return false
        val current = currentTrueTimeMillis()
        // If current time is earlier than last recorded by more than 5 minutes (allow slight skew), user rolled back clock
        return (lastMax - current) > 300_000L
    }

    fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(currentTrueTimeMillis()))
    }

    fun parseScannedHashesToday(prefs: Preferences, today: String = todayString()): Set<String> {
        if (isClockManipulated(prefs)) {
            val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY] ?: today
            return prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
                .orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
        }
        val storedDay = prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY]
        if (storedDay != today) return emptySet()
        return prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES]
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun isHashAlreadyScannedToday(context: Context, sha256: String): Boolean {
        val normalized = sha256.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return false
        return try {
            val prefs = context.dataStore.data.first()
            normalized in parseScannedHashesToday(prefs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isTrialQuotaAvailable(context: Context, sha256: String, dailyLimit: Int): Boolean {
        if (dailyLimit <= 0) return false
        return try {
            val prefs = context.dataStore.data.first()
            val hashes = parseScannedHashesToday(prefs)
            val normalized = sha256.trim().lowercase(Locale.US)
            if (normalized.isNotBlank() && normalized in hashes) true else hashes.size < dailyLimit
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun recordTrialHashUsed(context: Context, sha256: String) {
        val normalized = sha256.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return
        try {
            val today = todayString()
            val now = currentTrueTimeMillis()
            context.dataStore.edit { prefs ->
                val current = parseScannedHashesToday(prefs, today).toMutableSet()
                current.add(normalized)
                prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY] = today
                prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES] = current.joinToString(",")
                val lastMax = prefs[SharedPrefsKeys.QUOTA_LAST_MAX_RECORDED_TIMESTAMP] ?: 0L
                if (now > lastMax) {
                    prefs[SharedPrefsKeys.QUOTA_LAST_MAX_RECORDED_TIMESTAMP] = now
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist VT trial hash usage")
        }
    }

    fun parseAppShieldHashesToday(prefs: Preferences, today: String = todayString()): Set<String> {
        if (isClockManipulated(prefs)) {
            val storedDay = prefs[SharedPrefsKeys.APPSHIELD_USAGE_DAY] ?: today
            return prefs[SharedPrefsKeys.APPSHIELD_SCANNED_HASHES]
                .orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
        }
        val storedDay = prefs[SharedPrefsKeys.APPSHIELD_USAGE_DAY]
        if (storedDay != today) return emptySet()
        return prefs[SharedPrefsKeys.APPSHIELD_SCANNED_HASHES]
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun recordAppShieldHashUsed(context: Context, sha256: String) {
        val normalized = sha256.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return
        try {
            val today = todayString()
            val now = currentTrueTimeMillis()
            context.dataStore.edit { prefs ->
                val current = parseAppShieldHashesToday(prefs, today).toMutableSet()
                current.add(normalized)
                prefs[SharedPrefsKeys.APPSHIELD_USAGE_DAY] = today
                prefs[SharedPrefsKeys.APPSHIELD_SCANNED_HASHES] = current.joinToString(",")
                val lastMax = prefs[SharedPrefsKeys.QUOTA_LAST_MAX_RECORDED_TIMESTAMP] ?: 0L
                if (now > lastMax) {
                    prefs[SharedPrefsKeys.QUOTA_LAST_MAX_RECORDED_TIMESTAMP] = now
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist AppShield hash usage")
        }
    }
}
