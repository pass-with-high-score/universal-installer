package app.pwhs.universalinstaller.presentation.setting

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.pwhs.core.data.local.SharedPrefsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPreferencesDelegateTest {

    @Test
    fun defaults_matchExpectedSecurityConfigurations() {
        val empty = emptyPreferences()

        assertEquals("virusdetec", SharedPrefsKeys.getPrimaryScannerId(empty))
        assertEquals("virustotal", SharedPrefsKeys.getBackupScannerId(empty))
        assertTrue(SharedPrefsKeys.isAutoFallbackEnabled(empty))
        assertFalse(SharedPrefsKeys.isSecurityScanningDisabled(empty))
        assertFalse(empty[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] ?: false)
    }

    @Test
    fun primaryScanner_canBeSetToVirusTotal() {
        val prefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_PRIMARY_SCANNER to "virustotal",
            SharedPrefsKeys.SECURITY_BACKUP_SCANNER to "virusdetec",
        )

        assertEquals("virustotal", SharedPrefsKeys.getPrimaryScannerId(prefs))
        assertEquals("virusdetec", SharedPrefsKeys.getBackupScannerId(prefs))
    }

    @Test
    fun autoFallbackToggle_worksAccurately() {
        val enabled = mutablePreferencesOf(SharedPrefsKeys.SECURITY_AUTO_FALLBACK to true)
        assertTrue(SharedPrefsKeys.isAutoFallbackEnabled(enabled))

        val disabled = mutablePreferencesOf(SharedPrefsKeys.SECURITY_AUTO_FALLBACK to false)
        assertFalse(SharedPrefsKeys.isAutoFallbackEnabled(disabled))
    }

    @Test
    fun offlineChecksToggle_worksAccurately() {
        val enabled = mutablePreferencesOf(SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS to true)
        assertTrue(enabled[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] ?: false)

        val disabled = mutablePreferencesOf(SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS to false)
        assertFalse(disabled[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] ?: false)
    }

    @Test
    fun globalDisableFlag_isBackwardCompatible() {
        val disabled = mutablePreferencesOf(SharedPrefsKeys.DISABLE_VIRUSTOTAL to true)
        assertTrue(SharedPrefsKeys.isSecurityScanningDisabled(disabled))

        val enabled = mutablePreferencesOf(SharedPrefsKeys.DISABLE_VIRUSTOTAL to false)
        assertFalse(SharedPrefsKeys.isSecurityScanningDisabled(enabled))
    }
}
