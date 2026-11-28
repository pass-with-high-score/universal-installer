package app.pwhs.universalinstaller.presentation.stats.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.pro.MilestoneRewardTier
import app.pwhs.universalinstaller.domain.pro.ProTrialManager

/**
 * Reward-track card showing the 5 milestone Pro tiers (2 - 5 - 7 - 10 - 15) on the Milestones tab.
 */
@Composable
fun MilestoneRewardTrackCard(
    unlockedCount: Int,
    claimedTiers: Set<String>,
    baseRewardHours: Int,
    claimingTierId: String?,
    onClaimTier: (MilestoneRewardTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tiers = ProTrialManager.REWARD_TIERS
    val claimableTier = tiers.firstOrNull {
        unlockedCount >= it.requiredUnlocked && it.tierId !in claimedTiers
    }
    val nextLockedTier = tiers.firstOrNull { unlockedCount < it.requiredUnlocked }
    val allClaimed = tiers.all { it.tierId in claimedTiers }

    val trackFraction = computeTrackFraction(unlockedCount, tiers)
    val animatedFraction by animateFloatAsState(
        targetValue = trackFraction,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "rewardTrackProgress",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer.copy(alpha = 0.28f).compositeOver(colors.surface),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.milestone_reward_track_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitle = when {
                        claimableTier != null -> stringResource(
                            R.string.milestone_reward_track_ready,
                            formatRewardHours(claimableTier.durationHours(baseRewardHours)),
                        )
                        allClaimed -> stringResource(R.string.milestone_reward_track_all_claimed)
                        nextLockedTier != null -> stringResource(
                            R.string.milestone_reward_track_next,
                            (nextLockedTier.requiredUnlocked - unlockedCount).coerceAtLeast(1),
                            formatRewardHours(nextLockedTier.durationHours(baseRewardHours)),
                        )
                        else -> stringResource(R.string.milestone_reward_track_all_claimed)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5-node horizontal reward track (2 - 5 - 7 - 10 - 15)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Connecting progress bar aligned with circle centers (top padding = 14dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 15.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.primary),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    tiers.forEach { tier ->
                        val isClaimed = tier.tierId in claimedTiers
                        val isReached = unlockedCount >= tier.requiredUnlocked
                        val isNextTarget = tier == nextLockedTier
                        TierNode(
                            tier = tier,
                            rewardText = formatRewardHours(tier.durationHours(baseRewardHours)),
                            isClaimed = isClaimed,
                            isReached = isReached,
                            isNextTarget = isNextTarget,
                        )
                    }
                }
            }

            if (claimableTier != null) {
                Spacer(modifier = Modifier.height(14.dp))
                val isClaiming = claimingTierId == claimableTier.tierId
                Button(
                    onClick = { onClaimTier(claimableTier) },
                    enabled = !isClaiming,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    if (isClaiming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CardGiftcard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.milestone_reward_claim_cta,
                            formatRewardHours(claimableTier.durationHours(baseRewardHours)),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TierNode(
    tier: MilestoneRewardTier,
    rewardText: String,
    isClaimed: Boolean,
    isReached: Boolean,
    isNextTarget: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp),
    ) {
        val circleBg = when {
            isClaimed -> colors.primary
            isReached -> colors.tertiary
            isNextTarget -> colors.primaryContainer
            else -> colors.surfaceContainerHighest
        }
        val circleContent = when {
            isClaimed -> colors.onPrimary
            isReached -> colors.onTertiary
            isNextTarget -> colors.onPrimaryContainer
            else -> colors.onSurfaceVariant
        }
        val borderColor = when {
            isReached && !isClaimed -> colors.tertiary
            isNextTarget -> colors.primary
            else -> colors.outlineVariant.copy(alpha = 0.5f)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(circleBg)
                .border(
                    width = if (isNextTarget || (isReached && !isClaimed)) 2.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isClaimed -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = circleContent,
                    modifier = Modifier.size(18.dp),
                )
                isReached -> Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = null,
                    tint = circleContent,
                    modifier = Modifier.size(16.dp),
                )
                else -> Text(
                    text = tier.requiredUnlocked.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = circleContent,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${tier.requiredUnlocked}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isReached || isNextTarget) FontWeight.Bold else FontWeight.Medium,
            color = if (isReached || isNextTarget) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = when {
                isClaimed -> colors.primary.copy(alpha = 0.14f)
                isReached -> colors.tertiary.copy(alpha = 0.18f)
                else -> colors.surfaceContainerHigh
            },
        ) {
            Text(
                text = "+$rewardText",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    isClaimed -> colors.primary
                    isReached -> colors.tertiary
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                maxLines = 1,
            )
        }
    }
}

private fun formatRewardHours(hours: Int): String =
    if (hours >= 24 && hours % 24 == 0) "${hours / 24}d" else "${hours}h"

/**
 * Piecewise linear progress across the 5 evenly-spaced visual nodes so the filled bar
 * aligns cleanly with each milestone node circle.
 */
private fun computeTrackFraction(
    unlockedCount: Int,
    tiers: List<MilestoneRewardTier>,
): Float {
    if (tiers.size < 2) return if (unlockedCount >= (tiers.firstOrNull()?.requiredUnlocked ?: 1)) 1f else 0f
    val firstTarget = tiers.first().requiredUnlocked
    if (unlockedCount <= 0) return 0f
    val segmentCount = (tiers.size - 1).toFloat()
    if (unlockedCount < firstTarget) {
        return (unlockedCount.toFloat() / firstTarget.toFloat()) * (0.5f / segmentCount)
    }
    for (i in 0 until tiers.lastIndex) {
        val current = tiers[i].requiredUnlocked
        val next = tiers[i + 1].requiredUnlocked
        if (unlockedCount <= next) {
            val segFraction = (unlockedCount - current).toFloat() / (next - current).coerceAtLeast(1).toFloat()
            return ((i + segFraction) / segmentCount).coerceIn(0f, 1f)
        }
    }
    return 1f
}
