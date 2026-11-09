package app.pwhs.universalinstaller.presentation.stats.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.stats.UserStatsSummary

@Composable
internal fun rememberCountUpInt(target: Int, durationMillis: Int = 750): Int {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animated by animateIntAsState(
        targetValue = if (entered) target else 0,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "countUpInt",
    )
    return animated
}

@Composable
internal fun rememberGrowFloat(target: Float, durationMillis: Int = 800): Float {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animated by animateFloatAsState(
        targetValue = if (entered) target else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "growFloat",
    )
    return animated
}

@Composable
fun UserRankBannerCard(
    summary: UserStatsSummary,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progressTarget = if (summary.totalMilestones > 0) {
        summary.unlockedCount.toFloat() / summary.totalMilestones.toFloat()
    } else {
        0f
    }
    val animatedProgress = rememberGrowFloat(target = progressTarget, durationMillis = 900)
    val animatedUnlocked = rememberCountUpInt(target = summary.unlockedCount, durationMillis = 700)

    val infiniteTransition = rememberInfiniteTransition(label = "rankPulse")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rankBadgeScale",
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary,
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_rank_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(summary.rank.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(summary.rank.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.stats_next_rank_progress,
                        animatedUnlocked,
                        summary.totalMilestones,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                )
                val pct = (animatedProgress * 100).toInt()
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = colors.primary,
                trackColor = colors.onPrimaryContainer.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            Spacer(modifier = Modifier.height(8.dp))

            val nextRank = summary.nextRank
            Text(
                text = if (nextRank != null) {
                    val needed = (nextRank.minUnlockedMilestones - summary.unlockedCount).coerceAtLeast(1)
                    stringResource(
                        R.string.stats_next_rank_hint,
                        needed,
                        stringResource(nextRank.titleRes),
                    )
                } else {
                    stringResource(R.string.stats_max_rank_reached)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun InstallUninstallOverviewCard(
    summary: UserStatsSummary,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animInstalled = rememberCountUpInt(summary.totalSuccessInstalls)
    val animRate = rememberCountUpInt(summary.successRatePercent)
    val animUninstalled = rememberCountUpInt(summary.totalUninstalls)
    val animNew = rememberCountUpInt(summary.newInstallCount)
    val animUpdate = rememberCountUpInt(summary.updateCount)
    val animDowngrade = rememberCountUpInt(summary.downgradeCount)
    val animFailed = rememberCountUpInt(summary.totalFailedInstalls)

    StatsSectionCard(
        title = stringResource(R.string.stats_section_install_uninstall),
        icon = Icons.Rounded.InstallMobile,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HighlightMetricBox(
                label = stringResource(R.string.stats_total_installed),
                value = animInstalled.toString(),
                accentColor = colors.primary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            HighlightMetricBox(
                label = stringResource(R.string.stats_success_rate),
                value = "$animRate%",
                accentColor = colors.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            HighlightMetricBox(
                label = stringResource(R.string.stats_total_uninstalled),
                value = animUninstalled.toString(),
                accentColor = colors.secondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniStatPill(
                label = stringResource(R.string.stats_op_new),
                value = animNew.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_op_update),
                value = animUpdate.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_op_downgrade),
                value = animDowngrade.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_failed_installs),
                value = animFailed.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
fun StatsSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailingHeader: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "cardAlpha",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                trailingHeader?.invoke()
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun HighlightMetricBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MiniStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
