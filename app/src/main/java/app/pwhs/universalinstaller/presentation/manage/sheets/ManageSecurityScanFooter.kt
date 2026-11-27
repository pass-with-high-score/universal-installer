package app.pwhs.universalinstaller.presentation.manage.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.model.Verdict
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.install.dialog.ReportFalsePositiveDialog

@Composable
internal fun ManageSecurityScanFooter(
    app: InstalledApp,
    sha256: String,
    vtResult: VtResult?,
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showReportDialog by remember { mutableStateOf(false) }
    val status = vtResult?.status
    val hasVerdict = status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)
    val isCleanVerdict = status == VtStatus.CLEAN && vtResult?.report?.verdict != Verdict.RISKWARE

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasVerdict && sha256.isNotBlank()) {
            OutlinedButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (isCleanVerdict) R.string.fn_report_button_title
                        else R.string.fp_dispute_button_title,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.find_auto_rescan))
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.virustotal_detail_close))
            }
        }
    }

    if (showReportDialog && vtResult != null) {
        ReportFalsePositiveDialog(
            sha256 = sha256,
            packageName = app.packageName,
            appName = app.appName,
            isCleanVerdict = isCleanVerdict,
            verdictLabel = vtResult.report?.verdict?.name ?: vtResult.status.name,
            riskScore = vtResult.report?.riskScore,
            onDismiss = { showReportDialog = false },
        )
    }
}
