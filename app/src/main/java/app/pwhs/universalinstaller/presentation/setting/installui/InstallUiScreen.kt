package app.pwhs.universalinstaller.presentation.setting.installui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Everything about how the installer *looks* and how it asks, split out of Settings > Installation.
 *
 * That section had grown to ten controls in four idioms — segmented buttons, switches, radios and
 * thumbnails — in one scrolling run. Worse, two of them decided the same thing ("Don't ask, just
 * install" against the auto-confirm switch) and one applied only to a mode the user might not have
 * picked, with nothing on screen saying so. Splitting the appearance questions out leaves the
 * Installation section to the engine and lets the real dependencies show as nesting.
 */
@Composable
fun InstallUiScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val externalOpenMode by viewModel.externalOpenMode.collectAsState()
    val installUiStyle by viewModel.installUiStyle.collectAsState()
    val context = LocalContext.current

    InstallUiContent(
        modifier = modifier,
        mode = externalOpenMode,
        style = installUiStyle,
        autoConfirm = uiState.autoConfirmExternalInstall,
        autoApprove = uiState.autoApproveCallerApps,
        autoApproveCount = uiState.autoApproveCount,
        autoApproveBlockTrackers = uiState.autoApproveBlockTrackers,
        showDownloadTab = uiState.showDownloadTab,
        onModeChange = viewModel::setExternalOpenMode,
        onStyleChange = viewModel::setInstallUiStyle,
        onAutoConfirmChange = viewModel::setAutoConfirmExternalInstall,
        onAutoApproveChange = viewModel::setAutoApproveEnabled,
        onAutoApproveBlockTrackersChange = viewModel::setAutoApproveBlockTrackers,
        onShowDownloadTabChange = viewModel::setShowDownloadTab,
        onBack = { (context as? android.app.Activity)?.finish() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUiContent(
    modifier: Modifier = Modifier,
    mode: ExternalOpenMode = ExternalOpenMode.Dialog,
    style: InstallUiStyle = InstallUiStyle.Dialog,
    autoConfirm: Boolean = false,
    autoApprove: Boolean = false,
    autoApproveCount: Int = 0,
    autoApproveBlockTrackers: Boolean = false,
    showDownloadTab: Boolean = true,
    onModeChange: (ExternalOpenMode) -> Unit = {},
    onStyleChange: (InstallUiStyle) -> Unit = {},
    onAutoConfirmChange: (Boolean) -> Unit = {},
    onAutoApproveChange: (Boolean) -> Unit = {},
    onAutoApproveBlockTrackersChange: (Boolean) -> Unit = {},
    onShowDownloadTabChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The card only exists in this mode, so everything about where it sits belongs to it.
    val cardIsShown = mode == ExternalOpenMode.Dialog

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.install_ui_screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // The answer to "what will this look like", shown rather than named.
            item { InstallUiPreview(mode = mode, style = style) }

            item { SectionLabel(stringResource(R.string.setting_external_open_title)) }

            items(EXTERNAL_OPEN_OPTIONS.size) { index ->
                val option = EXTERNAL_OPEN_OPTIONS[index]
                ModeRow(
                    title = stringResource(option.titleRes),
                    description = stringResource(option.descriptionRes),
                    selected = option.mode == mode,
                    onSelect = { if (option.mode != mode) onModeChange(option.mode) },
                )
            }

            // Nested, and gone entirely for the two modes that draw no card — which is what makes
            // the old contradiction with auto-confirm disappear instead of needing explaining.
            item {
                AnimatedVisibility(visible = cardIsShown) {
                    Column {
                        SectionLabel(
                            text = stringResource(R.string.install_ui_card_group),
                            indented = true,
                        )
                        CardPositionPicker(
                            current = style,
                            onChange = onStyleChange,
                        )
                        SwitchRow(
                            title = stringResource(R.string.setting_auto_confirm_title),
                            description = stringResource(R.string.install_ui_auto_confirm_sub),
                            checked = autoConfirm,
                            onCheckedChange = onAutoConfirmChange,
                            indented = true,
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    thickness = 0.5.dp,
                )
            }

            // Auto-approve section — always visible regardless of mode
            item { SectionLabel(stringResource(R.string.setting_auto_approve_title)) }

            item {
                SwitchRow(
                    title = stringResource(R.string.setting_auto_approve_title),
                    description = stringResource(R.string.setting_auto_approve_subtitle),
                    checked = autoApprove,
                    onCheckedChange = onAutoApproveChange,
                )
            }

            item {
                AnimatedVisibility(visible = autoApprove) {
                    val context = LocalContext.current
                    val subtitle = if (autoApproveCount > 0) {
                        stringResource(R.string.setting_auto_approve_count, autoApproveCount)
                    } else {
                        stringResource(R.string.setting_auto_approve_none)
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_auto_approve_apps_title)) },
                        supportingContent = { Text(subtitle) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .clickable {
                                context.startActivity(
                                    android.content.Intent(
                                        context,
                                        app.pwhs.universalinstaller.presentation.setting.autoapprove.AutoApproveActivity::class.java,
                                    )
                                )
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SwitchRow(
                        title = stringResource(R.string.setting_auto_approve_block_trackers_title),
                        description = stringResource(R.string.setting_auto_approve_block_trackers_subtitle),
                        checked = autoApproveBlockTrackers,
                        onCheckedChange = onAutoApproveBlockTrackersChange,
                    )
                }
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.setting_show_download_tab_title),
                    description = stringResource(R.string.setting_show_download_tab_subtitle),
                    checked = showDownloadTab,
                    onCheckedChange = onShowDownloadTabChange,
                )
            }
        }
    }
}

private data class ExternalOpenOption(
    val mode: ExternalOpenMode,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val EXTERNAL_OPEN_OPTIONS = listOf(
    ExternalOpenOption(
        ExternalOpenMode.Dialog,
        R.string.setting_external_open_dialog,
        R.string.setting_external_open_dialog_sub,
    ),
    ExternalOpenOption(
        ExternalOpenMode.Notification,
        R.string.setting_external_open_notification,
        R.string.setting_external_open_notification_sub,
    ),
    ExternalOpenOption(
        ExternalOpenMode.AutoNotification,
        R.string.setting_external_open_auto_notification,
        R.string.setting_external_open_auto_notification_sub,
    ),
)

@Composable
private fun SectionLabel(text: String, indented: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = if (indented) 40.dp else 24.dp,
            end = 24.dp,
            top = 16.dp,
            bottom = 8.dp,
        ),
    )
}

@Composable
private fun ModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = if (indented) 40.dp else 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


