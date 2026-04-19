package app.pwhs.universalinstaller.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import androidx.core.content.edit

/**
 * Per-app language.
 *
 * On API 33+ we delegate to the platform [LocaleManager] so the choice shows up in the system
 * per-app language screen and the activity is recreated by the framework. On older API levels we
 * persist the tag ourselves and wrap [MainActivity.attachBaseContext] so resources resolve with
 * the chosen locale.
 */
object LocaleHelper {

    private const val PREFS = "locale_prefs"
    private const val KEY_LANG = "app_language"

    /** BCP-47 tag (e.g. "vi", "pt-BR") or empty string meaning "follow system". */
    fun getStoredLanguage(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "") ?: ""
    }

    fun setAppLanguage(context: Context, tag: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_LANG, tag)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
            context.getSystemService(LocaleManager::class.java).applicationLocales = localeList
        }
    }

    /**
     * Wrap [base] so resources use the stored locale. On API 33+ the system already applies the
     * per-app locale, so this is a no-op there.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = getStoredLanguage(base)
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
