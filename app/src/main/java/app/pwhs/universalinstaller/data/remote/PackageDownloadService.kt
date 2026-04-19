package app.pwhs.universalinstaller.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.File
import java.io.IOException

/**
 * Streams a package (APK / APKS / XAPK / APKM / ZIP) from a remote URL into a local file.
 * Progress is reported as (bytesRead, totalBytes) — totalBytes is -1 when the server
 * omits Content-Length. Callers should cancel the coroutine to abort.
 */
class PackageDownloadService(private val client: HttpClient) {

    suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<DownloadedPackage> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            client.prepareGet(url) {
                timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP ${response.status.value} ${response.status.description}")
                }
                val total = response.contentLength() ?: -1L
                val fileName = response.headers[HttpHeaders.ContentDisposition]
                    ?.let { parseFileNameFromContentDisposition(it) }
                    ?: url.substringAfterLast('/').substringBefore('?').ifBlank { destination.name }

                val channel: ByteReadChannel = response.bodyAsChannel()
                var read = 0L
                destination.outputStream().use { out ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.exhausted()) {
                            val bytes = packet.readByteArray()
                            out.write(bytes)
                            read += bytes.size
                            onProgress(read, total)
                        }
                    }
                }
                DownloadedPackage(file = destination, fileName = fileName, totalBytes = read)
            }
        }.onFailure { destination.delete() }
    }

    private fun parseFileNameFromContentDisposition(header: String): String? {
        val starRegex = Regex("""filename\*\s*=\s*(?:[^']*'[^']*')?([^;\n]+)""", RegexOption.IGNORE_CASE)
        starRegex.find(header)?.groupValues?.get(1)?.let { raw ->
            val trimmed = raw.trim().trim('"')
            runCatching { return java.net.URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }
        }
        val regex = Regex("""filename\s*=\s*"?([^";\n]+)"?""", RegexOption.IGNORE_CASE)
        return regex.find(header)?.groupValues?.get(1)?.trim()
    }

    data class DownloadedPackage(
        val file: File,
        val fileName: String,
        val totalBytes: Long,
    )

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
        private const val DOWNLOAD_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
