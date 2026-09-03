package app.pwhs.universalinstaller.wearos.presentation.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.install.ApkInstaller
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.domain.ApkCompatibilityCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DetailViewModel(
    private val apkId: String,
    private val repository: WearApkRepository,
    private val installer: ApkInstaller,
    private val compatibility: ApkCompatibilityCheck,
    application: Application,
) : AndroidViewModel(application) {

    // Observed rather than read once: opening this screen from a notification can beat the
    // repository's disk scan.
    val apkInfo: StateFlow<WearApkInfo?> = repository.apks
        .map { list -> list.find { it.id == apkId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getById(apkId))

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    fun install() {
        val info = apkInfo.value ?: return

        if (!getApplication<Application>().packageManager.canRequestPackageInstalls()) {
            _installState.value = InstallState.NeedsUnknownSources
            return
        }

        val incompatibility = compatibility.check(info)
        if (incompatibility != null) {
            _installState.value = InstallState.Incompatible(incompatibility)
            return
        }

        installAnyway()
    }

    fun installAnyway() {
        val info = apkInfo.value ?: return
        val apkFile = File(info.cachedFilePath)
        if (!apkFile.exists()) {
            _installState.value = InstallState.Failed("Package file not found")
            return
        }

        viewModelScope.launch {
            _installState.value = InstallState.Installing(null)
            val result = installer.install(
                uri = android.net.Uri.fromFile(apkFile),
                isBundle = info.isBundle,
                totalBytes = info.sizeBytes,
                onProgress = { written, total ->
                    if (total > 0) {
                        _installState.value = InstallState.Installing(written.toFloat() / total)
                    }
                },
            )
            when (result) {
                is ApkInstaller.Result.Success -> {
                    _installState.value = InstallState.Success
                    repository.deleteById(apkId)
                }
                // The cached file stays put so the user can retry.
                is ApkInstaller.Result.Failure ->
                    _installState.value = InstallState.Failed(result.message)
            }
        }
    }

    fun delete() {
        viewModelScope.launch { repository.deleteById(apkId) }
    }

    fun resetInstallState() {
        _installState.value = InstallState.Idle
    }
}
