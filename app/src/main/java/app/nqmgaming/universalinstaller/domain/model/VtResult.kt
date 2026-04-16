package app.nqmgaming.universalinstaller.domain.model

enum class VtStatus {
    CLEAN,
    MALICIOUS,
    SUSPICIOUS,
    NOT_FOUND,
    NO_API_KEY,
    ERROR,
    SCANNING,
}

data class VtResult(
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val undetected: Int = 0,
    val status: VtStatus = VtStatus.NO_API_KEY,
    val errorMessage: String = "",
)
