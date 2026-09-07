package app.pwhs.updater.domain.matcher

import android.os.Build
import app.pwhs.updater.domain.model.AssetArtifact

/**
 * Matches and selects the most optimal APK asset for the current device's CPU architecture.
 *
 * Employs architecture alias mapping (e.g. arm64-v8a <-> aarch64 / arm64) inspired by Obtainium
 * and falls back gracefully to universal APKs when specific architecture builds are absent.
 */
object SmartAbiMatcher {

    val PACKAGE_EXTENSIONS = setOf(".apk", ".apks", ".xapk", ".apkm")

    fun isPackageAsset(name: String): Boolean =
        PACKAGE_EXTENSIONS.any { ext -> name.endsWith(ext, ignoreCase = true) }

    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64", "aarch64"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armv7", "armeabi", "arm7"),
        "x86_64" to listOf("x86_64", "x64"),
        "x86" to listOf("x86"),
    )

    private val UNIVERSAL_TOKENS = listOf("universal", "all", "fat", "full")

    /**
     * Selects the best asset from [assets] for the given [deviceAbis].
     */
    fun selectBestAsset(
        assets: List<AssetArtifact>,
        deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        customFilterRegex: String? = null,
    ): AssetArtifact? {
        if (assets.isEmpty()) return null

        var packageAssets = assets.filter { asset ->
            PACKAGE_EXTENSIONS.any { ext -> asset.name.endsWith(ext, ignoreCase = true) }
        }
        if (packageAssets.isEmpty()) return null

        // Apply custom user regex if present
        if (!customFilterRegex.isNullOrBlank()) {
            val regex = runCatching { Regex(customFilterRegex, RegexOption.IGNORE_CASE) }.getOrNull()
            if (regex != null) {
                val filtered = packageAssets.filter { regex.containsMatchIn(it.name) }
                if (filtered.isNotEmpty()) {
                    packageAssets = filtered
                }
            }
        }

        if (packageAssets.size == 1) return packageAssets.first()

        // 1. Try matching against device ABIs in order of priority
        for (abi in deviceAbis) {
            val aliases = ABI_ALIASES[abi] ?: listOf(abi)
            val matched = packageAssets.filter { asset ->
                val nameLower = asset.name.lowercase()
                aliases.any { alias ->
                    nameLower.contains(alias.lowercase())
                }
            }
            if (matched.isNotEmpty()) {
                // If single match found, return it; otherwise prefer standard apk
                return matched.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: matched.first()
            }
        }

        // 2. Try universal / all fallback
        val universalAsset = packageAssets.firstOrNull { asset ->
            val nameLower = asset.name.lowercase()
            UNIVERSAL_TOKENS.any { token -> nameLower.contains(token) }
        }
        if (universalAsset != null) return universalAsset

        // 3. Fallback to the first apk
        return packageAssets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: packageAssets.first()
    }
}
