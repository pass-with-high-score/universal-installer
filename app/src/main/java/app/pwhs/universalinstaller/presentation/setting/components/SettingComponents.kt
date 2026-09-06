package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import android.widget.Toast
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.DirectionsCar
import app.pwhs.universalinstaller.util.AndroidAutoCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.composable.UniversalSearchBar
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.util.AppIconData
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.presentation.setting.profile.PackageNamePickerDialog
import androidx.datastore.preferences.core.Preferences
import org.koin.androidx.compose.koinViewModel

internal fun matchesQuery(query: String, haystacks: List<String>): Boolean =
    query.isBlank() || haystacks.any { it.contains(query, ignoreCase = true) }

/**
 * Renders [content] only when the search [query] is blank or matches [label] / any of the
 * extra space-joined [keywords]. Lets individual rows hide while their section stays
 * visible (section-level gates decide whether the section appears at all).
 */
@Composable
internal fun SearchableItem(
    query: String,
    label: String,
    keywords: String = "",
    content: @Composable () -> Unit,
) {
    if (query.isBlank() ||
        label.contains(query, ignoreCase = true) ||
        keywords.contains(query, ignoreCase = true)
    ) {
        content()
    }
}

@Composable
internal fun OptionGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
internal fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
internal fun InstallSourceItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    installerPackageName: String,
    onToggle: (Boolean) -> Unit,
    onInstallerChange: (String) -> Unit,
) {
    Column {
        OptionSwitch(
            title = title,
            subtitle = subtitle,
            checked = enabled,
            onCheckedChange = onToggle,
        )
        if (enabled) {
            var showDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_shizuku_installer_label), style = MaterialTheme.typography.bodyMedium) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(installerPackageName, style = MaterialTheme.typography.bodySmall)
                        if (installerPackageName == "com.android.vending") {
                            Text(
                                text = stringResource(R.string.dialog_menu_install_source_aa_hint, "Google Play Store"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                leadingContent = { Spacer(Modifier.width(32.dp)) },
                modifier = Modifier.clickable { showDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            if (showDialog) {
                PackageNamePickerDialog(
                    initialValue = installerPackageName,
                    onDismiss = { showDialog = false },
                    onConfirm = { newPkg ->
                        onInstallerChange(newPkg)
                        showDialog = false
                    }
                )
            }
        }
    }
}




/**
 * Normal vs Strict, as a segmented pair rather than a switch.
 *
 * A switch labelled "strict check" said nothing about what normal was, and the pushy Scan button
 * was on regardless — including for the many users with no API key at all. Two named levels make
 * the trade explicit and let Normal actually mean "stay out of the way".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SecurityLevelSelector(
    current: SecurityLevel,
    onChange: (SecurityLevel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.setting_security_level_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SecurityLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = level == current,
                    onClick = { if (level != current) onChange(level) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SecurityLevel.entries.size,
                    ),
                    label = {
                        Text(
                            when (level) {
                                SecurityLevel.Normal -> stringResource(R.string.setting_security_normal)
                                SecurityLevel.Strict -> stringResource(R.string.setting_security_strict)
                            }
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (current) {
                SecurityLevel.Normal -> stringResource(R.string.setting_security_normal_sub)
                SecurityLevel.Strict -> stringResource(R.string.setting_security_strict_sub)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}






