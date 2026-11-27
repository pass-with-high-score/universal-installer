package app.pwhs.core.network

data class RateLimitInfo(
    val limit: Int? = null,
    val remaining: Int? = null,
    val reset: Long? = null,
)

data class ServerTimeResponse(
    val timestamp: Long,
    val iso: String,
    val dateUtc: String,
)

data class TrialStatusResponse(
    val granted: Boolean = false,
    val newUserTrialClaimed: Boolean = false,
    val claimedMilestones: Set<String> = emptySet(),
    val trialExpiresAt: Long = 0L,
    val serverTime: Long = 0L,
    val isTrialActive: Boolean = false,
)

data class VirusDetecApiException(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String,
    val rateLimit: RateLimitInfo? = null,
) : RuntimeException(message)
