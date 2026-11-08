package app.pwhs.core.network

import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.FalsePositiveRequest
import app.pwhs.core.domain.model.FalsePositiveResponse
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.model.LookupResponse
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.ScanStepUpdate
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.UploadTicketResponse
import app.pwhs.core.domain.model.Verdict
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class VirusDetecService(
    private val client: HttpClient = defaultClient,
    private val baseUrl: String = PRIMARY_BASE_URL,
    private val apiKey: String = DEFAULT_CLIENT_API_KEY,
) {
    companion object {
        const val PRIMARY_BASE_URL = "https://virusdetect.pwhs.app"
        const val FALLBACK_BASE_URL = "https://virusdetec.nguyenquangminh623.workers.dev"
        const val DEFAULT_CLIENT_API_KEY = "vd_client_live_u89b21f00a"
        const val NETWORK_ERROR_TAG = "__NETWORK_ERROR__"

        val defaultClient: HttpClient by lazy {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
        }
    }

    suspend fun computeSha256(input: InputStream): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response: HttpResponse = client.get("$baseUrl/health")
            response.status.isSuccess()
        }
    }

    suspend fun lookup(request: LookupRequest, language: String = "vi"): Result<LookupResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("sha256", request.sha256)
                if (request.packageName.isNotBlank()) put("package_name", request.packageName)
                if (request.appName.isNotBlank()) put("app_name", request.appName)
                if (request.versionName.isNotBlank()) put("version_name", request.versionName)
                if (request.versionCode > 0) put("version_code", request.versionCode)
                if (request.minSdk > 0) put("min_sdk", request.minSdk)
                if (request.targetSdk > 0) put("target_sdk", request.targetSdk)
                if (request.certSha256.isNotBlank()) put("cert_sha256", request.certSha256)
                if (request.permissions.isNotEmpty()) put("permissions", JSONArray(request.permissions))
                if (request.services.isNotEmpty()) put("services", JSONArray(request.services))
                if (request.receivers.isNotEmpty()) put("receivers", JSONArray(request.receivers))
            }

            val response = executeWithFallback { url ->
                client.post("$url/api/v1/scan/lookup") {
                    contentType(ContentType.Application.Json)
                    header("X-API-Key", apiKey)
                    header("Accept-Language", language)
                    setBody(payload.toString())
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("Lookup failed with HTTP ${response.status.value}: ${response.bodyAsText()}")
            }

            parseLookupResponse(JSONObject(response.bodyAsText()))
        }
    }

    suspend fun createUploadTicket(sha256: String, fileSize: Long, language: String = "vi"): Result<UploadTicketResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("sha256", sha256)
                put("file_size", fileSize)
            }

            val response = executeWithFallback { url ->
                client.post("$url/api/v1/scan/upload-ticket") {
                    contentType(ContentType.Application.Json)
                    header("X-API-Key", apiKey)
                    header("Accept-Language", language)
                    setBody(payload.toString())
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("UploadTicket failed with HTTP ${response.status.value}: ${response.bodyAsText()}")
            }

            val json = JSONObject(response.bodyAsText())
            UploadTicketResponse(
                jobId = json.optString("job_id"),
                uploadUrl = json.optString("upload_url"),
                method = json.optString("method", "PUT"),
                contentType = json.optString("content_type", "application/vnd.android.package-archive"),
                expiresInSeconds = json.optInt("expires_in_seconds", 900),
            )
        }
    }

    suspend fun uploadBinary(
        uploadUrl: String,
        file: File,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response: HttpResponse = client.put(uploadUrl) {
                contentType(ContentType.parse("application/vnd.android.package-archive"))
                setBody(file.readChannel())
                onUpload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0) {
                        val progress = ((bytesSentTotal.toDouble() / contentLength) * 100).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Binary upload failed with HTTP ${response.status.value}")
            }
        }
    }

    suspend fun startScan(jobId: String, language: String = "vi"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply { put("job_id", jobId) }
            val response = executeWithFallback { url ->
                client.post("$url/api/v1/scan/start") {
                    contentType(ContentType.Application.Json)
                    header("X-API-Key", apiKey)
                    header("Accept-Language", language)
                    setBody(payload.toString())
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("StartScan failed with HTTP ${response.status.value}")
            }
            JSONObject(response.bodyAsText()).optString("status")
        }
    }

    fun watchScanStream(jobId: String, language: String = "vi"): Flow<ScanStepUpdate> = flow {
        val streamUrl = "$baseUrl/api/v1/scan/jobs/$jobId/stream?lang=$language"
        val response: HttpResponse = client.get(streamUrl) {
            header("X-API-Key", apiKey)
            header("Accept-Language", language)
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("data:")) {
                val dataContent = line.removePrefix("data:").trim()
                if (dataContent.isNotEmpty() && dataContent != "{}") {
                    runCatching {
                        val json = JSONObject(dataContent)
                        val update = parseScanStepUpdate(json)
                        emit(update)
                        if (update.status == "COMPLETED" || update.status == "FAILED") {
                            return@flow
                        }
                    }.onFailure {
                        Timber.w(it, "Failed to parse SSE line: $dataContent")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun submitFalsePositive(
        request: FalsePositiveRequest,
        language: String = "vi",
    ): Result<FalsePositiveResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("sha256", request.sha256)
                if (request.packageName.isNotBlank()) put("package_name", request.packageName)
                if (request.appName.isNotBlank()) put("app_name", request.appName)
                put("reason", request.reason)
                if (request.contactEmail.isNotBlank()) put("contact_email", request.contactEmail)
            }

            val response = executeWithFallback { url ->
                client.post("$url/api/v1/reports/false-positive") {
                    contentType(ContentType.Application.Json)
                    header("X-API-Key", apiKey)
                    header("Accept-Language", language)
                    setBody(payload.toString())
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("SubmitFalsePositive failed (${response.status.value}): ${response.bodyAsText()}")
            }

            val json = JSONObject(response.bodyAsText())
            FalsePositiveResponse(
                status = json.optString("status"),
                message = json.optString("message"),
                reportId = json.optString("report_id"),
                sha256 = json.optString("sha256"),
            )
        }
    }

    private suspend fun executeWithFallback(block: suspend (String) -> HttpResponse): HttpResponse {
        return try {
            block(baseUrl)
        } catch (e: Exception) {
            Timber.w(e, "Request to primary base URL ($baseUrl) failed, attempting fallback ($FALLBACK_BASE_URL)")
            block(FALLBACK_BASE_URL)
        }
    }

    private fun parseLookupResponse(json: JSONObject): LookupResponse {
        val cached = json.optBoolean("cached", false)
        val needUpload = json.optBoolean("need_upload", false)
        val reportObj = json.optJSONObject("report")
        val precheckObj = json.optJSONObject("precheck_report")

        return LookupResponse(
            cached = cached,
            needUpload = needUpload,
            report = reportObj?.let { parseScanReport(it) },
            precheckReport = precheckObj?.let { parseScanReport(it) },
        )
    }

    private fun parseScanStepUpdate(json: JSONObject): ScanStepUpdate {
        return ScanStepUpdate(
            jobId = json.optString("job_id"),
            status = json.optString("status"),
            stepCode = json.optString("step_code"),
            progressPercent = json.optInt("progress_percent", 0),
            message = json.optString("message"),
            errorCode = json.optString("error_code").takeIf { it.isNotBlank() },
            report = json.optJSONObject("report")?.let { parseScanReport(it) },
        )
    }

    private fun parseScanReport(json: JSONObject): ScanReport {
        val findingsArray = json.optJSONArray("findings") ?: JSONArray()
        val findings = mutableListOf<Finding>()
        for (i in 0 until findingsArray.length()) {
            val f = findingsArray.optJSONObject(i) ?: continue
            findings.add(
                Finding(
                    scanner = f.optString("scanner"),
                    ruleId = f.optString("rule_id"),
                    severity = SeverityLevel.fromString(f.optString("severity")),
                    scoreImpact = f.optInt("score_impact", 0),
                    title = f.optString("title"),
                    description = f.optString("description"),
                    evidence = f.optString("evidence").takeIf { it.isNotBlank() },
                )
            )
        }

        val dexHashesArray = json.optJSONArray("dex_hashes") ?: JSONArray()
        val dexHashes = (0 until dexHashesArray.length()).mapNotNull { dexHashesArray.optString(it) }

        return ScanReport(
            sha256 = json.optString("sha256"),
            dexHashes = dexHashes,
            packageName = json.optString("package_name"),
            appName = json.optString("app_name"),
            versionName = json.optString("version_name"),
            versionCode = json.optLong("version_code", 0),
            certSha256 = json.optString("cert_sha256"),
            verdict = Verdict.fromString(json.optString("verdict")),
            riskScore = json.optInt("risk_score", 0),
            findings = findings,
            scannerVersion = json.optString("scanner_version", "1.2.1"),
            createdAt = json.optString("created_at"),
            updatedAt = json.optString("updated_at"),
        )
    }
}
