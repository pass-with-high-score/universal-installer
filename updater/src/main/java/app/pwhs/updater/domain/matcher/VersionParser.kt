package app.pwhs.updater.domain.matcher

import timber.log.Timber

object VersionParser {

    /**
     * Validates if [pattern] is a valid regular expression.
     * Returns null if valid (or blank), or the error description if invalid.
     */
    fun validateRegex(pattern: String?): String? {
        if (pattern.isNullOrBlank()) return null
        return try {
            Regex(pattern.trim())
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Invalid regular expression"
        }
    }

    /**
     * Replaces `$N` references (or plain number `N`) in [matchGroupString] with
     * the corresponding regex match group value, replicating Obtainium's
     * `replaceMatchGroupsInString` logic.
     */
    fun replaceMatchGroupsInString(
        matchResult: MatchResult,
        matchGroupString: String,
    ): String? {
        var pattern = matchGroupString.trim()
        if (pattern.matches(Regex("^\\d+$"))) {
            pattern = "\$$pattern"
        }

        val numberRegex = Regex("""\\?\$(\d+)""")
        val matches = numberRegex.findAll(pattern).toList()
        if (matches.isEmpty()) {
            return null
        }

        var output = pattern
        for (m in matches) {
            val fullToken = m.value
            if (fullToken.startsWith("\\$")) {
                output = output.replace(fullToken, fullToken.removePrefix("\\"))
            } else {
                val groupIndex = m.groupValues[1].toIntOrNull() ?: continue
                val groupValue = if (groupIndex < matchResult.groupValues.size) {
                    matchResult.groupValues[groupIndex]
                } else {
                    ""
                }
                output = output.replace(fullToken, groupValue)
            }
        }
        return output
    }

    /**
     * Applies version extraction regex to candidate strings, supporting match groups
     * and release title selection like Obtainium.
     */
    fun extractVersion(
        tagName: String?,
        releaseTitle: String? = null,
        defaultVersion: String? = null,
        versionRegex: String? = null,
        matchGroup: String? = null,
        useReleaseTitleAsVersion: Boolean = false,
    ): String {
        val primaryInput = if (useReleaseTitleAsVersion) {
            releaseTitle?.takeIf { it.isNotBlank() } ?: tagName.orEmpty()
        } else {
            tagName?.takeIf { it.isNotBlank() } ?: releaseTitle.orEmpty()
        }

        val fallback = defaultVersion?.takeIf { it.isNotBlank() }
            ?: primaryInput

        if (versionRegex.isNullOrBlank()) {
            return fallback
        }

        return try {
            val regex = Regex(versionRegex.trim())
            val allMatches = regex.findAll(primaryInput).toList()
            val match = allMatches.lastOrNull()
                ?: if (!useReleaseTitleAsVersion && !releaseTitle.isNullOrBlank()) {
                    regex.findAll(releaseTitle).toList().lastOrNull()
                } else if (fallback != primaryInput) {
                    regex.findAll(fallback).toList().lastOrNull()
                } else null

            if (match == null) {
                return fallback
            }

            val groupStr = matchGroup?.trim().orEmpty()
            val extracted = if (groupStr.isNotEmpty()) {
                replaceMatchGroupsInString(match, groupStr)
            } else {
                if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) {
                    match.groupValues[1]
                } else {
                    match.value
                }
            }

            if (!extracted.isNullOrBlank()) {
                extracted.trim()
            } else {
                fallback
            }
        } catch (e: Exception) {
            Timber.w(e, "Invalid version regex pattern: $versionRegex")
            fallback
        }
    }
}
