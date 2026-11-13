package app.pwhs.core.network

data class VtQuotaStatus(
    val hasPersonalKey: Boolean = false,
    val isProUser: Boolean = false,
    val isFallbackEnabled: Boolean = false,
    val usedToday: Int = 0,
    val dailyLimit: Int = VirusTotalKeyManager.DEFAULT_TRIAL_DAILY_LIMIT,
) {
    val isUnlimited: Boolean get() = hasPersonalKey || isProUser
    val remainingToday: Int get() = (dailyLimit - usedToday).coerceAtLeast(0)
    val isQuotaExhausted: Boolean get() = !isUnlimited && (!isFallbackEnabled || remainingToday <= 0)
}

interface VtRemoteConfigSource {
    suspend fun ensureFetched()
    fun isFallbackEnabled(): Boolean
    fun getRawKeys(): String
    fun getDailyLimit(): Int
}

object NoOpVtRemoteConfigSource : VtRemoteConfigSource {
    override suspend fun ensureFetched() = Unit
    override fun isFallbackEnabled(): Boolean = false
    override fun getRawKeys(): String = ""
    override fun getDailyLimit(): Int = 0
}
