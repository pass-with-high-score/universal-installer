package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.pwhs.core.util.WatchAppCheck
import app.pwhs.universalinstaller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell
import timber.log.Timber
import java.io.File

/**
 * State of a found APK relative to what's currently installed on the device.
 * Drives the chip tag shown next to each scan result.
 */
enum class InstallState {
    /** Couldn't determine — typically split-bundle archives we don't unpack during scan. */
    Unknown,
    /** Package isn't installed on this device. */
    NotInstalled,
    /** Same versionCode is already installed (re-install / no-op upgrade). */
    SameVersion,
    /** APK is older than what's installed (would be a downgrade). */
    Older,
    /** APK is newer than what's installed (would be an update). */
    Newer,
}

data class FoundPackageFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val extension: String,
    /** Parsed from the APK archive at scan time; null for split bundles or parse failures. */
    val packageName: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val installState: InstallState = InstallState.Unknown,
    val isAndroidAutoSupported: Boolean = false,
    val isWearOsSupported: Boolean = false,
    val originalPath: String? = null,
)

object ApkScanner {

    private val SUPPORTED_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "apk+")
    private val ARCHIVE_EXTENSIONS = setOf("apks", "xapk", "apkm", "apk+")

    private val IGNORED_DIR_NAMES = setOf(
        "android", "dcim", "pictures", "movies", "music", "podcasts",
        "alarms", "ringtones", "notifications", "audiobooks",
        ".thumbnails", "cache", ".cache", ".git"
    )

    private fun isExcludedDir(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith(".") || lower.equals("android", ignoreCase = true) || lower in IGNORED_DIR_NAMES
    }



    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun buildGrantIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
    }

    /**
     * Finds installable package files quickly by querying MediaStore and scanning
     * common user download directories directly, falling back to a bounded recursive scan
     * if MediaStore yields no results. Enriches metadata in parallel to minimize latency.
     */
    suspend fun scan(
        context: Context,
        onProgress: (status: String, foundCount: Int, progress: Float?) -> Unit = { _, _, _ -> },
    ): List<FoundPackageFile> = withContext(Dispatchers.IO) {
        val foundMap = LinkedHashMap<String, FoundPackageFile>()

        // 1. Fast path: MediaStore query (fetches all indexed APKs on the device in milliseconds)
        onProgress(context.getString(R.string.find_auto_scanning_mediastore), 0, 0.05f)
        scanMediaStore(context).forEach { foundMap[it.path] = it }
        onProgress(context.getString(R.string.find_auto_scanning_storage), foundMap.size, 0.10f)

        // 2. Comprehensive filesystem walk across all volume roots (excluding heavy media folders).
        val roots = collectVolumeRoots(context)
        val hasRoot = RootApkScanner.isRootAvailable()
        val walkMaxProgress = if (hasRoot) 0.35f else 0.40f

        val topDirs = roots.flatMap { root ->
            root.listFiles { f -> f.isDirectory && !isExcludedDir(f.name) }?.toList() ?: emptyList()
        }
        val totalTopDirs = topDirs.size.coerceAtLeast(1)
        var topDirsProcessed = 0

        for (root in roots) {
            currentCoroutineContext().ensureActive()
            val dirs = root.listFiles { f -> f.isDirectory && !isExcludedDir(f.name) } ?: emptyArray()
            for (dir in dirs) {
                currentCoroutineContext().ensureActive()
                val currentP = 0.10f + (walkMaxProgress - 0.10f) * (topDirsProcessed.toFloat() / totalTopDirs)
                onProgress(context.getString(R.string.find_auto_scanning_folder, dir.name), foundMap.size, currentP)
                scanRecursive(dir, foundMap, depth = 1, maxDepth = 6) { subDir ->
                    onProgress(context.getString(R.string.find_auto_scanning_folder, subDir.name), foundMap.size, currentP)
                }
                topDirsProcessed++
            }

            // Direct files in volume root
            root.listFiles { f -> f.isFile }?.forEach { addIfPackageFile(it, foundMap) }

            // Specifically scan Android/media (e.g. WhatsApp Documents) since Android/ is skipped at root level
            val androidMedia = File(root, "Android/media")
            if (androidMedia.exists() && androidMedia.canRead()) {
                scanRecursive(androidMedia, foundMap, depth = 0, maxDepth = 5) { subDir ->
                    onProgress(context.getString(R.string.find_auto_scanning_folder, subDir.name), foundMap.size, walkMaxProgress)
                }
            }
        }

        // 3. If Root is available, scan restricted Android/data, Android/obb, and app cache folders via high-speed native shell
        if (hasRoot) {
            onProgress(context.getString(R.string.find_auto_scanning_root), foundMap.size, 0.38f)
            RootApkScanner.scanRootRestrictedDirs(context, roots, SUPPORTED_EXTENSIONS, foundMap)
        }

        if (foundMap.isEmpty()) {
            onProgress("", 0, 1.0f)
            return@withContext emptyList()
        }

        // 4. Enrich APK metadata concurrently (limited parallelism to avoid I/O starvation)
        val pm = context.packageManager
        val rawList = foundMap.values.toList()
        val enrichDispatcher = Dispatchers.IO.limitedParallelism(8)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            rawList.map { file ->
                async(enrichDispatcher) {
                    currentCoroutineContext().ensureActive()
                    val enriched = if (file.extension == "apk") enrichWithPackageInfo(pm, file) else file
                    val done = completedCount.incrementAndGet()
                    val enrichProgress = 0.40f + 0.60f * (done.toFloat() / rawList.size)
                    onProgress(
                        context.getString(R.string.find_auto_scanning_enrich, done, rawList.size),
                        foundMap.size,
                        enrichProgress,
                    )
                    enriched
                }
            }.awaitAll()
        }.sortedByDescending { it.modifiedMillis }
    }

    private fun scanMediaStore(context: Context): List<FoundPackageFile> {
        val out = mutableListOf<FoundPackageFile>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )

        val nameCol = MediaStore.Files.FileColumns.DISPLAY_NAME
        val selection = SUPPORTED_EXTENSIONS.joinToString(" OR ") { "$nameCol LIKE '%.$it'" }
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val path = if (dataIdx != -1) cursor.getString(dataIdx) else null
                    if (path.isNullOrBlank()) continue
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) continue

                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: file.name else file.name
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else file.length()
                    val modifiedSec = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val modifiedMillis = if (modifiedSec > 0L) modifiedSec * 1000L else file.lastModified()
                    val ext = file.extension.lowercase()

                    if (ext in SUPPORTED_EXTENSIONS) {
                        out.add(
                            FoundPackageFile(
                                path = file.absolutePath,
                                name = name,
                                sizeBytes = if (size > 0L) size else file.length(),
                                modifiedMillis = modifiedMillis,
                                extension = ext,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaStore package query failed")
        }
        return out
    }



    private fun addIfPackageFile(file: File, out: MutableMap<String, FoundPackageFile>) {
        val ext = file.extension.lowercase()
        if (ext in SUPPORTED_EXTENSIONS) {
            val path = file.absolutePath
            if (!out.containsKey(path)) {
                out[path] = FoundPackageFile(
                    path = path,
                    name = file.name,
                    sizeBytes = file.length(),
                    modifiedMillis = file.lastModified(),
                    extension = ext,
                )
            }
        }
    }

    /**
     * Returns root directories for every readable mounted volume — primary emulated storage,
     * SD cards, and (when the system mounts them as a regular volume) OTG drives. Falls back
     * to the primary volume only on API 24–29 for non-primary volumes, since
     * [android.os.storage.StorageVolume.getDirectory] is API 30+.
     */
    private fun collectVolumeRoots(context: Context): List<File> {
        val out = LinkedHashSet<File>()
        
        // 1. StorageManager API (API 24+)
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null) {
            for (vol in sm.storageVolumes) {
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else if (vol.isPrimary) {
                    Environment.getExternalStorageDirectory()
                } else {
                    null
                }
                if (dir != null && dir.exists() && dir.canRead()) out.add(dir)
            }
        }
        
        // 2. ContextCompat API to catch all external volumes (e.g. SD cards on older APIs)
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in externalDirs) {
            if (dir != null) {
                val rootPath = dir.absolutePath.substringBefore("/Android/data/")
                val rootDir = File(rootPath)
                if (rootDir.exists() && rootDir.canRead()) {
                    out.add(rootDir)
                }
            }
        }

        if (out.isEmpty()) {
            Environment.getExternalStorageDirectory()
                ?.takeIf { it.exists() && it.canRead() }
                ?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun enrichWithPackageInfo(
        pm: PackageManager,
        file: FoundPackageFile,
    ): FoundPackageFile {
        val archive = runCatching {
            val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_SERVICES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.path, flags)
            }
        }.getOrNull() ?: return file

        val pkgName = archive.packageName.takeIf { it.isNotBlank() } ?: return file
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        val installedCode = runCatching {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(pkgName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installed.longVersionCode
            } else {
                @Suppress("DEPRECATION") installed.versionCode.toLong()
            }
        }.getOrNull()

        val state = when {
            installedCode == null -> InstallState.NotInstalled
            archiveCode == installedCode -> InstallState.SameVersion
            archiveCode > installedCode -> InstallState.Newer
            else -> InstallState.Older
        }

        val hasCarService = archive.services?.any { service ->
            service.name.contains("MediaBrowserService", ignoreCase = true) ||
                service.name.contains("CarAppService", ignoreCase = true) ||
                service.name.contains("CarService", ignoreCase = true)
        } == true

        val isAa = if (hasCarService) true else runCatching {
            java.util.zip.ZipFile(file.path).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                if (entry != null) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val text = String(bytes, Charsets.ISO_8859_1)
                    text.contains("com.google.android.gms.car") ||
                        text.contains("androidx.car.app") ||
                        text.contains("MediaBrowserService") ||
                        text.contains("CarAppService") ||
                        text.contains("automotive_app_desc")
                } else false
            }
        }.getOrDefault(false)

        return file.copy(
            packageName = pkgName,
            versionCode = archiveCode,
            versionName = archive.versionName,
            installState = state,
            isAndroidAutoSupported = isAa,
            isWearOsSupported = archive.reqFeatures?.any { it.name == WatchAppCheck.WATCH_FEATURE } == true,
        )
    }

    private suspend fun scanRecursive(
        dir: File,
        out: MutableMap<String, FoundPackageFile>,
        depth: Int,
        maxDepth: Int,
        onDirectoryVisited: ((File) -> Unit)? = null,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > maxDepth) return
        if (!dir.exists() || !dir.canRead()) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (depth == 0 && name.equals("Android", ignoreCase = true)) continue
                if (name.lowercase() in IGNORED_DIR_NAMES) continue
                onDirectoryVisited?.invoke(child)
                scanRecursive(child, out, depth + 1, maxDepth, onDirectoryVisited)
            } else {
                addIfPackageFile(child, out)
            }
        }
    }

}
