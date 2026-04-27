package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

/**
 * Stage 5: Result — success or failure with action buttons.
 */
@Composable
fun DialogResultContent(
    isSuccess: Boolean,
    errorMessage: String = "",
    appName: String = "",
    onOpen: (() -> Unit)? = null,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Icon ──
        Icon(
            imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = if (isSuccess) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Title ──
        Text(
            text = if (isSuccess) stringResource(R.string.dialog_success_title)
            else stringResource(R.string.dialog_failed_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSuccess) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.error,
        )

        if (appName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Error Message ──
        if (!isSuccess && errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Buttons ──
        if (isSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onOpen != null) {
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dialog_success_open))
                    }
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dialog_success_done))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dialog_failed_retry))
                    }
                }
                FilledTonalButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dialog_failed_close))
                }
            }
        }
    }
}
