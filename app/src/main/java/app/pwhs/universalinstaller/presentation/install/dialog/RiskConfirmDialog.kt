package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.util.SignatureCheck
import kotlinx.coroutines.launch

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

    /**
     * The installed app is signed with a different key. Unlike the others this one cannot be
     * accepted and pushed through — Android will refuse the install until the old copy is gone.
     */
    data class SignatureMismatch(val packageName: String) : InstallRisk

    /** VirusTotal flagged the APK as malicious — N engines reported a threat. */
    data class VtMalicious(val engineCount: Int, val sha256: String = "") : InstallRisk

    /** VirusTotal flagged the APK as suspicious. */
    data class VtSuspicious(val engineCount: Int, val sha256: String = "") : InstallRisk

    /** Strict mode: APK was not scanned by VirusTotal. */
    data object VtUnscanned : InstallRisk

    /** Exodus Privacy detected trackers in the APK. */
    data class TrackersDetected(val count: Int) : InstallRisk
}

/**
 * True when installing [apkInfo] would replace a newer already-installed version.
 *
 * Shared with the install path on purpose: whether we *warn* about a downgrade and whether we
 * *allow* one must be the same question, or the app ends up asking for consent it then ignores.
 */
fun isDowngrade(apkInfo: ApkInfo): Boolean {
    val installedCode = apkInfo.installedVersionCode ?: return false
    return installedCode > 0 && apkInfo.versionCode < installedCode
}

fun detectInstallRisks(
    apkInfo: ApkInfo,
    strictVirusTotal: Boolean = false,
    blockOnTrackers: Boolean = false,
): List<InstallRisk> {
    val risks = mutableListOf<InstallRisk>()
    if (isDowngrade(apkInfo)) {
        risks += InstallRisk.Downgrade(
            installedVersionName = apkInfo.installedVersionName.orEmpty().ifBlank { "?" },
            newVersionName = apkInfo.versionName.ifBlank { "?" },
        )
    }
    // Only `true` counts. `null` means the check couldn't run and must not raise an alarm.
    if (apkInfo.signatureMismatch == true) {
        risks += InstallRisk.SignatureMismatch(apkInfo.packageName)
    }
    if (blockOnTrackers && apkInfo.trackers.isNotEmpty()) {
        risks += InstallRisk.TrackersDetected(apkInfo.trackers.size)
    }
    when (val status = apkInfo.vtResult?.status) {
        VtStatus.MALICIOUS -> risks += InstallRisk.VtMalicious(apkInfo.vtResult.malicious, apkInfo.sha256)
        VtStatus.SUSPICIOUS -> risks += InstallRisk.VtSuspicious(apkInfo.vtResult.suspicious, apkInfo.sha256)
        VtStatus.CLEAN -> Unit
        null -> if (strictVirusTotal) risks += InstallRisk.VtUnscanned
        else -> if (strictVirusTotal) risks += InstallRisk.VtUnscanned
    }
    return risks
}

@Composable
fun RiskConfirmDialog(
    risks: List<InstallRisk>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    /** The conflicting app was actually removed — the caller should drop the risks it caused. */
    onExistingAppUninstalled: () -> Unit = {},
    /**
     * Remove the conflicting app through the backend this install will use. Returns false when no
     * privileged backend is active, in which case we fall back to the system uninstaller.
     */
    onPrivilegedUninstall: (suspend (String) -> Boolean)? = null,
) {
    if (risks.isEmpty()) return
    val severe = risks.any { it is InstallRisk.VtMalicious }
    val titleRes = if (severe) R.string.dialog_risk_title_severe else R.string.dialog_risk_title_warn
    val proceedRes = if (severe) R.string.dialog_risk_proceed_severe else R.string.dialog_risk_proceed_warn

    AlertDialog(
        onDismissRequest = onCancel,
        // This dialog opens on top of the install dialog, which is dark and rounded too. Without
        // a distinct container the two overlapping surfaces read as one notched shape.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed: the list is mutated at runtime (resolving the mismatch removes entries),
                // and without an identity Compose would reuse a card's slot — and its remembered
                // activity-result launcher — for a different risk.
                risks.forEach { risk ->
                    key(risk) { RiskCard(risk, onExistingAppUninstalled, onPrivilegedUninstall) }
                }
                Text(
                    text = stringResource(R.string.dialog_risk_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                )
            }
        },
        // Filled for the action that carries the risk, outlined for the safe one: two flat text
        // buttons gave equal visual weight to "back out" and "do it anyway".
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(text = stringResource(proceedRes), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel_btn))
            }
        },
    )
}

/**
 * One risk, one card, with its remedy inside the same surface as the problem it solves.
 *
 * The remedy used to be a bare link under the whole list, which read as unattached to any
 * particular line — with two risks showing it was ambiguous which one it applied to.
 */
@Composable
private fun RiskCard(
    risk: InstallRisk,
    onExistingAppUninstalled: () -> Unit,
    onPrivilegedUninstall: (suspend (String) -> Boolean)?,
) {
    val severe = risk is InstallRisk.VtMalicious
    val (icon: ImageVector, line: String) = when (risk) {
        is InstallRisk.Downgrade -> Icons.Rounded.Warning to
            stringResource(R.string.dialog_risk_downgrade, risk.installedVersionName, risk.newVersionName)
        is InstallRisk.SignatureMismatch -> Icons.Rounded.Key to
            stringResource(R.string.dialog_risk_signature_mismatch)
        is InstallRisk.VtMalicious -> Icons.Rounded.Security to
            stringResource(R.string.dialog_risk_vt_malicious, risk.engineCount)
        is InstallRisk.VtSuspicious -> Icons.Rounded.Warning to
            stringResource(R.string.dialog_risk_vt_suspicious, risk.engineCount)
        is InstallRisk.VtUnscanned -> Icons.Rounded.Warning to
            stringResource(R.string.dialog_risk_vt_unscanned)
        is InstallRisk.TrackersDetected -> Icons.Rounded.Shield to
            stringResource(R.string.dialog_risk_trackers_detected, risk.count)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        // Tinted with the error colour rather than a neutral surface: on a dark theme a plain
        // surfaceContainer is indistinguishable from the dialog behind it and the card vanishes.
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (severe) 1f else 0.30f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            RiskAction(risk, onExistingAppUninstalled, onPrivilegedUninstall)
        }
    }
}

/**
 * The way out of this particular risk, when there is one. Indented to line up with the risk text
 * above it (icon 20dp + gap 10dp, less the button's own 8dp content padding).
 *
 * Resolved from LocalContext here rather than hoisted, so both InstallScreen and
 * DialogInstallActivity get the actions without either having to pass anything in.
 */
@Composable
private fun RiskAction(
    risk: InstallRisk,
    onExistingAppUninstalled: () -> Unit,
    onPrivilegedUninstall: (suspend (String) -> Boolean)?,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The result code is not dependable here — some OEM uninstallers report RESULT_CANCELED
        // even on success, and the user can also uninstall and then back out. Ask PackageManager
        // what actually happened instead of trusting the code.
        if (risk is InstallRisk.SignatureMismatch &&
            !SignatureCheck.isInstalled(context, risk.packageName)
        ) {
            onExistingAppUninstalled()
        }
    }
    val report = { sha: String -> uriHandler.openUri("$VT_FILE_REPORT_URL$sha") }

    val label: Int
    val actionIcon: ImageVector
    val onClick: () -> Unit
    when (risk) {
        is InstallRisk.SignatureMismatch -> {
            label = R.string.dialog_risk_action_uninstall_existing
            actionIcon = Icons.Rounded.DeleteOutline
            onClick = {
                // ACTION_DELETE shows the platform's own uninstall confirmation — we are not
                // removing anyone's app behind their back, and it needs no extra permission.
                runCatching {
                    uninstallLauncher.launch(
                        Intent(Intent.ACTION_DELETE, "package:${risk.packageName}".toUri())
                    )
                }
                Unit
            }
        }
        is InstallRisk.VtMalicious -> {
            if (risk.sha256.isBlank()) return
            label = R.string.dialog_risk_action_view_report
            actionIcon = Icons.AutoMirrored.Rounded.OpenInNew
            onClick = { report(risk.sha256) }
        }
        is InstallRisk.VtSuspicious -> {
            if (risk.sha256.isBlank()) return
            label = R.string.dialog_risk_action_view_report
            actionIcon = Icons.AutoMirrored.Rounded.OpenInNew
            onClick = { report(risk.sha256) }
        }
        // Downgrade is consented to right here and carried into the session; unscanned has no
        // action beyond running the scan, which the install screen already offers.
        is InstallRisk.Downgrade, InstallRisk.VtUnscanned, is InstallRisk.TrackersDetected -> return
    }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 22.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

private const val VT_FILE_REPORT_URL = "https://www.virustotal.com/gui/file/"
