package com.nqmgaming.universalinstaller.di

import androidx.lifecycle.SavedStateHandle
import com.nqmgaming.universalinstaller.data.repository.AppManagerRepositoryImpl
import com.nqmgaming.universalinstaller.data.repository.FileScannerRepositoryImpl
import com.nqmgaming.universalinstaller.data.repository.SessionDataRepositoryImpl
import com.nqmgaming.universalinstaller.domain.installer.AppInstaller
import com.nqmgaming.universalinstaller.domain.installer.StandardInstaller
import com.nqmgaming.universalinstaller.domain.parser.AppParser
import com.nqmgaming.universalinstaller.domain.parser.SmartApkParser
import com.nqmgaming.universalinstaller.domain.repository.AppManagerRepository
import com.nqmgaming.universalinstaller.domain.repository.FileScannerRepository
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import com.nqmgaming.universalinstaller.presentation.install.InstallViewModel
import com.nqmgaming.universalinstaller.presentation.manager.AppManagerViewModel
import com.nqmgaming.universalinstaller.presentation.scanner.ScannerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.uninstaller.PackageUninstaller

val appModule = module {
    single { PackageInstaller.getInstance(androidContext()) }
    single { PackageUninstaller.getInstance(androidContext()) }
    
    // Inject Session Repository
    factory { (handle: SavedStateHandle) -> SessionDataRepositoryImpl(handle) }
    singleOf(::SessionDataRepositoryImpl) { bind<SessionDataRepository>() }
    
    // Inject Parser
    singleOf(::SmartApkParser) { bind<AppParser>() }
    
    // Inject File Scanner Array
    single<FileScannerRepository> { FileScannerRepositoryImpl(androidContext()) }
    
    // Inject App Manager
    single<AppManagerRepository> { AppManagerRepositoryImpl(androidContext(), get()) }
    
    // Inject Installer (Default to StandardInstaller)
    single<AppInstaller> { StandardInstaller(androidContext(), get()) }
    
    // ViewModels
    viewModel { ScannerViewModel(get()) }
    viewModel { InstallViewModel(get(), get(), get(), androidContext()) }
    viewModel { AppManagerViewModel(get()) }
}