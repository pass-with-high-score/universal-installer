package app.pwhs.tv.di

import app.pwhs.updater.di.updaterModule
import app.pwhs.updater.worker.PeriodicUpdateCheckWorker
import org.koin.dsl.module

val flavorModule = module {
    includes(updaterModule)
    single {
        PeriodicUpdateCheckWorker.schedule(get())
        Unit
    }
}
