package app.pwhs.universalinstaller.util

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence
import ru.solrudev.ackpine.splits.ZippedApkSplits

@JvmInline
value class ApkFileIconData(val path: String)

class ApkFileIconFetcher(
    private val data: ApkFileIconData,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val file = java.io.File(data.path)
        val ext = file.extension.lowercase()
        val isSplit = ext in listOf("apks", "xapk", "apkm", "zip")
        var tempFile: java.io.File? = null

        val pathForParsing = if (isSplit) {
            try {
                val uri = android.net.Uri.fromFile(file)
                var baseApk: Apk.Base? = null
                val apks = ZippedApkSplits.getApksForUri(uri, context)
                try {
                    for (apk in apks) {
                        if (apk is Apk.Base) {
                            baseApk = apk
                            break
                        }
                    }
                } finally {
                    apks.close()
                }
                if (baseApk == null) return@withContext null

                // Copy base apk sequence to a temp file
                tempFile = java.io.File(context.cacheDir, "temp_icon_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(baseApk.uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile.absolutePath
            } catch (e: Exception) {
                return@withContext null
            }
        } else {
            data.path
        }

        try {
            val pm = context.packageManager
            val pi = pm.getPackageArchiveInfo(pathForParsing, 0) ?: return@withContext null
            pi.applicationInfo?.sourceDir = pathForParsing
            pi.applicationInfo?.publicSourceDir = pathForParsing
            
            if (pi.applicationInfo == null) return@withContext null
            val drawable = pi.applicationInfo!!.loadIcon(pm)
            val bitmap = drawable.toBitmap(192, 192)
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            null
        } finally {
            tempFile?.delete()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<ApkFileIconData> {
        override fun create(data: ApkFileIconData, options: Options, imageLoader: ImageLoader): Fetcher {
            return ApkFileIconFetcher(data, context)
        }
    }
}
