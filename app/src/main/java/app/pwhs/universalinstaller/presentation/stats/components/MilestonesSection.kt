package app.pwhs.universalinstaller.presentation.stats.components

import android.text.format.Formatter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.roundToLong

@Composable
fun MilestoneCategoryFilterRow(
    selectedCategory: MilestoneCategory?,
    onSelectCategory: (MilestoneCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
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
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val animatedProgress by animateFloatAsState(
        targetValue = if (entered) item.progressFraction else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "milestone_${item.id.name}",
    )

    val iconPopScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "iconPop_${item.id.name}",
    )

    val displayedCurrent = (item.currentValue.coerceAtMost(item.targetValue) *
        (if (entered) 1f else 0f)).roundToLong()

    val progressText = if (item.id.isBytes) {
        val currentFmt = Formatter.formatFileSize(
            context,
            (item.targetValue * animatedProgress).roundToLong(),
        )
        val targetFmt = Formatter.formatFileSize(context, item.targetValue)
        "$currentFmt / $targetFmt"
    } else {
        "${(item.targetValue * animatedProgress).roundToLong().coerceAtMost(displayedCurrent)} / ${item.targetValue}"
    }

    ElevatedCard(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.isUnlocked) {
                colors.secondaryContainer.copy(alpha = 0.55f).compositeOver(colors.surface)
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
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = iconPopScale
                        scaleY = iconPopScale
                    },
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
                        val pulseTransition = rememberInfiniteTransition(label = "newBadgePulse")
                        val newBadgeScale by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(650, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "newBadgeScale",
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tertiary,
                            modifier = Modifier.graphicsLayer {
                                scaleX = newBadgeScale
                                scaleY = newBadgeScale
                            },
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
                        gapSize = 0.dp,
                        drawStopIndicator = {},
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
    MilestoneId.PRO_SUPPORTER -> Icons.Rounded.WorkspacePremium
    MilestoneId.PRO_VIRUSTOTAL_GUARDIAN -> Icons.Rounded.Shield
    MilestoneId.PRO_UNLIMITED_CHAMPION -> Icons.Rounded.AutoAwesome
}
