package app.pwhs.universalinstaller.presentation.manage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Extracts an installed app's APK(s) to /sdcard/Download/UniversalInstaller/Extracted.
 *
 * - No splits → straight `.apk` copy.
 * - Splits → `.apks` zip (bundletool format) containing `base.apk` plus each split's original
 *   on-disk filename (e.g. `split_config.arm64_v8a.apk`). Verbatim names so ackpine's
 *   split-type detection (which keys off `splitName` in each split's manifest, not the
 *   filename) keeps working when the file is re-installed.
 *
 * Inner entries are written with [ZipEntry.STORED] — APK content is already compressed,
 * so DEFLATE wastes CPU for ~0% size gain and slows extraction by 5–10× on large apps.
 */
object ApkExtractor {

    private const val SUBFOLDER = "UniversalInstaller/Extracted"
    private const val COPY_BUFFER = 64 * 1024

    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * [outputDir] defaults to the user-visible Download/UniversalInstaller/Extracted folder.
     * Pass a cache directory (e.g. `cacheDir/share/`) when extracting for one-shot use like
     * the Share action so the file doesn't pollute the user's Backups list.
     */
    suspend fun extract(
        context: Context,
        packageName: String,
        outputDir: File? = null,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo: ApplicationInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext Result.Failure("Package not found: $packageName")
        }

        val baseApk = File(appInfo.sourceDir ?: return@withContext Result.Failure("No sourceDir"))
        if (!baseApk.exists() || !baseApk.canRead()) {
            return@withContext Result.Failure("APK not readable: ${baseApk.path}")
        }

        val splitDirs = appInfo.splitSourceDirs
            ?.mapNotNull { path -> path?.let(::File)?.takeIf { it.exists() && it.canRead() } }
            ?: emptyList()

        val versionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).versionName
            }
        } catch (_: Exception) { null } ?: ""

        val appName = appInfo.loadLabel(pm).toString()
        val effectiveOutputDir = (outputDir ?: File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUBFOLDER,
        )).apply { mkdirs() }

        val baseName = sanitize(appName) +
            (if (versionName.isNotBlank()) "-$versionName" else "")
        val targetExt = if (splitDirs.isEmpty()) "apk" else "apks"
        val target = File(
            effectiveOutputDir,
            uniqueName(effectiveOutputDir, "$baseName.$targetExt"),
        )

        val totalBytes = baseApk.length() + splitDirs.sumOf { it.length() }

        return@withContext try {
            if (splitDirs.isEmpty()) {
                copyFile(baseApk, target, totalBytes, 0L, onProgress)
            } else {
                writeSplitBundle(baseApk, splitDirs, target, totalBytes, onProgress)
            }
            Result.Success(target)
        } catch (t: Throwable) {
            runCatching { target.delete() }
            Result.Failure(t.message ?: t::class.java.simpleName)
        }
    }

    private fun copyFile(
        source: File,
        target: File,
        totalBytes: Long,
        startOffset: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var copied = startOffset
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    copied += read
                    onProgress(copied, totalBytes)
                }
            }
        }
    }

    private fun writeSplitBundle(
        baseApk: File,
        splits: List<File>,
        target: File,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var copied = 0L
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.setLevel(0)
            // Base APK keeps a stable canonical name so the split-installer can find it
            // even if the platform ever stops naming the base "base.apk" on disk.
            copied += addStoredEntry(zip, baseApk, "base.apk") { delta ->
                onProgress(copied + delta, totalBytes)
            }
            for (split in splits) {
                copied += addStoredEntry(zip, split, split.name) { delta ->
                    onProgress(copied + delta, totalBytes)
                }
            }
        }
    }

    /**
     * Writes [source] into [zip] under [entryName] using STORED (no compression). STORED
     * requires size + crc up front, so we make one streaming pass to compute crc, then a
     * second pass to write the data — still O(n) and far cheaper than DEFLATE's CPU.
     */
    private fun addStoredEntry(
        zip: ZipOutputStream,
        source: File,
        entryName: String,
        onDelta: (Long) -> Unit,
    ): Long {
        val crc = CRC32()
        source.inputStream().use { input ->
            val buf = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                crc.update(buf, 0, read)
            }
        }
        val size = source.length()
        val entry = ZipEntry(entryName).apply {
            method = ZipEntry.STORED
            this.size = size
            compressedSize = size
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        var written = 0L
        source.inputStream().use { input ->
            val buf = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                zip.write(buf, 0, read)
                written += read
                onDelta(written)
            }
        }
        zip.closeEntry()
        return size
    }

    private fun sanitize(name: String): String {
        // Strip path separators, control chars, and characters that break common file managers.
        // Keep unicode letters/digits — Android file systems accept them and users often see
        // localized app names.
        val cleaned = name.map { c ->
            when {
                c.isLetterOrDigit() -> c
                c == ' ' || c == '-' || c == '_' || c == '.' -> c
                else -> '_'
            }
        }.joinToString("").trim().ifBlank { "app" }
        return cleaned.take(80)
    }

    private fun uniqueName(dir: File, desired: String): String {
        if (!File(dir, desired).exists()) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.take(dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (File(dir, "$stem ($i)$ext").exists()) i++
        return "$stem ($i)$ext"
    }

}
