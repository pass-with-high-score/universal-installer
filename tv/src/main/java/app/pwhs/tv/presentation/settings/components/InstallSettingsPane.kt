package app.pwhs.tv.presentation.settings.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.tv.R
import app.pwhs.tv.install.TvShizuku
import app.pwhs.tv.presentation.settings.ShizukuSettingsViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InstallSettingsPane(
    onOpenInstallOptions: () -> Unit,
    modifier: Modifier = Modifier,
    shizukuModel: ShizukuSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Shizuku state ─────────────────────────────────────────────────────────
    val shizukuEnabled by shizukuModel.enabled.collectAsState()
    val shizukuStatus by shizukuModel.status.collectAsState()
    val shizukuMessage by shizukuModel.message.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, shizukuModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) shizukuModel.refresh()
        }
        lifecycle.addObserver(observer)
        shizukuModel.refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }

    // ── Root state ───────────────────────────────────────────────────────────
    val rootAvailable by produceState<Boolean?>(initialValue = null) {
        value = RootShell.isAvailable()
    }
    val silentEnabled by context.dataStore.data
        .map { it[SharedPrefsKeys.ROOT_SILENT_INSTALL] ?: true }
        .collectAsState(initial = true)

    // ── Auto-delete state ────────────────────────────────────────────────────
    val deleteApkEnabled by context.dataStore.data
        .map { it[SharedPrefsKeys.DELETE_APK_AFTER_INSTALL] ?: false }
        .collectAsState(initial = false)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Installation Backends & Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // 1. Unknown Sources Permission
        TvSettingRow(
            title = stringResource(R.string.tv_settings_allow_installs_title),
            subtitle = stringResource(R.string.tv_settings_allow_installs_subtitle),
            icon = Icons.Default.Security,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            trailingContent = {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        // 2. Shizuku Backend Row
        val shizukuStatusText = stringResource(
            shizukuMessage ?: when (shizukuStatus) {
                TvShizuku.Status.Missing -> R.string.tv_shizuku_missing
                TvShizuku.Status.Stopped -> R.string.tv_shizuku_stopped
                TvShizuku.Status.Unsupported -> R.string.tv_shizuku_unsupported
                TvShizuku.Status.PermissionRequired -> R.string.tv_shizuku_permission
                TvShizuku.Status.Ready -> R.string.tv_shizuku_ready
            }
        )
        TvSettingSwitchRow(
            title = stringResource(R.string.tv_shizuku_title),
            subtitle = shizukuStatusText,
            iconPainter = painterResource(R.drawable.ic_shizuku_logo),
            checked = shizukuEnabled,
            badgeText = if (shizukuStatus == TvShizuku.Status.Ready) "READY" else null,
            badgeColor = MaterialTheme.colorScheme.primaryContainer,
            onCheckedChange = { shizukuModel.toggle() }
        )

        // 3. Silent Install (Root) Row
        val rootStatusText = when (rootAvailable) {
            null -> stringResource(R.string.tv_settings_checking_root)
            true -> stringResource(R.string.tv_settings_root_available)
            else -> stringResource(R.string.tv_settings_root_unavailable)
        }
        TvSettingSwitchRow(
            title = stringResource(R.string.tv_settings_silent_install_title),
            subtitle = rootStatusText,
            icon = Icons.Default.VerifiedUser,
            checked = silentEnabled && (rootAvailable == true),
            enabled = rootAvailable == true,
            badgeText = if (rootAvailable == true) "ROOT" else null,
            badgeColor = MaterialTheme.colorScheme.primaryContainer,
            onCheckedChange = { checked ->
                scope.launch {
                    context.dataStore.edit { it[SharedPrefsKeys.ROOT_SILENT_INSTALL] = checked }
                }
            }
        )

        // 4. Auto Delete APK Row
        TvSettingSwitchRow(
            title = stringResource(R.string.tv_settings_delete_apk_title),
            subtitle = "Automatically clean up APK files after successful installation",
            icon = Icons.Default.Delete,
            checked = deleteApkEnabled,
            onCheckedChange = { checked ->
                scope.launch {
                    context.dataStore.edit { it[SharedPrefsKeys.DELETE_APK_AFTER_INSTALL] = checked }
                }
            }
        )

        // 5. Advanced Flags
        TvSettingRow(
            title = "Advanced Install Options",
            subtitle = "Configure replacement, downgrade, and user permission flags",
            icon = Icons.Default.Tune,
            onClick = onOpenInstallOptions,
            trailingContent = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
