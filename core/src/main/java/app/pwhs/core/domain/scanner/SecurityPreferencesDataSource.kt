package app.pwhs.core.domain.scanner

import android.content.Context
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

interface SecurityPreferencesDataSource {
    suspend fun isScanningDisabled(context: Context): Boolean
    suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId
    suspend fun getActiveBackupScanner(context: Context): ScannerProviderId
    suspend fun isAutoFallbackEnabled(context: Context): Boolean
    suspend fun isOfflineChecksEnabled(context: Context): Boolean
}

class DefaultSecurityPreferencesDataSource : SecurityPreferencesDataSource {
    override suspend fun isScanningDisabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[SharedPrefsKeys.DISABLE_VIRUSTOTAL] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getActivePrimaryScanner(context: Context): ScannerProviderId {
        return try {
            val rawId = context.dataStore.data.first()[SharedPrefsKeys.SECURITY_PRIMARY_SCANNER]
            ScannerProviderId.fromId(rawId, default = ScannerProviderId.VIRUSDETEC)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ScannerProviderId.VIRUSDETEC
        }
    }

    override suspend fun getActiveBackupScanner(context: Context): ScannerProviderId {
        return try {
            val rawId = context.dataStore.data.first()[SharedPrefsKeys.SECURITY_BACKUP_SCANNER]
            ScannerProviderId.fromId(rawId, default = ScannerProviderId.VIRUSTOTAL)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ScannerProviderId.VIRUSTOTAL
        }
    }

    override suspend fun isAutoFallbackEnabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[SharedPrefsKeys.SECURITY_AUTO_FALLBACK] ?: true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    override suspend fun isOfflineChecksEnabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
