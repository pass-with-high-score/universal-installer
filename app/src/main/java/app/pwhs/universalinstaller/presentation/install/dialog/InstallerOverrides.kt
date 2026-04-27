package app.pwhs.universalinstaller.presentation.install.dialog

/**
 * Per-package installer-source override map serialised as one entry per line:
 * `pkg=installer\npkg=installer\n…`. Lightweight by design — no JSON dep needed
 * for a flat string→string map, and human-readable in dumpsys.
 *
 * Both keys and values are lowercase Java package names (e.g. `com.foo.app`,
 * `com.android.vending`). Lines with no `=` are ignored. Trailing whitespace
 * is trimmed. Empty values are treated as "no override" and skipped.
 */
object InstallerOverrides {

    fun parse(serialized: String?): Map<String, String> {
        if (serialized.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (line in serialized.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0 || eq == trimmed.lastIndex) continue
            val pkg = trimmed.substring(0, eq).trim()
            val installer = trimmed.substring(eq + 1).trim()
            if (pkg.isNotEmpty() && installer.isNotEmpty()) out[pkg] = installer
        }
        return out
    }

    fun serialize(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }

    fun get(serialized: String?, packageName: String): String? {
        if (packageName.isBlank()) return null
        return parse(serialized)[packageName]
    }

    fun put(serialized: String?, packageName: String, installer: String): String {
        if (packageName.isBlank() || installer.isBlank()) return serialized.orEmpty()
        val map = parse(serialized).toMutableMap()
        map[packageName] = installer
        return serialize(map)
    }

    fun remove(serialized: String?, packageName: String): String {
        if (packageName.isBlank()) return serialized.orEmpty()
        val map = parse(serialized).toMutableMap()
        map.remove(packageName)
        return serialize(map)
    }
}
