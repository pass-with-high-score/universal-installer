package app.pwhs.universalinstaller.presentation.manage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Extracts an installed app's APK(s).
 */
object ApkExtractor {

    private const val SUBFOLDER = "UniversalInstaller/Extracted"
    private const val COPY_BUFFER = 64 * 1024

    sealed interface Result {
        data class Success(val uri: Uri) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun extract(
        context: Context,
        packageName: String,
        outputDirUri: String? = null,
        filenameTemplate: String = "{name}-{version}",
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
        
        val outputDir: DocumentFile = if (!outputDirUri.isNullOrBlank()) {
            DocumentFile.fromTreeUri(context, Uri.parse(outputDirUri))
                ?: return@withContext Result.Failure("Invalid output directory URI")
        } else {
            val defaultPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SUBFOLDER,
            ).apply { mkdirs() }
            DocumentFile.fromFile(defaultPath)
        }

        if (!outputDir.exists() || !outputDir.isDirectory) {
            return@withContext Result.Failure("Output directory does not exist or is not a directory")
        }

        val resolvedName = resolveTemplate(
            template = filenameTemplate,
            name = appName,
            version = versionName,
            pkg = packageName,
        )
        val targetExt = if (splitDirs.isEmpty()) "apk" else "apks"
        val mimeType = if (splitDirs.isEmpty()) "application/vnd.android.package-archive" else "application/zip"
        
        val finalFileName = uniqueName(outputDir, "$resolvedName.$targetExt")
        // Both RawDocumentFile (the default Downloads path) and the SAF providers append a
        // MIME-derived extension to whatever display name we pass — ".apk" for the package
        // MIME, ".zip" for zip. Passing our already-suffixed name doubled it
        // ("App.apk" → "App.apk.apk", "App.apks" → "App.apks.zip"). So we create with the
        // bare stem (provider appends one clean extension) and then rename to the exact name
        // we want; renameTo writes the literal name with no further mangling.
        val targetFile = outputDir.createFile(mimeType, finalFileName.substringBeforeLast('.'))
            ?: return@withContext Result.Failure("Could not create target file")
        if (targetFile.name != finalFileName) {
            runCatching { targetFile.renameTo(finalFileName) }
        }

        val totalBytes = baseApk.length() + splitDirs.sumOf { it.length() }

        return@withContext try {
            if (splitDirs.isEmpty()) {
                copyFile(context, baseApk, targetFile, totalBytes, 0L, onProgress)
            } else {
                writeSplitBundle(context, baseApk, splitDirs, targetFile, totalBytes, onProgress)
            }
            Result.Success(targetFile.uri)
        } catch (t: Throwable) {
            targetFile.delete()
            Result.Failure(t.message ?: t::class.java.simpleName)
        }
    }

    private fun copyFile(
        context: Context,
        source: File,
        target: DocumentFile,
        totalBytes: Long,
        startOffset: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var copied = startOffset
        source.inputStream().use { input ->
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                val buf = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    copied += read
                    onProgress(copied, totalBytes)
                }
            } ?: throw IllegalStateException("Could not open output stream")
        }
    }

    private fun writeSplitBundle(
        context: Context,
        baseApk: File,
        splits: List<File>,
        target: DocumentFile,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var copied = 0L
        val os = context.contentResolver.openOutputStream(target.uri) 
            ?: throw IllegalStateException("Could not open output stream")
            
        ZipOutputStream(os.buffered()).use { zip ->
            zip.setLevel(0)
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
        val cleaned = name.map { c ->
            when {
                c.isLetterOrDigit() -> c
                c == ' ' || c == '-' || c == '_' || c == '.' -> c
                else -> '_'
            }
        }.joinToString("").trim().ifBlank { "app" }
        return cleaned.take(80)
    }

    private fun resolveTemplate(
        template: String,
        name: String,
        version: String,
        pkg: String,
    ): String {
        val resolved = template
            .replace("{name}", name)
            .replace("{version}", version)
            .replace("{package}", pkg)
            .replace("{pkg}", pkg)
        return sanitize(resolved)
    }

    private fun uniqueName(dir: DocumentFile, desired: String): String {
        if (dir.findFile(desired) == null) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.take(dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (dir.findFile("$stem ($i)$ext") != null) i++
        return "$stem ($i)$ext"
    }

}
