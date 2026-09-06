package app.pwhs.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.tv.R
import app.pwhs.tv.install.TvInstallBackend
import app.pwhs.tv.install.TvShizuku
import kotlinx.coroutines.flow.map
import rikka.shizuku.Shizuku

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvInstallerModeBadge(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val shizukuEnabled by remember(context) {
        context.dataStore.data.map { it[TvShizuku.enabledKey] ?: false }
    }.collectAsState(initial = false)

    val rootSilentEnabled by remember(context) {
        context.dataStore.data.map { it[SharedPrefsKeys.ROOT_SILENT_INSTALL] ?: true }
    }.collectAsState(initial = true)

    var shizukuReady by remember { mutableStateOf(TvShizuku.status(context) == TvShizuku.Status.Ready) }
    var rootAvailable by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, context) {
        fun refresh() {
            shizukuReady = TvShizuku.status(context) == TvShizuku.Status.Ready
        }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycle.addObserver(observer)
        refresh()
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching {
                Shizuku.removeBinderReceivedListener(received)
                Shizuku.removeBinderDeadListener(dead)
            }
        }
    }

    LaunchedEffect(Unit) {
        rootAvailable = RootShell.isAvailable()
    }

    val currentMode = remember(shizukuEnabled, shizukuReady, rootSilentEnabled, rootAvailable) {
        when {
            shizukuEnabled && shizukuReady -> TvInstallBackend.Shizuku
            rootSilentEnabled && rootAvailable -> TvInstallBackend.Root
            else -> TvInstallBackend.System
        }
    }

    val (icon, labelRes) = when (currentMode) {
        TvInstallBackend.Shizuku -> Icons.Rounded.AdminPanelSettings to R.string.installer_mode_shizuku
        TvInstallBackend.Root -> Icons.Rounded.Key to R.string.installer_mode_root
        TvInstallBackend.System -> Icons.Rounded.Android to R.string.installer_mode_package_installer
    }

    val isPrivileged = currentMode != TvInstallBackend.System
    val containerColor = if (isPrivileged) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = if (isPrivileged) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (isPrivileged) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.border.copy(alpha = 0.25f)
    }

    val label = stringResource(labelRes)
    val text = stringResource(R.string.installer_mode_using, label)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
