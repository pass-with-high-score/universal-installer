package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.install.dialog.components.DialogPill
import app.pwhs.universalinstaller.presentation.install.dialog.components.MenuCard
import app.pwhs.universalinstaller.presentation.install.dialog.components.PermissionRowList
import app.pwhs.universalinstaller.presentation.install.resolvePermissionEntries
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

@Composable
fun UpdateDiffCard(apkInfo: ApkInfo) {
    if (!apkInfo.isUpdateInstalled) return
    val added = apkInfo.addedPermissions
    val sigMismatch = apkInfo.signatureMismatch == true
    val hasAttention = sigMismatch || added.isNotEmpty()
    val badgeText = when {
        sigMismatch -> "⚠️"
        added.isNotEmpty() -> "+${added.size}"
        else -> "OK"
    }
    val summaryText = when {
        sigMismatch -> stringResource(R.string.update_diff_sig_mismatch)
        added.isNotEmpty() -> stringResource(R.string.update_diff_added_perms_title, added.size)
        else -> stringResource(R.string.update_diff_sig_match)
    }

    SectionCard(
        icon = if (hasAttention) Icons.Rounded.Warning else Icons.Rounded.VerifiedUser,
        title = stringResource(R.string.update_diff_card_title),
        summary = summaryText,
        badge = badgeText,
        defaultExpanded = hasAttention,
    ) {
        UpdateDiffBody(apkInfo = apkInfo)
    }
}

@Composable
internal fun UpdateDiffMenuCard(apkInfo: ApkInfo) {
    if (!apkInfo.isUpdateInstalled) return
    val extendedColors = LocalExtendedColors.current
    val added = apkInfo.addedPermissions
    val sigMismatch = apkInfo.signatureMismatch == true
    val hasAttention = sigMismatch || added.isNotEmpty()
    var expanded by remember(hasAttention) { mutableStateOf(hasAttention) }

    val desc = when {
        sigMismatch -> stringResource(R.string.update_diff_sig_mismatch)
        added.isNotEmpty() -> stringResource(R.string.update_diff_added_perms_title, added.size)
        else -> stringResource(R.string.update_diff_sig_match)
    }
    val descColor = when {
        sigMismatch -> MaterialTheme.colorScheme.error
        added.isNotEmpty() -> extendedColors.warning
        else -> MaterialTheme.colorScheme.tertiary
    }

    MenuCard(
        title = stringResource(R.string.update_diff_card_title),
        description = desc,
        descriptionColor = descColor,
        icon = {
            Icon(
                imageVector = if (hasAttention) Icons.Rounded.Difference else Icons.Rounded.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = descColor,
            )
        },
        expanded = expanded,
        onClick = { expanded = !expanded },
        badge = if (added.isNotEmpty()) "+${added.size}" else null,
    ) {
        UpdateDiffBody(
            apkInfo = apkInfo,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun UpdateDiffBody(
    apkInfo: ApkInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current
    val sigMismatch = apkInfo.signatureMismatch == true
    val added = apkInfo.addedPermissions
    val removed = apkInfo.removedPermissions
    val addedEntries = remember(added) { resolvePermissionEntries(context, added) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Signature Verification Row
        if (apkInfo.signatureMismatch != null) {
            val sigBg = if (sigMismatch) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            }
            val sigTint = if (sigMismatch) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(sigBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (sigMismatch) Icons.Rounded.Warning else Icons.Rounded.GppGood,
                    contentDescription = null,
                    tint = sigTint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (sigMismatch) {
                        stringResource(R.string.update_diff_sig_mismatch)
                    } else {
                        stringResource(R.string.update_diff_sig_match)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = sigTint,
                )
            }
        }

        // 2. Added Permissions Diff
        if (addedEntries.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = extendedColors.success,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.update_diff_no_new_perms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.update_diff_added_perms_title, addedEntries.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = extendedColors.warning,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            PermissionRowList(entries = addedEntries)
        }

        // 3. Removed Permissions Diff
        if (removed.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.RemoveCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.update_diff_removed_perms_title, removed.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun UpdateDiffStatusChip(
    apkInfo: ApkInfo,
    onClick: () -> Unit,
) {
    if (!apkInfo.isUpdateInstalled) return
    val extendedColors = LocalExtendedColors.current
    val sigMismatch = apkInfo.signatureMismatch == true
    val addedCount = apkInfo.addedPermissions.size

    when {
        sigMismatch -> InfoChip(
            label = stringResource(R.string.update_diff_chip_sig_mismatch),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onClick,
        )
        addedCount > 0 -> InfoChip(
            label = stringResource(R.string.update_diff_chip_new_perms, addedCount),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Difference,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = extendedColors.warning,
                )
            },
            containerColor = extendedColors.warningContainer,
            contentColor = extendedColors.warning,
            onClick = onClick,
        )
        apkInfo.signatureMismatch == false -> InfoChip(
            label = stringResource(R.string.update_diff_chip_clean),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = extendedColors.success,
                )
            },
            contentColor = extendedColors.success,
            onClick = onClick,
        )
    }
}

@Composable
internal fun UpdateDiffDialogPill(
    apkInfo: ApkInfo,
    onClick: () -> Unit,
) {
    if (!apkInfo.isUpdateInstalled) return
    val extendedColors = LocalExtendedColors.current
    val sigMismatch = apkInfo.signatureMismatch == true
    val addedCount = apkInfo.addedPermissions.size

    when {
        sigMismatch -> DialogPill(
            label = stringResource(R.string.update_diff_chip_sig_mismatch),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onClick,
        )
        addedCount > 0 -> DialogPill(
            label = stringResource(R.string.update_diff_chip_new_perms, addedCount),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Difference,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = extendedColors.warning,
                )
            },
            containerColor = extendedColors.warningContainer,
            contentColor = extendedColors.warning,
            onClick = onClick,
        )
        apkInfo.signatureMismatch == false -> DialogPill(
            label = stringResource(R.string.update_diff_chip_clean),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = extendedColors.success,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = extendedColors.success,
            onClick = onClick,
        )
    }
}
