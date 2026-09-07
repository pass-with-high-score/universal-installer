package app.pwhs.updater.domain

import app.pwhs.updater.domain.matcher.VersionParser
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionParserTest {

    @Test
    fun extractVersion_withCapturingGroup_extractsGroup1() {
        val result = VersionParser.extractVersion(
            tagName = "NeoPlayer1.6.7",
            defaultVersion = "NeoPlayer1.6.7",
            versionRegex = "^NeoPlayer(.+)$",
        )
        assertEquals("1.6.7", result)
    }

    @Test
    fun extractVersion_withoutCapturingGroup_extractsFullMatch() {
        val result = VersionParser.extractVersion(
            tagName = "NeoPlayer1.6.7",
            defaultVersion = "NeoPlayer1.6.7",
            versionRegex = "[0-9]+\\.[0-9]+\\.[0-9]+",
        )
        assertEquals("1.6.7", result)
    }

    @Test
    fun extractVersion_nullOrBlankRegex_returnsDefault() {
        val resultNull = VersionParser.extractVersion(
            tagName = "v1.2.3",
            defaultVersion = "1.2.3",
            versionRegex = null,
        )
        assertEquals("1.2.3", resultNull)

        val resultBlank = VersionParser.extractVersion(
            tagName = "v1.2.3",
            defaultVersion = "1.2.3",
            versionRegex = "   ",
        )
        assertEquals("1.2.3", resultBlank)
    }

    @Test
    fun extractVersion_regexNoMatch_returnsFallback() {
        val result = VersionParser.extractVersion(
            tagName = "some-weird-tag",
            defaultVersion = "some-weird-tag",
            versionRegex = "^v(\\d+\\.\\d+)$",
        )
        assertEquals("some-weird-tag", result)
    }

    @Test
    fun extractVersion_invalidRegex_handlesGracefully() {
        val result = VersionParser.extractVersion(
            tagName = "v1.2.3",
            defaultVersion = "1.2.3",
            versionRegex = "[unclosed-bracket",
        )
        assertEquals("1.2.3", result)
    }

    @Test
    fun extractVersion_trackedAppUpdateComparison_worksWithCustomRegex() {
        val tagName = "NeoPlayer1.6.7"
        val regex = "^NeoPlayer(.+)$"
        val parsedVersion = VersionParser.extractVersion(
            tagName = tagName,
            defaultVersion = tagName,
            versionRegex = regex,
        )

        val app = TrackedApp(
            packageName = "com.nextplayer",
            appName = "NextPlayer",
            sourceType = UpdateSourceType.GITHUB,
            sourceUrl = "https://github.com/NextPlayerCloud/NextPlayerCloud",
            currentVersionName = "1.6.6",
            currentVersionCode = 166L,
            latestVersionName = parsedVersion,
            latestReleaseTag = tagName,
            latestDownloadUrl = "https://github.com/NextPlayerCloud/NextPlayerCloud/releases/download/NeoPlayer1.6.7/app.apk",
            versionRegex = regex,
        )

        assertTrue(app.hasUpdate)
    }
}
