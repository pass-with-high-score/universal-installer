package app.pwhs.universalinstaller.presentation.setting.autoapprove

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.manager.AutoApproveApps
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

data class SelectableApp(
    val appName: String,
    val packageName: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoApproveScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val approvedPackages by viewModel.autoApprovePackages.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<SelectableApp>>(emptyList()) }
    var showCustomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val rawApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            val appList = rawApps
                .filter { it.packageName != context.packageName }
                .map { info ->
                    SelectableApp(
                        appName = info.loadLabel(pm).toString(),
                        packageName = info.packageName,
                    )
                }
            installedApps = appList
        }
    }

    // Merge any custom approved packages that might not be in installedApps
    val allSelectableApps = remember(installedApps, approvedPackages) {
        val installedPkgs = installedApps.map { it.packageName }.toSet()
        val customApps = approvedPackages
            .filter { it !in installedPkgs }
            .map { pkg ->
                val label = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                SelectableApp(appName = label, packageName = pkg)
            }
        customApps + installedApps
    }

    val filteredApps = remember(allSelectableApps, approvedPackages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allSelectableApps.filter {
            q.isEmpty() || it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }.sortedWith(
            compareByDescending<SelectableApp> { it.packageName in approvedPackages }
                .thenBy { it.appName.lowercase() }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_auto_approve_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.setting_auto_approve_add_custom),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.setting_auto_approve_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Info Banner
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.setting_auto_approve_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Preset popular sources
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = stringResource(R.string.setting_auto_approve_preset_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AutoApproveApps.PRESET_APPS.forEach { preset ->
                                val isApproved = preset.packageName in approvedPackages
                                FilterChip(
                                    selected = isApproved,
                                    onClick = {
                                        viewModel.toggleAutoApprovePackage(preset.packageName, !isApproved)
                                    },
                                    label = { Text(preset.name) },
                                    leadingIcon = if (isApproved) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Header for list
                item {
                    Text(
                        text = stringResource(R.string.setting_auto_approve_all_apps_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Android,
                            title = stringResource(R.string.setting_auto_approve_empty),
                            subtitle = stringResource(R.string.setting_auto_approve_empty_hint),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isApproved = app.packageName in approvedPackages
                        AppApprovalRow(
                            app = app,
                            isApproved = isApproved,
                            onToggle = { viewModel.toggleAutoApprovePackage(app.packageName, !isApproved) },
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AddCustomAppDialog(
            onDismiss = { showCustomDialog = false },
            onAdd = { pkg ->
                viewModel.toggleAutoApprovePackage(pkg, true)
                showCustomDialog = false
            },
        )
    }
}

@Composable
private fun AppApprovalRow(
    app: SelectableApp,
    isApproved: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isApproved) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onToggle),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = app.appName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isApproved) FontWeight.Medium else FontWeight.Normal,
                )
            },
            supportingContent = {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(AppIconData(app.packageName)).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    error = rememberVectorPainter(Icons.Rounded.Android),
                    fallback = rememberVectorPainter(Icons.Rounded.Android),
                )
            },
            trailingContent = {
                Switch(
                    checked = isApproved,
                    onCheckedChange = { onToggle() },
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun AddCustomAppDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_auto_approve_custom_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.setting_auto_approve_custom_dialog_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank()) onAdd(trimmed)
                },
                enabled = text.trim().isNotBlank(),
            ) {
                Text(stringResource(R.string.setting_auto_approve_custom_dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
