package app.pwhs.updater.domain

import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppTest {

    @Test
    fun hasUpdate_karingStyleTagWithVersionCodeSuffix_returnsFalse() {
        val app = trackedApp(
            currentVersionName = "1.2.24",
            currentVersionCode = 2709,
            latestVersionName = "1.2.24.2709",
        )

        assertFalse(app.hasUpdate)
    }

    @Test
    fun hasUpdate_semverPatchIncrease_returnsTrue() {
        val app = trackedApp(
            currentVersionName = "1.2.24",
            currentVersionCode = 2709,
            latestVersionName = "1.2.25",
        )

        assertTrue(app.hasUpdate)
    }

    @Test
    fun hasUpdate_differentVersionCodeSuffix_returnsTrue() {
        val app = trackedApp(
            currentVersionName = "1.2.24",
            currentVersionCode = 2708,
            latestVersionName = "1.2.24.2709",
        )

        assertTrue(app.hasUpdate)
    }

    @Test
    fun hasUpdate_prereleaseToStable_returnsTrue() {
        val app = trackedApp(
            currentVersionName = "4.2.0-rc10",
            currentVersionCode = 100,
            latestVersionName = "4.2.0",
        )

        assertTrue(app.hasUpdate)
    }

    @Test
    fun hasUpdate_noDownloadUrl_returnsFalse() {
        val app = trackedApp(
            currentVersionName = "5.0.1.5",
            currentVersionCode = 5015,
            latestVersionName = "5.0.2.1",
            latestDownloadUrl = null,
        )

        assertFalse(app.hasUpdate)
    }

    @Test
    fun hasUpdate_notInstalledApp_returnsFalse() {
        val app = trackedApp(
            currentVersionName = "Not Installed",
            currentVersionCode = 0L,
            latestVersionName = "1.0.0",
        )

        assertFalse(app.hasUpdate)
    }

    @Test
    fun hasUpdate_shizukuPlus_withInstalledVersionRegex_sameVersion_returnsFalse() {
        val app = trackedApp(
            currentVersionName = "Shizuku+ 13.6.0.r2499",
            currentVersionCode = 2499L,
            latestVersionName = "13.6.0.r2499",
            installedVersionRegex = """[0-9]+\.[0-9]+\.[0-9]+\.r[0-9]+""",
        )

        assertFalse(app.hasUpdate)
    }

    @Test
    fun hasUpdate_shizukuPlus_withInstalledVersionRegex_newerVersion_returnsTrue() {
        val app = trackedApp(
            currentVersionName = "Shizuku+ 13.6.0.r2499",
            currentVersionCode = 2499L,
            latestVersionName = "13.6.0.r2500",
            installedVersionRegex = """[0-9]+\.[0-9]+\.[0-9]+\.r[0-9]+""",
        )

        assertTrue(app.hasUpdate)
    }

    @Test
    fun hasUpdate_shizukuPlus_withCaptureGroup_sameVersion_returnsFalse() {
        val app = trackedApp(
            currentVersionName = "Shizuku+ 13.6.0.r2499",
            currentVersionCode = 2499L,
            latestVersionName = "13.6.0.r2499",
            installedVersionRegex = """Shizuku\+\s*(.+)""",
            installedVersionMatchGroup = "1",
        )

        assertFalse(app.hasUpdate)
    }

    private fun trackedApp(
        currentVersionName: String,
        currentVersionCode: Long,
        latestVersionName: String,
        latestDownloadUrl: String? = "https://example.com/app.apk",
        installedVersionRegex: String? = null,
        installedVersionMatchGroup: String? = null,
    ) = TrackedApp(
        packageName = "com.nebula.karing",
        appName = "Karing",
        sourceType = UpdateSourceType.GITHUB,
        sourceUrl = "https://github.com/KaringX/karing",
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        latestVersionName = latestVersionName,
        latestDownloadUrl = latestDownloadUrl,
        installedVersionRegex = installedVersionRegex,
        installedVersionMatchGroup = installedVersionMatchGroup,
    )
}
