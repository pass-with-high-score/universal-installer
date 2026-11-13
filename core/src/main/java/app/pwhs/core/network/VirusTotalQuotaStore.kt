package app.pwhs.core.network

import android.content.Context
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

object VirusTotalQuotaStore {

    fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun parseScannedHashesToday(prefs: Preferences, today: String = todayString()): Set<String> {
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
            context.dataStore.edit { prefs ->
                val current = parseScannedHashesToday(prefs, today).toMutableSet()
                current.add(normalized)
                prefs[SharedPrefsKeys.VT_TRIAL_USAGE_DAY] = today
                prefs[SharedPrefsKeys.VT_TRIAL_SCANNED_HASHES] = current.joinToString(",")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist VT trial hash usage")
        }
    }

    fun parseAppShieldHashesToday(prefs: Preferences, today: String = todayString()): Set<String> {
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
            context.dataStore.edit { prefs ->
                val current = parseAppShieldHashesToday(prefs, today).toMutableSet()
                current.add(normalized)
                prefs[SharedPrefsKeys.APPSHIELD_USAGE_DAY] = today
                prefs[SharedPrefsKeys.APPSHIELD_SCANNED_HASHES] = current.joinToString(",")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist AppShield hash usage")
        }
    }
}
