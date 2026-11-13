package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtEngineResult
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.model.toVtResult

/**
 * Overall safety classification for an analyzed APK.
 */
enum class ScannerVerdict {
    SAFE,
    RISKWARE,
    SUSPICIOUS,
    MALICIOUS,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ScannerVerdict = when (value?.uppercase()) {
            "MALICIOUS" -> MALICIOUS
            "SUSPICIOUS" -> SUSPICIOUS
            "RISKWARE" -> RISKWARE
            "SAFE" -> SAFE
            else -> UNKNOWN
        }
    }
}

/**
 * Severity level of an individual security finding.
 */
enum class ScannerSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;

    companion object {
        fun fromString(value: String?): ScannerSeverity = when (value?.uppercase()) {
            "CRITICAL" -> CRITICAL
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> INFO
        }
    }
}

/**
 * Specific security rule violation, suspicious behavior, or static finding.
 */
data class ScannerFinding(
    val scanner: String = "",
    val ruleId: String,
    val severity: ScannerSeverity,
    val scoreImpact: Int = 0,
    val title: String,
    val description: String,
    val evidence: String? = null,
)

/**
 * Per-antivirus-engine verdict and signature label (e.g. VirusTotal engines).
 */
data class EngineDetection(
    val engineName: String,
    val category: String, // "malicious", "suspicious", "harmless", "undetected"
    val result: String?,  // e.g. "Trojan.AndroidOS.Agent", or null if clean
    val method: String? = null,
)

/**
 * Unified result emitted by any security scanning provider.
 */
data class SecurityScanResult(
    val providerId: ScannerProviderId,
    val sha256: String,
    val verdict: ScannerVerdict,
    val riskScore: Int = 0,
    val findings: List<ScannerFinding> = emptyList(),
    val engineDetections: List<EngineDetection> = emptyList(),
    val maliciousCount: Int = 0,
    val suspiciousCount: Int = 0,
    val harmlessCount: Int = 0,
    val undetectedCount: Int = 0,
    val totalEngines: Int = 0,
    val permalink: String? = null,
    val scannedAtMillis: Long = System.currentTimeMillis(),
    val rawReport: ScanReport? = null,
)

/**
 * Bridges [SecurityScanResult] into legacy [VtResult] with 100% field compatibility.
 *
 * Guarantees that existing UI consumers (`VirusTotalCard`, `TvVirusTotalCard`,
 * `VirusTotalDetailSheet`, `SecurityFindingsSection`, `SecurityTab`) continue to render
 * seamlessly whether results originated from VirusDetec, VirusTotal, or Offline static scans.
 */
fun SecurityScanResult.toVtResult(): VtResult {
    // 1. If we have a native rawReport (e.g. from VirusDetec), use it directly for exact fidelity
    if (rawReport != null) {
        val vt = rawReport.toVtResult()
        return if (engineDetections.isNotEmpty() && vt.engineResults.isEmpty()) {
            vt.copy(
                engineResults = engineDetections.map { VtEngineResult(it.engineName, it.category, it.result) }
            )
        } else {
            vt
        }
    }

    // 2. Map verdict to legacy VtStatus
    val status = when (verdict) {
        ScannerVerdict.SAFE -> VtStatus.CLEAN
        ScannerVerdict.MALICIOUS -> VtStatus.MALICIOUS
        ScannerVerdict.SUSPICIOUS,
        ScannerVerdict.RISKWARE -> VtStatus.SUSPICIOUS
        ScannerVerdict.UNKNOWN -> {
            if (maliciousCount > 0) VtStatus.MALICIOUS
            else if (suspiciousCount > 0) VtStatus.SUSPICIOUS
            else VtStatus.CLEAN
        }
    }

    // 3. Resolve engine detections and findings
    val engineResults = when {
        engineDetections.isNotEmpty() -> engineDetections.map {
            VtEngineResult(
                engineName = it.engineName,
                category = it.category,
                result = it.result,
            )
        }
        findings.isNotEmpty() -> findings.map { finding ->
            VtEngineResult(
                engineName = finding.title,
                category = when (finding.severity) {
                    ScannerSeverity.CRITICAL, ScannerSeverity.HIGH -> "malicious"
                    ScannerSeverity.MEDIUM -> "suspicious"
                    else -> "harmless"
                },
                result = finding.description,
            )
        }
        else -> emptyList()
    }

    // 4. Resolve counts
    val effectiveMalicious = if (maliciousCount > 0) {
        maliciousCount
    } else {
        findings.count { it.severity == ScannerSeverity.CRITICAL || it.severity == ScannerSeverity.HIGH }
    }

    val effectiveSuspicious = if (suspiciousCount > 0) {
        suspiciousCount
    } else {
        findings.count { it.severity == ScannerSeverity.MEDIUM }
    }

    val effectiveHarmless = if (harmlessCount > 0) {
        harmlessCount
    } else if (findings.isEmpty() && engineDetections.isEmpty()) {
        70
    } else {
        (70 - (effectiveMalicious + effectiveSuspicious)).coerceAtLeast(0)
    }

    // 5. Construct synthetic ScanReport if findings exist but rawReport was null
    val syntheticReport: ScanReport? = if (findings.isNotEmpty()) {
        ScanReport(
            sha256 = sha256,
            verdict = when (verdict) {
                ScannerVerdict.SAFE -> Verdict.SAFE
                ScannerVerdict.RISKWARE -> Verdict.RISKWARE
                ScannerVerdict.SUSPICIOUS -> Verdict.SUSPICIOUS
                ScannerVerdict.MALICIOUS -> Verdict.MALICIOUS
                ScannerVerdict.UNKNOWN -> Verdict.SAFE
            },
            riskScore = riskScore,
            findings = findings.map { f ->
                Finding(
                    scanner = f.scanner,
                    ruleId = f.ruleId,
                    severity = when (f.severity) {
                        ScannerSeverity.CRITICAL -> SeverityLevel.CRITICAL
                        ScannerSeverity.HIGH -> SeverityLevel.HIGH
                        ScannerSeverity.MEDIUM -> SeverityLevel.MEDIUM
                        ScannerSeverity.LOW -> SeverityLevel.LOW
                        ScannerSeverity.INFO -> SeverityLevel.INFO
                    },
                    scoreImpact = f.scoreImpact,
                    title = f.title,
                    description = f.description,
                    evidence = f.evidence,
                )
            },
        )
    } else {
        null
    }

    return VtResult(
        malicious = effectiveMalicious,
        suspicious = effectiveSuspicious,
        harmless = effectiveHarmless,
        undetected = undetectedCount,
        status = status,
        errorMessage = "",
        uploadProgress = 100,
        analysisId = sha256,
        engineResults = engineResults,
        report = syntheticReport,
    )
}

/**
 * Converts a legacy [ScanReport] to [SecurityScanResult].
 */
fun ScanReport.toSecurityScanResult(
    providerId: ScannerProviderId = ScannerProviderId.VIRUSDETEC,
): SecurityScanResult {
    val mappedVerdict = when (verdict) {
        Verdict.SAFE -> ScannerVerdict.SAFE
        Verdict.RISKWARE -> ScannerVerdict.RISKWARE
        Verdict.SUSPICIOUS -> ScannerVerdict.SUSPICIOUS
        Verdict.MALICIOUS -> ScannerVerdict.MALICIOUS
    }

    val mappedFindings = findings.map { f ->
        ScannerFinding(
            scanner = f.scanner,
            ruleId = f.ruleId,
            severity = when (f.severity) {
                SeverityLevel.CRITICAL -> ScannerSeverity.CRITICAL
                SeverityLevel.HIGH -> ScannerSeverity.HIGH
                SeverityLevel.MEDIUM -> ScannerSeverity.MEDIUM
                SeverityLevel.LOW -> ScannerSeverity.LOW
                SeverityLevel.INFO -> ScannerSeverity.INFO
            },
            scoreImpact = f.scoreImpact,
            title = f.title,
            description = f.description,
            evidence = f.evidence,
        )
    }

    val critOrHigh = mappedFindings.count { it.severity == ScannerSeverity.CRITICAL || it.severity == ScannerSeverity.HIGH }
    val med = mappedFindings.count { it.severity == ScannerSeverity.MEDIUM }

    return SecurityScanResult(
        providerId = providerId,
        sha256 = sha256,
        verdict = mappedVerdict,
        riskScore = riskScore,
        findings = mappedFindings,
        maliciousCount = critOrHigh,
        suspiciousCount = med,
        harmlessCount = (70 - mappedFindings.size).coerceAtLeast(0),
        totalEngines = 70,
        rawReport = this,
    )
}
