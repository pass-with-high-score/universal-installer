package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.billing.ProEntitlement
import app.pwhs.core.billing.ProProductType
import app.pwhs.core.network.VirusTotalQuotaStore

@Composable
fun formatTrialRemaining(expiresAtMillis: Long): String {
    val remainingMs = (expiresAtMillis - VirusTotalQuotaStore.currentTrueTimeMillis()).coerceAtLeast(0L)
    val hours = remainingMs / (3600L * 1000L)
    val days = (hours + 23L) / 24L
    return when {
        hours >= 24L && days > 1L -> stringResource(R.string.pro_trial_days_left, days.toInt())
        hours >= 24L -> stringResource(R.string.pro_trial_day_left)
        hours >= 1L -> stringResource(R.string.pro_trial_hours_left, hours.toInt())
        else -> stringResource(R.string.pro_trial_less_than_hour)
    }
}

@Composable
fun SettingProBanner(
    isProUser: Boolean,
    isBillingAvailable: Boolean,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    proEntitlement: ProEntitlement = if (isProUser) ProEntitlement.Purchased() else ProEntitlement.Free,
) {
    if (!isProUser && !isBillingAvailable) return

    val isTrial = proEntitlement is ProEntitlement.Trial
    val isPurchased = proEntitlement is ProEntitlement.Purchased

    val cardColor = when {
        isPurchased -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        isTrial -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderColor = when {
        isPurchased -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        isTrial -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    OutlinedCard(
        onClick = onUpgradeClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isTrial) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        else if (isPurchased) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = if (isTrial) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.setting_pro_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                when (proEntitlement) {
                    is ProEntitlement.Trial -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.pro_trial_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Text(
                                text = formatTrialRemaining(proEntitlement.expiresAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    is ProEntitlement.Purchased -> {
                        val planText = when (proEntitlement.planType) {
                            ProProductType.LIFETIME -> stringResource(R.string.pro_plan_lifetime_active)
                            ProProductType.YEARLY -> stringResource(R.string.pro_plan_yearly_active)
                            null -> stringResource(R.string.setting_pro_banner_desc_active)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_pro_banner_badge_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = planText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    ProEntitlement.Free -> {
                        Text(
                            text = stringResource(R.string.setting_pro_banner_desc_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isPurchased && isBillingAvailable) {
                Button(
                    onClick = onUpgradeClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_pro_banner_badge_upgrade),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (isPurchased) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
