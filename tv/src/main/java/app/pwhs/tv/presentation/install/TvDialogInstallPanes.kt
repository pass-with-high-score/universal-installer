package app.pwhs.tv.presentation.install

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.tv.R
import app.pwhs.tv.formatSize
import app.pwhs.tv.presentation.receive.components.getAndroidVersion
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ReadyView(
    ready: TvInstallState.Ready,
    onInstall: () -> Unit,
    onTogglePermissions: () -> Unit,
    onDismiss: () -> Unit,
    onUploadAndScanVt: () -> Unit = {},
    onRetryVt: () -> Unit = {},
) {
    val context = LocalContext.current
    val installFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { installFocus.requestFocus() }

    val strictVirusTotal by context.dataStore.data
        .map { it[SharedPrefsKeys.STRICT_VIRUSTOTAL_CHECK] ?: false }
        .collectAsState(initial = false)
    var showStrictWarning by remember { mutableStateOf(false) }

    val meta = ready.metadata
    val iconBitmap = remember(meta.icon) { meta.icon?.asImageBitmap() }

    val isUpdate = ready.installedVersionCode != null && meta.versionCode > ready.installedVersionCode
    val isDowngrade = ready.installedVersionCode != null && meta.versionCode < ready.installedVersionCode
    val isSame = ready.installedVersionCode != null && meta.versionCode == ready.installedVersionCode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp)
    ) {
        // App Identity Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "APK",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = meta.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (ready.isBundle) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "SPLIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (isDowngrade) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.found_chip_older),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else if (isUpdate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.found_chip_update),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else if (isSame) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.found_chip_installed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (ready.installedVersionCode == null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.found_chip_new),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Metadata grid
        val minVer = getAndroidVersion(meta.minSdk)
        val targetVer = getAndroidVersion(meta.targetSdk)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetaChip(
                label = stringResource(R.string.tv_details_version),
                value = meta.versionName.ifBlank { "—" },
                modifier = Modifier.weight(1f)
            )
            MetaChip(
                label = stringResource(R.string.tv_details_size),
                value = formatSize(context, ready.sizeBytes),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetaChip(
                label = stringResource(R.string.tv_details_min_sdk),
                value = if (meta.minSdk > 0) (if (minVer != null) "Android $minVer+" else "API ${meta.minSdk}") else "—",
                modifier = Modifier.weight(1f)
            )
            MetaChip(
                label = stringResource(R.string.tv_details_target_sdk),
                value = if (meta.targetSdk > 0) (if (targetVer != null) "Android $targetVer" else "API ${meta.targetSdk}") else "—",
                modifier = Modifier.weight(1f)
            )
        }

        // Installed version notice if available
        ready.installedVersion?.let { installed ->
            val statusText = when {
                isDowngrade -> stringResource(R.string.tv_details_status_downgrade, installed)
                isUpdate -> stringResource(R.string.tv_details_status_update, installed)
                isSame -> stringResource(R.string.tv_details_status_same, installed)
                else -> stringResource(R.string.tv_details_status_update, installed)
            }
            val statusColor = if (isDowngrade) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDowngrade) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
        }

        // VirusTotal Status Card
        if (ready.vtResult != null) {
            Spacer(Modifier.height(10.dp))
            TvVirusTotalCard(
                vtResult = ready.vtResult,
                onUploadAndScan = onUploadAndScanVt,
                onRetry = onRetryVt,
            )
        }

        Spacer(Modifier.height(20.dp))

        val handleInstall = {
            val isThreat = ready.vtResult?.status == VtStatus.MALICIOUS || ready.vtResult?.status == VtStatus.SUSPICIOUS
            if (strictVirusTotal && isThreat) {
                showStrictWarning = true
            } else {
                onInstall()
            }
        }

        // Action Buttons: Equal width and height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = handleInstall,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(installFocus),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tv_receive_install),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (meta.permissions.isNotEmpty()) {
                Button(
                    onClick = onTogglePermissions,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.tv_details_action_permissions),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tv_dialog_btn_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showStrictWarning) {
        Dialog(onDismissRequest = { showStrictWarning = false }) {
            Surface(
                modifier = Modifier.width(520.dp),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(Modifier.padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.tv_vt_strict_warning_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.tv_vt_strict_warning_desc, ready.vtResult?.malicious ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                showStrictWarning = false
                                onInstall()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.tv_receive_install),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = { showStrictWarning = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tv_dialog_btn_cancel))
                        }
                    }
                }
            }
        }
    }
}
