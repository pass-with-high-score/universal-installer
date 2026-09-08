package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.local.DownloadHistoryEntity
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.presentation.install.DownloadNotifier
import app.pwhs.universalinstaller.presentation.install.DownloadState
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File

object InstallDownloadHelper {

    const val DOWNLOADS_SUBFOLDER = "UniversalInstaller"

    fun renameToDisplayName(file: File, desiredName: String): File {
        if (file.name == desiredName) return file
        val parent = file.parentFile ?: return file
        val targetName = uniqueFileName(parent, desiredName)
        val target = File(parent, targetName)
        return if (file.renameTo(target)) target else file
    }

    fun uniqueFileName(dir: File, desired: String): String {
        if (!File(dir, desired).exists()) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.take(dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (File(dir, "$stem ($i)$ext").exists()) i++
        return "$stem ($i)$ext"
    }

    suspend fun executeDownload(
        context: Context,
        url: String,
        packageDownloadService: PackageDownloadService,
        downloadNotifier: DownloadNotifier,
        downloadHistoryDao: DownloadHistoryDao,
        onProgress: (DownloadState) -> Unit,
        onSuccess: (File, String, String) -> Unit,
    ) {
        val trimmed = url.trim()
        val displayName = trimmed.substringAfterLast('/').substringBefore('?')
            .ifBlank { "download_${System.currentTimeMillis()}" }

        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DOWNLOADS_SUBFOLDER,
        ).apply { mkdirs() }
        val destination = File(downloadsDir, uniqueFileName(downloadsDir, displayName))

        val estimator = app.pwhs.core.util.TransferEstimator()
        val result = packageDownloadService.download(trimmed, destination) { read, total ->
            val est = estimator.update(read, total)
            onProgress(
                DownloadState.Running(
                    url = trimmed,
                    bytesRead = read,
                    totalBytes = total,
                    speedBytesPerSec = est.speedBytesPerSec,
                    etaSeconds = est.etaSeconds,
                )
            )
            downloadNotifier.notifyProgress(displayName, read, total)
        }

        result.fold(
            onSuccess = { downloaded ->
                val ext = downloaded.fileName.substringAfterLast('.', "").lowercase()
                val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")
                if (ext !in validExtensions) {
                    downloaded.file.delete()
                    val msg = context.getString(R.string.remote_download_unsupported)
                    onProgress(DownloadState.Error(msg))
                    downloadNotifier.notifyFailed(msg)
                    return@fold
                }
                val finalFile = renameToDisplayName(downloaded.file, downloaded.fileName)
                MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
                onProgress(DownloadState.Idle)
                downloadNotifier.notifyDone(finalFile.name, android.net.Uri.fromFile(finalFile))
                runCatching {
                    downloadHistoryDao.insert(
                        DownloadHistoryEntity(
                            url = trimmed,
                            fileName = finalFile.name,
                            filePath = finalFile.absolutePath,
                            sizeBytes = finalFile.length(),
                        )
                    )
                }.onFailure { Timber.e(it, "Failed to insert download history") }
                onSuccess(finalFile, finalFile.name, ext)
            },
            onFailure = { e ->
                if (e is CancellationException) {
                    downloadNotifier.cancel()
                    throw e
                }
                Timber.e(e, "Download failed")
                val msg = context.getString(
                    R.string.remote_download_failed,
                    e.message ?: e::class.java.simpleName,
                )
                onProgress(DownloadState.Error(msg))
                downloadNotifier.notifyFailed(msg)
            },
        )
    }
}
