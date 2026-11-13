package app.pwhs.universalinstaller.di

import android.app.Application
import app.pwhs.core.domain.scanner.DefaultSecurityPreferencesDataSource
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.SecurityPreferencesDataSource
import app.pwhs.core.domain.scanner.SecurityScannerOrchestrator
import app.pwhs.core.domain.scanner.impl.MockSecurityScanner
import app.pwhs.core.domain.scanner.impl.OfflineStaticScanner
import app.pwhs.core.domain.scanner.impl.VirusDetecScanner
import app.pwhs.core.domain.scanner.impl.VirusTotalScanner
import app.pwhs.core.network.VirusDetecService
import app.pwhs.core.network.VirusTotalService
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingViewModel
import io.ktor.client.HttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AppModuleSecurityScannerTest {

    private val testModule = module {
        single { HttpClient() }
        single<Application> {
            val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_koin_app").apply { mkdirs() }
            object : Application() {
                override fun getApplicationContext(): android.content.Context = this
                override fun getPackageName(): String = "app.pwhs.universalinstaller"
                override fun getCacheDir(): java.io.File = tempDir
                override fun getFilesDir(): java.io.File = tempDir
            }
        }
        single { VirusDetecService(get()) }
        single { VirusTotalService(get()) }
        single { VirusDetecScanner(get<VirusDetecService>()) }
        single { VirusTotalScanner(get<VirusTotalService>()) }
        single { OfflineStaticScanner() }
        single { MockSecurityScanner() }
        single<SecurityPreferencesDataSource> { DefaultSecurityPreferencesDataSource() }
        single {
            SecurityScannerOrchestrator(
                scanners = mapOf(
                    ScannerProviderId.VIRUSDETEC to get<VirusDetecScanner>(),
                    ScannerProviderId.VIRUSTOTAL to get<VirusTotalScanner>(),
                    ScannerProviderId.OFFLINE_STATIC to get<OfflineStaticScanner>(),
                    ScannerProviderId.MOCK to get<MockSecurityScanner>(),
                ),
                preferencesDataSource = get(),
            )
        }
        single { VirusTotalSettingViewModel(get()) }
    }

    @Before
    fun setUp() {
        startKoin {
            modules(testModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun securityScanners_areResolvedFromKoin() {
        val koin = GlobalContext.get()
        val virusDetecScanner = koin.get<VirusDetecScanner>()
        val virusTotalScanner = koin.get<VirusTotalScanner>()
        val offlineStaticScanner = koin.get<OfflineStaticScanner>()
        val mockScanner = koin.get<MockSecurityScanner>()

        assertNotNull(virusDetecScanner)
        assertNotNull(virusTotalScanner)
        assertNotNull(offlineStaticScanner)
        assertNotNull(mockScanner)

        assertEquals(ScannerProviderId.VIRUSDETEC, virusDetecScanner.providerId)
        assertEquals(ScannerProviderId.VIRUSTOTAL, virusTotalScanner.providerId)
        assertEquals(ScannerProviderId.OFFLINE_STATIC, offlineStaticScanner.providerId)
        assertEquals(ScannerProviderId.MOCK, mockScanner.providerId)
    }

    @Test
    fun orchestrator_resolvesWithAllConfiguredScanners() {
        val koin = GlobalContext.get()
        val orchestrator = koin.get<SecurityScannerOrchestrator>()
        assertNotNull(orchestrator)

        val available = orchestrator.getAvailableScanners()
        assertEquals(4, available.size)
        assertTrue(available.contains(ScannerProviderId.VIRUSDETEC))
        assertTrue(available.contains(ScannerProviderId.VIRUSTOTAL))
        assertTrue(available.contains(ScannerProviderId.OFFLINE_STATIC))
        assertTrue(available.contains(ScannerProviderId.MOCK))
    }

    @Test
    fun virusTotalSettingViewModel_resolvesFromKoin() {
        val koin = GlobalContext.get()
        val viewModel = koin.get<VirusTotalSettingViewModel>()
        assertNotNull(viewModel)
    }
}
