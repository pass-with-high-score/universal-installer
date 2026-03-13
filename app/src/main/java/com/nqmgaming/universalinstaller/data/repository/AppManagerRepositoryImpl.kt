package com.nqmgaming.universalinstaller.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import com.nqmgaming.universalinstaller.domain.repository.AppManagerRepository
import com.nqmgaming.universalinstaller.domain.repository.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.media.MediaScannerConnection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppManagerRepositoryImpl(
    private val context: Context
) : AppManagerRepository {

    override suspend fun getInstalledApps(includeSystemApps: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        
        packages.mapNotNull { packageInfo ->
            try {
                val appInfo = packageInfo.applicationInfo
                if (appInfo != null) {
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!includeSystemApps && isSystemApp) return@mapNotNull null
                    
                    val name = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    val sourceDir = appInfo.sourceDir
                    val splitSourceDirs = appInfo.splitSourceDirs?.toList() ?: emptyList()
                    val versionName = packageInfo.versionName ?: "Unknown"
                    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                    
                    InstalledApp(
                        packageName = packageInfo.packageName,
                        name = name,
                        versionName = versionName,
                        versionCode = versionCode,
                        icon = icon,
                        sourceDir = sourceDir,
                        splitSourceDirs = splitSourceDirs,
                        isSystemApp = isSystemApp
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    override suspend fun extractApp(app: InstalledApp): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(app.sourceDir)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source APK not found."))
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeName = app.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            
            val isSplit = app.splitSourceDirs.isNotEmpty()
            val extension = if (isSplit) "apks" else "apk"
            val destFileName = "${safeName}_${app.versionName}_${timestamp}.$extension"

            // MediaStore on Android 10+ forcibly appends `.zip` or `.bin` to unrecognized extensions like `.apks`.
            // To prevent this, we only use MediaStore for standard `.apk` files. For `.apks` we use the raw File API.
            val useMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isSplit

            if (useMediaStore) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, destFileName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UniversalInstallerBackups")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry."))

                try {
                    resolver.openOutputStream(uri)?.buffered()?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    Result.success("Downloads/UniversalInstallerBackups/$destFileName")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    return@withContext Result.failure(e)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val subDir = File(downloadsDir, "UniversalInstallerBackups")
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
                val destFile = File(subDir, destFileName)

                destFile.outputStream().use { output ->
                    if (isSplit) {
                        ZipOutputStream(output.buffered()).use { zout ->
                            zout.putNextEntry(ZipEntry("base.apk"))
                            sourceFile.inputStream().buffered().use { it.copyTo(zout) }
                            zout.closeEntry()

                            app.splitSourceDirs.forEach { splitPath ->
                                val splitFile = File(splitPath)
                                if (splitFile.exists()) {
                                    zout.putNextEntry(ZipEntry(splitFile.name))
                                    splitFile.inputStream().buffered().use { it.copyTo(zout) }
                                    zout.closeEntry()
                                }
                            }
                        }
                    } else {
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                Result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
