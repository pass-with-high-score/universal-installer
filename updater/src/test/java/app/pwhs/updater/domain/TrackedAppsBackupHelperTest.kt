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
}
