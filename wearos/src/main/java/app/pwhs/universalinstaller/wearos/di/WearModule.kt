package app.pwhs.universalinstaller.wearos.di

import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.install.ApkInstaller
import app.pwhs.universalinstaller.wearos.domain.ApkCompatibilityCheck
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.presentation.detail.DetailViewModel
import app.pwhs.universalinstaller.wearos.presentation.home.HomeViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val wearModule = module {
    singleOf(::ApkMetadataReader)
    singleOf(::ApkInstaller)
    singleOf(::ApkCompatibilityCheck)
    singleOf(::WearApkRepository)
    viewModelOf(::HomeViewModel)
    viewModel { params ->
        DetailViewModel(
            apkId = params.get(),
            repository = get(),
            installer = get(),
            compatibility = get(),
            application = get(),
        )
    }
}
