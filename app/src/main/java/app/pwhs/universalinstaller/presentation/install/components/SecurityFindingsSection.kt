package app.pwhs.universalinstaller.presentation.install.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.domain.model.Finding
import app.pwhs.core.domain.model.ScanReport
import app.pwhs.core.domain.model.SeverityLevel
import app.pwhs.core.domain.model.Verdict
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

@Composable
fun SecurityScoreHeader(
    report: ScanReport,
    modifier: Modifier = Modifier,
) {
    val extendedColors = LocalExtendedColors.current
    val (statusColor, containerColor, verdictLabel, desc, icon) = when (report.verdict) {
        Verdict.SAFE -> Quintuple(
            extendedColors.success,
            extendedColors.success.copy(alpha = 0.12f),
            stringResource(R.string.verdict_safe),
            stringResource(R.string.verdict_safe_desc),
            Icons.Rounded.GppGood,
        )
        Verdict.RISKWARE -> Quintuple(
            extendedColors.warning,
            extendedColors.warningContainer.copy(alpha = 0.5f),
            stringResource(R.string.verdict_riskware),
            stringResource(R.string.verdict_riskware_desc),
            Icons.Rounded.Warning,
        )
        Verdict.SUSPICIOUS -> Quintuple(
            extendedColors.warning,
            extendedColors.warningContainer,
            stringResource(R.string.verdict_suspicious),
            stringResource(R.string.verdict_suspicious_desc),
            Icons.Rounded.Warning,
        )
        Verdict.MALICIOUS -> Quintuple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            stringResource(R.string.verdict_malicious),
            stringResource(R.string.verdict_malicious_desc),
            Icons.Rounded.Dangerous,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = verdictLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${report.riskScore}/100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SecurityFindingCard(
    finding: Finding,
    modifier: Modifier = Modifier,
) {
    val extendedColors = LocalExtendedColors.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeText) = when (finding.severity) {
        SeverityLevel.CRITICAL -> MaterialTheme.colorScheme.error to stringResource(R.string.severity_critical)
        SeverityLevel.HIGH -> MaterialTheme.colorScheme.error to stringResource(R.string.severity_high)
        SeverityLevel.MEDIUM -> extendedColors.warning to stringResource(R.string.severity_medium)
        SeverityLevel.LOW -> extendedColors.warning to stringResource(R.string.severity_low)
        SeverityLevel.INFO -> MaterialTheme.colorScheme.primary to stringResource(R.string.severity_info)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = finding.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val evidence = finding.evidence
            if (!evidence.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.finding_evidence_header),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = evidence,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService<ClipboardManager>()
                                cm?.setPrimaryClip(ClipData.newPlainText("Evidence", evidence))
                                Toast.makeText(context, context.getString(R.string.history_detail_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.finding_copy_evidence_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityFindingsSection(
    report: ScanReport,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecurityScoreHeader(report = report)

        if (report.findings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.findings_section_title, report.findings.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Render findings directly without nested LazyColumn so parent sheet scrolls smoothly
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                report.findings.forEach { finding ->
                    SecurityFindingCard(finding = finding)
                }
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)
