package app.pwhs.universalinstaller.di

import app.pwhs.core.billing.BillingManager
import app.pwhs.universalinstaller.billing.createBillingManager
import app.pwhs.universalinstaller.data.TrackedAppsBackupDataSourceImpl
import app.pwhs.universalinstaller.domain.backup.TrackedAppsBackupDataSource
import app.pwhs.universalinstaller.presentation.install.controller.FullInstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.updater.di.updaterModule
import app.pwhs.updater.worker.PeriodicUpdateCheckWorker
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val flavorModule = module {
    single<InstallerBackendFactory> { FullInstallerBackendFactory() }
    single<TrackedAppsBackupDataSource> { TrackedAppsBackupDataSourceImpl(androidContext()) }
    single<BillingManager> { createBillingManager(androidContext()) }
    includes(updaterModule)
    single {
        PeriodicUpdateCheckWorker.schedule(get())
        Unit
    }
}

