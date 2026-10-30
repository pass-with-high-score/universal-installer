package app.pwhs.core.presentation.onboarding

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.R

/** A VirusTotal API key is a 64-character hex string; used only to catch a half-finished paste. */
private val VIRUSTOTAL_API_KEY_FORMAT = Regex("[0-9a-fA-F]{64}")

internal data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val badgeType: OnboardingBadgeType = OnboardingBadgeType.GENERIC,
    /** Renders supported devices badges under the description. */
    val deviceSupportList: Boolean = false,
    /** Renders a two-option security picker under the description. */
    val securityPicker: Boolean = false,
    /** Renders the VirusTotal API key field under the action button. */
    val virusTotalKeyField: Boolean = false,
    /** Renders the anonymous-reporting opt-out switch under the description. */
    val analyticsToggle: Boolean = false,
    /** Optional secondary action rendered under the description (e.g. "Open Developer options"). */
    val actionLabel: String? = null,
    val actionIcon: ImageVector? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
internal fun PageContent(
    page: OnboardingPage,
    isPermissionPage: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    strictSecurity: Boolean = false,
    onStrictSecurityChange: (Boolean) -> Unit = {},
    virusTotalKey: String = "",
    onVirusTotalKeyChange: (String) -> Unit = {},
    analyticsEnabled: Boolean = true,
    onAnalyticsEnabledChange: (Boolean) -> Unit = {},
    pageOffset: Float = 0f,
) {
    // Centered while it fits, scrollable once the keyboard takes half the screen away.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedOnboardingBadge(
                page = page,
                pageOffset = pageOffset,
                isPermissionPage = isPermissionPage,
                hasPermission = hasPermission,
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (page.deviceSupportList) {
                Spacer(Modifier.height(24.dp))
                DeviceSupportGrid()
            }

            if (page.securityPicker) {
                Spacer(Modifier.height(28.dp))
                OnboardingSecurityPicker(
                    strict = strictSecurity,
                    onChange = onStrictSecurityChange,
                )
            }

            if (page.analyticsToggle) {
                Spacer(Modifier.height(28.dp))
                OnboardingAnalyticsToggle(
                    enabled = analyticsEnabled,
                    onChange = onAnalyticsEnabledChange,
                )
            }

            // The key block draws its own link, so the shared button would only duplicate it.
            if (!page.virusTotalKeyField) {
                page.onAction?.let { action ->
                    val label = page.actionLabel ?: return@let
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = action) {
                        page.actionIcon?.let { icon ->
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(label)
                    }
                }
            }

            // The key goes in right here, beside the link that fetches it. The page used to send the
            // user to Settings to paste it, which is where the flow lost people.
            if (page.virusTotalKeyField) {
                Spacer(Modifier.height(24.dp))
                VirusTotalKeySetup(
                    value = virusTotalKey,
                    onValueChange = onVirusTotalKeyChange,
                    getKeyLabel = page.actionLabel.orEmpty(),
                    onGetKey = page.onAction ?: {},
                )
            }

            // Permission button on last page
            if (isPermissionPage) {
                Spacer(Modifier.height(32.dp))
                if (hasPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.onboarding_permission_granted),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    OutlinedButton(onClick = onRequestPermission) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.onboarding_grant_permission))
                    }
                }
            }
        }
    }
}

/**
 * The VirusTotal API key field shown during onboarding, with the link that produces a key.
 *
 * Every keystroke is persisted, so there is no Save button to miss — walking away mid-paste still
 * leaves the key stored. The 64-hex check is advisory: it catches a truncated paste without
 * refusing a key format VirusTotal might change later.
 */
@Composable
private fun VirusTotalKeySetup(
    value: String,
    onValueChange: (String) -> Unit,
    getKeyLabel: String,
    onGetKey: () -> Unit,
) {
    val context = LocalContext.current
    val trimmed = value.trim()
    val looksValid = VIRUSTOTAL_API_KEY_FORMAT.matches(trimmed)
    val malformed = trimmed.isNotEmpty() && !looksValid

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_virustotal_key_label)) },
            trailingIcon = {
                IconButton(onClick = { pasteFromClipboard(context)?.let(onValueChange) }) {
                    Icon(
                        Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.onboarding_virustotal_paste),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            singleLine = true,
            isError = malformed,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            supportingText = {
                Text(
                    text = stringResource(
                        when {
                            trimmed.isEmpty() -> R.string.onboarding_virustotal_key_hint
                            looksValid -> R.string.onboarding_virustotal_key_saved
                            else -> R.string.onboarding_virustotal_key_malformed
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        if (getKeyLabel.isNotEmpty()) {
            TextButton(onClick = onGetKey) {
                Text(getKeyLabel)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The reporting opt-out, presented on rather than off.
 *
 * A switch in a card rather than a segmented picker: this is one thing you turn off, not a
 * choice between two modes, and the row has to make the "off" path as easy to hit as "next".
 */
@Composable
private fun OnboardingAnalyticsToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_analytics_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.onboarding_analytics_switch_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

/**
 * Normal vs Strict, offered during onboarding.
 *
 * Worth asking here rather than defaulting silently: Strict changes the install screen for every
 * install afterwards, and it needs a VirusTotal API key to be useful at all. Someone who never
 * gets a key should not be left with Scan sitting in the primary button forever.
 *
 * Uses the same segmented control as Settings > Advanced, so the two screens offering this choice
 * look like the same control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSecurityPicker(
    strict: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val options = listOf(
        false to R.string.onboarding_security_normal,
        true to R.string.onboarding_security_strict,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, labelRes) ->
                SegmentedButton(
                    selected = value == strict,
                    onClick = { if (value != strict) onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (strict) R.string.onboarding_security_strict_sub else R.string.onboarding_security_normal_sub
            ),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceSupportGrid(modifier: Modifier = Modifier) {
    val devices = listOf(
        Pair(Icons.Rounded.PhoneAndroid, R.string.onboarding_device_android),
        Pair(Icons.Rounded.Tv, R.string.onboarding_device_tv),
        Pair(Icons.Rounded.DirectionsCar, R.string.onboarding_device_auto),
        Pair(Icons.Rounded.Watch, R.string.onboarding_device_wearos),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceBadgeCard(
                modifier = Modifier.weight(1f),
                icon = devices[0].first,
                label = stringResource(devices[0].second),
            )
            DeviceBadgeCard(
                modifier = Modifier.weight(1f),
                icon = devices[1].first,
                label = stringResource(devices[1].second),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceBadgeCard(
                modifier = Modifier.weight(1f),
                icon = devices[2].first,
                label = stringResource(devices[2].second),
            )
            DeviceBadgeCard(
                modifier = Modifier.weight(1f),
                icon = devices[3].first,
                label = stringResource(devices[3].second),
            )
        }
    }
}

@Composable
private fun DeviceBadgeCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
