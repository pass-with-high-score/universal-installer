package app.pwhs.universalinstaller.domain.model

enum class VtStatus {
    CLEAN,
    MALICIOUS,
    SUSPICIOUS,
    NOT_FOUND,
    NO_API_KEY,
    ERROR,
    TOO_LARGE,
    SCANNING,    // hashing + hash lookup
    UPLOADING,   // posting bytes to VirusTotal
    QUEUED,      // VT received the file and queued it
    ANALYZING,   // VT engines are running
}

/**
 * Per-engine scan result from VirusTotal's `last_analysis_results`.
 * @param engineName  e.g. "Kaspersky", "Avira", "ClamAV"
 * @param category    one of "malicious", "suspicious", "harmless", "undetected", "type-unsupported", "timeout", "failure"
 * @param result      detection label when positive, e.g. "Trojan.GenericKD.12345"; null/blank when clean
 */
data class VtEngineResult(
    val engineName: String,
    val category: String,
    val result: String?,
)

data class VtResult(
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val undetected: Int = 0,
    val status: VtStatus = VtStatus.NO_API_KEY,
    val errorMessage: String = "",
    val uploadProgress: Int = 0,   // 0..100, meaningful only when status = UPLOADING
    val analysisId: String = "",
    val engineResults: List<VtEngineResult> = emptyList(),
)
