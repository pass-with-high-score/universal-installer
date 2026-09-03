package app.pwhs.universalinstaller.wearos.presentation.settings

import java.util.Locale

/**
 * The locales this module actually ships translations for. Android resolves `values-in` for
 * Indonesian and `values-pt-rBR` for Brazilian Portuguese, so those are the tags stored — a tag
 * the resource folders do not match would silently fall back to English.
 */
object AppLocale {

    /** Empty tag means "follow the watch". */
    const val SYSTEM = ""

    val TAGS = listOf(
        SYSTEM, "ar", "de", "el", "en", "es", "fr", "hi", "in", "it",
        "ja", "ko", "pl", "pt-BR", "ro", "ru", "tr", "uk", "vi", "zh",
    )

    /** Endonym — a language is easiest to find written the way its own speakers write it. */
    fun displayName(tag: String): String {
        val locale = toLocale(tag) ?: return ""
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    fun toLocale(tag: String): Locale? = when {
        tag.isEmpty() -> null
        tag == "in" -> Locale("in")
        tag.contains('-') -> tag.split('-').let { Locale(it[0], it[1]) }
        else -> Locale(tag)
    }
}
