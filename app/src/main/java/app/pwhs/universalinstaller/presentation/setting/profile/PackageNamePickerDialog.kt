package app.pwhs.universalinstaller.presentation.setting.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

private data class InstallerPreset(val packageName: String, val labelRes: Int)

/**
 * Dialog for choosing the "installed by" package name. Offers the common store
 * presets as tappable rows plus a free-text field for anything else. Confirming
 * returns the current text via [onConfirm].
 */
@Composable
fun PackageNamePickerDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val presets = remember {
        listOf(
            InstallerPreset("com.android.vending", R.string.setting_shizuku_installer_preset_play),
            InstallerPreset("com.aurora.store", R.string.setting_shizuku_installer_preset_aurora),
            InstallerPreset("org.fdroid.fdroid", R.string.setting_shizuku_installer_preset_fdroid),
            InstallerPreset("com.amazon.venezia", R.string.setting_shizuku_installer_preset_amazon),
            InstallerPreset("com.sec.android.app.samsungapps", R.string.setting_shizuku_installer_preset_samsung),
            InstallerPreset("com.huawei.appmarket", R.string.setting_shizuku_installer_preset_huawei),
            InstallerPreset("com.xiaomi.market", R.string.setting_shizuku_installer_preset_xiaomi),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.setting_shizuku_installer_label)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("com.android.vending") },
                )
                presets.forEach { preset ->
                    ListItem(
                        headlineContent = {
                            Text(stringResource(preset.labelRes), style = MaterialTheme.typography.bodyMedium)
                        },
                        supportingContent = {
                            Text(
                                text = preset.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { text = preset.packageName },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
    )
}
