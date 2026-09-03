package app.pwhs.universalinstaller.wearos

import android.app.Application
import android.content.Context
import app.pwhs.core.ui.ApkFileIconFetcher
import app.pwhs.universalinstaller.wearos.di.wearModule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class WearApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@WearApp)
            modules(wearModule)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(ApkFileIconFetcher.Factory(context)) }
            .build()
}
