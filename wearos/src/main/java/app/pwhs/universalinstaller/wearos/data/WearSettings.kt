package app.pwhs.universalinstaller.wearos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** Watch-side preferences, kept in the DataStore :core already owns. */
class WearSettings(private val context: Context) {

    /** Off by default, which is the behaviour the watch shipped with. */
    val keepAfterInstall: Flow<Boolean> =
        context.dataStore.data.map { it[KEEP_AFTER_INSTALL] ?: false }

    val accentId: Flow<String> = context.dataStore.data.map { it[ACCENT] ?: "" }

    /** Empty means "follow the watch", which is what an unset preference should do. */
    val languageTag: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "" }

    suspend fun setKeepAfterInstall(keep: Boolean) {
        context.dataStore.edit { it[KEEP_AFTER_INSTALL] = keep }
    }

    suspend fun setAccent(id: String) {
        context.dataStore.edit { it[ACCENT] = id }
    }

    suspend fun setLanguage(tag: String) {
        context.dataStore.edit { it[LANGUAGE] = tag }
    }

    companion object {
        private val KEEP_AFTER_INSTALL = booleanPreferencesKey("wear_keep_apk_after_install")
        private val ACCENT = stringPreferencesKey("wear_accent")
        private val LANGUAGE = stringPreferencesKey("wear_language")

        /**
         * attachBaseContext runs before anything can suspend, and the locale has to be in place
         * before the first resource is resolved. One small read off a local file.
         */
        fun readLanguageBlocking(context: Context): String =
            runCatching { runBlocking { context.dataStore.data.first()[LANGUAGE] ?: "" } }
                .getOrDefault("")
    }
}
