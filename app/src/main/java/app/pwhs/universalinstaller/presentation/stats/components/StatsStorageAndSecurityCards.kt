package app.pwhs.universalinstaller.presentation.stats.components

import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.stats.UserStatsSummary
import kotlin.math.roundToLong

@Composable
fun StorageFormatsCard(
    summary: UserStatsSummary,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val animFraction = rememberGrowFloat(target = 1f, durationMillis = 800)
    val installedFormatted = Formatter.formatFileSize(
        context,
        (summary.totalBytesInstalled * animFraction).roundToLong(),
    )
    val cleanedFormatted = Formatter.formatFileSize(
        context,
        (summary.totalBytesCleaned * animFraction).roundToLong(),
    )

    val animSingle = rememberCountUpInt(summary.singleApkCount)
    val animSplit = rememberCountUpInt(summary.splitBundleCount)
    val animObb = rememberCountUpInt(summary.obbInstallCount)
    val animCleanedFiles = rememberCountUpInt(summary.totalFilesCleaned)

    StatsSectionCard(
        title = stringResource(R.string.stats_section_storage_formats),
        icon = Icons.Rounded.Storage,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FolderZip,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stats_bytes_installed),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column {
                        Text(
                            text = installedFormatted,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "${summary.totalSuccessInstalls} ${stringResource(R.string.stats_category_install).lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stats_bytes_cleaned),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column {
                        Text(
                            text = cleanedFormatted,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.stats_files_cleaned_sub, animCleanedFiles),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val totalPackages = (summary.singleApkCount + summary.splitBundleCount).coerceAtLeast(1)
        val splitRatio = summary.splitBundleCount.toFloat() / totalPackages.toFloat()
        val animatedSplitRatio = rememberGrowFloat(target = splitRatio, durationMillis = 850)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.primary.copy(alpha = 0.25f)),
        ) {
            if (animatedSplitRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedSplitRatio.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .background(colors.tertiary),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniStatPill(
                label = stringResource(R.string.stats_format_single_apk),
                value = animSingle.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_format_split_bundle),
                value = animSplit.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_format_obb),
                value = animObb.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
fun ModesAndSecurityCard(
    summary: UserStatsSummary,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animDefault = rememberCountUpInt(summary.defaultModeCount)
    val animShizuku = rememberCountUpInt(summary.shizukuModeCount)
    val animRoot = rememberCountUpInt(summary.rootModeCount)
    val animDhizuku = rememberCountUpInt(summary.dhizukuModeCount)
    val animVt = rememberCountUpInt(summary.virusTotalScans)
    val animTracker = rememberCountUpInt(summary.trackerScans)

    StatsSectionCard(
        title = stringResource(R.string.stats_section_modes_security),
        icon = Icons.Rounded.AdminPanelSettings,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniStatPill(
                label = stringResource(R.string.stats_mode_default),
                value = animDefault.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_mode_shizuku),
                value = animShizuku.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_mode_root),
                value = animRoot.toString(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            MiniStatPill(
                label = stringResource(R.string.stats_mode_dhizuku),
                value = animDhizuku.toString(),
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stats_vt_scans),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = animVt.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stats_tracker_scans),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column {
                        Text(
                            text = animTracker.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        if (summary.trackersDetectedCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.stats_trackers_found,
                                    summary.trackersDetectedCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
