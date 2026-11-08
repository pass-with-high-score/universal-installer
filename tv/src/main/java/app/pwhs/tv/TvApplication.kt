package app.pwhs.tv

import android.app.Application
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.tv.di.flavorModule
import app.pwhs.tv.remoteconfig.createVtRemoteConfigSource
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class TvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else Timber.DebugTree())
        VirusTotalKeyManager.install(createVtRemoteConfigSource(BuildConfig.DEBUG))
        startKoin {
            androidLogger()
            androidContext(this@TvApplication)
            modules(flavorModule)
        }
    }
}
