package app.pwhs.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

/**
 * Provides a persistent, privacy-preserving client device identifier.
 * Uses SHA-256 hash of Settings.Secure.ANDROID_ID or falls back to a persistent random UUID.
 */
object DeviceIdProvider {
    @Volatile
    private var cachedId: String? = null

    @SuppressLint("HardwareIds")
    suspend fun getDeviceId(context: Context): String {
        cachedId?.let { return it }

        val prefs = context.dataStore.data.first()
        val storedId = prefs[SharedPrefsKeys.ANONYMOUS_DEVICE_ID]
        if (!storedId.isNullOrBlank()) {
            cachedId = storedId
            return storedId
        }

        val rawAndroidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val generatedId = if (!rawAndroidId.isNullOrBlank() && rawAndroidId != "9774d56d682e549c") {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawAndroidId.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }

        runCatching {
            context.dataStore.edit { it[SharedPrefsKeys.ANONYMOUS_DEVICE_ID] = generatedId }
        }

        cachedId = generatedId
        return generatedId
    }
}
