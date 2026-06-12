package app.pwhs.universalinstaller.di

import app.pwhs.universalinstaller.presentation.install.controller.FullInstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import org.koin.dsl.module

val flavorModule = module {
    single<InstallerBackendFactory> { FullInstallerBackendFactory() }
}
