package app.pwhs.universalinstaller.presentation.install

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.solrudev.ackpine.installer.InstallFailure

class InstallErrorHelperTest {

    @Test
    fun `sdkToAndroidVersion maps known SDK versions correctly`() {
        assertEquals("13", InstallErrorHelper.sdkToAndroidVersion(33))
        assertEquals("14", InstallErrorHelper.sdkToAndroidVersion(34))
        assertEquals("15", InstallErrorHelper.sdkToAndroidVersion(35))
        assertEquals("16", InstallErrorHelper.sdkToAndroidVersion(36))
        assertEquals("17", InstallErrorHelper.sdkToAndroidVersion(37))
        assertEquals("12", InstallErrorHelper.sdkToAndroidVersion(31))
        assertEquals("11", InstallErrorHelper.sdkToAndroidVersion(30))
        assertEquals("10", InstallErrorHelper.sdkToAndroidVersion(29))
        assertEquals("9", InstallErrorHelper.sdkToAndroidVersion(28))
        assertEquals("8.0", InstallErrorHelper.sdkToAndroidVersion(26))
        assertEquals("7.0", InstallErrorHelper.sdkToAndroidVersion(24))
    }

    @Test
    fun `sdkToAndroidVersion falls back to sdk int string for unknown version`() {
        assertEquals("99", InstallErrorHelper.sdkToAndroidVersion(99))
    }

    @Test
    fun `failureKey returns expected stable strings`() {
        assertEquals("incompatible", InstallErrorHelper.failureKey(InstallFailure.Incompatible("test")))
        assertEquals("conflict", InstallErrorHelper.failureKey(InstallFailure.Conflict("test")))
        assertEquals("aborted", InstallErrorHelper.failureKey(InstallFailure.Aborted("test")))
        assertEquals("blocked", InstallErrorHelper.failureKey(InstallFailure.Blocked("test")))
        assertEquals("invalid", InstallErrorHelper.failureKey(InstallFailure.Invalid("test")))
        assertEquals("storage", InstallErrorHelper.failureKey(InstallFailure.Storage("test")))
        assertEquals("timeout", InstallErrorHelper.failureKey(InstallFailure.Timeout("test")))
        assertEquals("generic", InstallErrorHelper.failureKey(InstallFailure.Generic("test")))
    }
}
