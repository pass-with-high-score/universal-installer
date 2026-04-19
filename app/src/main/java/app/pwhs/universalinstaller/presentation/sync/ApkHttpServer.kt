package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder

class ApkHttpServer(
    private val context: Context,
    port: Int,
    private val requirePin: Boolean,
    private val pinCode: String,
    private val onConnectionChange: (Int) -> Unit
) : NanoHTTPD(port) {

    private val baseDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Universal Installer")

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

        // 2. Serve Files
        if (uri == "/") {
            return serveDirectoryList()
        }

        // 3. Serve File Content
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
                else -> "application/octet-stream"
            }
            
            // Notify when download starts
            onConnectionChange(1)
            
            val trackingFis = object : FileInputStream(file) {
                override fun close() {
                    super.close()
                    onConnectionChange(-1) // Notify when download finishes
                }
            }
            
            val response = newChunkedResponse(Response.Status.OK, mime, trackingFis)
            response.addHeader("Content-Disposition", "attachment; filename=\"${URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")}\"")
            response.addHeader("Content-Length", file.length().toString())
            return response
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file.")
        }
    }

    private fun serveDirectoryList(): Response {
        val files = baseDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".apk", true) || it.name.endsWith(".apks", true) ||
                    it.name.endsWith(".xapk", true) || it.name.endsWith(".apkm", true))
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
