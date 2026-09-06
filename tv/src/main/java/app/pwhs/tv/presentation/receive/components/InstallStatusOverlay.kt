package app.pwhs.tv.presentation.receive.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.presentation.receive.ReceiveViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InstallStatusOverlay(
    installingLabel: String?,
    progress: Float?,
    result: ReceiveViewModel.InstallOutcome?,
    deleteOutcome: ReceiveViewModel.DeleteOutcome?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Success auto-dismisses; a failure lingers so the Retry/Dismiss action stays reachable.
    LaunchedEffect(result) {
        if (result is ReceiveViewModel.InstallOutcome.Success) {
            delay(3000)
            onDismiss()
        }
    }
    LaunchedEffect(deleteOutcome) {
        if (deleteOutcome is ReceiveViewModel.DeleteOutcome.Success) {
            delay(3000)
            onDismissDelete()
        }
    }

    val visible = installingLabel != null || result != null || deleteOutcome != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(20.dp)
        val isError = (result is ReceiveViewModel.InstallOutcome.Failure) || (deleteOutcome is ReceiveViewModel.DeleteOutcome.Failure)
        val (container, content) = when {
            installingLabel != null -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
            isError -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        Surface(
            modifier = Modifier.widthIn(min = 360.dp, max = 720.dp),
            shape = shape,
            colors = SurfaceDefaults.colors(containerColor = container, contentColor = content)
        ) {
            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                when {
                    installingLabel != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.tv_receive_installing, installingLabel),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (progress != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (progress != null) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = content.copy(alpha = 0.2f)
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = content.copy(alpha = 0.2f)
                            )
                        }
                    }
                    deleteOutcome != null -> {
                        when (deleteOutcome) {
                            is ReceiveViewModel.DeleteOutcome.Success -> {
                                Text(
                                    stringResource(R.string.tv_receive_deleted_success, deleteOutcome.label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            is ReceiveViewModel.DeleteOutcome.Failure -> {
                                Text(
                                    stringResource(R.string.tv_receive_delete_failed, deleteOutcome.label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(16.dp))
                                val dismissFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { runCatching { dismissFocus.requestFocus() } }
                                Button(
                                    onClick = onDismissDelete,
                                    shape = ButtonDefaults.shape(CircleShape),
                                    modifier = Modifier.clip(CircleShape).focusRequester(dismissFocus),
                                    colors = ButtonDefaults.colors(containerColor = content.copy(alpha = 0.15f), contentColor = content)
                                ) { Text(stringResource(R.string.tv_receive_dismiss)) }
                            }
                        }
                    }
                    result is ReceiveViewModel.InstallOutcome.Success -> {
                        Text(
                            stringResource(
                                if (result.silent) R.string.tv_receive_installed_silent
                                else R.string.tv_receive_installed_success,
                                result.label
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    result is ReceiveViewModel.InstallOutcome.Failure -> {
                        Text(
                            stringResource(R.string.tv_receive_failed, result.message),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(16.dp))
                        val retryFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val btnShape = CircleShape
                            Button(
                                onClick = onRetry,
                                shape = ButtonDefaults.shape(btnShape),
                                modifier = Modifier.clip(btnShape).focusRequester(retryFocus)
                            ) { Text(stringResource(R.string.tv_receive_retry)) }
                            Button(
                                onClick = onDismiss,
                                shape = ButtonDefaults.shape(btnShape),
                                modifier = Modifier.clip(btnShape),
                                colors = ButtonDefaults.colors(containerColor = content.copy(alpha = 0.15f), contentColor = content)
                            ) { Text(stringResource(R.string.tv_receive_dismiss)) }
                        }
                    }
                }
            }
        }
    }
}
