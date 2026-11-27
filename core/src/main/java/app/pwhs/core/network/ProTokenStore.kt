package app.pwhs.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

data class ProVerifyResponse(
    val proToken: String,
    val expiresAt: Long,
    val orderId: String = "",
    val productId: String = "",
)

/**
 * Persists and retrieves the HMAC-signed `X-Pro-Token` JWT issued by the VirusDetec backend
 * after verifying a Google Play purchase receipt.
 */
object ProTokenStore {
    private const val REFRESH_BUFFER_MS = 7L * 24 * 60 * 60 * 1000 // Refresh within 7 days of expiry

    suspend fun getValidToken(context: Context): String? {
        return try {
            val prefs = context.dataStore.data.first()
            val token = prefs[SharedPrefsKeys.PRO_TOKEN]?.trim().orEmpty()
            if (token.isBlank()) return null
            val expiresAt = prefs[SharedPrefsKeys.PRO_TOKEN_EXPIRES_AT] ?: 0L
            if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
                return null
            }
            token
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun needsRefresh(context: Context): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            val token = prefs[SharedPrefsKeys.PRO_TOKEN]?.trim().orEmpty()
            if (token.isBlank()) return true
            val expiresAt = prefs[SharedPrefsKeys.PRO_TOKEN_EXPIRES_AT] ?: 0L
            expiresAt <= 0L || (System.currentTimeMillis() + REFRESH_BUFFER_MS) >= expiresAt
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    suspend fun saveToken(context: Context, token: String, expiresAt: Long) {
        if (token.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[SharedPrefsKeys.PRO_TOKEN] = token.trim()
            prefs[SharedPrefsKeys.PRO_TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun clearToken(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(SharedPrefsKeys.PRO_TOKEN)
            prefs.remove(SharedPrefsKeys.PRO_TOKEN_EXPIRES_AT)
        }
    }
}
