package app.pwhs.tv.presentation.install

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.presentation.receive.components.ApkPermissionsPane

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDialogInstallContent(
    state: TvInstallState,
    onInstall: () -> Unit,
    onTogglePermissions: () -> Unit,
    onDismiss: () -> Unit,
    onOpenApp: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 520.dp, max = 660.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "dialogStateTransition"
            ) { currentState ->
                when (currentState) {
                    is TvInstallState.Loading -> LoadingView()
                    is TvInstallState.ParseError -> ParseErrorView(
                        message = currentState.message,
                        onDismiss = onDismiss
                    )
                    is TvInstallState.Ready -> {
                        if (currentState.showPermissions) {
                            ApkPermissionsPane(
                                name = currentState.metadata.appName,
                                permissions = currentState.metadata.permissions,
                                isInstalling = false,
                                icon = currentState.metadata.icon?.asImageBitmap(),
                                onInstall = onInstall,
                                onBack = onTogglePermissions
                            )
                        } else {
                            ReadyView(
                                ready = currentState,
                                onInstall = onInstall,
                                onTogglePermissions = onTogglePermissions,
                                onDismiss = onDismiss
                            )
                        }
                    }
                    is TvInstallState.Installing -> InstallingView(
                        appName = currentState.appName,
                        progress = currentState.progress
                    )
                    is TvInstallState.Success -> SuccessView(
                        appName = currentState.appName,
                        packageName = currentState.packageName,
                        onOpenApp = onOpenApp,
                        onDismiss = onDismiss
                    )
                    is TvInstallState.Failure -> FailureView(
                        appName = currentState.appName,
                        error = currentState.error,
                        onRetry = onRetry,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.tv_dialog_parsing_apk),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ParseErrorView(
    message: String,
    onDismiss: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.tv_dialog_parse_error),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.focusRequester(dismissFocus)
        ) {
            Text(stringResource(R.string.tv_dialog_btn_cancel))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InstallingView(
    appName: String,
    progress: Float?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.tv_receive_installing, appName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
