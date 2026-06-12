package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads an APK to a TV's receiver server (the QR encodes its `url` + `token`). Streams the
 * file via a multipart/form-data POST over HttpURLConnection — no extra deps, no whole-file
 * buffering, so large APKs don't OOM.
 */
object TvUploadClient {

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    /**
     * @param scanned the exact string from the TV QR, e.g. `http://192.168.1.5:8787/?token=ab12`.
     */
    suspend fun upload(
        context: Context,
        scanned: String,
        apk: Uri,
        fileName: String,
    ): Result = withContext(Dispatchers.IO) {
        val parsed = runCatching { Uri.parse(scanned) }.getOrNull()
        val host = parsed?.host
        val port = parsed?.port?.takeIf { it > 0 }
        val token = parsed?.getQueryParameter("token").orEmpty()
        if (host == null || port == null) {
            return@withContext Result.Failure("Not a valid TV code")
        }

        val boundary = "----uitv${System.currentTimeMillis()}"
        val crlf = "\r\n"
        val resolver = context.contentResolver
        val size = runCatching {
            resolver.openAssetFileDescriptor(apk, "r")?.use { it.length }
        }.getOrNull() ?: -1L

        val preamble = buildString {
            append("--").append(boundary).append(crlf)
            append("Content-Disposition: form-data; name=\"apk\"; filename=\"").append(fileName).append("\"").append(crlf)
            append("Content-Type: application/octet-stream").append(crlf).append(crlf)
        }.toByteArray()
        val epilogue = "$crlf--$boundary--$crlf".toByteArray()

        return@withContext try {
            val conn = (URL("http://$host:$port/upload?token=$token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (size >= 0) {
                    setFixedLengthStreamingMode(preamble.size + size + epilogue.size)
                } else {
                    setChunkedStreamingMode(0)
                }
            }
            DataOutputStream(conn.outputStream).use { out ->
                out.write(preamble)
                resolver.openInputStream(apk)?.use { input -> input.copyTo(out) }
                    ?: return@withContext Result.Failure("Could not read the selected file")
                out.write(epilogue)
                out.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.Success
            else Result.Failure("TV rejected upload (HTTP $code)")
        } catch (t: Throwable) {
            Result.Failure(t.message ?: "Upload failed")
        }
    }
}
