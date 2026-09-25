package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.util.extension.getDisplayName
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object PackageFileFilter {

    val SUPPORTED_PACKAGE_EXTENSIONS = setOf(
        "apk",
        "apks",
        "xapk",
        "apkm",
        "apk+",
        "zip",
    )

    /**
     * MIME types passed to Android file picker contracts (e.g. OpenMultipleDocuments)
     * to restrict selectable files to packages and archives.
     */
    val PACKAGE_MIME_TYPES = arrayOf(
        "application/vnd.android.package-archive",
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

    private val KNOWN_NON_PACKAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "heic", "webp", "gif", "bmp", "svg",
        "exe", "msi", "dmg", "pkg", "iso", "bin",
        "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp3", "m4a", "wav", "flac", "ogg",
        "mp4", "mkv", "avi", "mov", "flv", "webm",
    )

    fun isSupportedExtension(extension: String?): Boolean {
        if (extension.isNullOrBlank()) return false
        return extension.lowercase() in SUPPORTED_PACKAGE_EXTENSIONS
    }

    /**
     * Validates whether [uri] points to a supported Android package format by checking:
     * 1. Known invalid extensions (immediately rejected).
     * 2. Package MIME type and supported extensions.
     * 3. Magic bytes validation (all valid APK/APKS/XAPK/APKM/ZIP formats start with ZIP magic bytes 0x50, 0x4B).
     */
    fun isSupportedPackage(
        context: Context,
        uri: Uri,
        fileName: String? = null,
    ): Boolean {
        if (uri.scheme == "http" || uri.scheme == "https") {
            val path = uri.path ?: ""
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext.isEmpty() || ext in SUPPORTED_PACKAGE_EXTENSIONS
        }

        val resolvedName = fileName ?: runCatching {
            context.contentResolver.getDisplayName(uri)
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

        val ext = resolvedName.substringAfterLast('.', "").lowercase()
        val mimeType = runCatching { context.contentResolver.getType(uri)?.lowercase() }.getOrNull()

        if (ext.isNotEmpty() && ext in KNOWN_NON_PACKAGE_EXTENSIONS) {
            Timber.w("Rejecting unsupported file extension: %s", ext)
            return false
        }

        val hasValidExt = ext in SUPPORTED_PACKAGE_EXTENSIONS
        val hasApkMime = mimeType == "application/vnd.android.package-archive" ||
                mimeType == "application/zip" ||
                mimeType == "application/x-zip-compressed"

        if (!hasValidExt && !hasApkMime && ext.isNotEmpty()) {
            Timber.w("Rejecting unknown file extension: %s (MIME: %s)", ext, mimeType)
            return false
        }

        // Validate ZIP magic bytes (PK\x03\x04 or 0x50, 0x4B)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                checkZipMagicBytes(stream)
            } ?: (hasValidExt || hasApkMime)
        }.getOrDefault(hasValidExt || hasApkMime)
    }

    fun isSupportedPackageFile(file: File, fileName: String? = null): Boolean {
        val resolvedName = fileName ?: file.name
        val ext = resolvedName.substringAfterLast('.', "").lowercase()

        if (ext.isNotEmpty() && ext in KNOWN_NON_PACKAGE_EXTENSIONS) {
            return false
        }

        val hasValidExt = ext in SUPPORTED_PACKAGE_EXTENSIONS
        if (!hasValidExt && ext.isNotEmpty()) {
            return false
        }

        return runCatching {
            FileInputStream(file).use { stream ->
                checkZipMagicBytes(stream)
            }
        }.getOrDefault(hasValidExt)
    }

    private fun checkZipMagicBytes(stream: InputStream): Boolean {
        val header = ByteArray(2)
        val read = stream.read(header)
        if (read < 2) return false
        return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
    }
}
