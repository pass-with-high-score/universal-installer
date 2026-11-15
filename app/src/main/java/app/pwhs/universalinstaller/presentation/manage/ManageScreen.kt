@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.pwhs.universalinstaller.presentation.manage



import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Stop
import app.pwhs.universalinstaller.util.AndroidAutoCompat
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Store
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.UniversalSearchBar
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.ShimmerBox
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.manage.logs.UninstallLogsActivity
import app.pwhs.universalinstaller.presentation.manage.permissions.AppPermissionsActivity
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.util.AppIconData
import app.pwhs.universalinstaller.util.BiometricGate
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.pwhs.universalinstaller.util.extension.getDisplayName
import app.pwhs.universalinstaller.presentation.manage.sheets.ManageSecurityScanSheet
import org.koin.androidx.compose.koinViewModel


@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val blockedPackages by viewModel.blacklist.collectAsState()
    val securityScanTarget by viewModel.securityScanTarget.collectAsState()
    val securityScanResult by viewModel.securityScanResult.collectAsState()
    val securityScanSha256 by viewModel.securityScanSha256.collectAsState()

    val context = LocalContext.current
    UninstallUi(
        modifier = modifier,
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleAppFilter = viewModel::toggleAppFilter,
        onOpenAppPrivileged = viewModel::openAppPrivileged,
        onUninstall = viewModel::uninstallApp,
        onBlockPackage = viewModel::toggleBlockPackage,
        blockedPackages = blockedPackages,
        onExtract = viewModel::extractApp,
        onShare = viewModel::shareApp,
        onReinstall = viewModel::reinstallApp,
        onInstallToSecureFolder = viewModel::installToSecureFolder,
        onCheckVirusTotal = { app -> viewModel.scanVirusTotal(context, app) },
        onAddToServer = viewModel::addToServer,
        onForceStop = viewModel::forceStop,
        onSetEnabled = viewModel::setEnabled,
        onClearData = viewModel::clearAllData,
        queryStorage = viewModel::queryStorageStats,
        queryUsage = viewModel::queryUsageBuckets,
        onDismissExtractResult = viewModel::dismissExtractResult,
        onDismissPrivilegedResult = viewModel::dismissPrivilegedActionResult,
        onRefreshPrivileged = viewModel::refreshPrivilegedReady,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onUninstallSelected = viewModel::uninstallSelected,
        onForceStopSelected = viewModel::forceStopSelected,
        onDisableSelected = viewModel::disableSelected,
        onClearDataSelected = viewModel::clearDataSelected,
        onExtractSelected = viewModel::extractSelected,
        onDismissBatchExtractResult = viewModel::dismissBatchExtractResult,
        onOpenLogs = {
            context.startActivity(Intent(context, UninstallLogsActivity::class.java))
        },
        onOpenBackups = {
            context.startActivity(Intent(context, BackupsActivity::class.java))
        },
        onRefresh = viewModel::refreshApps,
        onSortChange = viewModel::setSort,
        onGroupByChange = viewModel::setGroupBy,
        onResetFilters = viewModel::resetFilters,
        onRequestUsageAccess = {
            // Send user to the system Usage Access settings — we re-check on resume via
            // refreshUsageAccess() and reload the list if it flipped.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        onRefreshUsageAccess = viewModel::refreshUsageAccess,
        onConfirmSystemApp = viewModel::confirmSystemAppPrompt,
        onDismissSystemApp = viewModel::dismissSystemAppPrompt,
    )

    securityScanTarget?.let { target ->
        ManageSecurityScanSheet(
            app = target,
            vtResult = securityScanResult,
            sha256 = securityScanSha256,
            onDismiss = viewModel::dismissSecurityScan,
            onRescan = { viewModel.scanVirusTotal(context, target) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun launchInstalledApp(context: android.content.Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun openAppInfoSettings(context: android.content.Context, packageName: String) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Wraps the cache-extracted APK in a FileProvider URI (if it's a file) or uses the SAF URI
 * directly, then fires a SEND chooser. Returns false when no chooser-capable Activity is
 * available so the caller can show feedback.
 */
internal fun launchShareIntent(
    context: android.content.Context,
    uri: android.net.Uri,
    appName: String,
): Boolean {
    val shareUri = if (uri.scheme == "file") {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${app.pwhs.universalinstaller.BuildConfig.APPLICATION_ID}.fileprovider",
            java.io.File(uri.path!!),
        )
    } else {
        uri
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(
        send,
        context.getString(
            app.pwhs.universalinstaller.R.string.manage_action_share_chooser,
            appName,
        ),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
}

@Suppress("unused")
internal fun appFilterLabel(filter: AppFilter): Int = when (filter) {
    AppFilter.User -> R.string.manage_filter_user
    AppFilter.System -> R.string.manage_filter_system
    AppFilter.Disabled -> R.string.manage_filter_disabled
    AppFilter.Blocked -> R.string.manage_filter_blocked
}

/**
 * Compact label/value chip for the storage row. Filled tonal so it reads as informational
 * rather than actionable — these are display-only.
 */
