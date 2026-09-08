package app.pwhs.tv.presentation.updates.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.R
import app.pwhs.updater.domain.matcher.SmartAbiMatcher
import app.pwhs.updater.domain.model.AssetArtifact

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAppFilePickerDialog(
    appName: String,
    assets: List<AssetArtifact>,
    currentDownloadUrl: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (AssetArtifact) -> Unit,
) {
    val recommendedAsset = remember(assets) {
        SmartAbiMatcher.selectBestAsset(assets)
    }

    var selectedAsset by remember(assets, currentDownloadUrl) {
        mutableStateOf(
            assets.firstOrNull { it.downloadUrl == currentDownloadUrl }
                ?: recommendedAsset
                ?: assets.firstOrNull()
        )
    }

    val supportedAbisText = remember {
        Build.SUPPORTED_ABIS.joinToString(", ")
    }

    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { confirmFocusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 520.dp, max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.padding(28.dp)) {
                // Header Row with Icon and Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.updates_dialog_pick_apk_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Device ABI Info Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.updates_dialog_device_arch, supportedAbisText),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Asset List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(assets, key = { it.downloadUrl }) { asset ->
                        val isSelected = asset.downloadUrl == selectedAsset?.downloadUrl
                        val isRecommended = asset.downloadUrl == recommendedAsset?.downloadUrl
                        val itemShape = RoundedCornerShape(12.dp)

                        Surface(
                            onClick = { selectedAsset = asset },
                            shape = ClickableSurfaceDefaults.shape(itemShape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = asset.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )

                                        if (isRecommended) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.updates_dialog_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }

                                    if (asset.sizeBytes > 0) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = formatBytes(asset.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons: Install & Cancel
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val btnShape = RoundedCornerShape(14.dp)

                    Button(
                        onClick = { selectedAsset?.let { onConfirm(it) } },
                        enabled = selectedAsset != null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape)
                            .focusRequester(confirmFocusRequester),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.updates_card_btn_install),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}
