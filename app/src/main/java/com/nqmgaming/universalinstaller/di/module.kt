package com.nqmgaming.universalinstaller.di

import androidx.lifecycle.SavedStateHandle
import com.nqmgaming.universalinstaller.data.repository.SessionDataRepositoryImpl
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import com.nqmgaming.universalinstaller.presentation.install.InstallViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.solrudev.ackpine.installer.PackageInstaller

val appModule = module {
    single { PackageInstaller.getInstance(get()) }
    factory { (handle: SavedStateHandle) -> SessionDataRepositoryImpl(handle) }
    singleOf(::SessionDataRepositoryImpl) { bind<SessionDataRepository>() }
    viewModelOf(::InstallViewModel)
}