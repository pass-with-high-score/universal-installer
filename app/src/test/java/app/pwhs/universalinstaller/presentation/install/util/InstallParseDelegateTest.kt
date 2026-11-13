package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.SecurityScannerOrchestrator
import app.pwhs.core.domain.scanner.impl.MockSecurityScanner
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.testutil.TestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InstallParseDelegateTest {

    private lateinit var context: TestContext
    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var mockScanner: MockSecurityScanner
    private lateinit var orchestrator: SecurityScannerOrchestrator
    private lateinit var notifier: VirusTotalNotifier
    private lateinit var obbDelegate: InstallObbDelegate

    @Before
    fun setUp() {
        context = TestContext()
        app = Application()
        mockScanner = MockSecurityScanner()
        orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.MOCK to mockScanner))
        notifier = VirusTotalNotifier(context)
        obbDelegate = InstallObbDelegate(app, scope)
    }

    @Test
    fun primaryConstructor_instantiatesCorrectly() {
        val delegate = InstallParseDelegate(
            application = app,
            scope = scope,
            securityScannerOrchestrator = orchestrator,
            virusTotalNotifier = notifier,
            obbDelegate = obbDelegate,
            onProfileMatched = {},
            onParseFailed = {},
        )

        assertNotNull(delegate)
        assertNull(delegate.pendingApkInfo.value)
    }

    @Test
    fun secondaryConstructor_instantiatesWithVirusDetecService() {
        val fakeService = VirusDetecService()
        val delegate = InstallParseDelegate(
            application = app,
            scope = scope,
            virusDetecService = fakeService,
            virusTotalNotifier = notifier,
            obbDelegate = obbDelegate,
            onProfileMatched = {},
            onParseFailed = {},
        )

        assertNotNull(delegate)
        assertNull(delegate.pendingApkInfo.value)
    }

    @Test
    fun dismissPendingInstall_clearsAllPendingInformation() {
        val delegate = InstallParseDelegate(
            application = app,
            scope = scope,
            securityScannerOrchestrator = orchestrator,
            virusTotalNotifier = notifier,
            obbDelegate = obbDelegate,
            onProfileMatched = {},
        )

        delegate.dismissPendingInstall()

        assertNull(delegate.pendingApkInfo.value)
        assertNull(delegate.pendingApkUris)
        assertNull(delegate.pendingFileName)
        assertNull(delegate.pendingOriginalUri)
    }
}
