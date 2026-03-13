package com.nqmgaming.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.domain.installer.AppInstaller
import com.nqmgaming.universalinstaller.domain.installer.InstallMethod
import com.nqmgaming.universalinstaller.domain.installer.InstallOptions
import com.nqmgaming.universalinstaller.domain.installer.InstallState
import com.nqmgaming.universalinstaller.domain.installer.StandardInstaller
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo
import com.nqmgaming.universalinstaller.domain.parser.AppParser
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber
import java.util.UUID

class InstallViewModel(
    private val appParser: AppParser,
    private val appInstaller: AppInstaller,
    private val sessionDataRepository: SessionDataRepository,
    private val context: Context
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _parsedAppInfo = MutableStateFlow<AppInfo?>(null)
    val parsedAppInfo: StateFlow<AppInfo?> = _parsedAppInfo.asStateFlow()

    private val _existingAppInfo = MutableStateFlow<ExistingAppInfo?>(null)
    val existingAppInfo: StateFlow<ExistingAppInfo?> = _existingAppInfo.asStateFlow()

    private var installJob: Job? = null

    val uiState = combine(
        _error,
        sessionDataRepository.sessions,
        sessionDataRepository.sessionsProgress,
        _parsedAppInfo,
        _existingAppInfo
    ) { error, sessions, progress, appInfo, existing ->
        InstallUiState(
            error = error,
            sessions = sessions,
            sessionsProgress = progress,
            parsedAppInfo = appInfo,
            existingAppInfo = existing
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    fun parseApp(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _error.value = null
                val appInfo = appParser.parse(context, uri)
                if (appInfo != null) {
                    _parsedAppInfo.value = appInfo
                    checkExistingApp(appInfo.packageName)
                } else {
                    _error.value = "Failed to parse application information."
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing app")
                _error.value = "Error parsing file: ${e.message}"
            }
        }
    }

    private fun checkExistingApp(packageName: String) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            val versionName = packageInfo.versionName ?: "Unknown"
            _existingAppInfo.value = ExistingAppInfo(versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            _existingAppInfo.value = null
        } catch (e: Exception) {
            Timber.e(e, "Error checking existing app")
            _existingAppInfo.value = null
        }
    }

    fun installPackage(uri: Uri, isApks: Boolean, fileName: String, deleteAfterInstall: Boolean, method: InstallMethod = InstallMethod.STANDARD) {
        installJob?.cancel()
        installJob = viewModelScope.launch(Dispatchers.IO) {
            val sessionId = UUID.randomUUID()
            val sessionData = SessionData(sessionId, fileName)
            sessionDataRepository.addSessionData(sessionData)

            try {
                // Prepare URIs
                val urisToInstall = if (isApks) {
                    val splitsScope = ZippedApkSplits.getApksForUri(uri, context)
                    val splits = splitsScope.toList()
                    splitsScope.close()
                    splits.map { it.uri }
                } else {
                    listOf(uri)
                }

                val options = InstallOptions(method = method)

                appInstaller.install(urisToInstall, options).collect { state ->
                    when (state) {
                        is InstallState.Idle -> {}
                        is InstallState.Processing -> {
                            sessionDataRepository.updateSessionProgress(sessionId, 0, 100)
                        }
                        is InstallState.Progress -> {
                            sessionDataRepository.updateSessionProgress(sessionId, state.percentage, 100)
                        }
                        is InstallState.Success -> {
                            sessionDataRepository.updateSessionProgress(sessionId, 100, 100)
                            if (deleteAfterInstall) {
                                try {
                                    val deletedRows = context.contentResolver.delete(uri, null, null)
                                    if (deletedRows > 0) {
                                        Timber.d("Successfully deleted file: $uri")
                                    } else {
                                        Timber.w("Failed to delete file: $uri (0 rows affected)")
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Exception while deleting file: $uri")
                                }
                            }
                            // Remove session after brief delay or keep it for history
                        }
                        is InstallState.Error -> {
                            val errorMessage = state.message
                            sessionDataRepository.setError(sessionId, errorMessage)
                            Timber.e(state.cause, "Installation error: $errorMessage")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Installation Coroutine failed")
                sessionDataRepository.setError(sessionId, e.message ?: "Unknown installation error")
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
    }
}