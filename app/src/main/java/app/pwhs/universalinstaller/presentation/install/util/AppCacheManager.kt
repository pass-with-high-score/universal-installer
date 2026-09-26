package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages local application cache and provides staging for external package URIs.
 *
 * Incoming content URIs from third-party apps (e.g. Telegram, WhatsApp, file managers)
 * have transient permissions that are revoked by the Android OS as soon as the receiving
 * Activity terminates. Staging them in [EXTERNAL_APKS_DIR] keeps the package accessible
 * to background installer sessions (Ackpine / Shizuku / Dhizuku / Root).
 */
object AppCacheManager {

    private const val EXTERNAL_APKS_DIR = "external_apks"
    private const val STAGED_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Stages an external content URI into the app's internal cache directory.
     * Returns a [Uri] with `file` scheme pointing to the cached file, or null if staging failed.
     */
    suspend fun stageExternalUri(context: Context, uri: Uri, displayName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, EXTERNAL_APKS_DIR).apply { mkdirs() }
            pruneOldStagedFiles(dir)

            val ext = displayName.substringAfterLast('.', "apk").lowercase()
            val safeBaseName = displayName.substringBeforeLast('.')
                .ifBlank { "package" }
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                .take(40)
            val dst = File(dir, "staged_${System.currentTimeMillis()}_$safeBaseName.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            Uri.fromFile(dst)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stage external content URI: $uri")
            null
        }
    }

    /**
     * Calculates total cached bytes in internal and external cache directories.
     */
    fun getCacheSizeBytes(context: Context): Long {
        var size = 0L
        runCatching {
            context.cacheDir?.let { size += getFolderSize(it) }
            context.externalCacheDir?.let { size += getFolderSize(it) }
        }
        return size
    }

    /**
     * Clears all temporary files in internal and external cache directories.
     * Returns the total number of bytes freed.
     */
    suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
        val initialSize = getCacheSizeBytes(context)
        runCatching {
            context.cacheDir?.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            context.externalCacheDir?.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        }.onFailure {
            Timber.e(it, "Error while clearing application cache")
        }
        val finalSize = getCacheSizeBytes(context)
        (initialSize - finalSize).coerceAtLeast(0L)
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { size += getFolderSize(it) }
        } else {
            size += file.length()
        }
        return size
    }

    private fun pruneOldStagedFiles(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > STAGED_MAX_AGE_MS) {
                file.deleteRecursively()
            }
        }
    }
}
