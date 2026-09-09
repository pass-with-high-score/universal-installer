package app.pwhs.updater.domain

import app.pwhs.updater.domain.backup.TrackedAppsBackupHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppsBackupHelperTest {

    @Test
    fun importFromJson_obtainiumFormat_parsesAdditionalSettingsCorrectly() {
        val obtainiumJson = """
        {
          "apps": [
            {
              "id": "com.nextplayer",
              "url": "https://github.com/NextPlayerCloud/NextPlayerCloud",
              "name": "NextPlayer",
              "author": "NextPlayerCloud",
              "additionalSettings": {
                "versionExtractionRegEx": "^NeoPlayer(.+)$",
                "matchGroupToUse": "1",
                "releaseTitleAsVersion": true,
                "apkFilterRegEx": ".*-arm64.*\\.apk",
                "includePrereleases": true
              }
            }
          ]
        }
        """.trimIndent()

        val apps = TrackedAppsBackupHelper.importFromJson(obtainiumJson)
        assertEquals(1, apps.size)
        val app = apps.first()
        assertEquals("com.nextplayer", app.packageName)
        assertEquals("NextPlayer", app.appName)
        assertEquals("^NeoPlayer(.+)$", app.versionRegex)
        assertEquals("1", app.matchGroup)
        assertTrue(app.useReleaseTitleAsVersion)
        assertEquals(".*-arm64.*\\.apk", app.customRegexFilter)
        assertTrue(app.includePrereleases)
    }

    @Test
    fun importFromJson_obtainiumStringifiedAdditionalSettings_parsesCorrectly() {
        val obtainiumJson = """
        [
          {
            "id": "org.fdroid.fdroid",
            "url": "https://gitlab.com/fdroid/fdroidclient",
            "name": "F-Droid",
            "author": "F-Droid",
            "additionalSettings": "{\"versionExtractionRegEx\":\"^v(.+)$\",\"matchGroupToUse\":\"1\",\"releaseTitleAsVersion\":false,\"apkFilterRegEx\":\".*\\.apk\",\"includePrereleases\":true}"
          },
          {
            "invalid_entry": true
          }
        ]
        """.trimIndent()

        val apps = TrackedAppsBackupHelper.importFromJson(obtainiumJson)
        assertEquals(1, apps.size)
        val app = apps.first()
        assertEquals("org.fdroid.fdroid", app.packageName)
        assertEquals("F-Droid", app.appName)
        assertEquals("^v(.+)$", app.versionRegex)
        assertEquals("1", app.matchGroup)
        assertFalse(app.useReleaseTitleAsVersion)
        assertEquals(".*\\.apk", app.customRegexFilter)
        assertTrue(app.includePrereleases)
    }
}
