package app.pwhs.core.receiver

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * LAN receiver: a phone (scanning the TV's QR) opens the upload page or POSTs an APK here;
 * the file is staged in cache and emitted via [TvReceiverState] for the TV UI to install.
 *
 * Streams uploads directly from the socket input stream with live progress updates.
 */
class ApkReceiverServer(
    private val context: Context,
    port: Int,
) : NanoHTTPD(port) {

    private val stageDir: File = File(context.cacheDir, "received").apply { mkdirs() }

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> uploadPage(session)
            session.method == Method.GET && session.uri == "/ping" -> handlePing(session)
            session.method == Method.GET && session.uri == "/disconnect" -> handleDisconnect(session)
            session.method == Method.GET && session.uri == "/logo.png" -> serveLogo()
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveLogo(): Response {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            newFixedLengthResponse(Response.Status.OK, "image/png", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Logo not found")
        }
    }

    private fun uploadPage(session: IHTTPSession): Response {
        recordClient(session)
        val html = try {
            context.assets.open("upload_page.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h2>Error loading UI</h2><p>${e.message}</p></body></html>"
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handlePing(session: IHTTPSession): Response {
        recordClient(session)
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "pong")
    }

    private fun handleDisconnect(session: IHTTPSession): Response {
        TvReceiverState.updateConnectedClient(null)
        TvReceiverState.emitReceivingProgress(null)
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
    }

    private fun cleanIp(raw: String?): String {
        if (raw.isNullOrBlank()) return "Phone"
        var ip = raw.trimStart('/')
        if (ip == "0:0:0:0:0:0:0:1" || ip == "::1") ip = "127.0.0.1"
        return ip
    }

    private fun recordClient(session: IHTTPSession) {
        val clientIp = cleanIp(session.remoteIpAddress)
        val userAgent = session.headers["user-agent"]
        val deviceName = parseDeviceFromUserAgent(userAgent) ?: clientIp
        TvReceiverState.updateConnectedClient(ConnectedClient(ip = clientIp, deviceName = deviceName))
    }

    private fun parseDeviceFromUserAgent(ua: String?): String? {
        if (ua == null) return null
        return when {
            ua.contains("Universal Installer App", ignoreCase = true) -> {
                ua.substringBefore(" (Universal").trim()
            }
            ua.contains("Android", ignoreCase = true) -> {
                val match = Regex(";\\s*([^;]+)\\s*Build").find(ua)?.groupValues?.get(1)
                match?.trim() ?: "Android Device"
            }
            ua.contains("iPhone", ignoreCase = true) -> "iPhone"
            ua.contains("iPad", ignoreCase = true) -> "iPad"
            ua.contains("Macintosh", ignoreCase = true) -> "Mac"
            ua.contains("Windows", ignoreCase = true) -> "Windows PC"
            ua.contains("Linux", ignoreCase = true) -> "Linux PC"
            else -> null
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        recordClient(session)

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val contentType = session.headers["content-type"] ?: ""
        val boundary = Regex("boundary=([^;\\s]+)").find(contentType)?.groupValues?.get(1)?.trim('"')

        // Initial progress: 0%
        TvReceiverState.emitReceivingProgress(
            ReceivingProgress(bytesReceived = 0L, totalBytes = contentLength.coerceAtLeast(1L))
        )

        return try {
            val savedFile = if (!boundary.isNullOrBlank()) {
                streamMultipartUpload(session.inputStream, boundary, contentLength, stageDir)
            } else {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val tempPath = files.values.firstOrNull()
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file")
                val original = session.parameters["apk"]?.firstOrNull()?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() } ?: "received-${System.currentTimeMillis()}.apk"
                val dest = File(stageDir, sanitize(original))
                File(tempPath).copyTo(dest, overwrite = true)
                dest
            }

            // Show 100% briefly
            TvReceiverState.emitReceivingProgress(
                ReceivingProgress(bytesReceived = savedFile.length(), totalBytes = savedFile.length())
            )
            Thread.sleep(100)
            TvReceiverState.emitReceivingProgress(null)
            TvReceiverState.emitReceived(
                ReceivedApk(path = savedFile.absolutePath, fileName = savedFile.name, sizeBytes = savedFile.length())
            )

            newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<h2>Sent ✓ — confirm the install on your TV.</h2>")
        } catch (t: Throwable) {
            Log.e(TAG, "Upload failed", t)
            TvReceiverState.emitReceivingProgress(null)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${t.message}")
        }
    }

    /**
     * Streams the multipart request body directly from [input] to disk,
     * emitting [TvReceiverState.emitReceivingProgress] as bytes stream in real-time.
     */
    private fun streamMultipartUpload(
        input: InputStream,
        boundary: String,
        contentLength: Long,
        destDir: File
    ): File {
        var fileName = "received-${System.currentTimeMillis()}.apk"
        val headerBytes = ByteArrayOutputStream()
        var prev = -1
        var prevPrev = -1
        var prev3 = -1

        var b = input.read()
        var headerCount = 0
        while (b != -1) {
            headerCount++
            headerBytes.write(b)
            if (prev3 == '\r'.code && prevPrev == '\n'.code && prev == '\r'.code && b == '\n'.code) {
                break
            }
            if (prev == '\n'.code && b == '\n'.code) {
                break
            }
            prev3 = prevPrev
            prevPrev = prev
            prev = b
            if (headerCount > 8192) break
            b = input.read()
        }

        val headerText = headerBytes.toString("UTF-8")
        val fnMatch = Regex("filename=\"([^\"]+)\"").find(headerText)
            ?: Regex("filename=([^;\\r\\n]+)").find(headerText)
        if (fnMatch != null) {
            val rawName = fnMatch.groupValues[1].trim().substringAfterLast('/').substringAfterLast('\\')
            if (rawName.isNotBlank()) fileName = sanitize(rawName)
        }

        val destFile = File(destDir, fileName)
        val tempFile = File(destDir, "$fileName.tmp")

        val boundaryMarker = ("\r\n--$boundary").toByteArray(Charsets.US_ASCII)
        val markerLen = boundaryMarker.size
        val totalExpected = (contentLength - headerCount - markerLen - 4).coerceAtLeast(1L)

        var lastProgressMs = 0L
        var totalWritten = 0L
        var remainingBodyBytes = if (contentLength > 0) (contentLength - headerCount) else Long.MAX_VALUE
        val estimator = app.pwhs.core.util.TransferEstimator()

        FileOutputStream(tempFile).use { fos ->
            val buf = ByteArray(64 * 1024)
            val tail = ByteArray(markerLen + 8)
            var tailLen = 0

            while (remainingBodyBytes > 0) {
                val toRead = minOf(buf.size.toLong(), remainingBodyBytes).toInt()
                val read = input.read(buf, 0, toRead)
                if (read == -1) break
                remainingBodyBytes -= read

                if (tailLen > 0) {
                    val combined = ByteArray(tailLen + read)
                    System.arraycopy(tail, 0, combined, 0, tailLen)
                    System.arraycopy(buf, 0, combined, tailLen, read)

                    val toWrite = (combined.size - tail.size).coerceAtLeast(0)
                    if (toWrite > 0) {
                        fos.write(combined, 0, toWrite)
                        totalWritten += toWrite
                    }
                    tailLen = combined.size - toWrite
                    System.arraycopy(combined, toWrite, tail, 0, tailLen)
                } else {
                    if (read > tail.size) {
                        val toWrite = read - tail.size
                        fos.write(buf, 0, toWrite)
                        totalWritten += toWrite
                        tailLen = tail.size
                        System.arraycopy(buf, toWrite, tail, 0, tailLen)
                    } else {
                        System.arraycopy(buf, 0, tail, 0, read)
                        tailLen = read
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastProgressMs >= 50L || totalWritten >= totalExpected) {
                    lastProgressMs = now
                    val written = totalWritten.coerceAtMost(totalExpected)
                    val estimate = estimator.update(written, totalExpected)
                    TvReceiverState.emitReceivingProgress(
                        ReceivingProgress(
                            bytesReceived = written,
                            totalBytes = totalExpected,
                            speedBytesPerSec = estimate.speedBytesPerSec,
                            etaSeconds = estimate.etaSeconds,
                        )
                    )
                }
            }

            if (tailLen > 0) {
                var boundaryIdx = -1
                for (i in 0..(tailLen - markerLen)) {
                    var match = true
                    for (j in 0 until markerLen) {
                        if (tail[i + j] != boundaryMarker[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        boundaryIdx = i
                        break
                    }
                }
                val finalBytesToWrite = if (boundaryIdx >= 0) boundaryIdx else tailLen
                if (finalBytesToWrite > 0) {
                    fos.write(tail, 0, finalBytesToWrite)
                    totalWritten += finalBytesToWrite
                }
            }
            fos.flush()
        }

        if (destFile.exists()) destFile.delete()
        tempFile.renameTo(destFile)
        return destFile
    }

    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("").take(120)

    companion object {
        private const val TAG = "ApkReceiverServer"
    }
}
