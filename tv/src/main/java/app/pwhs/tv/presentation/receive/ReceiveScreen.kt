package app.pwhs.tv.presentation.receive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.util.StorageUtil
import app.pwhs.tv.R
import app.pwhs.tv.presentation.receive.components.InstallStatusOverlay
import app.pwhs.tv.presentation.receive.components.LocalFilesContent
import app.pwhs.tv.presentation.receive.components.ReceiveContent
import app.pwhs.tv.presentation.receive.components.SidebarTab
import app.pwhs.tv.presentation.receive.components.TvStorageCard

private enum class InstallTab { Receive, LocalFiles }

@Composable
fun ReceiveScreen(
    modifier: Modifier = Modifier,
    viewModel: ReceiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val connectedClient by viewModel.connectedClient.collectAsState()
    val receivingProgress by viewModel.receivingProgress.collectAsState()
    val pending by viewModel.pendingApk.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val deleteOutcome by viewModel.deleteOutcome.collectAsState()
    val installingLabel by viewModel.installingLabel.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()

    var storageRefreshKey by remember { mutableStateOf(0) }
    val storageStats = remember(storageRefreshKey) { StorageUtil.getStorageStats() }
    var selectedApk by remember { mutableStateOf<TvApkItem?>(null) } 
    var currentTab by remember { mutableStateOf(InstallTab.Receive) }

    val installFocus = remember { FocusRequester() }

    val readPerm = if (Build.VERSION.SDK_INT <= 32) Manifest.permission.READ_EXTERNAL_STORAGE else null
    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            readPerm == null || ContextCompat.checkSelfPermission(context, readPerm) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasStorage by remember { mutableStateOf(checkStoragePermission()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasStorage = checkStoragePermission() }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasStorage = checkStoragePermission() }

    LaunchedEffect(pending) {
        if (pending != null) currentTab = InstallTab.Receive
    }
    
    LaunchedEffect(hasStorage) {
        if (hasStorage) viewModel.scanLocalApksIfNeeded()
    }

    LaunchedEffect(installResult) {
        if (installResult is ReceiveViewModel.InstallOutcome.Success) {
            storageRefreshKey++
        }
    }

    // Details Dialog
    selectedApk?.let { apk ->
        ApkDetailsDialog(
            apkItem = apk,
            isInstalling = installingLabel != null,
            onDismiss = { selectedApk = null },
            onInstall = { uri, isBundle, label, sizeBytes ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    openUnknownSources(context)
                } else {
                    viewModel.install(uri, isBundle, label, sizeBytes)
                    selectedApk = null
                }
            },
            onDelete = { apkFile ->
                viewModel.deleteLocalApk(apkFile)
                storageRefreshKey++
                selectedApk = null
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            // ── Left Pane: Sidebar Navigation ────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.tv_app_tab_install),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                SidebarTab(
                    selected = currentTab == InstallTab.Receive,
                    label = stringResource(R.string.tv_receive_from_phone),
                    icon = Icons.Rounded.PhoneAndroid,
                    onClick = { currentTab = InstallTab.Receive }
                )

                SidebarTab(
                    selected = currentTab == InstallTab.LocalFiles,
                    label = stringResource(R.string.tv_receive_local_files),
                    icon = Icons.Rounded.Folder,
                    onClick = { currentTab = InstallTab.LocalFiles }
                )

                Spacer(Modifier.weight(1f))

                TvStorageCard(stats = storageStats)
            }

            Spacer(Modifier.width(28.dp))

            // ── Right Pane: Dynamic Content Area ─────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight()
                    .padding(vertical = 24.dp)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "installTabTransition"
                ) { tab ->
                    when (tab) {
                        InstallTab.Receive -> ReceiveContent(
                            status = status,
                            connectedClient = connectedClient,
                            pending = pending,
                            receivingProgress = receivingProgress,
                            installingLabel = installingLabel,
                            installFocus = installFocus,
                            onInstall = { selectedApk = TvApkItem.Received(it) },
                            onDismissPending = { viewModel.dismissPending() },
                            onDisconnectClient = { viewModel.disconnect() }
                        )
                        InstallTab.LocalFiles -> LocalFilesContent(
                            hasStorage = hasStorage,
                            isScanning = isScanning,
                            downloads = downloads,
                            onApkClick = { selectedApk = TvApkItem.Local(it) },
                            onRescan = { viewModel.scanLocalApks() },
                            onReceiveFromPhone = { currentTab = InstallTab.Receive },
                            onGrantPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    settingsLauncher.launch(intent)
                                } else {
                                    readPerm?.let { permLauncher.launch(it) }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Install / Operation status overlay (progress · result pill) ────
        InstallStatusOverlay(
            installingLabel = installingLabel,
            progress = installProgress,
            result = installResult,
            deleteOutcome = deleteOutcome,
            onRetry = { viewModel.retryInstall() },
            onDismiss = { viewModel.clearInstallResult() },
            onDismissDelete = { viewModel.clearDeleteOutcome() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        )
    }
}

private fun openUnknownSources(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
