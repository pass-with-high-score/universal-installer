package com.nqmgaming.universalinstaller.di

import androidx.lifecycle.SavedStateHandle
import com.nqmgaming.universalinstaller.data.remote.VirusTotalService
import com.nqmgaming.universalinstaller.data.repository.SessionDataRepositoryImpl
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import com.nqmgaming.universalinstaller.presentation.install.InstallViewModel
import com.nqmgaming.universalinstaller.presentation.setting.SettingViewModel
import com.nqmgaming.universalinstaller.presentation.uninstall.UninstallViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.uninstaller.PackageUninstaller

val appModule = module {
    single { PackageInstaller.getInstance(get()) }
    single { PackageUninstaller.getInstance(get()) }
    factory { (handle: SavedStateHandle) -> SessionDataRepositoryImpl(handle) }
    singleOf(::SessionDataRepositoryImpl) { bind<SessionDataRepository>() }

    // Ktor HttpClient
    single { HttpClient(CIO) }
    single { VirusTotalService(get()) }

    viewModelOf(::InstallViewModel)
    viewModelOf(::UninstallViewModel)
    viewModelOf(::SettingViewModel)
}