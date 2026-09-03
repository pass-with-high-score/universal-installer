package app.pwhs.universalinstaller.wearos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Watch-side preferences, kept in the DataStore :core already owns. */
class WearSettings(private val context: Context) {

    /** Off by default, which is the behaviour the watch shipped with. */
    val keepAfterInstall: Flow<Boolean> =
        context.dataStore.data.map { it[KEEP_AFTER_INSTALL] ?: false }

    suspend fun setKeepAfterInstall(keep: Boolean) {
        context.dataStore.edit { it[KEEP_AFTER_INSTALL] = keep }
    }

    private companion object {
        val KEEP_AFTER_INSTALL = booleanPreferencesKey("wear_keep_apk_after_install")
    }
}
