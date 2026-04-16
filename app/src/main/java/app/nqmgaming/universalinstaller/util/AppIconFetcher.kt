package app.nqmgaming.universalinstaller.util

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

@JvmInline
value class AppIconData(val packageName: String)

class AppIconFetcher(
    private val data: AppIconData,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val drawable = context.packageManager.getApplicationIcon(data.packageName)
        val bitmap = drawable.toBitmap(192, 192)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(data, context)
        }
    }
}
