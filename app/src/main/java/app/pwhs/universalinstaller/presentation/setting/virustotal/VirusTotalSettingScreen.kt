package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VtQuotaStatus
import app.pwhs.core.telemetry.TelemetryEvents
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.pro.UpgradeProBottomSheet
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import org.koin.androidx.compose.koinViewModel
import app.pwhs.core.R as CoreR

private val VIRUSTOTAL_API_KEY_FORMAT = Regex("[0-9a-fA-F]{64}")
private const val VIRUSTOTAL_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusTotalSettingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VirusTotalSettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showUpgradeProSheet by remember { mutableStateOf(false) }

    val quotaFlow = remember(context) { VirusTotalKeyManager.observeQuotaStatus(context) }
    val quota by quotaFlow.collectAsState(initial = VtQuotaStatus())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(CoreR.string.setting_virustotal_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = navBarPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Positive Master Toggle Header Card
            item {
                VirusTotalMasterSwitchCard(
                    enabled = !uiState.virusTotalDisabled,
                    onEnabledChange = { enabled -> viewModel.setVirusTotalDisabled(!enabled) },
                )
            }

            // 2. Bento Grid Content when VirusTotal is enabled
            item {
                AnimatedVisibility(
                    visible = !uiState.virusTotalDisabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        // Bento Row 1: Quota Widget + Pro Unlock Card (2 columns)
                        QuotaAndProBentoRow(
                            quota = quota,
                            onUpgradeProClick = { showUpgradeProSheet = true },
                        )

                        // Bento Row 2: Security Level (2-column selectable grid)
                        SecurityLevelBentoSection(
                            current = uiState.securityLevel,
                            onChange = viewModel::setSecurityLevel,
                        )

                        // Compact Personal API Key Section (Single field, no wall of text)
                        CompactApiKeySection(
                            apiKey = uiState.apiKey,
                            onApiKeyChange = viewModel::setApiKey,
                            onPasteClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                val clipText = clipboard?.primaryClip
                                    ?.getItemAt(0)
                                    ?.text
                                    ?.toString()
                                    ?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    viewModel.setApiKey(clipText)
                                }
                            },
                            onGetFreeKeyClick = {
                                uriHandler.openUriSafely(context, VIRUSTOTAL_API_KEY_URL)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showUpgradeProSheet) {
        UpgradeProBottomSheet(
            billingManager = viewModel.billingManager,
            onDismissRequest = { showUpgradeProSheet = false },
            source = TelemetryEvents.SOURCE_VIRUSTOTAL_SETTINGS,
        )
    }
}

@Composable
private fun VirusTotalMasterSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val statusText = if (enabled) {
        stringResource(CoreR.string.setting_virustotal_status_enabled)
    } else {
        stringResource(CoreR.string.setting_virustotal_status_disabled)
    }
    val dotColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_virustotal_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(CoreR.string.onboarding_virustotal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = "$statusText · 70+ Engines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun CompactApiKeySection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onGetFreeKeyClick: () -> Unit,
) {
    val trimmed = apiKey.trim()
    val looksValid = VIRUSTOTAL_API_KEY_FORMAT.matches(trimmed)
    val isMalformed = trimmed.isNotEmpty() && !looksValid

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.setting_vt_api_key_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGetFreeKeyClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(CoreR.string.onboarding_virustotal_get_key),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text(stringResource(R.string.setting_vt_api_key_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            leadingIcon = {
                Icon(
                    imageVector = if (looksValid) Icons.Rounded.CheckCircle else Icons.Rounded.Key,
                    contentDescription = null,
                    tint = when {
                        isMalformed -> MaterialTheme.colorScheme.error
                        looksValid -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (apiKey.isNotEmpty()) {
                        IconButton(onClick = { onApiKeyChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(android.R.string.cancel),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(onClick = onPasteClick) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = stringResource(R.string.virustotal_setup_paste),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            supportingText = if (trimmed.isNotEmpty()) {
                {
                    Text(
                        text = if (looksValid) {
                            stringResource(CoreR.string.onboarding_virustotal_key_saved)
                        } else {
                            stringResource(CoreR.string.onboarding_virustotal_key_malformed)
                        },
                        color = if (isMalformed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            } else {
                null
            },
            isError = isMalformed,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        )
    }
}
