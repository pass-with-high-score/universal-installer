package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nqmgaming.universalinstaller.domain.model.ApkInfo
import com.nqmgaming.universalinstaller.presentation.composable.EmptyStateView
import com.nqmgaming.universalinstaller.ui.theme.LocalExtendedColors
import com.nqmgaming.universalinstaller.presentation.composable.SessionCard
import com.nqmgaming.universalinstaller.util.extension.getDisplayName
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import org.koin.androidx.compose.koinViewModel
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber

@Destination<RootGraph>(start = true)
@Composable
fun InstallScreen(modifier: Modifier = Modifier, viewModel: InstallViewModel = koinViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    InstallUi(
        modifier = modifier,
        uiState = uiState,
        onFilePicked = { uri, splitPackage, fileName ->
            viewModel.parseApkInfo(context, uri, splitPackage, fileName)
        },
        onConfirmInstall = viewModel::confirmInstall,
        onDismissPreview = viewModel::dismissPendingInstall,
        onCancel = viewModel::cancelSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    uiState: InstallUiState = InstallUiState(),
    onFilePicked: (uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) -> Unit = { _, _, _ -> },
    onConfirmInstall: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onCancel: (java.util.UUID) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var fabVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fabVisible = true }

    val result = remember { mutableStateOf<Uri?>(null) }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            result.value = it
        }

    fun getApksFromUri(uri: Uri): SplitPackage.Provider {
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        val displayName = context.contentResolver.getDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        Timber.d("Selected file: $uri, MIME type: $mimeType")
        return when {
            (mimeType == "application/vnd.android.package-archive" || extension == "apk") -> SingletonApkSequence(
                uri,
                context
            ).toSplitPackage()

            extension in listOf("apks", "xapk", "apkm", "zip") -> ZippedApkSplits.getApksForUri(
                uri,
                context
            )
                .validate()
                .toSplitPackage()
                .filterCompatible(context)

            else -> SplitPackage.empty()
        }
    }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        val name = context.contentResolver.getDisplayName(uri)
        val apks = getApksFromUri(uri)
        onFilePicked(uri, apks, name)
    }

    // APK Info Preview Bottom Sheet
    if (uiState.pendingApkInfo != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissPreview,
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            ApkInfoContent(
                apkInfo = uiState.pendingApkInfo,
                onInstall = onConfirmInstall,
                onCancel = onDismissPreview,
            )
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 120.dp,
                title = {
                    Text(
                        text = "Install Package",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn()
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    icon = {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = null,
                            )
                        }
                    },
                    text = {
                        Text(
                            text = if (uiState.isLoading) "Parsing…" else "Install Package",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (uiState.sessions.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Rounded.FolderOpen,
                    title = "No install sessions",
                    subtitle = "Tap the button below to select an APK, APKS, or XAPK file to install",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.sessions,
                        key = { it.id }
                    ) { session ->
                        val sessionProgress =
                            uiState.sessionsProgress.find { it.id == session.id }
                        SessionCard(
                            sessionData = session,
                            sessionProgress = sessionProgress,
                            onCancel = { onCancel(session.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApkInfoContent(
    apkInfo: ApkInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App icon
        if (apkInfo.icon != null) {
            Image(
                bitmap = apkInfo.icon.toBitmap(128, 128).asImageBitmap(),
                contentDescription = apkInfo.appName,
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.large)
            )
            Spacer(Modifier.height(12.dp))
        } else {
            // Placeholder icon
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
        }

        // App name
        Text(
            text = apkInfo.appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))

        // Package name
        Text(
            text = apkInfo.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))

        // Info chips row
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (apkInfo.versionName.isNotBlank()) {
                InfoChip(
                    label = "v${apkInfo.versionName}",
                    leadingIcon = {
                        Icon(Icons.Rounded.Android, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (apkInfo.fileSizeBytes > 0) {
                InfoChip(
                    label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes),
                    leadingIcon = {
                        Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            InfoChip(label = apkInfo.fileFormat)
            if (apkInfo.minSdkVersion > 0) {
                InfoChip(
                    label = "Android ${sdkToAndroid(apkInfo.minSdkVersion)}+",
                    leadingIcon = {
                        Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (apkInfo.splitCount > 1) {
                InfoChip(label = "${apkInfo.splitCount} splits")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Details card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Package", apkInfo.packageName)
                if (apkInfo.versionName.isNotBlank()) {
                    InfoRow("Version", "${apkInfo.versionName} (${apkInfo.versionCode})")
                }
                if (apkInfo.minSdkVersion > 0) {
                    InfoRow("Min SDK", "API ${apkInfo.minSdkVersion} (Android ${sdkToAndroid(apkInfo.minSdkVersion)})")
                }
                if (apkInfo.targetSdkVersion > 0) {
                    InfoRow("Target SDK", "API ${apkInfo.targetSdkVersion} (Android ${sdkToAndroid(apkInfo.targetSdkVersion)})")
                }
            }
        }

        // Supported ABIs section
        if (apkInfo.supportedAbis.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Supported Architectures",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        apkInfo.supportedAbis.forEach { abi ->
                            InfoChip(label = abi)
                        }
                    }
                }
            }
        }

        // Supported Languages section
        if (apkInfo.supportedLanguages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Supported Languages (${apkInfo.supportedLanguages.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        apkInfo.supportedLanguages.take(20).forEach { lang ->
                            InfoChip(label = lang)
                        }
                        if (apkInfo.supportedLanguages.size > 20) {
                            InfoChip(label = "+${apkInfo.supportedLanguages.size - 20} more")
                        }
                    }
                }
            }
        }

        // VirusTotal Security Scan
        apkInfo.vtResult?.let { vt ->
            Spacer(Modifier.height(16.dp))
            val extendedColors = LocalExtendedColors.current
            val warningColor = extendedColors.warning
            val vtColor = when (vt.status) {
                com.nqmgaming.universalinstaller.domain.model.VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
                com.nqmgaming.universalinstaller.domain.model.VtStatus.MALICIOUS -> MaterialTheme.colorScheme.error
                com.nqmgaming.universalinstaller.domain.model.VtStatus.SUSPICIOUS -> warningColor
                com.nqmgaming.universalinstaller.domain.model.VtStatus.SCANNING -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val vtContainerColor = when (vt.status) {
                com.nqmgaming.universalinstaller.domain.model.VtStatus.MALICIOUS ->
                    MaterialTheme.colorScheme.errorContainer
                com.nqmgaming.universalinstaller.domain.model.VtStatus.SUSPICIOUS ->
                    extendedColors.warningContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = vtContainerColor,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = vtColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "VirusTotal Scan",
                            style = MaterialTheme.typography.labelLarge,
                            color = vtColor,
                        )
                        if (vt.status == com.nqmgaming.universalinstaller.domain.model.VtStatus.SCANNING) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = vtColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (vt.status) {
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.SCANNING -> {
                            Text(
                                text = "Scanning file…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.CLEAN -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "No threats detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "${vt.harmless} engines confirmed safe · ${vt.undetected} undetected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.MALICIOUS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${vt.malicious} engine(s) detected threats!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                text = "${vt.malicious} malicious · ${vt.suspicious} suspicious · ${vt.harmless} harmless",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.SUSPICIOUS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = warningColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${vt.suspicious} engine(s) flagged as suspicious",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = warningColor,
                                )
                            }
                            Text(
                                text = "${vt.suspicious} suspicious · ${vt.harmless} harmless · ${vt.undetected} undetected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.NOT_FOUND -> {
                            Text(
                                text = "File not found in VirusTotal database",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        com.nqmgaming.universalinstaller.domain.model.VtStatus.ERROR -> {
                            Text(
                                text = "Scan error: ${vt.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Permissions section
        if (apkInfo.permissions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Permissions (${apkInfo.permissions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    apkInfo.permissions.take(10).forEach { perm ->
                        val shortPerm = perm.substringAfterLast('.')
                        Text(
                            text = "• $shortPerm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (apkInfo.permissions.size > 10) {
                        Text(
                            text = "… and ${apkInfo.permissions.size - 10} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Install")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.invoke()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun sdkToAndroid(sdk: Int): String = when {
    sdk >= 35 -> "15"
    sdk >= 34 -> "14"
    sdk >= 33 -> "13"
    sdk >= 32 -> "12L"
    sdk >= 31 -> "12"
    sdk >= 30 -> "11"
    sdk >= 29 -> "10"
    sdk >= 28 -> "9"
    sdk >= 26 -> "8"
    sdk >= 24 -> "7"
    sdk >= 23 -> "6"
    sdk >= 21 -> "5"
    else -> "$sdk"
}