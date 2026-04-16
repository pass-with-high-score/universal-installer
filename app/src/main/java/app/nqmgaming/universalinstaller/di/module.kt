package app.nqmgaming.universalinstaller.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.pwhs.universalinstaller.data.local.AppDatabase
import com.pwhs.universalinstaller.data.remote.VirusTotalService
import com.pwhs.universalinstaller.data.repository.SessionDataRepositoryImpl
import com.pwhs.universalinstaller.domain.repository.SessionDataRepository
import com.pwhs.universalinstaller.presentation.install.InstallViewModel
import com.pwhs.universalinstaller.presentation.setting.SettingViewModel
import com.pwhs.universalinstaller.presentation.uninstall.UninstallViewModel
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

    // Room
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "universal_installer.db")
            .build()
    }
    single { get<AppDatabase>().installHistoryDao() }

    // Ktor HttpClient
    single { HttpClient(CIO) }
    single { VirusTotalService(get()) }

    viewModelOf(::InstallViewModel)
    viewModelOf(::UninstallViewModel)
    viewModelOf(::SettingViewModel)
}