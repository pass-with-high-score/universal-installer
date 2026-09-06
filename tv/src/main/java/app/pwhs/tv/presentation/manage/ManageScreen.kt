package app.pwhs.tv.presentation.manage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.presentation.components.TvInstallerModeBadge
import app.pwhs.tv.presentation.manage.components.AppDetailsPane
import app.pwhs.tv.presentation.manage.components.AppListRow
import app.pwhs.tv.presentation.manage.components.ConfirmAction
import app.pwhs.tv.presentation.manage.components.ConfirmDialog
import app.pwhs.tv.presentation.manage.components.LoadingRow
import app.pwhs.tv.presentation.manage.components.ManageStatusOverlay
import app.pwhs.tv.presentation.manage.components.SmallFilterChip

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    var focusedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    val context = LocalContext.current

    val firstRowFocus = remember { FocusRequester() }
    var didRequestFocus by remember { mutableStateOf(false) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    LaunchedEffect(uiState.filteredApps, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        val current = focusedApp
        val fresh = uiState.filteredApps.firstOrNull { it.packageName == current?.packageName }
        when {
            fresh != null -> if (fresh != current) focusedApp = fresh
            else -> {
                focusedApp = uiState.filteredApps.firstOrNull()
                if (uiState.filteredApps.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
            }
        }
        if (!didRequestFocus && uiState.filteredApps.isNotEmpty()) {
            didRequestFocus = true
            runCatching { firstRowFocus.requestFocus() }
        }
    }

    confirm?.let { pending ->
        ConfirmDialog(
            action = pending,
            onConfirm = {
                when (pending) {
                    is ConfirmAction.Uninstall -> viewModel.uninstallSilent(pending.app)
                    is ConfirmAction.ClearData -> viewModel.clearData(pending.app)
                }
                confirm = null
            },
            onCancel = { confirm = null },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Apps List (with Sidebar Background)
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.tv_manage_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    TvInstallerModeBadge()
                }
                
                Spacer(Modifier.height(20.dp))

                // Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f).height(60.dp),
                        textStyle = MaterialTheme.typography.titleSmall,
                        placeholder = { Text(stringResource(R.string.tv_manage_search_placeholder), style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.User,
                        label = stringResource(R.string.tv_manage_filter_user),
                        onClick = { viewModel.setFilter(AppFilter.User) }
                    )
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.System,
                        label = stringResource(R.string.tv_manage_filter_system),
                        onClick = { viewModel.setFilter(AppFilter.System) }
                    )
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.Disabled,
                        label = stringResource(R.string.tv_manage_filter_disabled),
                        onClick = { viewModel.setFilter(AppFilter.Disabled) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                
                // Sort chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFilterChip(
                        selected = uiState.sortBy == SortBy.Name,
                        label = stringResource(R.string.tv_manage_sort_name),
                        onClick = { viewModel.setSortBy(SortBy.Name) }
                    )
                    SmallFilterChip(
                        selected = uiState.sortBy == SortBy.Size,
                        label = stringResource(R.string.tv_manage_sort_size),
                        onClick = { viewModel.setSortBy(SortBy.Size) }
                    )
                    SmallFilterChip(
                        selected = uiState.sortBy == SortBy.Date,
                        label = stringResource(R.string.tv_manage_sort_date),
                        onClick = { viewModel.setSortBy(SortBy.Date) }
                    )
                }

                Spacer(Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        .focusGroup(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState.isLoading) {
                        items(8) { LoadingRow() }
                    } else if (uiState.filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.tv_manage_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else {
                        val firstPackage = uiState.filteredApps.firstOrNull()?.packageName
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            AppListRow(
                                app = app,
                                isSelected = { focusedApp?.packageName == app.packageName },
                                focusRequester = if (app.packageName == firstPackage) firstRowFocus else null,
                                onFocus = { focusedApp = app }
                            )
                        }
                    }
                }
            }

            // Right Column: Details & Actions (Main Content Background)
            AppDetailsPane(
                focusedApp = focusedApp,
                isLoading = uiState.isLoading,
                rootAvailable = uiState.rootAvailable,
                searchQuery = uiState.searchQuery,
                onOpen = { app ->
                    runCatching {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                            context.startActivity(intent)
                        }
                    }
                },
                onForceStop = { app -> viewModel.forceStop(app) },
                onToggleEnabled = { app -> viewModel.setEnabled(app, !app.enabled) },
                onClearData = { app -> confirm = ConfirmAction.ClearData(app) },
                onUninstall = { app ->
                    if (uiState.rootAvailable) {
                        confirm = ConfirmAction.Uninstall(app)
                    } else {
                        uninstallLauncher.launch(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        )
                    }
                },
                onExtract = { app -> viewModel.extractApp(app.packageName, app.appName) },
                onOpenSettings = { app ->
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                            }
                        )
                    }
                },
                onClearSearch = { viewModel.setSearchQuery("") },
                modifier = Modifier.weight(0.9f)
            )
        }

        ManageStatusOverlay(
            actionResult = actionResult,
            extractState = uiState.extractState,
            onDismissAction = { viewModel.clearActionResult() },
            onDismissExtract = { viewModel.dismissExtractResult() },
            onErrorDismiss = {
                viewModel.clearActionResult()
                viewModel.dismissExtractResult()
                runCatching { firstRowFocus.requestFocus() }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}
