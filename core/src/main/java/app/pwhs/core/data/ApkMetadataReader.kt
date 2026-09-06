package app.pwhs.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.core.domain.PackageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ZippedApkSplits
import java.io.File

/**
 * Utility to extract app metadata (icon, label, version) from an APK or bundle URI.
 * Uses Ackpine to handle split APKs by identifying and reading the base APK.
 */
class ApkMetadataReader(private val context: Context) {

    suspend fun readMetadata(uri: Uri, isBundle: Boolean): PackageMetadata? = withContext(Dispatchers.IO) {
        if (isBundle) {
            readBundleMetadata(uri)
        } else {
            readSingleApkMetadata(uri)
        }
    }

    private fun readBundleMetadata(uri: Uri): PackageMetadata? {
        val baseApkUri = runCatching {
            val apks = ZippedApkSplits.getApksForUri(uri, context)
            try {
                var found: Uri? = null
                for (apk in apks) {
                    if (apk is Apk.Base) {
                        found = apk.uri
                        break
                    }
                }
                found
            } finally {
                apks.close()
            }
        }.getOrNull()

        if (baseApkUri != null) {
            return readSingleApkMetadata(baseApkUri, isBundle = true)
        }

        // Fallback for generic ZIPs containing any standalone .apk
        return readZipArchiveMetadata(uri)
    }

    private fun readZipArchiveMetadata(uri: Uri): PackageMetadata? {
        val tempFile = File(context.cacheDir, "temp_zip_entry_${System.currentTimeMillis()}.apk")
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            java.util.zip.ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) {
                        tempFile.outputStream().use { zip.copyTo(it) }
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (!tempFile.exists() || tempFile.length() == 0L) return null
            return readSingleApkMetadata(Uri.fromFile(tempFile), isBundle = true)
        } catch (_: Exception) {
            return null
        } finally {
            tempFile.delete()
        }
    }

    private fun readSingleApkMetadata(uri: Uri, isBundle: Boolean = false): PackageMetadata? {
        val tempFile = File(context.cacheDir, "temp_metadata_${System.currentTimeMillis()}.apk")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
            } ?: return null
            val appInfo = pi.applicationInfo ?: return null
            
            // Important: set source paths so loadIcon/loadLabel work correctly
            appInfo.sourceDir = tempFile.absolutePath
            appInfo.publicSourceDir = tempFile.absolutePath

            val requestedPerms = pi.requestedPermissions?.toList().orEmpty()

            return PackageMetadata(
                packageName = pi.packageName,
                appName = appInfo.loadLabel(pm).toString(),
                versionName = pi.versionName ?: "",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong(),
                icon = appInfo.loadIcon(pm).toBitmap(512, 512),
                isBundle = isBundle,
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0,
                targetSdk = appInfo.targetSdkVersion,
                permissions = requestedPerms,
            )
        } catch (e: Exception) {
            return null
        } finally {
            tempFile.delete()
        }
    }
}
