package app.pwhs.universalinstaller.di

import app.pwhs.core.billing.BillingManager
import app.pwhs.universalinstaller.billing.createBillingManager
import app.pwhs.universalinstaller.data.NoOpTrackedAppsBackupDataSource
import app.pwhs.universalinstaller.domain.backup.TrackedAppsBackupDataSource
import app.pwhs.universalinstaller.presentation.install.controller.FullInstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val flavorModule = module {
    single<InstallerBackendFactory> { FullInstallerBackendFactory() }
    single<TrackedAppsBackupDataSource> { NoOpTrackedAppsBackupDataSource() }
    single<BillingManager> { createBillingManager(androidContext()) }
}


