package app.pwhs.universalinstaller.presentation.install.dialog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.util.AbiOverrideUtil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbiOverridePicker(
    apkAbis: List<String>,
    selectedAbi: String?,
    onSelectAbi: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceAbis = remember { AbiOverrideUtil.deviceSupportedAbis() }
    val options = remember(apkAbis, deviceAbis) {
        AbiOverrideUtil.selectableAbis(apkAbis = apkAbis, deviceAbis = deviceAbis)
    }
    val apkAbiSet = remember(apkAbis) { apkAbis.toSet() }
    val singleAbiDevice = remember(deviceAbis) { AbiOverrideUtil.isDeviceSingleAbi(deviceAbis) }

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedAbi == null,
                onClick = { onSelectAbi(null) },
                label = {
                    Text(
                        text = stringResource(R.string.dialog_menu_abi_override_auto),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
            options.forEach { abi ->
                val inApk = abi in apkAbiSet
                val label = if (inApk) {
                    stringResource(R.string.dialog_menu_abi_override_in_apk, abi)
                } else {
                    abi
                }
                FilterChip(
                    selected = selectedAbi == abi,
                    onClick = { onSelectAbi(if (selectedAbi == abi) null else abi) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

        val warningText = when {
            selectedAbi != null && !AbiOverrideUtil.isSupportedByDevice(selectedAbi, deviceAbis) ->
                stringResource(
                    R.string.dialog_menu_abi_override_unsupported_warning,
                    selectedAbi,
                    deviceAbis.joinToString(", "),
                )
            singleAbiDevice ->
                stringResource(
                    R.string.dialog_menu_abi_override_single_abi_warning,
                    deviceAbis.joinToString(", "),
                )
            else -> null
        }

        if (warningText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.dialog_menu_abi_override_rom_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
