package app.pwhs.core.ui

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.core.install.BaseApkExtractor
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JvmInline
value class ApkFileIconData(val path: String)

class ApkFileIconFetcher(
    private val data: ApkFileIconData,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val extracted = BaseApkExtractor.pathForParsing(context, data.path, "temp_icon")
            ?: return@withContext null
        val pathForParsing = extracted.path
        val tempFile = extracted.temp

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
