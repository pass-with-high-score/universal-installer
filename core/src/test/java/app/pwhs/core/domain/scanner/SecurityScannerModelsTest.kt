package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & 2 Tests for Unified Security Models, Enums, and Mappers.
 */
class SecurityScannerModelsTest {

    @Test
    fun testScannerVerdict_orderAndSeverityRanking() {
        val verdicts = ScannerVerdict.entries
        assertEquals(5, verdicts.size)
        assertTrue(verdicts.contains(ScannerVerdict.SAFE))
        assertTrue(verdicts.contains(ScannerVerdict.RISKWARE))
        assertTrue(verdicts.contains(ScannerVerdict.SUSPICIOUS))
        assertTrue(verdicts.contains(ScannerVerdict.MALICIOUS))
        assertTrue(verdicts.contains(ScannerVerdict.UNKNOWN))
    }

    @Test
    fun testScannerVerdict_fromString_caseInsensitiveAndDefaults() {
        assertEquals(ScannerVerdict.SAFE, ScannerVerdict.fromString("safe"))
        assertEquals(ScannerVerdict.SAFE, ScannerVerdict.fromString("SAFE"))
        assertEquals(ScannerVerdict.RISKWARE, ScannerVerdict.fromString("riskware"))
        assertEquals(ScannerVerdict.SUSPICIOUS, ScannerVerdict.fromString("suspicious"))
        assertEquals(ScannerVerdict.MALICIOUS, ScannerVerdict.fromString("malicious"))
        assertEquals(ScannerVerdict.UNKNOWN, ScannerVerdict.fromString("unknown"))
        assertEquals(ScannerVerdict.UNKNOWN, ScannerVerdict.fromString(null))
        assertEquals(ScannerVerdict.UNKNOWN, ScannerVerdict.fromString(""))
        assertEquals(ScannerVerdict.UNKNOWN, ScannerVerdict.fromString("invalid_verdict_string"))
    }

    @Test
    fun testScannerSeverity_rankingAndFromString() {
        assertEquals(ScannerSeverity.CRITICAL, ScannerSeverity.fromString("critical"))
        assertEquals(ScannerSeverity.HIGH, ScannerSeverity.fromString("high"))
        assertEquals(ScannerSeverity.MEDIUM, ScannerSeverity.fromString("medium"))
        assertEquals(ScannerSeverity.LOW, ScannerSeverity.fromString("low"))
        assertEquals(ScannerSeverity.INFO, ScannerSeverity.fromString("info"))
        assertEquals(ScannerSeverity.INFO, ScannerSeverity.fromString(null))
        assertEquals(ScannerSeverity.INFO, ScannerSeverity.fromString("gibberish"))
    }

    @Test
    fun testSecurityScanResult_toVtResult_cleanVerdictMapping() {
        val result = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = "test_sha256",
            verdict = ScannerVerdict.SAFE,
            riskScore = 0,
            maliciousCount = 0,
            suspiciousCount = 0,
            harmlessCount = 70,
            totalEngines = 70,
        )

        val vt = result.toVtResult()
        assertEquals(VtStatus.CLEAN, vt.status)
        assertEquals(0, vt.malicious)
        assertEquals(0, vt.suspicious)
        assertEquals(70, vt.harmless)
        assertEquals("test_sha256", vt.analysisId)
        assertNull(vt.report)
    }

    @Test
    fun testSecurityScanResult_toVtResult_maliciousVerdictMapping() {
        val detections = listOf(
            EngineDetection("Avast", "malicious", "Android:Dropper-AZ"),
            EngineDetection("Kaspersky", "malicious", "HEUR:Trojan.AndroidOS.Agent.a"),
        )
        val result = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "malicious_sha256",
            verdict = ScannerVerdict.MALICIOUS,
            riskScore = 85,
            engineDetections = detections,
            maliciousCount = 2,
            suspiciousCount = 0,
            harmlessCount = 68,
            totalEngines = 70,
        )

        val vt = result.toVtResult()
        assertEquals(VtStatus.MALICIOUS, vt.status)
        assertEquals(2, vt.malicious)
        assertEquals(0, vt.suspicious)
        assertEquals(68, vt.harmless)
        assertEquals(2, vt.engineResults.size)
        assertEquals("Avast", vt.engineResults[0].engineName)
        assertEquals("malicious", vt.engineResults[0].category)
        assertEquals("Android:Dropper-AZ", vt.engineResults[0].result)
    }

    @Test
    fun testSecurityScanResult_toVtResult_suspiciousAndRiskwareMapping() {
        val suspiciousResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = "suspicious_sha256",
            verdict = ScannerVerdict.SUSPICIOUS,
            riskScore = 45,
            suspiciousCount = 3,
        )
        assertEquals(VtStatus.SUSPICIOUS, suspiciousResult.toVtResult().status)

        val riskwareResult = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = "riskware_sha256",
            verdict = ScannerVerdict.RISKWARE,
            riskScore = 30,
        )
        assertEquals(VtStatus.SUSPICIOUS, riskwareResult.toVtResult().status)
    }

    @Test
    fun testSecurityScanResult_toVtResult_unknownVerdict_resolvedByCounts() {
        val unknownWithMalicious = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "sha1",
            verdict = ScannerVerdict.UNKNOWN,
            maliciousCount = 1,
        )
        assertEquals(VtStatus.MALICIOUS, unknownWithMalicious.toVtResult().status)

        val unknownWithSuspicious = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "sha2",
            verdict = ScannerVerdict.UNKNOWN,
            suspiciousCount = 2,
        )
        assertEquals(VtStatus.SUSPICIOUS, unknownWithSuspicious.toVtResult().status)

        val unknownClean = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "sha3",
            verdict = ScannerVerdict.UNKNOWN,
            maliciousCount = 0,
            suspiciousCount = 0,
        )
        assertEquals(VtStatus.CLEAN, unknownClean.toVtResult().status)
    }

    @Test
    fun testSecurityScanResult_toVtResult_withRawReport_preservesNativeReport() {
        val rawReport = ScanReport(
            sha256 = "native_sha",
            verdict = Verdict.SAFE,
            riskScore = 5,
            findings = listOf(
                Finding(
                    scanner = "heuristic",
                    ruleId = "rule_1",
                    severity = SeverityLevel.LOW,
                    title = "Debug Flag",
                    description = "Application is debuggable",
                )
            ),
        )

        val result = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSDETEC,
            sha256 = "native_sha",
            verdict = ScannerVerdict.SAFE,
            rawReport = rawReport,
        )

        val vt = result.toVtResult()
        assertNotNull(vt.report)
        assertEquals("native_sha", vt.report?.sha256)
        assertEquals(Verdict.SAFE, vt.report?.verdict)
    }

    @Test
    fun testSecurityScanResult_toVtResult_withFindingsOnly_createsSyntheticScanReport() {
        val findings = listOf(
            ScannerFinding(
                scanner = "exodus",
                ruleId = "tracker_google_admob",
                severity = ScannerSeverity.MEDIUM,
                scoreImpact = 10,
                title = "Google AdMob",
                description = "Ad tracker found in DEX",
            )
        )

        val result = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = "offline_sha",
            verdict = ScannerVerdict.RISKWARE,
            findings = findings,
        )

        val vt = result.toVtResult()
        assertEquals(1, vt.engineResults.size)
        assertEquals("Google AdMob", vt.engineResults[0].engineName)
        assertEquals("suspicious", vt.engineResults[0].category)
        assertNotNull(vt.report)
        assertEquals(1, vt.report?.findings?.size)
        assertEquals("tracker_google_admob", vt.report?.findings?.first()?.ruleId)
    }

    @Test
    fun testScanReport_toSecurityScanResult_conversion() {
        val report = ScanReport(
            sha256 = "report_sha",
            verdict = Verdict.MALICIOUS,
            riskScore = 90,
            findings = listOf(
                Finding(
                    scanner = "static_signatures",
                    ruleId = "trojan_sig",
                    severity = SeverityLevel.CRITICAL,
                    title = "Trojan Signature",
                    description = "Known malware signature",
                ),
                Finding(
                    scanner = "heuristics",
                    ruleId = "sms_abuse",
                    severity = SeverityLevel.MEDIUM,
                    title = "SMS Abuse",
                    description = "Hidden SMS sending",
                )
            ),
        )

        val result = report.toSecurityScanResult(providerId = ScannerProviderId.VIRUSDETEC)
        assertEquals(ScannerProviderId.VIRUSDETEC, result.providerId)
        assertEquals("report_sha", result.sha256)
        assertEquals(ScannerVerdict.MALICIOUS, result.verdict)
        assertEquals(90, result.riskScore)
        assertEquals(2, result.findings.size)
        assertEquals(1, result.maliciousCount) // 1 CRITICAL
        assertEquals(1, result.suspiciousCount) // 1 MEDIUM
        assertEquals(report, result.rawReport)
    }

    @Test
    fun testScanStep_toVtResult_lifecycleSteps() {
        val hashingStep = ScanStep.Hashing(progressPercent = 50)
        assertEquals(VtStatus.SCANNING, hashingStep.toVtResult("hash123").status)

        val lookupStep = ScanStep.LookupCache("hash123")
        assertEquals(VtStatus.SCANNING, lookupStep.toVtResult("hash123").status)

        val uploadStep = ScanStep.Uploading(progressPercent = 75, bytesUploaded = 750, totalBytes = 1000)
        val uploadVt = uploadStep.toVtResult("hash123")
        assertEquals(VtStatus.UPLOADING, uploadVt.status)
        assertEquals(75, uploadVt.uploadProgress)

        val queuedStep = ScanStep.Queued(queuePosition = 2, message = "Position 2 in queue")
        val queuedVt = queuedStep.toVtResult("hash123")
        assertEquals(VtStatus.QUEUED, queuedVt.status)
        assertEquals("Position 2 in queue", queuedVt.errorMessage)

        val analyzingStep = ScanStep.Analyzing(progressPercent = 90, stageDescription = "Heuristic check")
        val analyzingVt = analyzingStep.toVtResult("hash123")
        assertEquals(VtStatus.ANALYZING, analyzingVt.status)
        assertEquals("Heuristic check", analyzingVt.errorMessage)

        val fallbackStep = ScanStep.FallbackTriggered(
            primaryError = ScanError.RateLimited(ScannerProviderId.VIRUSDETEC),
            fallbackProvider = ScannerProviderId.VIRUSTOTAL,
        )
        val fallbackVt = fallbackStep.toVtResult("hash123")
        assertEquals(VtStatus.SCANNING, fallbackVt.status)
        assertTrue(fallbackVt.errorMessage.contains("VirusTotal"))

        val failedStep = ScanStep.Failed(ScanError.NoApiKey(ScannerProviderId.VIRUSTOTAL))
        assertEquals(VtStatus.NO_API_KEY, failedStep.toVtResult("hash123").status)
    }

    @Test
    fun testScanError_toVtResult_allVariants() {
        assertEquals(VtStatus.NO_API_KEY, ScanError.NoApiKey(ScannerProviderId.VIRUSTOTAL).toVtResult().status)
        assertEquals(VtStatus.INVALID_API_KEY, ScanError.InvalidApiKey(ScannerProviderId.VIRUSTOTAL).toVtResult().status)
        assertEquals(VtStatus.RATE_LIMITED, ScanError.RateLimited(ScannerProviderId.VIRUSTOTAL).toVtResult().status)
        assertEquals(VtStatus.TOO_LARGE, ScanError.FileTooLarge(ScannerProviderId.VIRUSDETEC, 400L * 1024 * 1024, 300L * 1024 * 1024).toVtResult().status)
        assertEquals(VtStatus.NOT_FOUND, ScanError.NotFound(ScannerProviderId.VIRUSDETEC, "sha").toVtResult().status)
        assertEquals(VtStatus.ERROR, ScanError.NetworkError(ScannerProviderId.VIRUSDETEC).toVtResult().status)
        assertEquals(VtStatus.ERROR, ScanError.ServerError(ScannerProviderId.VIRUSDETEC, 500).toVtResult().status)
        assertEquals(VtStatus.ERROR, ScanError.Timeout(ScannerProviderId.VIRUSDETEC, 30000).toVtResult().status)
        assertEquals(VtStatus.ERROR, ScanError.Cancelled(ScannerProviderId.VIRUSDETEC).toVtResult().status)
        assertEquals(VtStatus.ERROR, ScanError.Generic(ScannerProviderId.VIRUSDETEC, "custom error").toVtResult().status)
    }

    @Test
    fun testScanError_isEligibleForFallback_accurateClassification() {
        assertTrue(ScanError.RateLimited(ScannerProviderId.VIRUSDETEC).isEligibleForFallback())
        assertTrue(ScanError.NoApiKey(ScannerProviderId.VIRUSTOTAL).isEligibleForFallback())
        assertTrue(ScanError.InvalidApiKey(ScannerProviderId.VIRUSTOTAL).isEligibleForFallback())
        assertTrue(ScanError.NetworkError(ScannerProviderId.VIRUSDETEC).isEligibleForFallback())
        assertTrue(ScanError.ServerError(ScannerProviderId.VIRUSDETEC, 502).isEligibleForFallback())
        assertTrue(ScanError.Timeout(ScannerProviderId.VIRUSDETEC, 60000).isEligibleForFallback())
        assertTrue(ScanError.FileTooLarge(ScannerProviderId.VIRUSDETEC, 350L * 1024 * 1024, 300L * 1024 * 1024).isEligibleForFallback())
        assertTrue(ScanError.Generic(ScannerProviderId.VIRUSDETEC, "Some generic error").isEligibleForFallback())

        // Non-eligible errors:
        assertFalse(ScanError.NotFound(ScannerProviderId.VIRUSDETEC, "sha").isEligibleForFallback())
        assertFalse(ScanError.Cancelled(ScannerProviderId.VIRUSDETEC).isEligibleForFallback())
    }
}
