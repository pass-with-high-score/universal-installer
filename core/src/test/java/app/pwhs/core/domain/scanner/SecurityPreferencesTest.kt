package app.pwhs.core.domain.scanner

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.pwhs.core.data.local.SharedPrefsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & 3 Tests for Security Preferences, DataStore keys, and legacy backward compatibility.
 */
class SecurityPreferencesTest {

    @Test
    fun testSecurityPreferences_defaultValues_areSound() {
        val emptyPrefs = emptyPreferences()

        assertEquals("virusdetec", SharedPrefsKeys.getPrimaryScannerId(emptyPrefs))
        assertEquals("virustotal", SharedPrefsKeys.getBackupScannerId(emptyPrefs))
        assertTrue(SharedPrefsKeys.isAutoFallbackEnabled(emptyPrefs))
        assertFalse(SharedPrefsKeys.isSecurityScanningDisabled(emptyPrefs))
    }

    @Test
    fun testSecurityPreferences_primaryScanner_customValues() {
        val prefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_PRIMARY_SCANNER to "virustotal"
        )
        assertEquals("virustotal", SharedPrefsKeys.getPrimaryScannerId(prefs))

        val offlinePrefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_PRIMARY_SCANNER to "offline_static"
        )
        assertEquals("offline_static", SharedPrefsKeys.getPrimaryScannerId(offlinePrefs))
    }

    @Test
    fun testSecurityPreferences_backupScanner_customValues() {
        val prefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_BACKUP_SCANNER to "offline_static"
        )
        assertEquals("offline_static", SharedPrefsKeys.getBackupScannerId(prefs))
    }

    @Test
    fun testSecurityPreferences_autoFallbackToggle() {
        val disabledFallbackPrefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_AUTO_FALLBACK to false
        )
        assertFalse(SharedPrefsKeys.isAutoFallbackEnabled(disabledFallbackPrefs))

        val enabledFallbackPrefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_AUTO_FALLBACK to true
        )
        assertTrue(SharedPrefsKeys.isAutoFallbackEnabled(enabledFallbackPrefs))
    }

    @Test
    fun testSecurityPreferences_legacyDisableVirusTotal_backwardCompatibility() {
        // When user previously toggled legacy disable_virustotal to true
        val disabledPrefs = mutablePreferencesOf(
            SharedPrefsKeys.DISABLE_VIRUSTOTAL to true
        )
        assertTrue(SharedPrefsKeys.isSecurityScanningDisabled(disabledPrefs))

        val enabledPrefs = mutablePreferencesOf(
            SharedPrefsKeys.DISABLE_VIRUSTOTAL to false
        )
        assertFalse(SharedPrefsKeys.isSecurityScanningDisabled(enabledPrefs))
    }

    @Test
    fun testSecurityPreferences_virusTotalApiKey_storedAndRetrieved() {
        val testApiKey = "a".repeat(64)
        val prefs = mutablePreferencesOf(
            SharedPrefsKeys.VIRUSTOTAL_API_KEY to testApiKey
        )
        assertEquals(testApiKey, prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY])
    }

    @Test
    fun testSecurityPreferences_offlineChecksKey() {
        val prefs = mutablePreferencesOf(
            SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS to true
        )
        assertTrue(prefs[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] == true)
    }
}
