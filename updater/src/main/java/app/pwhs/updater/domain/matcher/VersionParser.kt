package app.pwhs.updater.domain.matcher

import timber.log.Timber

object VersionParser {
    /**
     * Extracts an effective clean version string using an optional regex.
     * If [versionRegex] is specified, it tries to match against [tagName] or [defaultVersion].
     * If matching produces capturing groups, group 1 is returned; otherwise the entire match value.
     * If matching fails or no regex is provided, falls back to [defaultVersion] (or [tagName] if default is empty).
     */
    fun extractVersion(
        tagName: String?,
        defaultVersion: String?,
        versionRegex: String?,
    ): String {
        val fallback = defaultVersion?.takeIf { it.isNotBlank() }
            ?: tagName.orEmpty()

        if (versionRegex.isNullOrBlank()) {
            return fallback
        }

        return try {
            val regex = Regex(versionRegex.trim())
            val input = tagName?.takeIf { it.isNotBlank() } ?: fallback
            val match = regex.find(input) ?: if (input != fallback) regex.find(fallback) else null

            if (match != null) {
                val extracted = if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) {
                    match.groupValues[1]
                } else {
                    match.value
                }
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
