package app.pwhs.universalinstaller.presentation.install.dialog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

private data class InstallerPreset(val packageName: String, val labelRes: Int)

private val INSTALLER_PRESETS = listOf(
    InstallerPreset("com.android.vending", R.string.setting_shizuku_installer_preset_play),
    InstallerPreset("com.aurora.store", R.string.setting_shizuku_installer_preset_aurora),
    InstallerPreset("org.fdroid.fdroid", R.string.setting_shizuku_installer_preset_fdroid),
    InstallerPreset("com.amazon.venezia", R.string.setting_shizuku_installer_preset_amazon),
    InstallerPreset("com.sec.android.app.samsungapps", R.string.setting_shizuku_installer_preset_samsung),
    InstallerPreset("com.huawei.appmarket", R.string.setting_shizuku_installer_preset_huawei),
    InstallerPreset("com.xiaomi.market", R.string.setting_shizuku_installer_preset_xiaomi),
)

@Composable
internal fun rememberInstallerLabel(packageName: String): String {
    val preset = INSTALLER_PRESETS.firstOrNull { it.packageName == packageName }
    return if (preset != null) stringResource(preset.labelRes) else packageName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstallerSourcePicker(
    installerPackageName: String,
    onInstallerChange: (String) -> Unit,
    rememberForThisApp: Boolean,
    onSetRemember: (Boolean) -> Unit,
    canRemember: Boolean,
    modifier: Modifier = Modifier,
) {
    val presets = INSTALLER_PRESETS.map { it.packageName to stringResource(it.labelRes) }

    var expanded by remember { mutableStateOf(false) }
    var text by remember(installerPackageName) { mutableStateOf(installerPackageName) }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onInstallerChange(it)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.setting_shizuku_installer_label)) },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.exposedDropdownSize(),
            ) {
                presets.forEach { (pkg, label) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            text = pkg
                            onInstallerChange(pkg)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (canRemember) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = rememberForThisApp,
                        onClick = { onSetRemember(!rememberForThisApp) },
                        role = Role.Checkbox,
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberForThisApp, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dialog_menu_remember_for_app),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.dialog_menu_remember_for_app_sub),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.dialog_menu_install_source_aa_hint, "Google Play Store"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
