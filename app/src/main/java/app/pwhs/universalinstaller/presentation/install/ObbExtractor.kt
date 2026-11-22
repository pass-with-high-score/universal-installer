package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ObbEntry(
    val entryPath: String,   // path inside the zip (e.g. "Android/obb/com.foo/main.1.com.foo.obb")
    val fileName: String,    // just the filename (e.g. "main.1.com.foo.obb")
    val sizeBytes: Long,     // -1 if unknown
)

/**
 * Scans + extracts `.obb` entries from bundle archives (XAPK/APKM/APKS/zip).
 *
 * Uses [ZipFile] (Central Directory random access, O(entries) in <2ms even on 3GB XAPKs)
 * whenever a seekable file or file descriptor is available, falling back to [ZipInputStream]
 * only for non-seekable streams.
 */
object ObbExtractor {

    suspend fun scan(context: Context, uri: Uri): List<ObbEntry> = withContext(Dispatchers.IO) {
        // 1. Fast path: direct ZipFile on local file:// URI
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    runCatching { return@withContext scanWithZipFile(file) }
                        .onFailure { Timber.w(it, "ZipFile OBB scan failed for $path") }
                }
            }
        }

        // 2. Fast path: ZipFile via seekable /proc/self/fd/<fd> from ContentResolver
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fdFile = File("/proc/self/fd/${pfd.fd}")
                if (fdFile.canRead()) {
                    return@withContext scanWithZipFile(fdFile)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.d(t, "FD ZipFile OBB scan unavailable for $uri — falling back to stream")
        }

        // 3. Fallback: sequential ZipInputStream for non-seekable providers
        val result = mutableListOf<ObbEntry>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered(64 * 1024)).use { zis ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".obb")) {
                            result.add(
                                ObbEntry(
                                    entryPath = entry.name,
                                    fileName = entry.name.substringAfterLast('/'),
                                    sizeBytes = entry.size,
                                )
                            )
                        }
                        zis.closeEntry()
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.e(t, "OBB scan failed for $uri")
        }
        result
    }

    private fun scanWithZipFile(file: File): List<ObbEntry> {
        val result = mutableListOf<ObbEntry>()
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".obb")) {
                    result.add(
                        ObbEntry(
                            entryPath = entry.name,
                            fileName = entry.name.substringAfterLast('/'),
                            sizeBytes = entry.size,
                        )
                    )
                }
            }
        }
        return result
    }
}
