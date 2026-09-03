package app.pwhs.universalinstaller.wearos.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.install.isBundleFileName
import app.pwhs.core.util.WatchAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Packages received from the paired phone.
 *
 * The cache directory is the source of truth — the in-memory list is rebuilt from it on start, so
 * an APK survives the watch killing this process between the transfer and the user opening the app.
 */
class WearApkRepository(
    private val context: Context,
    private val metadataReader: ApkMetadataReader,
) {

    private val cacheDir: File
        get() = File(context.filesDir, "wear_apk_cache").also { it.mkdirs() }

    private val _apks = MutableStateFlow<List<WearApkInfo>>(emptyList())
    val apks: StateFlow<List<WearApkInfo>> = _apks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Rebuilds the list from whatever is on disk. Files that no longer parse are deleted. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        val entries = cacheDir.listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { file -> readInfo(file) ?: run { file.delete(); null } }
            .sortedByDescending { it.receivedAt }
        _apks.value = entries
        _isLoading.value = false
    }

    /** Called by WearReceiverService once a full package has been written to disk. */
    suspend fun addApk(apkFile: File): WearApkInfo? = withContext(Dispatchers.IO) {
        val info = readInfo(apkFile) ?: return@withContext null
        _apks.value = (_apks.value.filterNot { it.id == info.id } + info)
            .sortedByDescending { it.receivedAt }
        info
    }

    fun getById(id: String): WearApkInfo? = _apks.value.find { it.id == id }

    /** Bytes the queue occupies — measured on disk, so files that failed to parse still count. */
    suspend fun queueBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDir.listFiles().orEmpty().forEach { it.delete() }
        _apks.value = emptyList()
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        val entry = getById(id) ?: return@withContext
        File(entry.cachedFilePath).delete()
        _apks.value = _apks.value.filter { it.id != id }
    }

    /** Create a file in the cache dir to write incoming bytes into. */
    fun createTempApkFile(fileName: String): File {
        val safe = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(cacheDir, "${UUID.randomUUID()}_$safe")
    }

    private suspend fun readInfo(file: File): WearApkInfo? {
        val metadata = metadataReader.readMetadata(Uri.fromFile(file), file.name.isBundleFileName())
            ?: return null
        return metadata.toWearApkInfo(
            file = file,
            declaresWatchFeature = WatchAppCheck.declaresWatchFeature(context, file.absolutePath),
            installedVersionCode = installedVersionCode(metadata.packageName),
        )
    }

    private fun installedVersionCode(packageName: String): Long? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(packageName, 0)
        }
        info.longVersionCode
    }.getOrNull()
}
