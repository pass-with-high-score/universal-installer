package com.nqmgaming.universalinstaller

import android.app.Application
import com.nqmgaming.universalinstaller.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin{
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }
}