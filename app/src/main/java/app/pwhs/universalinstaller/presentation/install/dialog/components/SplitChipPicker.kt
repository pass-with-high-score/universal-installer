package app.pwhs.universalinstaller.presentation.install.dialog.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.presentation.install.displayLanguage
import app.pwhs.universalinstaller.presentation.install.util.InstallApkSplitsHelper

@Composable
internal fun SmartSplitOptimizerBar(
    entries: List<SplitEntry>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.size <= 1) return
    val context = LocalContext.current
    val totalBytes = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val selectedBytes = entries.filter { it.selected }.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val savedBytes = (totalBytes - selectedBytes).coerceAtLeast(0L)
    val selectedCount = entries.count { it.selected }

    val smartTarget = remember(entries, context) {
        entries.map { it.copy() }.toMutableList().also {
            InstallApkSplitsHelper.applySmartPick(it, context)
        }
    }
    val isSmartOptimized = entries.indices.all { entries[it].selected == smartTarget[it].selected }
    val isAllSelected = entries.all { it.selected }
    val canSaveSpace = smartTarget.any { !it.selected }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (totalBytes > 0L) {
            val badgeBg = if (savedBytes > 0L) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val badgeColor = if (savedBytes > 0L) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (savedBytes > 0L) {
                        stringResource(
                            R.string.split_saved_badge,
                            Formatter.formatShortFileSize(context, savedBytes),
                            Formatter.formatShortFileSize(context, selectedBytes),
                            Formatter.formatShortFileSize(context, totalBytes),
                        )
                    } else {
                        stringResource(
                            R.string.split_full_size_badge,
                            Formatter.formatShortFileSize(context, totalBytes),
                            selectedCount,
                            entries.size,
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                )
            }
        }

        if (canSaveSpace || !isAllSelected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canSaveSpace) {
                    FilledTonalButton(
                        onClick = {
                            entries.indices.forEach { i ->
                                if (entries[i].type != SplitType.Base && entries[i].selected != smartTarget[i].selected) {
                                    onToggle(i)
                                }
                            }
                        },
                        enabled = !isSmartOptimized,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.split_optimize_action),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        entries.indices.forEach { i ->
                            if (entries[i].type != SplitType.Base && !entries[i].selected) {
                                onToggle(i)
                            }
                        }
                    },
                    enabled = !isAllSelected,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.split_select_all_action),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SplitChipPicker(
    entries: List<SplitEntry>,
    selectedBytes: Long,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        SmartSplitOptimizerBar(
            entries = entries,
            onToggle = onToggle,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val isBase = entry.type == SplitType.Base
                FilterChip(
                    selected = entry.selected,
                    onClick = { if (!isBase) onToggle(index) },
                    enabled = !isBase,
                    label = {
                        Text(
                            text = splitChipLabel(entry),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (isBase) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        if (entries.size <= 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (selectedBytes > 0) {
                    "Selected: ${Formatter.formatFileSize(context, selectedBytes)}"
                } else {
                    "Selected: —"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Friendly chip label per split type. Strips the "config." prefix that Android
 * Bundles add to ABI/locale/density splits, and runs locales through
 * displayLanguage so "config.en" renders as "English".
 */
@Composable
internal fun splitChipLabel(entry: SplitEntry): String {
    val raw = entry.name.removePrefix("config.")
    return when (entry.type) {
        SplitType.Base -> "Base"
        SplitType.Locale -> displayLanguage(raw)
        SplitType.Libs -> raw.replace('_', '-')
        SplitType.ScreenDensity -> raw
        SplitType.Feature -> raw
        SplitType.Other -> entry.name
    }
}
