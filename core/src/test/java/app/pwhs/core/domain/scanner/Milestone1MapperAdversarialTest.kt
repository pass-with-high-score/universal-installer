package app.pwhs.core.domain.scanner

import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial empirical tests by Challenger 2 for SecurityScanResult.toVtResult() mapping.
 * Verifies VirusDetec raw report preservation, multi-engine detections, and verdict translations.
 */
class Milestone1MapperAdversarialTest {

    @Test
    fun testToVtResult_virusDetecRawReport_allVerdictsAndFindingsPreserved() {
        // Test Verdict.SAFE
        val safeReport = ScanReport(
            sha256 = "safe_hash",
            verdict = Verdict.SAFE,
            riskScore = 0,
            findings = emptyList(),
        )
        val safeResult = safeReport.toSecurityScanResult(ScannerProviderId.VIRUSDETEC)
        val safeVt = safeResult.toVtResult()

        assertEquals(VtStatus.CLEAN, safeVt.status)
        assertEquals(0, safeVt.malicious)
        assertEquals(0, safeVt.suspicious)
        assertEquals(70, safeVt.harmless)
        assertEquals(safeReport, safeVt.report)
        assertTrue(safeVt.engineResults.isEmpty())

        // Test Verdict.MALICIOUS with all 5 severity levels
        val maliciousReport = ScanReport(
            sha256 = "mal_hash",
            verdict = Verdict.MALICIOUS,
            riskScore = 95,
            findings = listOf(
                Finding("vd", "r1", SeverityLevel.CRITICAL, 40, "Banking Trojan", "Steals OTP"),
                Finding("vd", "r2", SeverityLevel.HIGH, 30, "Dropper Payload", "Drops second stage"),
                Finding("vd", "r3", SeverityLevel.MEDIUM, 15, "Suspicious DexLoader", "Loads dynamic code"),
                Finding("vd", "r4", SeverityLevel.LOW, 5, "Debuggable Flag", "Debug enabled"),
                Finding("vd", "r5", SeverityLevel.INFO, 0, "Tracker Notice", "Admob tracker"),
            ),
        )
        val malResult = maliciousReport.toSecurityScanResult(ScannerProviderId.VIRUSDETEC)
        val malVt = malResult.toVtResult()

        assertEquals(VtStatus.MALICIOUS, malVt.status)
        assertEquals(2, malVt.malicious) // CRITICAL + HIGH
        assertEquals(1, malVt.suspicious) // MEDIUM
        assertEquals(65, malVt.harmless) // (70 - 5)
        assertEquals(maliciousReport, malVt.report)
        assertEquals(5, malVt.engineResults.size)
        assertEquals("malicious", malVt.engineResults[0].category)
        assertEquals("malicious", malVt.engineResults[1].category)
        assertEquals("suspicious", malVt.engineResults[2].category)
        assertEquals("harmless", malVt.engineResults[3].category)
        assertEquals("harmless", malVt.engineResults[4].category)

        // Test Verdict.RISKWARE -> maps to VtStatus.SUSPICIOUS
        val riskReport = ScanReport(
            sha256 = "risk_hash",
            verdict = Verdict.RISKWARE,
            riskScore = 40,
            findings = listOf(
                Finding("vd", "r_ad", SeverityLevel.MEDIUM, 20, "Aggressive Adware", "Displays intrusive popups"),
            ),
        )
        val riskResult = riskReport.toSecurityScanResult(ScannerProviderId.VIRUSDETEC)
        val riskVt = riskResult.toVtResult()

        assertEquals(VtStatus.SUSPICIOUS, riskVt.status)
        assertEquals(0, riskVt.malicious)
        assertEquals(1, riskVt.suspicious)
        assertEquals(riskReport, riskVt.report)

        // Test Verdict.SUSPICIOUS -> maps to VtStatus.SUSPICIOUS
        val suspReport = ScanReport(
            sha256 = "susp_hash",
            verdict = Verdict.SUSPICIOUS,
            riskScore = 55,
            findings = listOf(
                Finding("vd", "r_p", SeverityLevel.MEDIUM, 20, "SMS Permission", "Requests send SMS"),
            ),
        )
        val suspResult = suspReport.toSecurityScanResult(ScannerProviderId.VIRUSDETEC)
        val suspVt = suspResult.toVtResult()

        assertEquals(VtStatus.SUSPICIOUS, suspVt.status)
        assertEquals(0, suspVt.malicious)
        assertEquals(1, suspVt.suspicious)
        assertEquals(suspReport, suspVt.report)
    }

    @Test
    fun testToVtResult_virusTotalMultiEngineDetections_countsAndEngineResults() {
        val engineList = mutableListOf<EngineDetection>()
        engineList.add(EngineDetection("Avast", "malicious", "Android:Dropper-AZ"))
        engineList.add(EngineDetection("Kaspersky", "malicious", "HEUR:Trojan.AndroidOS.Agent.a"))
        engineList.add(EngineDetection("Microsoft", "malicious", "Trojan:AndroidOS/Agent!rfn"))
        engineList.add(EngineDetection("Symantec", "malicious", "Android.Trojan.Gen"))
        engineList.add(EngineDetection("ClamAV", "suspicious", "PUA.AndroidOS.Adware"))
        engineList.add(EngineDetection("Fortinet", "suspicious", "Riskware/Agent"))
        for (i in 1..62) {
            engineList.add(EngineDetection("Engine$i", "harmless", null))
        }
        engineList.add(EngineDetection("EngineUndetected1", "undetected", null))
        engineList.add(EngineDetection("EngineUndetected2", "undetected", null))

        val scanResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "vt_multi_hash",
            verdict = ScannerVerdict.MALICIOUS,
            riskScore = 100,
            engineDetections = engineList,
            maliciousCount = 4,
            suspiciousCount = 2,
            harmlessCount = 62,
            undetectedCount = 2,
            totalEngines = 70,
        )

        val vt = scanResult.toVtResult()

        assertEquals(VtStatus.MALICIOUS, vt.status)
        assertEquals(4, vt.malicious)
        assertEquals(2, vt.suspicious)
        assertEquals(62, vt.harmless)
        assertEquals(2, vt.undetected)
        assertEquals(70, vt.engineResults.size)

        assertEquals("Avast", vt.engineResults[0].engineName)
        assertEquals("malicious", vt.engineResults[0].category)
        assertEquals("Android:Dropper-AZ", vt.engineResults[0].result)

        assertEquals("ClamAV", vt.engineResults[4].engineName)
        assertEquals("suspicious", vt.engineResults[4].category)
        assertEquals("PUA.AndroidOS.Adware", vt.engineResults[4].result)
    }

    @Test
    fun testToVtResult_withoutRawReport_allScannerVerdictsAndUnknownResolution() {
        val cleanResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "hash_safe",
            verdict = ScannerVerdict.SAFE,
            harmlessCount = 70,
        )
        assertEquals(VtStatus.CLEAN, cleanResult.toVtResult().status)

        val malResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "hash_mal",
            verdict = ScannerVerdict.MALICIOUS,
            maliciousCount = 5,
        )
        assertEquals(VtStatus.MALICIOUS, malResult.toVtResult().status)

        val suspResult = SecurityScanResult(
            providerId = ScannerProviderId.VIRUSTOTAL,
            sha256 = "hash_susp",
            verdict = ScannerVerdict.SUSPICIOUS,
            suspiciousCount = 2,
        )
        assertEquals(VtStatus.SUSPICIOUS, suspResult.toVtResult().status)

        val riskResult = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = "hash_risk",
            verdict = ScannerVerdict.RISKWARE,
        )
        assertEquals(VtStatus.SUSPICIOUS, riskResult.toVtResult().status)

        val unknownMal = SecurityScanResult(
            providerId = ScannerProviderId.MOCK,
            sha256 = "hash_unk1",
            verdict = ScannerVerdict.UNKNOWN,
            maliciousCount = 1,
        )
        assertEquals(VtStatus.MALICIOUS, unknownMal.toVtResult().status)

        val unknownSusp = SecurityScanResult(
            providerId = ScannerProviderId.MOCK,
            sha256 = "hash_unk2",
            verdict = ScannerVerdict.UNKNOWN,
            suspiciousCount = 3,
        )
        assertEquals(VtStatus.SUSPICIOUS, unknownSusp.toVtResult().status)

        val unknownClean = SecurityScanResult(
            providerId = ScannerProviderId.MOCK,
            sha256 = "hash_unk3",
            verdict = ScannerVerdict.UNKNOWN,
            maliciousCount = 0,
            suspiciousCount = 0,
        )
        assertEquals(VtStatus.CLEAN, unknownClean.toVtResult().status)
    }

    @Test
    fun testToVtResult_syntheticReportCreation_whenFindingsPresentWithoutRawReport() {
        val findings = listOf(
            ScannerFinding(
                scanner = "offline_dex",
                ruleId = "sensitive_perm_sms",
                severity = ScannerSeverity.HIGH,
                scoreImpact = 25,
                title = "Send SMS Permission",
                description = "Can send premium SMS messages",
                evidence = "android.permission.SEND_SMS",
            ),
        )

        val result = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = "synthetic_hash",
            verdict = ScannerVerdict.SUSPICIOUS,
            riskScore = 25,
            findings = findings,
        )

        val vt = result.toVtResult()
        assertNotNull(vt.report)
        val report = vt.report!!
        assertEquals("synthetic_hash", report.sha256)
        assertEquals(Verdict.SUSPICIOUS, report.verdict)
        assertEquals(25, report.riskScore)
        assertEquals(1, report.findings.size)
        assertEquals("offline_dex", report.findings[0].scanner)
        assertEquals("sensitive_perm_sms", report.findings[0].ruleId)
        assertEquals(SeverityLevel.HIGH, report.findings[0].severity)
        assertEquals("Send SMS Permission", report.findings[0].title)
        assertEquals("android.permission.SEND_SMS", report.findings[0].evidence)
    }
}
