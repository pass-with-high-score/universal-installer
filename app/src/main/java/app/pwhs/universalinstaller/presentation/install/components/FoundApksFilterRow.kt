package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.InstallState

enum class FoundApkFilter {
    All,
    Updates,
    NotInstalled,
    Installed,
    Older,
    Bundles,
    AndroidAuto,
}

fun getFilterLabelRes(filter: FoundApkFilter): Int = when (filter) {
    FoundApkFilter.All -> R.string.found_filter_all
    FoundApkFilter.Updates -> R.string.found_chip_update
    FoundApkFilter.NotInstalled -> R.string.found_chip_new
    FoundApkFilter.Installed -> R.string.found_chip_installed
    FoundApkFilter.Older -> R.string.found_chip_older
    FoundApkFilter.Bundles -> R.string.found_chip_split
    FoundApkFilter.AndroidAuto -> R.string.aa_compatibility_ok
}

fun countFilterMatches(filter: FoundApkFilter, files: List<FoundPackageFile>): Int = when (filter) {
    FoundApkFilter.All -> files.size
    FoundApkFilter.Updates -> files.count { it.installState == InstallState.Newer }
    FoundApkFilter.NotInstalled -> files.count { it.installState == InstallState.NotInstalled }
    FoundApkFilter.Installed -> files.count { it.installState == InstallState.SameVersion }
    FoundApkFilter.Older -> files.count { it.installState == InstallState.Older }
    FoundApkFilter.Bundles -> files.count { it.extension in setOf("xapk", "apks", "apkm", "apk+") }
    FoundApkFilter.AndroidAuto -> files.count { it.isAndroidAutoSupported }
}

fun filterFoundFiles(
    files: List<FoundPackageFile>,
    query: String,
    filter: FoundApkFilter,
): List<FoundPackageFile> {
    var result = files
    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        result = result.filter { f ->
            f.name.lowercase().contains(q) || f.path.lowercase().contains(q)
        }
    }
    result = when (filter) {
        FoundApkFilter.All -> result
        FoundApkFilter.Updates -> result.filter { it.installState == InstallState.Newer }
        FoundApkFilter.NotInstalled -> result.filter { it.installState == InstallState.NotInstalled }
        FoundApkFilter.Installed -> result.filter { it.installState == InstallState.SameVersion }
        FoundApkFilter.Older -> result.filter { it.installState == InstallState.Older }
        FoundApkFilter.Bundles -> result.filter { it.extension in setOf("xapk", "apks", "apkm", "apk+") }
        FoundApkFilter.AndroidAuto -> result.filter { it.isAndroidAutoSupported }
    }
    return result
}

@Composable
internal fun FoundApksFilterRow(
    allFiles: List<FoundPackageFile>,
    activeFilter: FoundApkFilter,
    onFilterSelected: (FoundApkFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (allFiles.isEmpty()) return

    val availableFilters = remember(allFiles) {
        val list = mutableListOf<FoundApkFilter>()
        list.add(FoundApkFilter.All)
        FoundApkFilter.entries.forEach { f ->
            if (f != FoundApkFilter.All && countFilterMatches(f, allFiles) > 0) {
                list.add(f)
            }
        }
        list
    }

    if (availableFilters.size <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableFilters.forEach { filter ->
            val isSelected = activeFilter == filter
            val count = countFilterMatches(filter, allFiles)
            val label = stringResource(getFilterLabelRes(filter))
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onFilterSelected(FoundApkFilter.All)
                    } else {
                        onFilterSelected(filter)
                    }
                },
                label = {
                    Text("$label ($count)")
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
