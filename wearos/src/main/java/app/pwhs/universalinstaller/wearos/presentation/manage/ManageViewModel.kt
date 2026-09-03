package app.pwhs.universalinstaller.wearos.presentation.manage

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.domain.InstalledApp
import app.pwhs.universalinstaller.wearos.data.WearInstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManageViewModel(
    private val repository: WearInstalledAppsRepository,
    application: Application,
) : AndroidViewModel(application) {

    val apps: StateFlow<List<InstalledApp>> = repository.apps
    val isLoading: StateFlow<Boolean> = repository.isLoading

    private val _includeSystem = MutableStateFlow(false)
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh(_includeSystem.value) }
    }

    fun toggleSystemApps() {
        _includeSystem.value = !_includeSystem.value
        refresh()
    }

    fun sourceDirOf(packageName: String): String? = repository.sourceDirOf(packageName)

    /**
     * Tapping a row must not destroy anything — that is what the swipe is for. Wear's Settings
     * does honour the per-app details intent, contrary to what resolve-activity suggests: it
     * answers with its main activity but routes on the package URI.
     */
    fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    /** Wear ships the standard uninstaller, so the system dialog does the asking and the work. */
    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }
}
