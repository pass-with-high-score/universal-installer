package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLEncoder

class ApkHttpServer(
    private val context: Context,
    port: Int,
    private val requirePin: Boolean,
    private val pinCode: String,
    private val onConnectionChange: (Int) -> Unit
) : NanoHTTPD(port) {

    private val baseDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "UniversalInstaller",
    )

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val cookies = session.cookies

        // 1. PIN Authentication
        if (requirePin) {
            val hasAuthCookie = cookies.read("sync_auth") == "true"
            if (!hasAuthCookie) {
                try {
                    session.parseBody(HashMap<String, String>())
                } catch (e: Exception) {
                    // Ignore parse errors
                }
                
                val params = session.parameters
                if (session.method == Method.POST && params["pin"]?.firstOrNull() == pinCode) {
                    val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
                    response.addHeader("Location", uri)
                    response.addHeader("Set-Cookie", "sync_auth=true; Path=/; Max-Age=86400")
                    return response
                }
                
                // Show PIN form
                val authHtml = try {
                    context.assets.open("sync_auth.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "<html><body>Error loading auth template</body></html>"
                }

                return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED, MIME_HTML, authHtml
                )
            }
        }

        // 2. Handle File Upload
        if (session.method == Method.POST && (uri == "/upload" || uri.startsWith("/upload"))) {
            return handleUpload(session)
        }

        // 3. Serve Files
        if (uri == "/") {
            return serveDirectoryList()
        }

        // 4. Serve File Content
        val fileName = uri.substringAfterLast("/")
        val file = File(baseDir, fileName)
        
        if (!file.exists() || file.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found.")
        }

        try {
            val mime = when {
                fileName.endsWith(".apk", true) -> "application/vnd.android.package-archive"
                fileName.endsWith(".apks", true) -> "application/vnd.apks"
                fileName.endsWith(".xapk", true) -> "application/vnd.xapk"
                fileName.endsWith(".apkm", true) -> "application/vnd.apkm"
                fileName.endsWith(".apk+", true) -> "application/vnd.apks" // Same as APKS
                else -> "application/octet-stream"
            }

            val totalBytes = file.length()
            val fis = FileInputStream(file)
            // Wrap with tracking to detect download start/finish and report progress
            onConnectionChange(1)
            val transferId = SyncManager.nextTransferId()
            val trackingStream = TrackingInputStream(
                stream = fis,
                transferId = transferId,
                fileName = file.name,
                totalBytes = totalBytes,
                onClose = {
                    SyncManager.removeTransfer(transferId)
                    onConnectionChange(-1)
                }
            )
            
            val response = newChunkedResponse(Response.Status.OK, mime, trackingStream)
            // Use RFC 5987 filename* for proper encoding of special characters like ()
            val safeFilename = file.name.replace("\"", "\\\"")
            val encodedFilename = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            response.addHeader("Content-Disposition", "attachment; filename=\"$safeFilename\"; filename*=UTF-8''$encodedFilename")
            response.addHeader("Content-Length", totalBytes.toString())
            return response
        } catch (e: Exception) {
            onConnectionChange(-1) // Ensure decrement on error
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file.")
        }
    }

    private val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip", "apk+")

    private fun handleUpload(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val contentType = session.headers["content-type"] ?: ""
        val boundary = Regex("boundary=([^;\\s]+)").find(contentType)?.groupValues?.get(1)?.trim('"')
        val transferId = SyncManager.nextTransferId()
        val uploadDir = File(baseDir, "received").apply { if (!exists()) mkdirs() }

        return try {
            val savedFile = if (!boundary.isNullOrBlank()) {
                streamMultipartUpload(session.inputStream, boundary, contentLength, uploadDir, transferId)
            } else {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val tempPath = files.values.firstOrNull()
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file uploaded")
                val original = session.parameters["file"]?.firstOrNull()
                    ?: session.parameters["apk"]?.firstOrNull()
                    ?: "uploaded-${System.currentTimeMillis()}.apk"
                val rawName = original.substringAfterLast('/').substringAfterLast('\\')
                val dest = File(uploadDir, sanitize(rawName))
                File(tempPath).copyTo(dest, overwrite = true)
                dest
            }

            val ext = savedFile.extension.lowercase()
            if (ext !in validExtensions && !savedFile.name.endsWith(".apk+", true)) {
                savedFile.delete()
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    MIME_PLAINTEXT,
                    "Unsupported file format. Please upload APK, APKS, XAPK, APKM, or ZIP."
                )
            }

            SyncManager.emitFileReceived(savedFile)
            val json = """{"status":"ok","fileName":"${savedFile.name}","size":${savedFile.length()}}"""
            newFixedLengthResponse(Response.Status.OK, "application/json", json)
        } catch (t: Throwable) {
            SyncManager.removeTransfer(transferId)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload error: ${t.message}")
        }
    }

    private fun streamMultipartUpload(
        input: InputStream,
        boundary: String,
        contentLength: Long,
        destDir: File,
        transferId: String,
    ): File {
        var fileName = "uploaded-${System.currentTimeMillis()}.apk"
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

        try {
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
                    if (now - lastProgressMs >= 100L || totalWritten >= totalExpected) {
                        lastProgressMs = now
                        val written = totalWritten.coerceAtMost(totalExpected)
                        SyncManager.updateProgress(transferId, fileName, written, totalExpected)
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
        } finally {
            SyncManager.removeTransfer(transferId)
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("").take(120)

    private fun serveDirectoryList(): Response {
        val files = baseDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".apk", true) || it.name.endsWith(".apks", true) ||
                    it.name.endsWith(".xapk", true) || it.name.endsWith(".apkm", true) ||
                    it.name.endsWith(".apk+", true))
        }?.sortedBy { it.name } ?: emptyList()

        val listHtml = if (files.isEmpty()) {
            """<div class="empty"><div class="empty-icon">📭</div><div class="empty-text">No packages found.<br>Add files via Universal Installer app.</div></div>"""
        } else {
            files.joinToString("\n") { file ->
                val size = formatFileSize(file.length())
                val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                """
                <a href="/$encodedName" class="file-item">
                    <div class="file-icon">📦</div>
                    <div class="file-info">
                        <div class="file-name">${file.name}</div>
                        <div class="file-size">$size</div>
                    </div>
                    <div class="download-btn">
                        <svg viewBox="0 0 24 24"><path d="M12 4v12m0 0l-4-4m4 4l4-4M5 18h14" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
                    </div>
                </a>
                """.trimIndent()
            }
        }

        val totalBytes = files.sumOf { it.length() }
        val totalSize = formatFileSize(totalBytes)
        val fileCount = files.size.toString()

        val templateHtml = try {
            context.assets.open("sync_index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body>Error loading index template</body></html>"
        }

        return newFixedLengthResponse(
            Response.Status.OK, MIME_HTML,
            templateHtml
                .replace("{{listHtml}}", listHtml)
                .replace("{{fileCount}}", fileCount)
                .replace("{{totalSize}}", totalSize)
        )
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else -> "$bytes B"
        }
    }
}

/**
 * A stream wrapper that fires [onClose] exactly once when the stream is closed
 * and reports transfer progress to [SyncManager].
 * NanoHTTPD always closes the response data stream after sending, so this
 * reliably tracks when a download finishes (or the client disconnects).
 */
private class TrackingInputStream(
    stream: InputStream,
    private val transferId: String,
    private val fileName: String,
    private val totalBytes: Long,
    private val onClose: () -> Unit,
) : FilterInputStream(stream) {
    @Volatile
    private var closed = false
    private var bytesRead: Long = 0L
    private var lastReportedPercent: Int = -1
    private var lastReportTime: Long = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            bytesRead++
            reportProgress()
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            bytesRead += count
            reportProgress()
        }
        return count
    }

    private fun reportProgress() {
        if (totalBytes <= 0) return
        val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()
        // Update when percentage changes or at least every 400ms for smooth speed/ETA
        if (percent != lastReportedPercent || now - lastReportTime >= 400L) {
            lastReportedPercent = percent
            lastReportTime = now
            SyncManager.updateProgress(transferId, fileName, bytesRead, totalBytes)
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            try {
                super.close()
            } finally {
                onClose()
            }
        }
    }
}
