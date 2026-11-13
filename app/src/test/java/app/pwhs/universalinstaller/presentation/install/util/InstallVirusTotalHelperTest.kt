package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.createTestUri
import app.pwhs.core.domain.scanner.EngineDetection
import app.pwhs.core.domain.scanner.ScanError
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.ScannerVerdict
import app.pwhs.core.domain.scanner.SecurityPreferencesDataSource
import app.pwhs.core.domain.scanner.SecurityScanResult
import app.pwhs.core.domain.scanner.SecurityScannerOrchestrator
import app.pwhs.core.domain.scanner.impl.MockSecurityScanner
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.testutil.TestContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InstallVirusTotalHelperTest {

    private lateinit var context: TestContext
    private lateinit var mockNotifier: VirusTotalNotifier

    @Before
    fun setUp() {
        context = TestContext()
        mockNotifier = VirusTotalNotifier(context)
    }

    private fun createTestApkInfo(
        appName: String = "Sample App",
        packageName: String = "com.sample.app",
        versionName: String = "1.0",
        versionCode: Long = 1L,
        minSdkVersion: Int = 21,
        targetSdkVersion: Int = 34,
        fileSizeBytes: Long = 10L * 1024 * 1024,
        permissions: List<String> = emptyList(),
        sha256: String = "sample_sha256",
    ): ApkInfo = ApkInfo(
        appName = appName,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        icon = null,
        minSdkVersion = minSdkVersion,
        targetSdkVersion = targetSdkVersion,
        fileSizeBytes = fileSizeBytes,
        permissions = permissions,
        sha256 = sha256,
    )

    @Test
    fun launchHashLookupOnly_whenDisabled_returnsEmptyPair() = runBlocking {
        val mockScanner = MockSecurityScanner()
        val disabledPrefs = object : SecurityPreferencesDataSource {
            override suspend fun isScanningDisabled(context: Context): Boolean = true
            override suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId = ScannerProviderId.MOCK
            override suspend fun getActiveBackupScanner(context: Context): ScannerProviderId = ScannerProviderId.MOCK
            override suspend fun isAutoFallbackEnabled(context: Context): Boolean = true
            override suspend fun isOfflineChecksEnabled(context: Context): Boolean = false
        }
        val orchestrator = SecurityScannerOrchestrator(
            scanners = mapOf(ScannerProviderId.MOCK to mockScanner),
            preferencesDataSource = disabledPrefs,
        )

        val fakeUri = createTestUri("content://test/app.apk")
        val (hash, result) = InstallVirusTotalHelper.launchHashLookupOnly(context, fakeUri, orchestrator)
        assertEquals("", hash)
        assertEquals(null, result)
    }

    @Test
    fun scanVirusTotal_fileTooLarge_returnsTooLargeImmediately() = runBlocking {
        val mockScanner = MockSecurityScanner()
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.MOCK to mockScanner))
        val current = createTestApkInfo(
            packageName = "com.large.app",
            fileSizeBytes = 700L * 1024 * 1024,
            sha256 = "dummy_sha256",
        )
        val fakeUri = createTestUri("content://test/large.apk")
        var recordedResult: VtResult? = null

        InstallVirusTotalHelper.scanVirusTotal(
            context = context,
            uri = fakeUri,
            fileName = "large.apk",
            current = current,
            scannerOrchestrator = orchestrator,
            virusTotalNotifier = mockNotifier,
            onUpdateSha256 = {},
            onProgress = { recordedResult = it },
        )

        assertNotNull(recordedResult)
        assertEquals(VtStatus.TOO_LARGE, recordedResult?.status)
    }

    @Test
    fun scanVirusTotal_executesSuccessfullyWithMockScanner() = runBlocking {
        val mockScanner = MockSecurityScanner(
            simulatedVerdict = ScannerVerdict.SAFE,
            cachedResult = SecurityScanResult(
                providerId = ScannerProviderId.MOCK,
                sha256 = "test_sha_safe",
                verdict = ScannerVerdict.SAFE,
                riskScore = 0,
                harmlessCount = 70,
                engineDetections = listOf(EngineDetection("Avast", "clean", null)),
            ),
        )
        val orchestrator = SecurityScannerOrchestrator(mapOf(ScannerProviderId.MOCK to mockScanner))
        val current = createTestApkInfo(
            packageName = "com.sample.app",
            fileSizeBytes = 10 * 1024 * 1024,
            sha256 = "test_sha_safe",
        )
        val fakeUri = createTestUri("content://test/sample.apk")
        val progressResults = mutableListOf<VtResult>()

        InstallVirusTotalHelper.scanVirusTotal(
            context = context,
            uri = fakeUri,
            fileName = "sample.apk",
            current = current,
            scannerOrchestrator = orchestrator,
            virusTotalNotifier = mockNotifier,
            onUpdateSha256 = {},
            onProgress = { progressResults.add(it) },
        )

        assertTrue(progressResults.isNotEmpty())
        val finalResult = progressResults.last()
        assertEquals(VtStatus.CLEAN, finalResult.status)
        assertEquals(70, finalResult.harmless)
    }
}
