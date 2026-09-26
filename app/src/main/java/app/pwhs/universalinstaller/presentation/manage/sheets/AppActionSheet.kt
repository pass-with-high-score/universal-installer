package app.pwhs.universalinstaller.presentation.manage

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.manage.StorageBreakdown
import app.pwhs.universalinstaller.presentation.manage.UsageBucket
import app.pwhs.universalinstaller.presentation.manage.UsageChart
import app.pwhs.universalinstaller.presentation.manage.StorageChip
import app.pwhs.universalinstaller.presentation.manage.permissions.AppPermissionsActivity
import app.pwhs.universalinstaller.presentation.manage.resolveInstallerInfo
import app.pwhs.universalinstaller.util.AndroidAutoCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppActionSheet(
    app: InstalledApp,
    extractInProgress: Boolean,
    privilegedReady: Boolean,
    onOpenApp: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onShare: () -> Unit,
    onReinstall: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onAddToServer: () -> Unit,
    onExtract: () -> Unit,
    onForceStop: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onClearData: () -> Unit,
    queryStorage: suspend (String) -> StorageBreakdown?,
    queryUsage: suspend (String) -> List<UsageBucket>,
    onUninstall: () -> Unit,
    onBlockPackage: () -> Unit,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val launchable = remember(app.packageName) {
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null
    }
    var storage by remember(app.packageName) { mutableStateOf<StorageBreakdown?>(null) }
    var usage by remember(app.packageName) { mutableStateOf<List<UsageBucket>>(emptyList()) }

    LaunchedEffect(app.packageName) {
        storage = queryStorage(app.packageName)
        usage = queryUsage(app.packageName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            AppActionHeader(
                app = app,
                context = context,
                isBlocked = isBlocked,
            )

            // Usage chart
            val totalUsageMs = remember(usage) { usage.sumOf { it.foregroundMillis } }
            if (totalUsageMs > 0L) {
                UsageChart(
                    buckets = usage,
                    totalMillis = totalUsageMs,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            // Storage breakdown (from UsageStatsManager/StorageStatsManager)
            if (storage != null) {
                storage?.let { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StorageChip(
                            label = stringResource(R.string.manage_storage_app),
                            value = android.text.format.Formatter.formatShortFileSize(context, s.appBytes),
                            weight = 1f,
                        )
                        StorageChip(
                            label = stringResource(R.string.manage_storage_data),
                            value = android.text.format.Formatter.formatShortFileSize(context, s.dataBytes),
                            weight = 1f,
                        )
                        StorageChip(
                            label = stringResource(R.string.manage_storage_cache),
                            value = android.text.format.Formatter.formatShortFileSize(context, s.cacheBytes),
                            weight = 1f,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Quick Actions Hero Row
            val quickActions = buildList {
                if (launchable) {
                    add(
                        AppActionItem(
                            icon = Icons.AutoMirrored.Rounded.Launch,
                            iconTint = MaterialTheme.colorScheme.primary,
                            label = stringResource(R.string.manage_action_open_app),
                            onClick = onOpenApp,
                        )
                    )
                }
                add(
                    AppActionItem(
                        icon = if (app.hasSplits) Icons.Rounded.FolderZip else Icons.Rounded.Inventory2,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.extract_action),
                        enabled = !extractInProgress,
                        onClick = onExtract,
                    )
                )
                add(
                    AppActionItem(
                        icon = Icons.Rounded.Share,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_share),
                        enabled = !extractInProgress,
                        onClick = onShare,
                    )
                )
                add(
                    AppActionItem(
                        icon = Icons.Rounded.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_app_info),
                        onClick = onOpenAppInfo,
                    )
                )
            }

            AppQuickActionsRow(items = quickActions)

            Spacer(Modifier.height(4.dp))

            // Utilities & Tools Grid
            val storeInfo = remember(app.installerPackage, app.packageName) {
                resolveInstallerInfo(app.installerPackage, app.packageName)
            }
            val isAaApp = app.isAndroidAutoSupported
            val isAaInstalled = remember { AndroidAutoCompat.isAndroidAutoInstalled(context) }

            val utilityActions = buildList {
                storeInfo?.let { info ->
                    if (info.intent != null) {
                        add(
                            AppActionItem(
                                icon = Icons.Rounded.Store,
                                iconTint = MaterialTheme.colorScheme.primary,
                                label = stringResource(R.string.manage_action_open_in_store, info.displayName),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            info.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                    onDismiss()
                                },
                            )
                        )
                    } else {
                        add(
                            AppActionItem(
                                icon = Icons.Rounded.Android,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                label = stringResource(R.string.manage_action_installer_source, info.displayName),
                                onClick = {},
                            )
                        )
                    }
                }
                add(
                    AppActionItem(
                        icon = Icons.Rounded.Refresh,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_reinstall),
                        enabled = !extractInProgress,
                        onClick = onReinstall,
                    )
                )
                add(
                    AppActionItem(
                        icon = Icons.Rounded.Security,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_permissions),
                        onClick = {
                            val intent = Intent(context, AppPermissionsActivity::class.java)
                                .putExtra(AppPermissionsActivity.EXTRA_PACKAGE_NAME, app.packageName)
                            runCatching { context.startActivity(intent) }
                            onDismiss()
                        },
                    )
                )
                add(
                    AppActionItem(
                        icon = Icons.Rounded.Search,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_check_vt),
                        onClick = {
                            onCheckVirusTotal()
                            onDismiss()
                        },
                    )
                )
                add(
                    AppActionItem(
                        icon = Icons.Rounded.WifiTethering,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.manage_action_add_to_server),
                        subtitle = stringResource(R.string.manage_action_add_to_server_sub),
                        enabled = !extractInProgress,
                        onClick = onAddToServer,
                    )
                )
                if (isAaApp && app.installerPackage != "com.android.vending" && isAaInstalled) {
                    add(
                        AppActionItem(
                            icon = Icons.Rounded.DirectionsCar,
                            iconTint = MaterialTheme.colorScheme.primary,
                            label = stringResource(R.string.aa_open_settings),
                            onClick = {
                                val aaIntent = AndroidAutoCompat.getSettingsIntent(context)
                                if (aaIntent != null) {
                                    runCatching { context.startActivity(aaIntent) }
                                }
                                onDismiss()
                            },
                        )
                    )
                }
            }

            AppActionCardGrid(items = utilityActions)

            // Advanced Actions (Privileged)
            if (privilegedReady) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = stringResource(R.string.manage_section_advanced),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                val advancedActions = listOf(
                    AppActionItem(
                        icon = Icons.Rounded.Block,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        label = stringResource(R.string.manage_action_force_stop),
                        onClick = onForceStop,
                    ),
                    if (app.enabled) {
                        AppActionItem(
                            icon = Icons.Rounded.PowerSettingsNew,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            label = stringResource(R.string.manage_action_disable),
                            onClick = { onSetEnabled(false) },
                        )
                    } else {
                        AppActionItem(
                            icon = Icons.Rounded.PlayCircle,
                            iconTint = MaterialTheme.colorScheme.primary,
                            label = stringResource(R.string.manage_action_enable),
                            onClick = { onSetEnabled(true) },
                        )
                    },
                    AppActionItem(
                        icon = Icons.Rounded.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        label = stringResource(R.string.manage_action_clear_data),
                        onClick = onClearData,
                    ),
                )

                AppActionCardGrid(items = advancedActions)
            }

            // Danger Section (Uninstall, Block)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            AppActionDangerCard(
                onUninstall = onUninstall,
                onBlockPackage = onBlockPackage,
                isBlocked = isBlocked,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
