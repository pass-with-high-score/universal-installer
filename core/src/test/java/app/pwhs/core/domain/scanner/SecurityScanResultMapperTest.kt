package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScanResultMapperTest {

    @Test
    fun toVtResult_withRawReport_preservesNativeReport() {
        val rawReport = ScanReport(
            sha256 = "test_sha256",
            verdict = Verdict.SAFE,
            riskScore = 0,
            findings = emptyList(),
        )
        val result = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = "test_sha256",
            verdict = ScannerVerdict.SAFE,
            rawReport = rawReport,
        )

        val vt = result.toVtResult()

        assertEquals(rawReport, vt.report)
        assertEquals(VtStatus.CLEAN, vt.status)
        assertEquals("test_sha256", vt.analysisId)
    }

    @Test
    fun toVtResult_withoutRawReport_constructsAccurateVtResult() {
        val findings = listOf(
            ScannerFinding(
                scanner = "test_scanner",
                ruleId = "rule_1",
                severity = ScannerSeverity.CRITICAL,
                title = "Dangerous Accessibility Service",
                description = "Can intercept sensitive input",
            ),
            ScannerFinding(
                scanner = "test_scanner",
                ruleId = "rule_2",
                severity = ScannerSeverity.MEDIUM,
                title = "Overlay Permission",
                description = "Can draw overlays",
            ),
        )
        val result = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "hash_456",
            verdict = ScannerVerdict.MALICIOUS,
            riskScore = 80,
            findings = findings,
            maliciousCount = 1,
            suspiciousCount = 1,
            harmlessCount = 68,
        )

        val vt = result.toVtResult()

        assertEquals(VtStatus.MALICIOUS, vt.status)
        assertEquals(1, vt.malicious)
        assertEquals(1, vt.suspicious)
        assertEquals(68, vt.harmless)
        assertEquals(2, vt.engineResults.size)
        assertEquals("Dangerous Accessibility Service", vt.engineResults[0].engineName)
        assertEquals("malicious", vt.engineResults[0].category)
        assertNotNull(vt.report)
        assertEquals(Verdict.MALICIOUS, vt.report?.verdict)
    }

    @Test
    fun toSecurityScanResult_convertsScanReportAccurately() {
        val report = ScanReport(
            sha256 = "sha256_789",
            verdict = Verdict.MALICIOUS,
            riskScore = 95,
            findings = listOf(
                Finding(
                    scanner = "vd",
                    ruleId = "r1",
                    severity = SeverityLevel.HIGH,
                    scoreImpact = 40,
                    title = "Trojan signature",
                    description = "Detected Android trojan",
                    evidence = "Lcom/malware/agent;",
                )
            ),
        )

        val securityResult = report.toSecurityScanResult(providerId = ScannerProviderId.VIRUSDETEC)

        assertEquals(ScannerProviderId.VIRUSDETEC, securityResult.providerId)
        assertEquals("sha256_789", securityResult.sha256)
        assertEquals(ScannerVerdict.MALICIOUS, securityResult.verdict)
        assertEquals(95, securityResult.riskScore)
        assertEquals(1, securityResult.findings.size)
        assertEquals(ScannerSeverity.HIGH, securityResult.findings[0].severity)
        assertEquals("Trojan signature", securityResult.findings[0].title)
        assertEquals("Lcom/malware/agent;", securityResult.findings[0].evidence)
        assertEquals(report, securityResult.rawReport)
    }

    @Test
    fun scanStep_toVtResult_mapsAllStepTypesAccurately() {
        val hashing = ScanStep.Hashing(50).toVtResult("hash_1")
        assertEquals(VtStatus.SCANNING, hashing.status)
        assertEquals("hash_1", hashing.analysisId)

        val uploading = ScanStep.Uploading(75, 750L, 1000L).toVtResult("hash_1")
        assertEquals(VtStatus.UPLOADING, uploading.status)
        assertEquals(75, uploading.uploadProgress)

        val queued = ScanStep.Queued(queuePosition = 2, message = "In queue", analysisId = "analysis_99").toVtResult()
        assertEquals(VtStatus.QUEUED, queued.status)
        assertEquals("In queue", queued.errorMessage)
        assertEquals("analysis_99", queued.analysisId)

        val analyzing = ScanStep.Analyzing(60, "Scanning heuristics").toVtResult("hash_1")
        assertEquals(VtStatus.ANALYZING, analyzing.status)
        assertEquals("Scanning heuristics", analyzing.errorMessage)

        val fallback = ScanStep.FallbackTriggered(
            primaryError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC),
            fallbackProvider = ScannerProviderId.VIRUSTOTAL,
        ).toVtResult("hash_1")
        assertEquals(VtStatus.SCANNING, fallback.status)
        assertTrue(fallback.errorMessage.contains("Fallback to VirusTotal"))
    }

    @Test
    fun scanError_toVtResult_mapsAllErrorTypesAccurately() {
        val noKey = ScanError.NoApiKey(ScannerProviderId.VIRUSTOTAL).toVtResult("hash_1")
        assertEquals(VtStatus.NO_API_KEY, noKey.status)

        val invalidKey = ScanError.InvalidApiKey(ScannerProviderId.VIRUSTOTAL).toVtResult("hash_1")
        assertEquals(VtStatus.INVALID_API_KEY, invalidKey.status)

        val rateLimited = ScanError.RateLimited(ScannerProviderId.VIRUSTOTAL, retryAfterSeconds = 60).toVtResult("hash_1")
        assertEquals(VtStatus.RATE_LIMITED, rateLimited.status)
        assertEquals("60", rateLimited.errorMessage)

        val tooLarge = ScanError.FileTooLarge(ScannerProviderId.VIRUSTOTAL, 700L * 1024 * 1024, 650L * 1024 * 1024).toVtResult("hash_1")
        assertEquals(VtStatus.TOO_LARGE, tooLarge.status)

        val notFound = ScanError.NotFound(ScannerProviderId.VIRUSDETEC, "hash_1").toVtResult("hash_1")
        assertEquals(VtStatus.NOT_FOUND, notFound.status)
    }
}
