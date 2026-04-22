package app.pwhs.universalinstaller.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.data.local.AppDatabase
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.data.repository.SessionDataRepositoryImpl
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.download.DownloadHistoryViewModel
import app.pwhs.universalinstaller.presentation.install.InstallViewModel
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.sync.SyncViewModel
import app.pwhs.universalinstaller.presentation.uninstall.UninstallViewModel
import app.pwhs.universalinstaller.presentation.uninstall.logs.UninstallLogsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import timber.log.Timber

val appModule = module {
    single { PackageInstaller.getInstance(get()) }
    single { PackageUninstaller.getInstance(get()) }
    factory { (handle: SavedStateHandle) -> SessionDataRepositoryImpl(handle) }
    singleOf(::SessionDataRepositoryImpl) { bind<SessionDataRepository>() }

    // Room
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "universal_installer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }
    single { get<AppDatabase>().installHistoryDao() }
    single { get<AppDatabase>().uninstallLogDao() }
    single { get<AppDatabase>().downloadHistoryDao() }

    // Ktor HttpClient. Uploads to VirusTotal can take minutes on slow connections, so we bump
    // the request timeout well past Ktor's default and leave the socket/connect timeouts sane.
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000L   // 5 minutes — covers 32 MB upload over 3G
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 5 * 60 * 1000L     // match request timeout for large uploads
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Timber.tag("KtorHttp").d(message)
                        }
                    }
                    // HEADERS logs method/URL/status/headers without dumping request bodies —
                    // keeps multipart APK uploads out of logcat. Bump to LogLevel.BODY when
                    // you need to inspect JSON payloads (VT responses etc.).
                    level = LogLevel.HEADERS
                    sanitizeHeader { header -> header.equals("x-apikey", ignoreCase = true) }
                }
            }
        }
    }
    single { VirusTotalService(get()) }
    single { VirusTotalNotifier(get()) }
    single { PackageDownloadService(get()) }

    viewModelOf(::InstallViewModel)
    viewModelOf(::UninstallViewModel)
    viewModelOf(::SettingViewModel)
    viewModelOf(::UninstallLogsViewModel)
    viewModelOf(::DownloadHistoryViewModel)
    viewModelOf(::SyncViewModel)
}