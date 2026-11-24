package app.pwhs.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Direct OkHttp streaming uploader for presigned R2 PUT URLs.
 * Streams in 128 KB chunks straight from [File] to Okio's [BufferedSink] without
 * Ktor channel hopping, while reporting 0..100% progress and honoring coroutine cancellation.
 */
internal object BinaryUploadClient {
    private const val BUFFER_SIZE_BYTES = 128 * 1024
    private const val DEFAULT_CONTENT_TYPE = "application/vnd.android.package-archive"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    suspend fun putFile(
        uploadUrl: String,
        file: File,
        contentType: String = DEFAULT_CONTENT_TYPE,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val totalLength = file.length()
            val startMs = System.currentTimeMillis()
            Timber.d("BinaryUpload: starting PUT size=${totalLength}B to ${uploadUrl.substringBefore('?')}")
            val mediaType = contentType.ifBlank { DEFAULT_CONTENT_TYPE }.toMediaType()
            val body = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength() = totalLength
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    var uploaded = 0L
                    var lastReportedPct = -1
                    file.inputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            uploaded += read
                            if (totalLength > 0L) {
                                val pct = ((uploaded * 100L) / totalLength).toInt().coerceIn(0, 100)
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    if (pct % 20 == 0 || pct == 1) {
                                        Timber.d("BinaryUpload: progress=$pct% (${uploaded}/${totalLength}B)")
                                    }
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
            val request = Request.Builder()
                .url(uploadUrl)
                .put(body)
                .build()
            val call = client.newCall(request)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) {
                    Timber.d("BinaryUpload: cancelled (${cause.javaClass.simpleName})")
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    val elapsedMs = System.currentTimeMillis() - startMs
                    Timber.d("BinaryUpload: HTTP ${response.code} in ${elapsedMs}ms")
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Binary upload failed with HTTP ${response.code}")
                    }
                }
            } finally {
                cancelHandle?.dispose()
            }
        }.onFailure { err ->
            Timber.w(err, "BinaryUpload: failed")
        }
    }
}
