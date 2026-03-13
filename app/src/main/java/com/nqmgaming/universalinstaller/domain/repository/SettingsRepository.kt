package com.nqmgaming.universalinstaller.domain.repository

import com.nqmgaming.universalinstaller.domain.installer.InstallMethod
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val installMethodFlow: Flow<InstallMethod>
    suspend fun setInstallMethod(method: InstallMethod)
}
