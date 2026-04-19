package app.pwhs.universalinstaller

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import app.pwhs.universalinstaller.di.appModule
import app.pwhs.universalinstaller.di.flavorModule
import app.pwhs.universalinstaller.util.AppIconFetcher
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin{
            androidLogger()
            androidContext(this@App)
            modules(appModule, flavorModule)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AppIconFetcher.Factory(context))
            }
            .build()
    }
}