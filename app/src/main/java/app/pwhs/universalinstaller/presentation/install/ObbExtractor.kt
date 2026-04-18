package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class ObbEntry(
    val entryPath: String,   // path inside the zip (e.g. "Android/obb/com.foo/main.1.com.foo.obb")
    val fileName: String,    // just the filename (e.g. "main.1.com.foo.obb")
    val sizeBytes: Long,     // -1 if unknown
)

/**
 * Scans + extracts `.obb` entries from bundle archives (XAPK/APKM/APKS/zip).
 *
 * AppManager uses the same "match `.obb` suffix, preserve filename" heuristic — the XAPK
 * `manifest.json` declares specific `install_path` targets, but in practice the filename
 * itself (e.g. `main.1.com.foo.obb`) is what Android's expansion-file API looks for, so we
 * keep it simple and don't parse the manifest.
 */
object ObbExtractor {

    /** Lightweight scan — walks local file headers, never reads entry content. */
    suspend fun scan(context: Context, uri: Uri): List<ObbEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ObbEntry>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
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

}
