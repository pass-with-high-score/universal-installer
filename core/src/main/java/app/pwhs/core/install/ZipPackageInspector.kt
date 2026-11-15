package app.pwhs.core.install

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

sealed interface ZipPackageType {
    /** The ZIP is directly an APK (contains AndroidManifest.xml at root). */
    data object SingleApk : ZipPackageType

    /** The ZIP contains split APKs (*.apk entries inside), or manifest.json / toc.pb. */
    data class SplitBundle(val apkEntries: List<String>) : ZipPackageType

    /** Not an installable Android package archive (no AndroidManifest, no APKs inside). */
    data object NotAPackage : ZipPackageType
}

data class ZipInspectionResult(
    val type: ZipPackageType,
    val hasRootManifest: Boolean = false,
    val apkEntries: List<String> = emptyList(),
    val hasManifestJson: Boolean = false,
    val hasTocPb: Boolean = false,
    val manifestPackageName: String? = null,
    val manifestVersionName: String? = null,
    val manifestVersionCode: Long? = null,
) {
    val isInstallable: Boolean
        get() = type !is ZipPackageType.NotAPackage
}

object ZipPackageInspector {

    private const val MAX_STREAM_ENTRIES = 500

    /**
     * Inspects a local [file] by reading its central directory headers.
     */
    fun inspect(file: File): ZipInspectionResult {
        if (!file.exists() || !file.canRead() || file.length() < 22) {
            return ZipInspectionResult(ZipPackageType.NotAPackage)
        }

        var hasRootManifest = false
        val apkEntries = mutableListOf<String>()
        var hasManifestJson = false
        var hasTocPb = false
        var manifestPackageName: String? = null
        var manifestVersionName: String? = null
        var manifestVersionCode: Long? = null

        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
                        hasRootManifest = true
                    }
                    if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) {
                        apkEntries.add(name)
                    }
                    if (name.equals("manifest.json", ignoreCase = true)) {
                        hasManifestJson = true
                        runCatching {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            val json = String(bytes, Charsets.UTF_8)
                            parseXapkManifest(json)?.let {
                                manifestPackageName = it.packageName
                                manifestVersionName = it.versionName
                                manifestVersionCode = it.versionCode
                            }
                        }
                    }
                    if (name.equals("toc.pb", ignoreCase = true)) {
                        hasTocPb = true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read zip central directory for %s", file.name)
            return ZipInspectionResult(ZipPackageType.NotAPackage)
        }

        val type = determineType(hasRootManifest, apkEntries, hasManifestJson, hasTocPb)
        return ZipInspectionResult(
            type = type,
            hasRootManifest = hasRootManifest,
            apkEntries = apkEntries,
            hasManifestJson = hasManifestJson,
            hasTocPb = hasTocPb,
            manifestPackageName = manifestPackageName,
            manifestVersionName = manifestVersionName,
            manifestVersionCode = manifestVersionCode,
        )
    }

    /**
     * Inspects a [uri] by checking if it's a file or streaming its zip entry headers.
     */
    fun inspect(context: Context, uri: Uri): ZipInspectionResult {
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            val file = File(uri.path!!)
            if (file.exists() && file.canRead()) {
                return inspect(file)
            }
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                inspectStream(stream)
            } ?: ZipInspectionResult(ZipPackageType.NotAPackage)
        } catch (e: Exception) {
            Timber.w(e, "Failed to inspect zip stream from %s", uri)
            ZipInspectionResult(ZipPackageType.NotAPackage)
        }
    }

    fun isZipPackage(file: File): Boolean = inspect(file).isInstallable

    fun isZipPackage(context: Context, uri: Uri): Boolean = inspect(context, uri).isInstallable

    private fun inspectStream(stream: InputStream): ZipInspectionResult {
        var hasRootManifest = false
        val apkEntries = mutableListOf<String>()
        var hasManifestJson = false
        var hasTocPb = false
        var manifestPackageName: String? = null
        var manifestVersionName: String? = null
        var manifestVersionCode: Long? = null

        var count = 0
        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && count < MAX_STREAM_ENTRIES) {
                count++
                val name = entry.name

                if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
                    hasRootManifest = true
                }
                if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) {
                    apkEntries.add(name)
                }
                if (name.equals("manifest.json", ignoreCase = true)) {
                    hasManifestJson = true
                    runCatching {
                        val bytes = zip.readBytes()
                        val json = String(bytes, Charsets.UTF_8)
                        parseXapkManifest(json)?.let {
                            manifestPackageName = it.packageName
                            manifestVersionName = it.versionName
                            manifestVersionCode = it.versionCode
                        }
                    }
                }
                if (name.equals("toc.pb", ignoreCase = true)) {
                    hasTocPb = true
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val type = determineType(hasRootManifest, apkEntries, hasManifestJson, hasTocPb)
        return ZipInspectionResult(
            type = type,
            hasRootManifest = hasRootManifest,
            apkEntries = apkEntries,
            hasManifestJson = hasManifestJson,
            hasTocPb = hasTocPb,
            manifestPackageName = manifestPackageName,
            manifestVersionName = manifestVersionName,
            manifestVersionCode = manifestVersionCode,
        )
    }

    private fun determineType(
        hasRootManifest: Boolean,
        apkEntries: List<String>,
        hasManifestJson: Boolean,
        hasTocPb: Boolean,
    ): ZipPackageType {
        return when {
            // Root has AndroidManifest.xml and no nested APKs: It is a single APK renamed as .zip
            hasRootManifest && apkEntries.isEmpty() -> ZipPackageType.SingleApk

            // Has APKs inside, or bundle manifest/TOC: It is a split bundle
            apkEntries.isNotEmpty() || hasManifestJson || hasTocPb -> ZipPackageType.SplitBundle(apkEntries)

            // Has AndroidManifest somewhere (e.g. nested in root): Single APK fallback
            hasRootManifest -> ZipPackageType.SingleApk

            // No Android package content found at all
            else -> ZipPackageType.NotAPackage
        }
    }

    private data class ParsedManifest(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long?,
    )

    private fun parseXapkManifest(json: String): ParsedManifest? {
        return try {
            val obj = JSONObject(json)
            val pkg = obj.optString("package_name").takeIf { it.isNotBlank() } ?: return null
            val verName = obj.optString("version_name").takeIf { it.isNotBlank() }
            val verCode = if (obj.has("version_code")) obj.optLong("version_code", 0L) else null
            ParsedManifest(pkg, verName, verCode)
        } catch (_: Exception) {
            null
        }
    }
}
