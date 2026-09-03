package app.pwhs.universalinstaller.wearos.di

import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.install.ApkInstaller
import app.pwhs.universalinstaller.wearos.domain.ApkCompatibilityCheck
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.data.WearInstalledAppsRepository
import app.pwhs.universalinstaller.wearos.data.WearSettings
import app.pwhs.universalinstaller.wearos.presentation.detail.DetailViewModel
import app.pwhs.universalinstaller.wearos.presentation.home.HomeViewModel
import app.pwhs.universalinstaller.wearos.presentation.manage.ManageViewModel
import app.pwhs.universalinstaller.wearos.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val wearModule = module {
    singleOf(::ApkMetadataReader)
    singleOf(::ApkInstaller)
    singleOf(::ApkCompatibilityCheck)
    singleOf(::WearApkRepository)
    singleOf(::WearInstalledAppsRepository)
    singleOf(::WearSettings)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ManageViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params ->
        DetailViewModel(
            apkId = params.get(),
            repository = get(),
            installer = get(),
            compatibility = get(),
            settings = get(),
            application = get(),
        )
    }
}
