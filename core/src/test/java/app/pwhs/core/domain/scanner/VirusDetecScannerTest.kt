package app.pwhs.core.domain.scanner

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.model.LookupResponse
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.UploadTicketResponse
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.scanner.impl.VirusDetecScanner
import app.pwhs.core.network.VirusDetecService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VirusDetecScannerTest {

    private val context = FakeContext()
    private val fakeUri = createTestUri("content://app.pwhs/test.apk")

    @Test
    fun capabilities_areConfiguredCorrectly() {
        val scanner = VirusDetecScanner()

        assertEquals(ScannerProviderId.VIRUSDETEC, scanner.providerId)
        assertTrue(scanner.capabilities.supportsHashLookup)
        assertTrue(scanner.capabilities.supportsBinaryUpload)
        assertTrue(scanner.capabilities.requiresNetwork)
        assertEquals(300L * 1024 * 1024, scanner.capabilities.maxFileSizeBytes)
    }

    @Test
    fun isConfigured_alwaysReturnsTrue() = runBlocking {
        val scanner = VirusDetecScanner()
        assertTrue(scanner.isConfigured(context))
    }

    @Test
    fun scanApk_fileTooLarge_emitsFailedImmediately() = runBlocking {
        val scanner = VirusDetecScanner()
        val metadata = ScanApkMetadata(
            sha256 = "test_large_sha256",
            fileSizeBytes = 350L * 1024 * 1024, // 350MB > 300MB
        )

        val steps = scanner.scanApk(context, fakeUri, "large.apk", metadata).toList()

        assertEquals(1, steps.size)
        assertTrue(steps[0] is ScanStep.Failed)
        val failed = steps[0] as ScanStep.Failed
        assertTrue(failed.error is ScanError.FileTooLarge)
        val fileTooLarge = failed.error as ScanError.FileTooLarge
        assertEquals(ScannerProviderId.VIRUSDETEC, fileTooLarge.provider)
        assertEquals(350L * 1024 * 1024, fileTooLarge.fileSizeBytes)
        assertEquals(300L * 1024 * 1024, fileTooLarge.maxSizeBytes)
        assertTrue(fileTooLarge.isEligibleForFallback())
    }

    @Test
    fun scanApk_blankHashWithEmptyUri_emitsHashingAndFails() = runBlocking {
        val scanner = VirusDetecScanner()
        val metadata = ScanApkMetadata(
            sha256 = "", // triggers hash computation attempt
            fileSizeBytes = 10L * 1024 * 1024,
        )

        val steps = scanner.scanApk(context, fakeUri, "test.apk", metadata).toList()

        assertTrue(steps.first() is ScanStep.Hashing)
        assertTrue(steps.last() is ScanStep.Failed)
        val failed = steps.last() as ScanStep.Failed
        assertTrue(failed.error is ScanError.Generic)
    }

    @Test
    fun toSecurityScanResult_mapsReportCorrectly() {
        val report = ScanReport(
            sha256 = "report_hash_1",
            verdict = Verdict.RISKWARE,
            riskScore = 45,
            findings = listOf(
                Finding(
                    scanner = "vd",
                    ruleId = "rule_sms",
                    severity = SeverityLevel.MEDIUM,
                    scoreImpact = 20,
                    title = "SMS Permission",
                    description = "Can send SMS messages",
                )
            ),
        )

        val result = report.toSecurityScanResult(providerId = ScannerProviderId.VIRUSDETEC)

        assertEquals(ScannerProviderId.VIRUSDETEC, result.providerId)
        assertEquals("report_hash_1", result.sha256)
        assertEquals(ScannerVerdict.RISKWARE, result.verdict)
        assertEquals(45, result.riskScore)
        assertEquals(1, result.findings.size)
        assertEquals(ScannerSeverity.MEDIUM, result.findings[0].severity)
        assertEquals("SMS Permission", result.findings[0].title)
    }

    private class FakeVirusDetecService : VirusDetecService() {
        var lookupResult: Result<LookupResponse> = Result.success(LookupResponse(cached = false, needUpload = true))
        var uploadTicketResult: Result<UploadTicketResponse> = Result.success(UploadTicketResponse("job1", "https://upload.url"))
        var startScanResult: Result<String> = Result.success("STARTED")

        override suspend fun lookup(request: LookupRequest, language: String): Result<LookupResponse> = lookupResult
        override suspend fun createUploadTicket(sha256: String, fileSize: Long, language: String): Result<UploadTicketResponse> = uploadTicketResult
        override suspend fun uploadBinary(uploadUrl: String, file: File, onProgress: (Int) -> Unit): Result<Unit> = Result.success(Unit)
        override suspend fun startScan(jobId: String, language: String): Result<String> = startScanResult
    }

    @Test
    fun testLookupHash_malformedJsonResponse_mapsToGenericError() = runBlocking {
        val fakeService = FakeVirusDetecService()
        fakeService.lookupResult = Result.failure(org.json.JSONException("Value <truncated> at 0 of "))
        val scanner = VirusDetecScanner(service = fakeService)

        val result = scanner.lookupHash(context, "hash123", ScanApkMetadata(sha256 = "hash123"))

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is VirusDetecScanner.ScanErrorException)
        val scanError = (ex as VirusDetecScanner.ScanErrorException).error
        assertTrue(scanError is ScanError.Generic)
        assertEquals(ScannerProviderId.VIRUSDETEC, scanError.provider)
    }

    @Test
    fun testScanApk_lookupHashFailsWithMalformedJson_emitsFailed() = runBlocking {
        val fakeService = FakeVirusDetecService()
        fakeService.lookupResult = Result.failure(org.json.JSONException("Corrupted JSON payload"))
        val scanner = VirusDetecScanner(service = fakeService)
        val metadata = ScanApkMetadata(sha256 = "hash123", fileSizeBytes = 1000L)

        val steps = scanner.scanApk(context, fakeUri, "test.apk", metadata).toList()

        assertTrue(steps.any { it is ScanStep.LookupCache })
        assertTrue(steps.last() is ScanStep.Failed)
        val failed = steps.last() as ScanStep.Failed
        assertTrue(failed.error is ScanError.Generic)
        assertEquals(ScannerProviderId.VIRUSDETEC, failed.error.provider)
    }

    @Test
    fun testParseScanReport_truncatedAndMalformedJson_handlesMissingFieldsGracefully() {
        val service = VirusDetecService()

        // Empty JSON object
        val emptyReport = service.parseScanReport(org.json.JSONObject("{}"))
        assertEquals("", emptyReport.sha256)
        assertEquals(Verdict.SAFE, emptyReport.verdict)
        assertEquals(0, emptyReport.riskScore)
        assertTrue(emptyReport.findings.isEmpty())
        assertTrue(emptyReport.dexHashes.isEmpty())

        // JSON with missing fields in findings
        val jsonWithIncompleteFinding = org.json.JSONObject("""
            {
                "sha256": "partial_sha",
                "findings": [
                    { "title": "Missing severity finding", "score_impact": 15 }
                ]
            }
        """.trimIndent())
        val partialReport = service.parseScanReport(jsonWithIncompleteFinding)
        assertEquals("partial_sha", partialReport.sha256)
        assertEquals(1, partialReport.findings.size)
        assertEquals(SeverityLevel.INFO, partialReport.findings[0].severity)
        assertEquals("Missing severity finding", partialReport.findings[0].title)
    }

    @Test
    fun testParseScanStepUpdate_corruptedFields_handlesSafely() {
        val service = VirusDetecService()

        val emptyUpdate = service.parseScanStepUpdate(org.json.JSONObject("{}"))
        assertEquals("", emptyUpdate.jobId)
        assertEquals("", emptyUpdate.status)
        assertEquals(0, emptyUpdate.progressPercent)
        assertNull(emptyUpdate.errorCode)
        assertNull(emptyUpdate.report)

        val validUpdateJson = org.json.JSONObject("""
            {
                "job_id": "job_123",
                "status": "ANALYZING",
                "progress_percent": 60,
                "message": "Analyzing DEX bytecode"
            }
        """.trimIndent())
        val update = service.parseScanStepUpdate(validUpdateJson)
        assertEquals("job_123", update.jobId)
        assertEquals("ANALYZING", update.status)
        assertEquals(60, update.progressPercent)
        assertEquals("Analyzing DEX bytecode", update.message)
    }

    @Test
    fun testParseSseLine_corruptedAndMalformedLines_skippedWithoutCrashing() {
        val service = VirusDetecService()

        // 1. Non-data lines (ping, comments) should be skipped
        assertNull(service.parseSseLine(": ping heartbeat"))
        assertNull(service.parseSseLine("event: keepalive"))

        // 2. Empty data lines
        assertNull(service.parseSseLine("data:"))
        assertNull(service.parseSseLine("data: {}"))

        // 3. Corrupted / truncated JSON lines should be safely skipped without throwing
        assertNull(service.parseSseLine("data: {corrupted json [[[ missing brackets"))
        assertNull(service.parseSseLine("data: null"))
        assertNull(service.parseSseLine("data: \"just a raw string\""))

        // 4. Valid progress SSE event
        val progressUpdate = service.parseSseLine("""data: {"status":"ANALYZING","progress_percent":75,"message":"Engine checks"}""")
        assertNotNull(progressUpdate)
        assertEquals("ANALYZING", progressUpdate?.status)
        assertEquals(75, progressUpdate?.progressPercent)

        // 5. Valid terminal SSE event
        val completedUpdate = service.parseSseLine("""data: {"status":"COMPLETED","report":{"sha256":"done_sha","verdict":"SAFE","risk_score":0}}""")
        assertNotNull(completedUpdate)
        assertEquals("COMPLETED", completedUpdate?.status)
        assertEquals(Verdict.SAFE, completedUpdate?.report?.verdict)
    }
}
