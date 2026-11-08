package app.pwhs.universalinstaller.presentation.pro.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.billing.BillingPurchaseResult

@Composable
fun ProIssueDialog(
    result: BillingPurchaseResult,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onUsePersonalKey: () -> Unit,
    onContactSupport: () -> Unit,
) {
    when (result) {
        is BillingPurchaseResult.UserCanceled -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pro_issue_canceled_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.pro_issue_canceled_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = onUsePersonalKey,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Key, null)
                            Spacer(Modifier.height(0.dp))
                            Text(
                                text = stringResource(R.string.pro_issue_action_vt_key),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedButton(
                            onClick = onContactSupport,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.SupportAgent, null)
                            Text(
                                text = stringResource(R.string.pro_issue_action_contact),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.pro_issue_action_dismiss))
                    }
                },
            )
        }

        is BillingPurchaseResult.Error -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pro_issue_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(
                                R.string.pro_issue_error_desc,
                                result.message.ifBlank { "Google Play Service Unavailable" },
                                result.code,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onContactSupport,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.SupportAgent, null)
                            Text(
                                text = stringResource(R.string.pro_issue_action_contact),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.pro_issue_action_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.pro_issue_action_dismiss))
                    }
                },
            )
        }

        is BillingPurchaseResult.Pending -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pro_issue_pending_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.pro_issue_pending_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(onClick = onDismissRequest) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }

        is BillingPurchaseResult.AlreadyOwned -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pro_issue_already_owned_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.pro_issue_already_owned_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(onClick = onDismissRequest) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }

        BillingPurchaseResult.Success -> {
            // Success is rendered by ProCelebrationContent
        }
    }
}
