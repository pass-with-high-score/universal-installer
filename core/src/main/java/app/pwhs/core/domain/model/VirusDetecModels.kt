package app.pwhs.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SeverityLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;

    companion object {
        fun fromString(value: String?): SeverityLevel = when (value?.uppercase()) {
            "CRITICAL" -> CRITICAL
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> INFO
        }
    }
}

enum class Verdict {
    SAFE,
    RISKWARE,
    SUSPICIOUS,
    MALICIOUS;

    companion object {
        fun fromString(value: String?): Verdict = when (value?.uppercase()) {
            "MALICIOUS" -> MALICIOUS
            "SUSPICIOUS" -> SUSPICIOUS
            "RISKWARE" -> RISKWARE
            else -> SAFE
        }
    }
}

data class Finding(
    val scanner: String,
    val ruleId: String,
    val severity: SeverityLevel,
    val scoreImpact: Int = 0,
    val title: String,
    val description: String,
    val evidence: String? = null,
)

data class ScanReport(
    val sha256: String,
    val dexHashes: List<String> = emptyList(),
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val certSha256: String = "",
    val verdict: Verdict = Verdict.SAFE,
    val riskScore: Int = 0,
    val findings: List<Finding> = emptyList(),
    val scannerVersion: String = "1.2.1",
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class LookupRequest(
    val sha256: String,
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val certSha256: String = "",
    val permissions: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
)

data class LookupResponse(
    val cached: Boolean = false,
    val needUpload: Boolean = false,
    val report: ScanReport? = null,
    val precheckReport: ScanReport? = null,
)

data class UploadTicketResponse(
    val jobId: String,
    val uploadUrl: String,
    val method: String = "PUT",
    val contentType: String = "application/vnd.android.package-archive",
    val expiresInSeconds: Int = 900,
)

data class ScanStepUpdate(
    val jobId: String,
    val status: String,
    val stepCode: String = "",
    val progressPercent: Int = 0,
    val message: String = "",
    val errorCode: String? = null,
    val report: ScanReport? = null,
)

fun ScanReport.toVtResult(): VtResult {
    val status = when (verdict) {
        Verdict.SAFE -> VtStatus.CLEAN
        Verdict.MALICIOUS -> VtStatus.MALICIOUS
        Verdict.SUSPICIOUS,
        Verdict.RISKWARE -> VtStatus.SUSPICIOUS
    }

    val criticalOrHigh = findings.count { it.severity == SeverityLevel.CRITICAL || it.severity == SeverityLevel.HIGH }
    val medium = findings.count { it.severity == SeverityLevel.MEDIUM }

    val engineResults = findings.map { finding ->
        VtEngineResult(
            engineName = finding.title,
            category = when (finding.severity) {
                SeverityLevel.CRITICAL, SeverityLevel.HIGH -> "malicious"
                SeverityLevel.MEDIUM -> "suspicious"
                else -> "harmless"
            },
            result = finding.description,
        )
    }

    return VtResult(
        malicious = criticalOrHigh,
        suspicious = medium,
        harmless = if (findings.isEmpty()) 70 else (70 - findings.size).coerceAtLeast(0),
        undetected = 0,
        status = status,
        errorMessage = "",
        uploadProgress = 100,
        analysisId = sha256,
        engineResults = engineResults,
        report = this,
    )
}

@Serializable
data class FalsePositiveRequest(
    val sha256: String,
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_name") val appName: String = "",
    val reason: String,
    @SerialName("contact_email") val contactEmail: String = "",
)

@Serializable
data class FalsePositiveResponse(
    val status: String = "",
    val message: String = "",
    @SerialName("report_id") val reportId: String = "",
    val sha256: String = "",
)
