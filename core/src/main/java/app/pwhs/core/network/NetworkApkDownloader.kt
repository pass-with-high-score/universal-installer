package app.pwhs.core.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float?, // 0.0 .. 1.0, null if indeterminate (unknown total)
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
)

sealed interface DownloadResult {
    data class Success(val file: File, val fileName: String, val totalBytes: Long) : DownloadResult
    data class Error(val message: String, val throwable: Throwable? = null) : DownloadResult
    data object Cancelled : DownloadResult
}

/**
 * Downloads APKs/packages directly from HTTP/HTTPS URLs into a temporary cache directory.
 * Follows redirects, extracts filenames from Content-Disposition/URL, and tracks download progress.
 */
class NetworkApkDownloader(private val context: Context) {

    suspend fun download(
        url: String,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        var targetFile: File? = null

        try {
            val normalizedUrl = normalizeUrlOrShortcode(url.trim())
            val (finalConn, discoveredName) = openRedirectConnection(normalizedUrl)
            connection = finalConn

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext DownloadResult.Error("HTTP $responseCode ${connection.responseMessage ?: ""}".trim())
            }

            val contentType = connection.contentType.orEmpty().lowercase()
            if (contentType.contains("text/html") || contentType.contains("application/xhtml+xml")) {
                return@withContext DownloadResult.Error("Web page detected instead of APK. Please use a direct download link.")
            }

            val totalBytes = connection.contentLengthLong
            val downloadDir = File(context.cacheDir, "network_downloads").apply { mkdirs() }
            
            val ext = discoveredName.substringAfterLast('.', "apk").lowercase()
            val safeExtension = if (ext in listOf("apk", "xapk", "apks", "apkm", "zip")) ext else "apk"
            val baseName = discoveredName.substringBeforeLast('.').ifBlank { "download" }
            
            targetFile = File(downloadDir, "${baseName}_${System.currentTimeMillis()}.$safeExtension")
            
            inputStream = connection.inputStream.buffered(32 * 1024)
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(32 * 1024)
            var bytesReadTotal = 0L
            val estimator = app.pwhs.core.util.TransferEstimator()
            var lastUpdate = System.currentTimeMillis()

            while (isActive) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                bytesReadTotal += read

                val now = System.currentTimeMillis()
                val delta = now - lastUpdate
                if (delta >= 250) {
                    lastUpdate = now
                    val estimate = estimator.update(bytesReadTotal, totalBytes)
                    val progressRatio = if (totalBytes > 0) {
                        (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    onProgress(
                        DownloadProgress(
                            bytesDownloaded = bytesReadTotal,
                            totalBytes = totalBytes,
                            progress = progressRatio,
                            speedBytesPerSec = estimate.speedBytesPerSec,
                            etaSeconds = estimate.etaSeconds,
                        )
                    )
                }
            }

            if (!isActive) {
                targetFile.delete()
                return@withContext DownloadResult.Cancelled
            }

            outputStream.flush()
            onProgress(DownloadProgress(bytesReadTotal, totalBytes, 1f, 0L, 0L))

            DownloadResult.Success(
                file = targetFile,
                fileName = discoveredName.ifBlank { targetFile.name },
                totalBytes = bytesReadTotal,
            )
        } catch (e: Throwable) {
            targetFile?.delete()
            val friendlyMsg = toFriendlyErrorMessage(e)
            DownloadResult.Error(friendlyMsg, e)
        } finally {
            runCatching { inputStream?.close() }
            runCatching { outputStream?.close() }
            runCatching { connection?.disconnect() }
        }
    }

    private fun openRedirectConnection(initialUrl: String, maxRedirects: Int = 10): Pair<HttpURLConnection, String> {
        var currentUrl = initialUrl
        var redirects = 0
        var conn: HttpURLConnection

        while (redirects < maxRedirects) {
            val urlObj = URL(currentUrl)
            conn = (urlObj.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "UniversalInstaller/${getAppVersionName()}")
                setRequestProperty("Accept", "*/*")
            }

            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                    location
                } else {
                    URL(urlObj, location).toString()
                }
                redirects++
            } else {
                val nameFromHeader = parseContentDispositionFileName(conn.getHeaderField("Content-Disposition"))
                val nameFromUrl = extractFileNameFromUrl(currentUrl)
                val resolvedName = nameFromHeader ?: nameFromUrl
                return Pair(conn, resolvedName)
            }
        }

        val finalUrl = URL(currentUrl)
        val finalConn = (finalUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "UniversalInstaller/${getAppVersionName()}")
        }
        return Pair(finalConn, extractFileNameFromUrl(currentUrl))
    }

    private fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        return runCatching {
            val filenameRegex = """filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
            val match = filenameRegex.find(header)
            match?.groupValues?.get(1)?.let { rawName ->
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
            }
        }.getOrNull()
    }

    private fun extractFileNameFromUrl(urlString: String): String {
        return runCatching {
            val uri = Uri.parse(urlString)
            val lastSegment = uri.lastPathSegment?.substringBefore('?')?.substringBefore('#')
            if (!lastSegment.isNullOrBlank()) {
                URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
            } else {
                "download.apk"
            }
        }.getOrDefault("download.apk")
    }

    private fun getAppVersionName(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().ifEmpty { "1.0.0" }
        }.getOrDefault("1.0.0")
    }

    private fun normalizeUrlOrShortcode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
            return "https://aftv.news/$trimmed"
        }
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun toFriendlyErrorMessage(throwable: Throwable): String {
        val msg = throwable.message.orEmpty()
        return when {
            throwable is java.net.UnknownHostException -> "Server not found. Check network connection or URL."
            throwable is java.net.SocketTimeoutException -> "Connection timed out. Server is taking too long to respond."
            throwable is javax.net.ssl.SSLHandshakeException || msg.contains("Trust anchor", ignoreCase = true) ->
                "SSL certificate error. The server uses an untrusted or outdated certificate."
            msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space left", ignoreCase = true) ->
                "Insufficient storage space on device."
            else -> throwable.localizedMessage ?: "Download failed"
        }
    }
}
