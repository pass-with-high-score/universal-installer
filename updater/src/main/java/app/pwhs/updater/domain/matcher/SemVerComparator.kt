package app.pwhs.updater.domain.matcher

/**
 * Intelligent version comparator supporting:
 * - Semantic Versioning (e.g. 1.2.3, 1.10.0 > 1.9.0)
 * - Prefixes & Suffixes (e.g. v1.2.3, release-1.2.3, 1.2.3-beta1)
 * - Date-based versions (e.g. 2024.08.31, 20240831)
 * - Single/Multi-part numerical versions (e.g. 1.0 vs 1.0.1)
 */
object SemVerComparator {

    fun isNewer(current: String?, latest: String?): Boolean {
        if (latest.isNullOrBlank()) return false
        if (current.isNullOrBlank() || current.equals("Not Installed", ignoreCase = true)) {
            return false // App not installed, so it's not strictly an "update" (or handled separately)
        }

        if (current.trim().equals(latest.trim(), ignoreCase = true)) {
            return false
        }

        return compareVersions(latest, current) > 0
    }

    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = cleanVersion(v1)
        val clean2 = cleanVersion(v2)

        if (clean1 == clean2) return 0

        val parts1 = splitVersionParts(clean1)
        val parts2 = splitVersionParts(clean2)

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i)
            val p2 = parts2.getOrNull(i)

            if (p1 == null && p2 == null) break
            if (p1 == null) {
                // p1 ended. Check if remaining parts of p2 represent a pre-release tag (e.g. -rc10, -beta)
                // A normal version (p1) is GREATER than a pre-release version (p2).
                val remainingHasNonNumeric = parts2.subList(i, parts2.size).any { !it.isNumeric }
                return if (remainingHasNonNumeric) {
                    1 // p1 (normal) > p2 (pre-release)
                } else if (parts2.subList(i, parts2.size).all { it.numValue == 0L }) {
                    0 // e.g. 1.0 vs 1.0.0
                } else {
                    -1 // p1 (1.0) < p2 (1.0.1)
                }
            }
            if (p2 == null) {
                // p2 ended. Check if remaining parts of p1 represent a pre-release tag
                val remainingHasNonNumeric = parts1.subList(i, parts1.size).any { !it.isNumeric }
                return if (remainingHasNonNumeric) {
                    -1 // p1 (pre-release) < p2 (normal)
                } else if (parts1.subList(i, parts1.size).all { it.numValue == 0L }) {
                    0 // e.g. 1.0.0 vs 1.0
                } else {
                    1 // p1 (1.0.1) > p2 (1.0)
                }
            }

            if (p1.isNumeric && p2.isNumeric) {
                if (p1.numValue != p2.numValue) {
                    return p1.numValue.compareTo(p2.numValue)
                }
            } else {
                val strCompare = p1.rawValue.compareTo(p2.rawValue, ignoreCase = true)
                if (strCompare != 0) {
                    // Numeric components always rank higher than pre-release tags (e.g. 1.0.0 > 1.0.0-alpha)
                    return when {
                        p1.isNumeric -> 1
                        p2.isNumeric -> -1
                        else -> strCompare
                    }
                }
            }
        }

        return 0
    }

    private fun cleanVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("release-")
            .removePrefix("release_")
            .removePrefix("app-")
            .removePrefix("v.")
            .removeSuffix(".apk")
            .trim()
    }

    private data class VersionPart(
        val rawValue: String,
        val numValue: Long = 0L,
        val isNumeric: Boolean = false,
    )

    private fun splitVersionParts(version: String): List<VersionPart> {
        val tokens = version.split('.', '-', '_', '+', ' ')
        val result = mutableListOf<VersionPart>()

        for (token in tokens) {
            if (token.isBlank()) continue

            // Split embedded letters and digits (e.g. "1beta2" -> ["1", "beta", "2"])
            val subTokens = token.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
            for (sub in subTokens) {
                val num = sub.toLongOrNull()
                if (num != null) {
                    result.add(VersionPart(rawValue = sub, numValue = num, isNumeric = true))
                } else {
                    result.add(VersionPart(rawValue = sub, isNumeric = false))
                }
            }
        }

        return result
    }
}
