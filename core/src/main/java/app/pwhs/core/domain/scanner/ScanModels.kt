package app.pwhs.core.domain.scanner

/**
 * Metadata extracted from the target APK required for security inspection,
 * cloud lookup, heuristic analysis, and pre-checks.
 */
data class ScanApkMetadata(
    val sha256: String = "",
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val certSha256: String = "",
    val fileSizeBytes: Long = 0L,
    val permissions: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val dexHashes: List<String> = emptyList(),
)

/**
 * Execution parameters and behavior toggles for security scanning operations.
 *
 * @property forceRecheck Skip local/fast cache lookup and force server re-inspection if supported.
 * @property allowUpload If hash lookup misses, allow binary upload; if false, stop at not found.
 * @property preferredLanguage ISO-639-1 language code for localized scan rule descriptions (e.g., "vi", "en").
 * @property timeoutMillis Maximum timeout duration for full upload and analysis lifecycle.
 * @property enableFallback Allow orchestrator to attempt backup scanner if primary fails.
 * @property runOfflineChecks Concurrently execute offline static checks and merge findings.
 */
data class ScanOptions(
    val forceRecheck: Boolean = false,
    val allowUpload: Boolean = true,
    val preferredLanguage: String = "vi",
    val timeoutMillis: Long = 180_000L,
    val enableFallback: Boolean = true,
    val runOfflineChecks: Boolean = false,
)
