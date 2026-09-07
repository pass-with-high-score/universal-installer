package app.pwhs.tv

import android.app.Application
import app.pwhs.tv.di.flavorModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class TvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else Timber.DebugTree())
        startKoin {
            androidLogger()
            androidContext(this@TvApplication)
            modules(flavorModule)
        }
    }
}
