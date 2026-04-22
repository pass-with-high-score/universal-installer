package app.pwhs.universalinstaller.data.remote

import app.pwhs.universalinstaller.domain.model.VtEngineResult
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class VirusTotalService(
    private val client: HttpClient,
) {
    /**
     * Compute SHA-256 hash of an [InputStream] without writing it to disk.
     * Caller is responsible for closing the stream (use `.use {}`).
     */
    suspend fun computeSha256(input: InputStream): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Overload kept for callers that still pass a [File]. */
    suspend fun computeSha256(file: File): String =
        file.inputStream().use { computeSha256(it) }

    /**
     * Hash lookup. Fast path — returns CLEAN/MALICIOUS/SUSPICIOUS/NOT_FOUND/ERROR without
     * uploading anything.
     */
    suspend fun checkFile(apiKey: String, sha256: String): VtResult = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get("$BASE_URL/files/$sha256") {
                headers {
                    append(HEADER_KEY, apiKey)
                    append(HttpHeaders.Accept, "application/json")
                }
            }
            when (response.status.value) {
                200 -> parseFileStats(response.bodyAsText())
                404 -> VtResult(status = VtStatus.NOT_FOUND)
                else -> VtResult(
                    status = VtStatus.ERROR,
                    errorMessage = "HTTP ${response.status.value}: ${response.status.description}",
                )
            }
        }.getOrElse { e ->
            Timber.e(e, "VirusTotal hash lookup failed")
            VtResult(status = VtStatus.ERROR, errorMessage = e.message ?: "Unknown error")
        }
    }

    /**
     * Stream [file] to VirusTotal. Picks the right endpoint based on file size:
     *   - ≤ 32 MB → POST /files directly
     *   - 32 – 650 MB → GET /files/upload_url then POST to the returned URL
     * Files > 650 MB should be rejected upstream as [VtStatus.TOO_LARGE].
     *
     * @param onProgress invoked with upload completion percent (0..100) on the calling context.
     */
    suspend fun uploadFile(
        apiKey: String,
        file: File,
        onProgress: suspend (Int) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val size = file.length()
            val uploadTarget = if (size <= SIZE_LIMIT_DIRECT) {
                "$BASE_URL/files"
            } else {
                requestLargeUploadUrl(apiKey)
            }
            postFileMultipart(uploadTarget, apiKey, file, onProgress)
        }
    }

    /**
     * Fetch a one-time upload URL for files > 32 MB. Throws if the endpoint fails so the
     * surrounding [runCatching] in [uploadFile] captures it as a Result failure.
     */
    private suspend fun requestLargeUploadUrl(apiKey: String): String {
        val response = client.get("$BASE_URL/files/upload_url") {
            headers {
                append(HEADER_KEY, apiKey)
                append(HttpHeaders.Accept, "application/json")
            }
        }
        if (response.status.value !in 200..299) {
            error("upload_url HTTP ${response.status.value}: ${response.status.description}")
        }
        return JSONObject(response.bodyAsText()).getString("data")
    }

    /**
     * Shared multipart POST for both the direct /files endpoint and the single-use large-file URL.
     * We always attach `x-apikey`; the direct endpoint requires it and the presigned URL ignores
     * it harmlessly. The per-request timeout is bumped past the 5-minute global to cover slow
     * uploads of the full 650 MB ceiling.
     *
     * Retries up to [MAX_UPLOAD_RETRIES] times on transient I/O failures (e.g. EOFException from
     * VT's bigfiles endpoint prematurely closing the connection).
     */
    private suspend fun postFileMultipart(
        url: String,
        apiKey: String,
        file: File,
        onProgress: suspend (Int) -> Unit,
    ): String {
        var lastException: Throwable? = null
        repeat(MAX_UPLOAD_RETRIES + 1) { attempt ->
            if (attempt > 0) {
                Timber.w("Upload attempt ${attempt + 1}/${MAX_UPLOAD_RETRIES + 1} after transient failure")
                delay(UPLOAD_RETRY_DELAY_MS)
                onProgress(0) // reset progress indicator
            }
            try {
                val response: HttpResponse = client.submitFormWithBinaryData(
                    url = url,
                    formData = formData {
                        append(
                            key = "file",
                            value = ChannelProvider(size = file.length()) { file.readChannel() },
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                            },
                        )
                    },
                ) {
                    headers {
                        append(HEADER_KEY, apiKey)
                        append(HttpHeaders.Accept, "application/json")
                    }
                    timeout {
                        // 20 minutes covers the 650 MB ceiling on a sub-1 MB/s link.
                        requestTimeoutMillis = UPLOAD_REQUEST_TIMEOUT_MS
                        socketTimeoutMillis = UPLOAD_REQUEST_TIMEOUT_MS
                    }
                    onUpload { sent, total ->
                        val t = total ?: file.length()
                        if (t > 0) {
                            val pct = ((sent * 100L) / t).toInt().coerceIn(0, 100)
                            onProgress(pct)
                        }
                    }
                }
                if (response.status.value !in 200..299) {
                    error("Upload HTTP ${response.status.value}: ${response.status.description}")
                }
                return JSONObject(response.bodyAsText()).getJSONObject("data").getString("id")
            } catch (e: java.io.IOException) {
                // Transient I/O error (EOFException, SocketException, etc.) — retry
                Timber.w(e, "Upload I/O error on attempt ${attempt + 1}")
                lastException = e
            }
        }
        throw lastException ?: java.io.IOException("Upload failed after ${MAX_UPLOAD_RETRIES + 1} attempts")
    }

    /**
     * Poll `/analyses/{id}` until the verdict is ready. Uses staggered backoff to stay under
     * VT's free-tier rate limit (4 req/min).
     */
    suspend fun pollAnalysis(
        apiKey: String,
        analysisId: String,
        onStatusChange: suspend (VtStatus) -> Unit = {},
    ): VtResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastReportedStatus: VtStatus? = null
        val totalTimeoutMs = 3 * 60 * 1000L
        val deadline = System.currentTimeMillis() + totalTimeoutMs

        while (System.currentTimeMillis() < deadline) {
            val delayMs = when (attempt) {
                0 -> 3_000L
                1, 2 -> 5_000L
                3, 4 -> 10_000L
                else -> 15_000L
            }
            delay(delayMs)
            attempt++

            val result = runCatching {
                val response = client.get("$BASE_URL/analyses/$analysisId") {
                    headers {
                        append(HEADER_KEY, apiKey)
                        append(HttpHeaders.Accept, "application/json")
                    }
                }
                if (response.status.value !in 200..299) {
                    return@runCatching VtResult(
                        status = VtStatus.ERROR,
                        errorMessage = "Poll HTTP ${response.status.value}",
                        analysisId = analysisId,
                    )
                }
                parseAnalysisResponse(response.bodyAsText(), analysisId)
            }.getOrElse { e ->
                Timber.w(e, "Analysis poll attempt $attempt failed — retrying")
                null
            } ?: continue

            if (result.status != lastReportedStatus &&
                (result.status == VtStatus.QUEUED || result.status == VtStatus.ANALYZING)
            ) {
                lastReportedStatus = result.status
                onStatusChange(result.status)
            }

            if (result.status !in POLL_CONTINUE_STATES) return@withContext result
        }
        VtResult(
            status = VtStatus.ERROR,
            errorMessage = "Analysis timed out",
            analysisId = analysisId,
        )
    }

    private fun parseFileStats(body: String): VtResult = try {
        val attributes = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
        val stats = attributes.getJSONObject("last_analysis_stats")
        val engines = parseEngineResults(attributes.optJSONObject("last_analysis_results"))
        toResult(stats, engines)
    } catch (e: Exception) {
        Timber.e(e, "Error parsing VT file response")
        VtResult(status = VtStatus.ERROR, errorMessage = "Parse error: ${e.message}")
    }

    private fun parseAnalysisResponse(body: String, analysisId: String): VtResult = try {
        val attributes = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
        when (val apiStatus = attributes.optString("status", "queued")) {
            "queued" -> VtResult(status = VtStatus.QUEUED, analysisId = analysisId)
            "in-progress" -> VtResult(status = VtStatus.ANALYZING, analysisId = analysisId)
            "completed" -> {
                val stats = attributes.getJSONObject("stats")
                val engines = parseEngineResults(attributes.optJSONObject("results"))
                toResult(stats, engines).copy(analysisId = analysisId)
            }
            else -> VtResult(
                status = VtStatus.ERROR,
                errorMessage = "Unknown analysis status: $apiStatus",
                analysisId = analysisId,
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Error parsing VT analysis response")
        VtResult(
            status = VtStatus.ERROR,
            errorMessage = "Parse error: ${e.message}",
            analysisId = analysisId,
        )
    }

    /**
     * Extract per-engine results from `last_analysis_results` (file endpoint) or
     * `results` (analysis endpoint). Returns engines sorted: malicious first, then
     * suspicious, then the rest alphabetically.
     */
    private fun parseEngineResults(resultsObj: JSONObject?): List<VtEngineResult> {
        if (resultsObj == null) return emptyList()
        val list = mutableListOf<VtEngineResult>()
        for (key in resultsObj.keys()) {
            val engine = resultsObj.optJSONObject(key) ?: continue
            list.add(
                VtEngineResult(
                    engineName = key,
                    category = engine.optString("category", "undetected"),
                    result = engine.optString("result", "").let {
                        if (it.isBlank() || it == "null") null else it
                    },
                )
            )
        }
        // Sort: threats first (malicious > suspicious), then alphabetical
        val categoryOrder = mapOf("malicious" to 0, "suspicious" to 1)
        return list.sortedWith(
            compareBy<VtEngineResult> { categoryOrder[it.category] ?: 2 }
                .thenBy { it.engineName.lowercase() }
        )
    }

    private fun toResult(stats: JSONObject, engines: List<VtEngineResult> = emptyList()): VtResult {
        val malicious = stats.optInt("malicious", 0)
        val suspicious = stats.optInt("suspicious", 0)
        val harmless = stats.optInt("harmless", 0)
        val undetected = stats.optInt("undetected", 0)
        val status = when {
            malicious > 0 -> VtStatus.MALICIOUS
            suspicious > 0 -> VtStatus.SUSPICIOUS
            else -> VtStatus.CLEAN
        }
        return VtResult(
            malicious = malicious,
            suspicious = suspicious,
            harmless = harmless,
            undetected = undetected,
            status = status,
            engineResults = engines,
        )
    }

    companion object {
        const val BASE_URL = "https://www.virustotal.com/api/v3"
        private const val HEADER_KEY = "x-apikey"

        /** Files up to this size use the direct POST /files endpoint. */
        const val SIZE_LIMIT_DIRECT: Long = 32L * 1024 * 1024

        /** Hard ceiling enforced by VirusTotal even for the large-file upload URL. */
        const val SIZE_LIMIT_LARGE: Long = 650L * 1024 * 1024

        private const val UPLOAD_REQUEST_TIMEOUT_MS: Long = 20 * 60 * 1000L
        private const val MAX_UPLOAD_RETRIES = 2
        private const val UPLOAD_RETRY_DELAY_MS = 5000L

        private val POLL_CONTINUE_STATES = setOf(VtStatus.QUEUED, VtStatus.ANALYZING)
    }
}
