package app.pwhs.universalinstaller.presentation.stats.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.stats.TopAppStat
import app.pwhs.universalinstaller.domain.stats.UserStatsSummary
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun ActivityTimelineCard(
    summary: UserStatsSummary,
    selectedDays: Int,
    onSelectDays: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    StatsSectionCard(
        title = stringResource(R.string.stats_section_activity),
        icon = Icons.Rounded.BarChart,
        modifier = modifier,
        trailingHeader = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHighest)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PeriodToggleChip(
                    label = stringResource(R.string.stats_period_7d),
                    selected = selectedDays == 7,
                    onClick = { onSelectDays(7) },
                )
                PeriodToggleChip(
                    label = stringResource(R.string.stats_period_30d),
                    selected = selectedDays == 30,
                    onClick = { onSelectDays(30) },
                )
            }
        },
    ) {
        AnimatedContent(
            targetState = selectedDays,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
            label = "timelinePeriodSwitch",
        ) { periodDays ->
            val points = if (periodDays == 30) summary.last30DaysActivity else summary.last7DaysActivity
            val maxCount = (points.maxOfOrNull { it.totalCount } ?: 0).coerceAtLeast(1)

            var barsEntered by remember(periodDays) { mutableStateOf(false) }
            LaunchedEffect(periodDays) { barsEntered = true }

            if (points.all { it.totalCount == 0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_activity_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (periodDays == 30) 3.dp else 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        points.forEachIndexed { index, stat ->
                            val fraction = (stat.totalCount.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                            val targetHeightFraction = if (stat.totalCount > 0) fraction.coerceAtLeast(0.12f) else 0.04f
                            val animatedFraction by animateFloatAsState(
                                targetValue = if (barsEntered) targetHeightFraction else 0f,
                                animationSpec = tween(
                                    durationMillis = 550,
                                    delayMillis = index * (if (periodDays == 30) 12 else 40),
                                    easing = FastOutSlowInEasing,
                                ),
                                label = "bar_$index",
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Fixed top slot so every bar shares the exact same baseline
                                if (periodDays == 7) {
                                    Box(
                                        modifier = Modifier.height(18.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (stat.totalCount > 0) {
                                            Text(
                                                text = stat.totalCount.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.primary,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                // Fixed bar track area
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((80 * animatedFraction).dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 6.dp,
                                                    topEnd = 6.dp,
                                                    bottomStart = 3.dp,
                                                    bottomEnd = 3.dp,
                                                ),
                                            )
                                            .background(
                                                if (stat.totalCount > 0) colors.primary
                                                else colors.outlineVariant.copy(alpha = 0.35f),
                                            ),
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Fixed bottom label slot so day labels never shift or clip
                                Box(
                                    modifier = Modifier.height(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (periodDays == 7 || index % 6 == 0 || index == points.lastIndex) {
                                        Text(
                                            text = if (periodDays == 7) stat.dayLabel else stat.dateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = if (index == points.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            color = if (index == points.lastIndex) colors.primary else colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (summary.busiestDayCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.stats_busiest_day, summary.busiestDayCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopInstalledAppsCard(
    topApps: List<TopAppStat>,
    modifier: Modifier = Modifier,
) {
    if (topApps.isEmpty()) return
    val colors = MaterialTheme.colorScheme

    StatsSectionCard(
        title = stringResource(R.string.stats_section_top_apps),
        icon = Icons.AutoMirrored.Rounded.TrendingUp,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            topApps.forEachIndexed { index, app ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.primaryContainer,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))

                        val iconFile = app.iconPath?.let { File(it) }?.takeIf { it.exists() }
                        if (iconFile != null) {
                            AsyncImage(
                                model = iconFile,
                                contentDescription = app.appName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.secondaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = null,
                                        tint = colors.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.stats_top_app_meta,
                                    app.totalInstalls,
                                    app.updateCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.surfaceContainerHighest,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
