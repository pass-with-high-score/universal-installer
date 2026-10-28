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

    @Test
    fun `isNoCertificatesMessage detects certificate errors correctly`() {
        org.junit.Assert.assertTrue(InstallErrorHelper.isNoCertificatesMessage("INSTALL_PARSE_FAILED_NO_CERTIFICATES"))
        org.junit.Assert.assertTrue(InstallErrorHelper.isNoCertificatesMessage("Package has no certificates"))
        org.junit.Assert.assertFalse(InstallErrorHelper.isNoCertificatesMessage("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
    }

    @Test
    fun `isMissingSplitMessage detects split errors correctly`() {
        org.junit.Assert.assertTrue(InstallErrorHelper.isMissingSplitMessage("INSTALL_FAILED_MISSING_SPLIT: missing split config.arm64"))
        org.junit.Assert.assertFalse(InstallErrorHelper.isMissingSplitMessage("INSTALL_FAILED_VERSION_DOWNGRADE"))
    }

    @Test
    fun `isSecurityMessage detects security exceptions correctly`() {
        org.junit.Assert.assertTrue(InstallErrorHelper.isSecurityMessage("java.lang.SecurityException: Session does not belong to uid"))
        org.junit.Assert.assertTrue(InstallErrorHelper.isSecurityMessage("STATUS_FAILURE_SECURITY"))
        org.junit.Assert.assertFalse(InstallErrorHelper.isSecurityMessage("INSTALL_FAILED_ABORTED"))
    }

    @Test
    fun `shouldSuggestShizuku returns true for system and security errors`() {
        org.junit.Assert.assertTrue(InstallErrorHelper.shouldSuggestShizuku("SecurityException: User 0 cannot install"))
        org.junit.Assert.assertTrue(InstallErrorHelper.shouldSuggestShizuku("INSTALL_FAILED_INTERNAL_ERROR"))
        org.junit.Assert.assertTrue(InstallErrorHelper.shouldSuggestShizuku("STATUS_FAILURE_SECURITY"))
        org.junit.Assert.assertTrue(InstallErrorHelper.shouldSuggestShizuku("INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION"))
        org.junit.Assert.assertTrue(InstallErrorHelper.shouldSuggestShizuku("ColorOS isolation restriction"))
        org.junit.Assert.assertFalse(InstallErrorHelper.shouldSuggestShizuku("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
    }

    @Test
    fun `isIsolationViolationMessage detects HyperOS and ColorOS restrictions`() {
        org.junit.Assert.assertTrue(InstallErrorHelper.isIsolationViolationMessage("INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION"))
        org.junit.Assert.assertTrue(InstallErrorHelper.isIsolationViolationMessage("coloros isolation"))
        org.junit.Assert.assertFalse(InstallErrorHelper.isIsolationViolationMessage("INSTALL_FAILED_VERSION_DOWNGRADE"))
    }

    @Test
    fun `extractErrorCode detects hyperos isolation violation`() {
        val failure = InstallFailure.Generic("INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: commit failed")
        assertEquals("INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION", InstallErrorHelper.extractErrorCode(failure))
    }
}
