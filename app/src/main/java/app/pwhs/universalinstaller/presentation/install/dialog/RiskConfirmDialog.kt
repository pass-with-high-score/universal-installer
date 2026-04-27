package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus

/**
 * A risk the user must explicitly confirm before installing. We surface only items
 * the user can actually act on — VT status flags get filtered to MALICIOUS/SUSPICIOUS;
 * informational states (CLEAN, SCANNING, NOT_FOUND, etc.) don't trigger this gate.
 */
sealed interface InstallRisk {
    /** The APK's versionCode is lower than the installed package's. */
    data class Downgrade(
        val installedVersionName: String,
        val newVersionName: String,
    ) : InstallRisk

    /** VirusTotal flagged the APK as malicious — N engines reported a threat. */
    data class VtMalicious(val engineCount: Int) : InstallRisk

    /** VirusTotal flagged the APK as suspicious. */
    data class VtSuspicious(val engineCount: Int) : InstallRisk
}

fun detectInstallRisks(apkInfo: ApkInfo): List<InstallRisk> {
    val risks = mutableListOf<InstallRisk>()
    val installedCode = apkInfo.installedVersionCode
    if (installedCode != null && installedCode > 0 && apkInfo.versionCode < installedCode) {
        risks += InstallRisk.Downgrade(
            installedVersionName = apkInfo.installedVersionName.orEmpty().ifBlank { "?" },
            newVersionName = apkInfo.versionName.ifBlank { "?" },
        )
    }
    when (apkInfo.vtResult?.status) {
        VtStatus.MALICIOUS -> risks += InstallRisk.VtMalicious(apkInfo.vtResult.malicious)
        VtStatus.SUSPICIOUS -> risks += InstallRisk.VtSuspicious(apkInfo.vtResult.suspicious)
        else -> Unit
    }
    return risks
}

@Composable
fun RiskConfirmDialog(
    risks: List<InstallRisk>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (risks.isEmpty()) return
    val severe = risks.any { it is InstallRisk.VtMalicious }
    val titleRes = if (severe) R.string.dialog_risk_title_severe else R.string.dialog_risk_title_warn
    val proceedRes = if (severe) R.string.dialog_risk_proceed_severe else R.string.dialog_risk_proceed_warn

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = if (severe) Icons.Rounded.Security else Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringResource(titleRes),
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                risks.forEach { risk -> RiskRow(risk) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dialog_risk_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(proceedRes),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel_btn))
            }
        },
    )
}

@Composable
private fun RiskRow(risk: InstallRisk) {
    val (icon: ImageVector, line: String) = when (risk) {
        is InstallRisk.Downgrade -> Icons.Rounded.Warning to
            stringResource(R.string.dialog_risk_downgrade, risk.installedVersionName, risk.newVersionName)
        is InstallRisk.VtMalicious -> Icons.Rounded.Security to
            stringResource(R.string.dialog_risk_vt_malicious, risk.engineCount)
        is InstallRisk.VtSuspicious -> Icons.Rounded.Warning to
            stringResource(R.string.dialog_risk_vt_suspicious, risk.engineCount)
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
