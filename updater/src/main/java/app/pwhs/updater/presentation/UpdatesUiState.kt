package app.pwhs.updater.presentation

import app.pwhs.updater.domain.model.TrackedApp

enum class AppSortOption {
    NAME_ASC,
    NAME_DESC,
    UPDATES_FIRST,
    LAST_CHECKED,
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isTracked: Boolean = false,
)

data class UpdatesUiState(
    val trackedApps: List<TrackedApp> = emptyList(),
    val isChecking: Boolean = false,
    val isAdding: Boolean = false,
    val isUpdatingAll: Boolean = false,
    val downloadingPackage: String? = null,
    val downloadProgress: Float = 0f,
    val downloadBytesText: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val sortOption: AppSortOption = AppSortOption.UPDATES_FIRST,
    val selectedAppForDetail: TrackedApp? = null,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val showAppPickerDialog: Boolean = false,
    val showSourceTokensDialog: Boolean = false,
    val githubToken: String = "",
    val gitlabToken: String = "",
    val codebergToken: String = "",
    val installedApps: List<InstalledAppItem> = emptyList(),
    val isLoadingInstalledApps: Boolean = false,
    val checkingPackageNames: Set<String> = emptySet(),
    val checkingProgress: Pair<Int, Int>? = null,
) {
    val updateCount: Int
        get() = trackedApps.count { it.hasUpdate }

    val installedCount: Int
        get() = trackedApps.count { it.isInstalled }

    val categories: List<String>
        get() = trackedApps.mapNotNull { it.category?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }

    val filteredApps: List<TrackedApp>
        get() {
            val base = trackedApps.filter { app ->
                val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

                val matchesCategory = when (selectedCategory) {
                    null -> true
                    CATEGORY_INSTALLED -> app.isInstalled
                    CATEGORY_UPDATES -> app.hasUpdate
                    else -> app.category?.equals(selectedCategory, ignoreCase = true) == true
                }

                matchesSearch && matchesCategory
            }

            return when (sortOption) {
                AppSortOption.NAME_ASC -> base.sortedBy { it.appName.lowercase() }
                AppSortOption.NAME_DESC -> base.sortedByDescending { it.appName.lowercase() }
                AppSortOption.UPDATES_FIRST -> base.sortedWith(
                    compareByDescending<TrackedApp> { it.hasUpdate }
                        .thenBy { it.appName.lowercase() }
                )
                AppSortOption.LAST_CHECKED -> base.sortedByDescending { it.lastCheckedAt }
            }
        }

    companion object {
        const val CATEGORY_INSTALLED = "__INSTALLED_ONLY__"
        const val CATEGORY_UPDATES = "__UPDATES_ONLY__"
    }
}
