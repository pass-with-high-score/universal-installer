package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import kotlinx.coroutines.launch

@Composable
private fun TargetIcon(iconPath: String?, sizeDp: Int = 64) {
    // Decode is served from DialogIconCache so re-opening the dialog for the same
    // APK (e.g. Retry) doesn't re-decode on the main thread.
    val bitmap = remember(iconPath) { DialogIconCache.get(iconPath) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Android,
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
    }
}

/**
 * Installing stage — progress bar + app identity.
 *
 * Note: the dialog stays on this screen as long as the session lives. If the user
 * dismisses the dialog (back/outside-tap), the install continues in the background;
 * progress is then visible only via the system notification.
 */
@Composable
fun DialogInstallingContent(
    target: DialogTarget,
    progressFraction: Float?,
    onBackground: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TargetIcon(target.iconPath)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = target.appName.ifBlank { target.packageName },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.dialog_installing_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (progressFraction != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction.coerceIn(0f, 1f),
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "InstallProgress",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onBackground,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.dialog_installing_background))
        }
    }
}

/**
 * Success stage — checkmark + app identity + Open / Done buttons.
 *
 * @param canOpen whether the package has a launchable activity. When false, the
 * Open button is hidden (services / library packages have no MAIN/LAUNCHER intent).
 * @param autoOpenCountdownStartSeconds when non-null AND [canOpen] is true, a
 * countdown starts on first composition; reaching zero invokes [onOpen]. The user
 * can cancel by tapping Done (which transitions out of this stage and disposes
 * the LaunchedEffect) or short-circuit by tapping Open. Pass null to disable.
 */
@Composable
fun DialogSuccessContent(
    target: DialogTarget,
    canOpen: Boolean,
    autoOpenCountdownStartSeconds: Int?,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = DialogMotion.GenericSpring,
        label = "successIconScale",
    )

    // Countdown ticks down from the start value on a 1-second cadence. We only
    // arm it when the package is actually launchable — otherwise the auto-open
    // would silently fail and the user would just see the dialog dismiss.
    val countdownActive = canOpen && autoOpenCountdownStartSeconds != null
    var remaining by remember(autoOpenCountdownStartSeconds, canOpen) {
        mutableStateOf(autoOpenCountdownStartSeconds.takeIf { countdownActive } ?: 0)
    }
    if (countdownActive) {
        LaunchedEffect(autoOpenCountdownStartSeconds) {
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining -= 1
            }
            onOpen()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.dialog_success_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = target.appName.ifBlank { target.packageName },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.dialog_success_done))
            }

            if (canOpen) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                ) {
                    val openLabel = stringResource(R.string.dialog_success_open)
                    val label = if (countdownActive && remaining > 0) {
                        "$openLabel ($remaining)"
                    } else {
                        openLabel
                    }
                    Text(label)
                }
            }
        }
    }
}

/**
 * Failed stage — error icon + scrollable message + actionable buttons.
 *
 * [onRetry] is invoked when the user wants to attempt the install again with the
 * same parameters. Null disables the Retry button — useful for terminal failures
 * (signature conflict, etc.) where retrying without user intervention can't
 * succeed.
 */
@Composable
fun DialogFailedContent(
    target: DialogTarget?,
    errorMessage: String,
    onClose: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val copiedToast = stringResource(R.string.dialog_failed_copied)

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
            // "Copy error" — secondary affordance. Always available when there's a message,
            // so users have something useful to share with support / file a bug.
            androidx.compose.material3.TextButton(
                onClick = {
                    scope.launch {
                        val clip = android.content.ClipData.newPlainText("install-error", errorMessage)
                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clip))
                        android.widget.Toast.makeText(context, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.dialog_failed_copy_error))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (onRetry != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.dialog_failed_close))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
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
}
