package app.pwhs.core.domain.scanner

/**
 * Unique identifier for security scanning engines.
 *
 * @param id Stable string key used for persistence in DataStore / preferences.
 * @param displayName Human-readable label displayed in UI / settings.
 */
enum class ScannerProviderId(val id: String, val displayName: String) {
    VIRUSDETEC("virusdetec", "AppShield"),
    VIRUSTOTAL("virustotal", "VirusTotal"),
    OFFLINE_STATIC("offline_static", "Offline Static Analyzer"),
    MOCK("mock", "Mock Scanner");

    companion object {
        fun fromId(id: String?, default: ScannerProviderId = VIRUSDETEC): ScannerProviderId {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}

/**
 * Declares the capabilities and execution constraints of a scanner provider.
 *
 * @property supportsHashLookup Whether provider can verify known files by SHA-256 without upload.
 * @property supportsBinaryUpload Whether provider supports uploading full APK binaries for analysis.
 * @property requiresNetwork Whether provider requires active internet connection.
 * @property requiresApiKey Whether provider requires an API key before execution.
 * @property maxFileSizeBytes Maximum binary file size acceptable for scan.
 * @property supportsStreamingProgress Whether provider emits granular step/percentage updates.
 */
data class ScannerCapabilities(
    val supportsHashLookup: Boolean = true,
    val supportsBinaryUpload: Boolean = true,
    val requiresNetwork: Boolean = true,
    val requiresApiKey: Boolean = false,
    val maxFileSizeBytes: Long = 300L * 1024 * 1024, // 300 MB default
    val supportsStreamingProgress: Boolean = true,
)
