package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.presentation.components.VirusTotalDailyQuotaBadge
import app.pwhs.core.presentation.components.VirusTotalUnlimitedOptionsSection
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.pro.UpgradeProBottomSheet
import app.pwhs.universalinstaller.presentation.setting.components.SecurityLevelSelector
import org.koin.androidx.compose.koinViewModel

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(app.pwhs.core.R.string.setting_virustotal_title),
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
                bottom = navBarPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(
                    title = stringResource(app.pwhs.core.R.string.setting_virustotal_title),
                    icon = Icons.Rounded.Security,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = uiState.virusTotalDisabled,
                                role = Role.Switch,
                                onValueChange = viewModel::setVirusTotalDisabled,
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_vt_disable_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.setting_vt_disable_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.virusTotalDisabled,
                            onCheckedChange = null,
                        )
                    }

                    if (!uiState.virusTotalDisabled) {
                        VirusTotalDailyQuotaBadge(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )

                        VirusTotalUnlimitedOptionsSection(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            showOption1Card = true,
                            onUpgradeProClick = { showUpgradeProSheet = true },
                        )

                        OutlinedTextField(
                            value = uiState.apiKey,
                            onValueChange = viewModel::setApiKey,
                            label = { Text(stringResource(R.string.setting_vt_api_key_title)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService<ClipboardManager>()
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                    if (!clipText.isNullOrBlank()) {
                                        viewModel.setApiKey(clipText)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = stringResource(R.string.virustotal_setup_paste),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            placeholder = { Text(stringResource(R.string.setting_vt_api_key_placeholder)) },
                            supportingText = {
                                Text(stringResource(R.string.virustotal_setup_dialog_desc))
                            },
                            singleLine = true,
                        )

                        TextButton(
                            onClick = { uriHandler.openUriSafely(context, "https://www.virustotal.com/gui/my-apikey") },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.virustotal_setup_get_free_key),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        SecurityLevelSelector(
                            current = uiState.securityLevel,
                            onChange = viewModel::setSecurityLevel,
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
            source = app.pwhs.core.telemetry.TelemetryEvents.SOURCE_VIRUSTOTAL_SETTINGS,
        )
    }
}
