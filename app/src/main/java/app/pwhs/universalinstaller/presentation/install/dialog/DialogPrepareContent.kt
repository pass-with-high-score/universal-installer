package app.pwhs.universalinstaller.presentation.install.dialog

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.rounded.Android

/**
 * Stage 2: Prepare — clean, compact APK info display.
 * Shows icon, name, version, warning chips, and 3 buttons: Menu, Install, Cancel.
 * Inspired by InstallerX-Revived's InstallPrepareDialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogPrepareContent(
    apkInfo: ApkInfo,
    installedVersionName: String? = null,
    installedVersionCode: Long? = null,
    onInstall: () -> Unit,
    onMenu: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val isUpdate = installedVersionCode != null && installedVersionCode > 0
    val isDowngrade = isUpdate && apkInfo.versionCode < (installedVersionCode ?: 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── App Icon ──
        AnimatedContent(
            targetState = apkInfo.icon,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(150))
            },
            label = "IconAnimation",
        ) { drawable ->
            if (drawable != null) {
                Image(
                    bitmap = drawable.toBitmap(128, 128).asImageBitmap(),
                    contentDescription = apkInfo.appName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── App Name ──
        Text(
            text = apkInfo.appName.ifBlank { apkInfo.packageName },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Package Name ──
        Text(
            text = apkInfo.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Version Info ──
        AnimatedContent(
            targetState = Triple(isUpdate, isDowngrade, apkInfo.versionName),
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "VersionAnimation",
        ) { (update, downgrade, newVersion) ->
            when {
                downgrade -> {
                    Text(
                        text = stringResource(
                            R.string.dialog_version_downgrade,
                            installedVersionName ?: "?",
                            newVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                update -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = installedVersionName ?: "?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(16.dp),
                        )
                        Text(
                            text = newVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                else -> {
                    Text(
                        text = "${newVersion} (${apkInfo.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Size ──
        val sizeText = Formatter.formatFileSize(context, apkInfo.fileSizeBytes)
        if (apkInfo.fileSizeBytes > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Warning Chips ──
        val hasChips = isDowngrade || apkInfo.splitCount > 1 || apkInfo.obbFileNames.isNotEmpty()
        AnimatedVisibility(visible = hasChips) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isDowngrade) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.dialog_chip_downgrade)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (apkInfo.splitCount > 1) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(stringResource(R.string.dialog_chip_split_apk))
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (apkInfo.obbFileNames.isNotEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(stringResource(R.string.dialog_chip_has_obb))
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Buttons: [Menu] [Install] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Menu button
            OutlinedButton(
                onClick = onMenu,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dialog_menu_btn))
            }

            // Install/Update/Downgrade button
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                colors = if (isDowngrade) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    text = when {
                        isDowngrade -> stringResource(R.string.dialog_downgrade_btn)
                        isUpdate -> stringResource(R.string.dialog_update_btn)
                        else -> stringResource(R.string.dialog_install_btn)
                    },
                )
            }
        }

        // Cancel
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dialog_cancel_btn))
        }
    }
}
