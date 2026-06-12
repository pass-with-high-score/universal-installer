package app.pwhs.universalinstaller.presentation.manage.permissions

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single permission entry for the read-only viewer. [granted] reflects the runtime state
 * for dangerous permissions; for normal permissions it's always true (granted at install).
 */
data class PermissionEntry(
    val name: String,
    val label: String,
    val description: String?,
    val group: String?,
    val isDangerous: Boolean,
    val granted: Boolean,
)

data class AppPermissionsUiState(
    val appLabel: String = "",
    val packageName: String = "",
    val granted: List<PermissionEntry> = emptyList(),
    val notGranted: List<PermissionEntry> = emptyList(),
    val isLoading: Boolean = true,
)

class AppPermissionsViewModel(
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppPermissionsUiState())
    val uiState: StateFlow<AppPermissionsUiState> = _uiState.asStateFlow()

    fun load(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, packageName = packageName)
            val data = withContext(Dispatchers.IO) { loadInternal(packageName) }
            _uiState.value = data
        }
    }

    private fun loadInternal(packageName: String): AppPermissionsUiState {
        val pm = application.packageManager
        val pkgInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return AppPermissionsUiState(
                appLabel = packageName,
                packageName = packageName,
                isLoading = false,
            )
        }

        val appLabel = try {
            pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (_: Exception) { packageName }

        val names = pkgInfo.requestedPermissions ?: emptyArray()
        val flags = pkgInfo.requestedPermissionsFlags ?: IntArray(names.size)

        val entries = names.mapIndexed { i, name ->
            val info: PermissionInfo? = try { pm.getPermissionInfo(name, 0) } catch (_: Exception) { null }
            val protection = info?.let { it.protection } ?: PermissionInfo.PROTECTION_NORMAL
            val isDangerous = protection == PermissionInfo.PROTECTION_DANGEROUS
            val grantedFlag = (flags.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            // Normal permissions are auto-granted at install — flags may report 0 even though
            // they're always granted at runtime, so the displayed state would be misleading.
            // Treat any non-dangerous protection as granted; only runtime perms can be "denied".
            val effectiveGranted = grantedFlag || !isDangerous
            PermissionEntry(
                name = name,
                label = info?.loadLabel(pm)?.toString() ?: name.substringAfterLast('.'),
                description = info?.loadDescription(pm)?.toString(),
                group = info?.group?.substringAfterLast('.'),
                isDangerous = isDangerous,
                granted = effectiveGranted,
            )
        }.sortedWith(
            // Dangerous first, then by label so the security-relevant permissions stay
            // at the top of each section. Stable secondary by name for determinism.
            compareByDescending<PermissionEntry> { it.isDangerous }
                .thenBy { it.label.lowercase() }
                .thenBy { it.name },
        )

        return AppPermissionsUiState(
            appLabel = appLabel,
            packageName = packageName,
            granted = entries.filter { it.granted },
            notGranted = entries.filter { !it.granted },
            isLoading = false,
        )
    }
}
