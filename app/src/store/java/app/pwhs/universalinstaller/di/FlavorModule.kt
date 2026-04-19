package app.pwhs.universalinstaller.di

import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.StoreInstallerBackendFactory
import org.koin.dsl.module

val flavorModule = module {
    single<InstallerBackendFactory> { StoreInstallerBackendFactory() }
}
