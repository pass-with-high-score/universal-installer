package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import app.pwhs.universalinstaller.presentation.install.InstallErrorHelper
import app.pwhs.universalinstaller.presentation.setting.SettingActivity
import kotlinx.coroutines.launch

/**
 * Terminal state when the install session finished in error.
 */
@Composable
fun DialogFailedContent(
    target: DialogTarget?,
    errorMessage: String,
    onClose: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onFallbackInstall: (() -> Unit)? = null,
    uiStyle: InstallUiStyle = InstallUiStyle.Dialog,
    onChangeEngine: (() -> Unit)? = null,
    onOpenAdvanced: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedToast = stringResource(R.string.dialog_failed_copied)
    var showSupportSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.dialog_failed_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )

        if (target != null && target.appName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = target.appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    scope.launch {
                        val clip = ClipData.newPlainText("install-error", errorMessage)
                        clipboard.setClipEntry(ClipEntry(clip))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.dialog_failed_copy_error))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action: Change installer engine via bottomsheet
        if (onChangeEngine != null) {
            FilledTonalButton(
                onClick = onChangeEngine,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_change_installer_engine),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action: Advanced Settings (for Dialog) or App Details (for Sheet)
        if (uiStyle == InstallUiStyle.Dialog && onOpenAdvanced != null) {
            OutlinedButton(
                onClick = onOpenAdvanced,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_open_advanced_settings),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else if (uiStyle == InstallUiStyle.Sheet && onOpenDetails != null) {
            OutlinedButton(
                onClick = onOpenDetails,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_menu_details),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val isGhostMap = errorMessage.contains("GhostMap", ignoreCase = true)

        if (!isGhostMap && InstallErrorHelper.shouldSuggestShizuku(errorMessage)) {
            FilledTonalButton(
                onClick = {
                    context.startActivity(Intent(context, SettingActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.install_error_suggest_shizuku_action),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!isGhostMap && onFallbackInstall != null) {
            Button(
                onClick = onFallbackInstall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dialog_failed_fallback_install, "Install via System Installer"))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action: Get Support
        OutlinedButton(
            onClick = { showSupportSheet = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SupportAgent,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dialog_failed_get_support),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (onRetry != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.dialog_failed_close))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dialog_failed_retry))
                }
            }
        } else {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dialog_failed_close))
            }
        }
    }

    if (showSupportSheet) {
        InstallSupportBottomSheet(
            target = target,
            errorMessage = errorMessage,
            onDismiss = { showSupportSheet = false },
        )
    }
}
