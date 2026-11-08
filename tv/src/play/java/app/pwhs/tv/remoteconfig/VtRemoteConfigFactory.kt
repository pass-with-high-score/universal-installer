package app.pwhs.tv.remoteconfig

import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VtRemoteConfigSource
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

fun createVtRemoteConfigSource(isDebug: Boolean): VtRemoteConfigSource =
    FirebaseVtRemoteConfigSource(isDebug)

private class FirebaseVtRemoteConfigSource(isDebug: Boolean) : VtRemoteConfigSource {

    private val remoteConfig: FirebaseRemoteConfig? = runCatching {
        FirebaseRemoteConfig.getInstance()
    }.onFailure {
        Timber.w(it, "FirebaseRemoteConfig unavailable on TV")
    }.getOrNull()

    @Volatile
    private var fetchAttempted = false

    init {
        remoteConfig?.let { rc ->
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (isDebug) 60L else 3600L)
                .setFetchTimeoutInSeconds(10L)
                .build()
            rc.setConfigSettingsAsync(settings)
            rc.setDefaultsAsync(
                mapOf(
                    VirusTotalKeyManager.RC_KEY_FALLBACK_ENABLED to true,
                    VirusTotalKeyManager.RC_KEY_FALLBACK_KEYS to "",
                    VirusTotalKeyManager.RC_KEY_TRIAL_DAILY_LIMIT to VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT.toLong(),
                )
            )
            rc.fetchAndActivate().addOnCompleteListener {
                fetchAttempted = true
            }
        }
    }

    override suspend fun ensureFetched() {
        val rc = remoteConfig ?: return
        if (fetchAttempted || rc.getString(VirusTotalKeyManager.RC_KEY_FALLBACK_KEYS).isNotBlank()) {
            return
        }
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                rc.fetchAndActivate().addOnCompleteListener {
                    fetchAttempted = true
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    override fun isFallbackEnabled(): Boolean {
        return runCatching {
            remoteConfig?.getBoolean(VirusTotalKeyManager.RC_KEY_FALLBACK_ENABLED) ?: false
        }.getOrDefault(false)
    }

    override fun getRawKeys(): String {
        return runCatching {
            remoteConfig?.getString(VirusTotalKeyManager.RC_KEY_FALLBACK_KEYS).orEmpty()
        }.getOrDefault("")
    }

    override fun getDailyLimit(): Int {
        return runCatching {
            remoteConfig?.getLong(VirusTotalKeyManager.RC_KEY_TRIAL_DAILY_LIMIT)?.toInt()
                ?: VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT
        }.getOrDefault(VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT)
    }
}
