package app.pwhs.updater.domain

import app.pwhs.updater.domain.matcher.VersionParser
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun extractVersion_withExplicitMatchGroup_extractsSpecifiedGroup() {
        val result1 = VersionParser.extractVersion(
            tagName = "app-release-2.4.0-final",
            defaultVersion = "app-release-2.4.0-final",
            versionRegex = "app-(release)-(\\d+\\.\\d+\\.\\d+)",
            matchGroup = "2",
        )
        assertEquals("2.4.0", result1)

        val resultTemplate = VersionParser.extractVersion(
            tagName = "v2-patch4",
            defaultVersion = "v2-patch4",
            versionRegex = "v(\\d+)-patch(\\d+)",
            matchGroup = "$1.$2",
        )
        assertEquals("2.4", resultTemplate)
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
    fun extractVersion_useReleaseTitleAsVersion_prioritizesTitleOverTag() {
        val result = VersionParser.extractVersion(
            tagName = "build-12345",
            releaseTitle = "NextPlayer v1.6.7",
            defaultVersion = "build-12345",
            versionRegex = "v?(\\d+\\.\\d+\\.\\d+)",
            useReleaseTitleAsVersion = true,
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
    fun validateRegex_detectsSyntaxErrors() {
        assertNull(VersionParser.validateRegex(null))
        assertNull(VersionParser.validateRegex(""))
        assertNull(VersionParser.validateRegex("^v?([0-9.]+)$"))
        assertNotNull(VersionParser.validateRegex("[unclosed"))
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
            matchGroup = "1",
        )

        assertTrue(app.hasUpdate)
    }
}
