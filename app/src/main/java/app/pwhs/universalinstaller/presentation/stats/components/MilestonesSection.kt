package app.pwhs.universalinstaller.presentation.stats.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.stats.MilestoneCategory
import app.pwhs.universalinstaller.domain.stats.MilestoneId
import app.pwhs.universalinstaller.domain.stats.MilestoneProgress

@Composable
fun MilestoneCategoryFilterRow(
    selectedCategory: MilestoneCategory?,
    onSelectCategory: (MilestoneCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text(stringResource(R.string.stats_category_all)) },
        )
        MilestoneCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(stringResource(category.labelRes)) },
            )
        }
    }
}

@Composable
fun MilestoneItemCard(
    item: MilestoneProgress,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = item.progressFraction,
        label = "milestone_${item.id.name}",
    )

    val progressText = if (item.id.isBytes) {
        val currentFmt = Formatter.formatFileSize(context, item.currentValue.coerceAtMost(item.targetValue))
        val targetFmt = Formatter.formatFileSize(context, item.targetValue)
        "$currentFmt / $targetFmt"
    } else {
        "${item.currentValue.coerceAtMost(item.targetValue)} / ${item.targetValue}"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.isUnlocked) {
                colors.secondaryContainer.copy(alpha = 0.55f)
            } else {
                colors.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (item.isUnlocked) colors.primary else colors.surfaceContainerHighest,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.id.iconVector(),
                        contentDescription = null,
                        tint = if (item.isUnlocked) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(item.id.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isNewlyUnlocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tertiary,
                        ) {
                            Text(
                                text = stringResource(R.string.milestone_badge_new),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else if (item.isUnlocked) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.milestone_unlocked_label),
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(item.id.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.isUnlocked) colors.primary else colors.secondary,
                        trackColor = colors.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isUnlocked) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun MilestoneId.iconVector(): ImageVector = when (this) {
    MilestoneId.FIRST_INSTALL -> Icons.Rounded.InstallMobile
    MilestoneId.TEN_INSTALLS -> Icons.Rounded.Verified
    MilestoneId.FIFTY_INSTALLS -> Icons.Rounded.MilitaryTech
    MilestoneId.HUNDRED_INSTALLS -> Icons.Rounded.EmojiEvents
    MilestoneId.SPLIT_BUNDLE -> Icons.Rounded.FolderZip
    MilestoneId.OBB_MASTER -> Icons.Rounded.SportsEsports
    MilestoneId.GIGABYTE_CLUB -> Icons.Rounded.Storage
    MilestoneId.TEN_GIGABYTES -> Icons.Rounded.Storage
    MilestoneId.PRIVILEGED_USER -> Icons.Rounded.AdminPanelSettings
    MilestoneId.TIME_TRAVELER -> Icons.Rounded.History
    MilestoneId.DEBLOAT_WARRIOR -> Icons.Rounded.DeleteSweep
    MilestoneId.SECURITY_GUARDIAN -> Icons.Rounded.Security
}
