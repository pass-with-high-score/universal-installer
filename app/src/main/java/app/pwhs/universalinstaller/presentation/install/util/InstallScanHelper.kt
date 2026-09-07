package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.presentation.install.ApkScanner
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.ScanState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object InstallScanHelper {

    suspend fun performDeviceScan(
        context: Context,
        onProgress: (status: String, foundCount: Int, progress: Float?) -> Unit = { _, _, _ -> },
    ): ScanState {
        if (!ApkScanner.hasAllFilesAccess(context)) {
            return ScanState.PermissionNeeded
        }
        val results = try {
            ApkScanner.scan(context, onProgress)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            emptyList()
        }
        return ScanState.Ready(results)
    }

    fun resolveUriForFile(context: Context, file: File): Uri {
        return runCatching {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }
    }

    fun collectUrisFromScan(context: Context, found: List<FoundPackageFile>): List<Uri> {
        return found.mapNotNull { entry ->
            val f = File(entry.path)
            if (!f.exists()) return@mapNotNull null
            resolveUriForFile(context, f)
        }
    }

    suspend fun deleteFoundFiles(files: List<FoundPackageFile>) = withContext(Dispatchers.IO) {
        files.forEach { entry ->
            runCatching { File(entry.path).delete() }
        }
    }
}
