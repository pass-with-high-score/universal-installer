package com.nqmgaming.universalinstaller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nqmgaming.universalinstaller.domain.installer.InstallMethod
import com.nqmgaming.universalinstaller.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val INSTALL_METHOD_KEY = stringPreferencesKey("install_method")

    override val installMethodFlow: Flow<InstallMethod> = context.dataStore.data.map { preferences ->
        val methodString = preferences[INSTALL_METHOD_KEY] ?: InstallMethod.STANDARD.name
        try {
            InstallMethod.valueOf(methodString)
        } catch (e: Exception) {
            InstallMethod.STANDARD
        }
    }

    override suspend fun setInstallMethod(method: InstallMethod) {
        context.dataStore.edit { preferences ->
            preferences[INSTALL_METHOD_KEY] = method.name
        }
    }
}
