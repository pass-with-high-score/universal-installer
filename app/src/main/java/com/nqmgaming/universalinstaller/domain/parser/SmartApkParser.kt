package com.nqmgaming.universalinstaller.domain.parser

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class SmartApkParser : AppParser {

    override suspend fun parse(context: Context, uri: Uri): AppInfo? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)?.lowercase()
            var displayName = ""
            var size = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }

            val extension = displayName.substringAfterLast('.', "").lowercase()
            val isApks = extension in listOf("apks", "xapk", "apkm", "zip")

            if (isApks) {
                return@withContext parseZip(context, uri, displayName, size)
            } else {
                return@withContext parseSingleApk(context, uri, displayName, size)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing file at URI: $uri")
            null
        }
    }

    private fun parseSingleApk(context: Context, uri: Uri, displayName: String, size: Long): AppInfo? {
        // Copy to temp to parse with PackageManager
        val tempFile = copyToTempFile(context, uri, "temp_single.apk") ?: return null
        
        try {
            val pm = context.packageManager
            val packageInfo: PackageInfo? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
            }

            packageInfo?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = tempFile.absolutePath
                appInfo.publicSourceDir = tempFile.absolutePath

                val name = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val versionName = packageInfo.versionName ?: ""
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                return AppInfo(
                    name = name,
                    packageName = packageInfo.packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    icon = icon,
                    size = size,
                    isApks = false
                )
            }
        } finally {
            tempFile.delete()
        }
        return null
    }

    private fun parseZip(context: Context, uri: Uri, displayName: String, size: Long): AppInfo? {
        var appInfo: AppInfo? = null
        val supportedAbis = mutableSetOf<String>()
        var foundBaseApkUri: Uri? = null
        var totalSize = 0L

        try {
            val splits = ZippedApkSplits.getApksForUri(uri, context)
            try {
                for (apk in splits) {
                    totalSize += apk.size
                    when (apk) {
                        is Apk.Base -> {
                            foundBaseApkUri = apk.uri
                        }
                        is Apk.Libs -> {
                            supportedAbis.add(apk.abi.name)
                        }
                        else -> {}
                    }
                }
            } finally {
                splits.close()
            }

            if (foundBaseApkUri != null) {
                val tempFile = copyToTempFile(context, foundBaseApkUri!!, "temp_base.apk")
                if (tempFile != null) {
                    val pm = context.packageManager
                    val pkgInfo: PackageInfo? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                    }

                    pkgInfo?.applicationInfo?.let { aInfo ->
                        aInfo.sourceDir = tempFile.absolutePath
                        aInfo.publicSourceDir = tempFile.absolutePath

                        val appName = pm.getApplicationLabel(aInfo).toString()
                        val icon = pm.getApplicationIcon(aInfo)
                        val vName = pkgInfo.versionName ?: ""
                        val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pkgInfo.longVersionCode
                        } else {
                            pkgInfo.versionCode.toLong()
                        }

                        appInfo = AppInfo(
                            name = appName.takeIf { it != aInfo.packageName } ?: displayName,
                            packageName = pkgInfo.packageName,
                            versionName = vName,
                            versionCode = vCode,
                            icon = icon,
                            size = totalSize.takeIf { it > 0 } ?: size,
                            isApks = true,
                            supportedAbis = supportedAbis.toList()
                        )
                    }
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing ZIP structure using Ackpine")
        }

        return appInfo
    }

    private fun copyToTempFile(context: Context, uri: Uri, tempName: String): File? {
        return try {
            val tempFile = File(context.cacheDir, tempName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy URI to temp file")
            null
        }
    }
}
