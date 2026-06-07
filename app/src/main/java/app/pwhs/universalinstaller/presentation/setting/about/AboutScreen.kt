package app.pwhs.universalinstaller.presentation.setting.about

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.R

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "1.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "0"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "0"
    }

    AboutUi(
        modifier = modifier,
        versionName = versionName,
        versionCode = versionCode,
        onBack = { (context as? Activity)?.finish() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutUi(
    modifier: Modifier = Modifier,
    versionName: String,
    versionCode: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showDeviceInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 120.dp,
                title = {
                    Text(
                        text = stringResource(R.string.setting_section_about),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.setting_about_app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "v$versionName ($versionCode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }

            // ── APP ──
            item { AboutSectionLabel(stringResource(R.string.about_section_app)) }
            item {
                AboutRowPainter(
                    iconPainter = painterResource(R.drawable.ic_google_play_circle),
                    title = stringResource(R.string.setting_rate_title),
                    subtitle = stringResource(R.string.setting_rate_subtitle),
                    onClick = {
                        uriHandler.openUri("https://play.google.com/store/apps/details?id=app.pwhs.universalinstaller")
                    },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Rounded.Favorite,
                    title = stringResource(R.string.setting_sponsor_title),
                    subtitle = stringResource(R.string.setting_sponsor_subtitle),
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { uriHandler.openUri("https://github.com/sponsors/pass-with-high-score") },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            // ── COMMUNITY ──
            item { AboutSectionLabel(stringResource(R.string.about_section_community)) }
            item {
                AboutRow(
                    icon = Icons.Rounded.Public,
                    title = stringResource(R.string.setting_website_title),
                    subtitle = stringResource(R.string.setting_website_subtitle),
                    onClick = { uriHandler.openUri("https://universal-installer.pwhs.app/") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRowPainter(
                    iconPainter = painterResource(R.drawable.ic_github),
                    title = stringResource(R.string.setting_github_title),
                    subtitle = stringResource(R.string.setting_github_subtitle),
                    onClick = { uriHandler.openUri("https://github.com/pass-with-high-score/universal-installer") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRowPainter(
                    iconPainter = painterResource(R.drawable.ic_telegram),
                    title = stringResource(R.string.setting_telegram_title),
                    subtitle = stringResource(R.string.setting_telegram_subtitle),
                    onClick = { uriHandler.openUri("https://t.me/blockads_android") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.about_creator_title),
                    subtitle = stringResource(R.string.about_creator_subtitle),
                    onClick = { uriHandler.openUri("https://github.com/pass-with-high-score") },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            // ── LEGAL ──
            item { AboutSectionLabel(stringResource(R.string.about_section_legal)) }
            item {
                AboutRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_subtitle),
                    onClick = {
                        uriHandler.openUri("https://github.com/pass-with-high-score/universal-installer/blob/main/LICENSE")
                    },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.setting_privacy_title),
                    subtitle = stringResource(R.string.setting_privacy_subtitle),
                    onClick = { uriHandler.openUri("https://universal-installer.pwhs.app/privacy") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.setting_terms_title),
                    subtitle = stringResource(R.string.setting_terms_subtitle),
                    onClick = { uriHandler.openUri("https://universal-installer.pwhs.app/terms") },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            // ── DEVICE ──
            item { AboutSectionLabel(stringResource(R.string.about_section_device)) }
            item {
                AboutRow(
                    icon = Icons.Rounded.Smartphone,
                    title = stringResource(R.string.about_device_info_title),
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
                    onClick = { showDeviceInfoDialog = true },
                )
            }
        }
    }

    if (showDeviceInfoDialog) {
        DeviceInfoDialog(onDismiss = { showDeviceInfoDialog = false })
    }
}

@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRowPainter(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resource = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val deviceInfo = remember {
        buildString {
            appendLine(resource.getString(R.string.about_device_manufacturer, Build.MANUFACTURER))
            appendLine(resource.getString(R.string.about_device_model, Build.MODEL))
            appendLine(resource.getString(R.string.about_device_board, Build.BOARD))
            appendLine(
                resource.getString(
                    R.string.about_device_arch,
                    Build.SUPPORTED_ABIS.joinToString(", "),
                )
            )
            appendLine(resource.getString(R.string.about_device_sdk, Build.VERSION.SDK_INT.toString()))
            appendLine(resource.getString(R.string.about_device_os, Build.VERSION.RELEASE))
            appendLine(
                resource.getString(
                    R.string.about_device_density,
                    android.content.res.Resources.getSystem().displayMetrics.density.toString(),
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_device_info_title)) },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = deviceInfo,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_btn_ok)) }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    val clip = android.content.ClipData.newPlainText("device-info", deviceInfo)
                    clipboard.setClipEntry(ClipEntry(clip))
                    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.about_btn_copy)) }
        },
    )
}
