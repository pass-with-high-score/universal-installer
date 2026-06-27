package app.pwhs.core.install

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

    /** Any "{...}" token — used to strip template tags we don't recognise. */
    private val UNRESOLVED_TAG = Regex("\\{[^}]*\\}")

    /** A run of 2+ of the SAME separator char (e.g. "__", "  ", "..") → collapse to one. */
    private val REPEATED_SEPARATOR = Regex("([-_. ])\\1+")

    sealed interface Result {
        data class Success(val uri: Uri) : Result
        data class Failure(val message: String) : Result
    }

    /** Container used for apps that ship split APKs. Single-APK apps always extract as ".apk". */
    enum class SplitFormat { APKS, XAPK }

    suspend fun extract(
        context: Context,
        packageName: String,
        outputDir: DocumentFile? = null,
        filenameTemplate: String = "{name}-{version}",
        splitFormat: SplitFormat = SplitFormat.APKS,
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

        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) { null }

        val versionName = pkgInfo?.versionName ?: ""
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toLong()
            }
        } ?: 0L

        val appName = appInfo.loadLabel(pm).toString()
        
        val targetDir: DocumentFile = outputDir ?: run {
            val defaultPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SUBFOLDER,
            ).apply { mkdirs() }
            DocumentFile.fromFile(defaultPath)
        }

        if (!targetDir.exists() || !targetDir.isDirectory) {
            return@withContext Result.Failure("Output directory does not exist or is not a directory")
        }

        val resolvedName = resolveTemplate(
            template = filenameTemplate,
            name = appName,
            version = versionName,
            code = versionCode.toString(),
            pkg = packageName,
        )
        val isXapk = splitDirs.isNotEmpty() && splitFormat == SplitFormat.XAPK
        val targetExt = when {
            splitDirs.isEmpty() -> "apk"
            isXapk -> "xapk"
            else -> "apks"
        }
        val mimeType = if (splitDirs.isEmpty()) "application/vnd.android.package-archive" else "application/zip"
        
        val finalFileName = uniqueName(targetDir, "$resolvedName.$targetExt")
        // Both RawDocumentFile (the default Downloads path) and the SAF providers append a
        // MIME-derived extension to whatever display name we pass — ".apk" for the package
        // MIME, ".zip" for zip. Passing our already-suffixed name doubled it
        // ("App.apk" → "App.apk.apk", "App.apks" → "App.apks.zip"). So we create with the
        // bare stem (provider appends one clean extension) and then rename to the exact name
        // we want; renameTo writes the literal name with no further mangling.
        val targetFile = targetDir.createFile(mimeType, finalFileName.substringBeforeLast('.'))
            ?: return@withContext Result.Failure("Could not create target file")
        if (targetFile.name != finalFileName) {
            runCatching { targetFile.renameTo(finalFileName) }
        }

        val totalBytes = baseApk.length() + splitDirs.sumOf { it.length() }

        return@withContext try {
            when {
                splitDirs.isEmpty() ->
                    copyFile(context, baseApk, targetFile, totalBytes, 0L, onProgress)
                isXapk -> {
                    val manifest = buildXapkManifest(
                        packageName = packageName,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0,
                        targetSdk = appInfo.targetSdkVersion,
                        baseApk = baseApk,
                        splits = splitDirs,
                    )
                    writeXapkBundle(context, baseApk, splitDirs, manifest, targetFile, totalBytes, onProgress)
                }
                else ->
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

    /**
     * Write an XAPK bundle: a ZIP holding `manifest.json` plus the base + split APKs under
     * their original names. This is the layout APKPure / SAI and similar installers expect,
     * so an extracted XAPK can be re-installed by those tools (and by our own importer).
     */
    private fun writeXapkBundle(
        context: Context,
        baseApk: File,
        splits: List<File>,
        manifestJson: String,
        target: DocumentFile,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var copied = 0L
        val os = context.contentResolver.openOutputStream(target.uri)
            ?: throw IllegalStateException("Could not open output stream")

        ZipOutputStream(os.buffered()).use { zip ->
            // manifest.json is small text — let it deflate (default). APK entries are already
            // compressed, so they go in STORED to avoid wasting CPU re-compressing.
            val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()

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

    /** Build the XAPK v2 `manifest.json` describing the base + split APKs. */
    private fun buildXapkManifest(
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        minSdk: Int,
        targetSdk: Int,
        baseApk: File,
        splits: List<File>,
    ): String {
        val splitApks = org.json.JSONArray()
        splitApks.put(org.json.JSONObject().put("file", "base.apk").put("id", "base"))
        for (split in splits) {
            splitApks.put(
                org.json.JSONObject()
                    .put("file", split.name)
                    .put("id", splitId(split.name)),
            )
        }
        val totalSize = baseApk.length() + splits.sumOf { it.length() }
        return org.json.JSONObject()
            .put("xapk_version", 2)
            .put("package_name", packageName)
            .put("name", appName)
            .put("version_code", versionCode.toString())
            .put("version_name", versionName)
            .put("min_sdk_version", if (minSdk > 0) minSdk.toString() else "")
            .put("target_sdk_version", if (targetSdk > 0) targetSdk.toString() else "")
            .put("total_size", totalSize)
            .put("split_apks", splitApks)
            .toString(2)
    }

    /** "split_config.arm64_v8a.apk" → "config.arm64_v8a"; otherwise the bare filename stem. */
    private fun splitId(fileName: String): String =
        fileName.removeSuffix(".apk").removePrefix("split_")

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
        code: String,
        pkg: String,
    ): String {
        val resolved = template
            .replace("{name}", name)
            .replace("{version}", version)
            .replace("{code}", code)
            .replace("{package}", pkg)
            .replace("{pkg}", pkg)
            // Drop any tags we don't recognise (e.g. a typo'd "{foo}"). Without this they'd
            // survive into sanitize(), which turns the stray "{" / "}" into "_" and leaves
            // litter like "App__foo_" in the filename.
            .replace(UNRESOLVED_TAG, "")
            // A removed tag can leave a dangling separator ("App-" from "{name}-{foo}", or
            // "App__" from "{name}_{foo}"). Collapse runs and trim edges so the result is clean.
            .replace(REPEATED_SEPARATOR, "$1")
            .trim(' ', '-', '_', '.')
        // If the template resolved to nothing usable (e.g. just "{foo}"), fall back to the
        // app name so we never emit a file literally named "app".
        return sanitize(resolved.ifBlank { name })
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
