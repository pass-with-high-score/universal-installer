package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
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

    /**
     * Copy standalone OBB files (user picked them via OpenDocument) into
     * `/storage/emulated/0/Android/obb/<pkg>/`. Filenames preserved from `AttachedObb`.
     */
    suspend fun extractFromUris(
        context: Context,
        files: List<AttachedObb>,
        packageName: String,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit,
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext Result.success(0)
        val targetDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext Result.failure(IOException("Cannot create $targetDir"))
        }
        val totalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        var bytesCopied = 0L
        val created = mutableListOf<File>()
        try {
            for (file in files) {
                coroutineContext.ensureActive()
                val input = context.contentResolver.openInputStream(file.uri)
                    ?: throw IOException("Cannot open ${file.fileName}")
                val out = File(targetDir, file.fileName)
                created.add(out)
                input.buffered().use { ins ->
                    out.outputStream().buffered().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = ins.read(buf)
                            if (n <= 0) break
                            os.write(buf, 0, n)
                            bytesCopied += n
                            onProgress(bytesCopied, totalBytes)
                        }
                    }
                }
            }
            Result.success(created.size)
        } catch (ce: CancellationException) {
            created.forEach { runCatching { it.delete() } }
            throw ce
        } catch (t: Throwable) {
            created.forEach { runCatching { it.delete() } }
            Timber.e(t, "Attached-OBB copy failed")
            Result.failure(t)
        }
    }

    /**
     * Stream OBB entries from the source zip into `/storage/emulated/0/Android/obb/<pkg>/`.
     * Deletes any partial files on failure so we don't leave a half-copied OBB behind.
     */
    suspend fun extract(
        context: Context,
        uri: Uri,
        entries: List<ObbEntry>,
        packageName: String,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit,
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext Result.success(0)

        val targetDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext Result.failure(IOException("Cannot create $targetDir"))
        }

        val entryPaths = entries.map { it.entryPath }.toSet()
        val totalBytes = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        var bytesCopied = 0L
        val created = mutableListOf<File>()

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Cannot open source"))
            input.buffered().use { bufferedInput ->
                ZipInputStream(bufferedInput).use { zis ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zis.nextEntry ?: break
                        if (entry.isDirectory || entry.name !in entryPaths) {
                            zis.closeEntry()
                            continue
                        }
                        val out = File(targetDir, entry.name.substringAfterLast('/'))
                        created.add(out)
                        out.outputStream().buffered().use { os ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = zis.read(buf)
                                if (n <= 0) break
                                os.write(buf, 0, n)
                                bytesCopied += n
                                onProgress(bytesCopied, totalBytes)
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
            Result.success(created.size)
        } catch (ce: CancellationException) {
            created.forEach { runCatching { it.delete() } }
            throw ce
        } catch (t: Throwable) {
            created.forEach { runCatching { it.delete() } }
            Timber.e(t, "OBB extract failed")
            Result.failure(t)
        }
    }
}
