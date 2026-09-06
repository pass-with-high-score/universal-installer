package app.pwhs.tv.presentation.manage.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.presentation.manage.ActionResult
import app.pwhs.tv.presentation.manage.ExtractState
import kotlinx.coroutines.delay

/** A destructive Manage action awaiting confirmation (silent root path has no system dialog). */
sealed interface ConfirmAction {
    val app: InstalledApp
    data class Uninstall(override val app: InstalledApp) : ConfirmAction
    data class ClearData(override val app: InstalledApp) : ConfirmAction
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConfirmDialog(
    action: ConfirmAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (action) {
        is ConfirmAction.Uninstall -> stringResource(R.string.tv_manage_confirm_uninstall_title, action.app.appName)
        is ConfirmAction.ClearData -> stringResource(R.string.tv_manage_confirm_clear_title, action.app.appName)
    }
    // Default focus on Cancel so a stray center-press never fires the destructive action.
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.padding(32.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.tv_manage_confirm_irreversible),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val btnShape = RoundedCornerShape(14.dp)
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(stringResource(R.string.tv_manage_confirm_yes), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).clip(btnShape).focusRequester(cancelFocus),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) { Text(stringResource(R.string.tv_manage_confirm_cancel), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageStatusOverlay(
    actionResult: ActionResult?,
    extractState: ExtractState,
    onDismissAction: () -> Unit,
    onDismissExtract: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Transient successes fade themselves; errors linger with a focusable Dismiss.
    LaunchedEffect(actionResult) {
        if (actionResult != null && !actionResult.isError) {
            delay(2500)
            onDismissAction()
        }
    }
    LaunchedEffect(extractState) {
        when (extractState) {
            is ExtractState.Done -> { delay(3000); onDismissExtract() }
            is ExtractState.Error -> { delay(5000); onDismissExtract() }
            else -> {}
        }
    }

    val visible = actionResult != null || extractState !is ExtractState.Idle
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val isError: Boolean
        val message: String
        if (actionResult != null) {
            isError = actionResult.isError
            message = actionResult.message
        } else {
            when (val e = extractState) {
                is ExtractState.Running -> {
                    isError = false
                    val pct = if (e.totalBytes > 0) (e.bytesCopied * 100 / e.totalBytes).toInt() else 0
                    message = stringResource(R.string.tv_manage_extracting, pct)
                }
                is ExtractState.Done -> { isError = false; message = stringResource(R.string.tv_manage_extracted_success) }
                is ExtractState.Error -> { isError = true; message = stringResource(R.string.tv_manage_extract_error_prefix, e.message) }
                else -> { isError = false; message = "" }
            }
        }
        val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            modifier = Modifier.widthIn(min = 360.dp, max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = container, contentColor = content),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isError) {
                    Spacer(Modifier.width(20.dp))
                    val btnShape = CircleShape
                    Button(
                        onClick = onErrorDismiss,
                        shape = ButtonDefaults.shape(btnShape),
                        modifier = Modifier.clip(btnShape),
                        colors = ButtonDefaults.colors(containerColor = content.copy(alpha = 0.15f), contentColor = content),
                    ) { Text(stringResource(R.string.tv_receive_dismiss)) }
                }
            }
        }
    }
}
