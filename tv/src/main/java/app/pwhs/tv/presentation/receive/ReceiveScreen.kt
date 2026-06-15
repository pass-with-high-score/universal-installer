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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.receiver.ReceivedApk
import app.pwhs.core.receiver.ReceiverStatus
import app.pwhs.core.util.StorageStats
import app.pwhs.core.util.StorageUtil
import app.pwhs.tv.R
import app.pwhs.tv.formatSize
import app.pwhs.tv.ui.components.QrCode
import java.io.File

private enum class InstallTab { Receive, LocalFiles }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReceiveScreen(
    modifier: Modifier = Modifier,
    viewModel: ReceiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val pending by viewModel.pendingApk.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val installingLabel by viewModel.installingLabel.collectAsState()
    
    val storageStats = remember { StorageUtil.getStorageStats() }
    var selectedApk by remember { mutableStateOf<Any?>(null) } 
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
        if (pending != null) {
            currentTab = InstallTab.Receive
            runCatching { installFocus.requestFocus() }
        }
    }
    
    LaunchedEffect(hasStorage) {
        if (hasStorage) viewModel.scanLocalApks()
    }

    // Details Dialog
    selectedApk?.let { apk ->
        ApkDetailsDialog(
            apk = apk,
            isInstalling = installingLabel != null,
            onDismiss = { selectedApk = null },
            onInstall = { uri, isBundle, label ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    openUnknownSources(context)
                } else {
                    viewModel.install(uri, isBundle, label)
                    selectedApk = null
                }
            }
        )
    }

    Row(modifier = modifier.fillMaxSize().padding(horizontal = 48.dp)) {
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

        Spacer(Modifier.width(48.dp))

        // ── Right Pane: Dynamic Content Area ─────────────────────────────────
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .padding(vertical = 32.dp)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "installTabTransition"
            ) { tab ->
                when (tab) {
                    InstallTab.Receive -> ReceiveContent(
                        status = status,
                        pending = pending,
                        installingLabel = installingLabel,
                        installFocus = installFocus,
                        onInstall = { selectedApk = it },
                        onDismissPending = { viewModel.dismissPending() }
                    )
                    InstallTab.LocalFiles -> LocalFilesContent(
                        hasStorage = hasStorage,
                        isScanning = isScanning,
                        downloads = downloads,
                        onApkClick = { selectedApk = it },
                        onGrantPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReceiveContent(
    status: ReceiverStatus,
    pending: ReceivedApk?,
    installingLabel: String?,
    installFocus: FocusRequester,
    onInstall: (ReceivedApk) -> Unit,
    onDismissPending: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (pending != null) {
            // Hero Pending Card
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.tv_receive_step3),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                HeroPendingCard(
                    apk = pending,
                    isInstalling = installingLabel != null,
                    installFocus = installFocus,
                    onInstall = { onInstall(pending) },
                    onDismiss = onDismissPending
                )
            }
        } else {
            // QR Connection Hub - Improved layout for better text flow
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val s = status) {
                    is ReceiverStatus.Running -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White)
                                    .padding(16.dp)
                            ) {
                                QrCode(data = s.url, modifier = Modifier.fillMaxSize())
                            }
                            
                            Spacer(Modifier.width(40.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "http://${s.ip}:${s.port}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(24.dp))
                                GuideStep(1, stringResource(R.string.tv_receive_step1))
                                Spacer(Modifier.height(12.dp))
                                GuideStep(2, stringResource(R.string.tv_receive_step2))
                            }
                        }
                    }
                    ReceiverStatus.Stopped -> {
                        Text(stringResource(R.string.tv_receive_starting), style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(num: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$num.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text, 
            style = MaterialTheme.typography.titleLarge, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LocalFilesContent(
    hasStorage: Boolean,
    isScanning: Boolean,
    downloads: List<ApkFile>,
    onApkClick: (ApkFile) -> Unit,
    onGrantPermission: () -> Unit
) {
    if (!hasStorage) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(480.dp)) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.tv_receive_allow_storage_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tv_receive_allow_storage_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                val shape = CircleShape
                Button(
                    onClick = onGrantPermission,
                    shape = ButtonDefaults.shape(shape),
                    modifier = Modifier.clip(shape)
                ) {
                    Text(stringResource(R.string.tv_receive_grant_all_files))
                }
            }
        }
    } else if (downloads.isEmpty() && !isScanning) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.tv_receive_no_apks), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1), // Wide cards look better in a single column list
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(downloads, key = { it.uri }) { apk ->
                ApkWideItem(apk) { onApkClick(apk) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ApkWideItem(apk: ApkFile, onClick: () -> Unit) {
    val context = LocalContext.current
    val meta = apk.metadata
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = meta?.icon
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("APK", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    meta?.appName ?: apk.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (meta != null) "${meta.packageName} · v${meta.versionName}" else apk.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (MaterialTheme.colorScheme.onSurface == MaterialTheme.colorScheme.onPrimary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = formatSize(context, apk.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroPendingCard(
    apk: ReceivedApk,
    isInstalling: Boolean,
    installFocus: FocusRequester,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val meta = apk.metadata
    val shape = RoundedCornerShape(28.dp)
    Surface(
        onClick = onInstall,
        modifier = Modifier
            .width(600.dp)
            .clip(shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = meta?.icon
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("APK", style = MaterialTheme.typography.displayMedium)
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(meta?.appName ?: apk.fileName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (meta != null) {
                    Text("${meta.packageName} · v${meta.versionName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatSize(context, apk.sizeBytes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val btnShape = CircleShape
                    Button(
                        onClick = onInstall,
                        modifier = Modifier
                            .focusRequester(installFocus)
                            .weight(1f)
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape)
                    ) {
                        Text(if (isInstalling) stringResource(R.string.tv_receive_installing_plain) else stringResource(R.string.tv_receive_install), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(stringResource(R.string.tv_receive_dismiss), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvStorageCard(stats: StorageStats) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.install_storage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.install_storage_value,
                        android.text.format.Formatter.formatShortFileSize(context, stats.freeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, stats.totalBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(stats.progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            when {
                                stats.progress >= 0.9f -> MaterialTheme.colorScheme.error
                                stats.progress >= 0.75f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ApkDetailsDialog(
    apk: Any,
    isInstalling: Boolean,
    onDismiss: () -> Unit,
    onInstall: (Uri, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    
    val name: String
    val pkg: String
    val version: String
    val size: Long
    val icon: androidx.compose.ui.graphics.ImageBitmap?
    val uri: Uri
    val isBundle: Boolean
    val minSdk: Int
    val targetSdk: Int

    when (apk) {
        is ReceivedApk -> {
            val meta = apk.metadata
            name = meta?.appName ?: apk.fileName
            pkg = meta?.packageName ?: ""
            version = meta?.versionName ?: ""
            size = apk.sizeBytes
            icon = meta?.icon?.asImageBitmap()
            uri = Uri.fromFile(File(apk.path))
            isBundle = apk.fileName.isBundleName()
            minSdk = meta?.minSdk ?: 0
            targetSdk = meta?.targetSdk ?: 0
        }
        is ApkFile -> {
            val meta = apk.metadata
            name = meta?.appName ?: apk.displayName
            pkg = meta?.packageName ?: ""
            version = meta?.versionName ?: ""
            size = apk.sizeBytes
            icon = meta?.icon?.asImageBitmap()
            uri = Uri.parse(apk.uri)
            isBundle = apk.isBundle
            minSdk = meta?.minSdk ?: 0
            targetSdk = meta?.targetSdk ?: 0
        }
        else -> return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        Modifier.size(100.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("APK", style = MaterialTheme.typography.displaySmall)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (pkg.isNotBlank()) {
                    Text(pkg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailMetaItem(label = "Version", value = version, modifier = Modifier.weight(1f))
                    DetailMetaItem(label = "Size", value = formatSize(context, size), modifier = Modifier.weight(1f))
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailMetaItem(label = "Min SDK", value = "Android $minSdk", modifier = Modifier.weight(1f))
                    DetailMetaItem(label = "Target SDK", value = "Android $targetSdk", modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(32.dp))

                val btnShape = RoundedCornerShape(14.dp)
                Button(
                    onClick = { onInstall(uri, isBundle, name) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(btnShape),
                    shape = ButtonDefaults.shape(btnShape)
                ) {
                    Text(
                        if (isInstalling) stringResource(R.string.tv_receive_installing_plain) else stringResource(R.string.tv_receive_install),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(btnShape),
                    shape = ButtonDefaults.shape(btnShape),
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        stringResource(R.string.tv_manage_action_close),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMetaItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun String.isBundleName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+")

private fun openUnknownSources(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
