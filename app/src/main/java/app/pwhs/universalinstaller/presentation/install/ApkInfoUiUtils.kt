package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PermissionInfo
import java.util.Locale

/**
 * Friendly display name for a BCP-47 / ISO language tag.
 *
 * Examples: `"en"` → "English", `"fr"` → "Français", `"zh-CN"` → "Chinese (China)".
 * Falls back to the original (uppercased) code when [Locale] can't resolve a name —
 * happens for non-locale strings that occasionally land here, e.g. raw ABI suffixes
 * fed in by mistake. Used by the splits chip picker to label `Apk.Localization`
 * entries readably ("English" instead of "config.en").
 */
fun displayLanguage(code: String): String {
    if (code.isBlank()) return code
    return runCatching {
        val locale = Locale.forLanguageTag(code.replace('_', '-'))
        val name = locale.getDisplayName(Locale.getDefault())
        if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) {
            code.uppercase(Locale.getDefault())
        } else {
            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }.getOrDefault(code)
}

/**
 * Resolved view of a single permission for UI rendering.
 *
 * @param name      raw permission identifier, e.g. `android.permission.CAMERA`
 * @param label     human-readable label from PackageManager.loadLabel(), or a
 *                  Title-cased short name fallback when the system has no label
 * @param prefix    everything before the last dot, used as a subtle subtitle
 * @param isDangerous true when PackageManager reports
 *                  [PermissionInfo.PROTECTION_DANGEROUS]; drives danger-tinting in UI
 */
data class PermissionEntry(
    val name: String,
    val label: String,
    val prefix: String,
    val isDangerous: Boolean,
)

/**
 * Build a sorted list of [PermissionEntry] from raw permission strings.
 *
 * Authoritative danger detection — uses [PermissionInfo.protection] from
 * PackageManager rather than a hardcoded allow-list, so newly-introduced
 * dangerous permissions (e.g. `POST_NOTIFICATIONS` on API 33+) get flagged
 * automatically. Sort order: dangerous-first, then alphabetical by label.
 *
 * Permission lookups can fail for unknown / vendor-specific names; those fall
 * back to PROTECTION_NORMAL and a Title-cased short label.
 */
fun resolvePermissionEntries(context: Context, names: List<String>): List<PermissionEntry> {
    val pm = context.packageManager
    return names.map { name ->
        val info = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
        val protection = info?.protection ?: PermissionInfo.PROTECTION_NORMAL
        val isDangerous = protection == PermissionInfo.PROTECTION_DANGEROUS
        val label = info?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() && it != name }
            ?: name.substringAfterLast('.').replace('_', ' ').lowercase(Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        PermissionEntry(
            name = name,
            label = label,
            prefix = name.substringBeforeLast('.', ""),
            isDangerous = isDangerous,
        )
    }.sortedWith(
        compareByDescending<PermissionEntry> { it.isDangerous }
            .thenBy { it.label.lowercase(Locale.getDefault()) },
    )
}
