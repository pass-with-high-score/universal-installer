package app.pwhs.tv.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.tv.TvNoScale
import app.pwhs.tv.install.TvShizuku
import app.pwhs.tv.presentation.settings.components.TvSubScreenHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InstallOptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val rootReady by produceState<Boolean?>(initialValue = null) { value = RootShell.isAvailable() }
    val shizukuStatus by produceState(initialValue = TvShizuku.Status.Stopped) {
        value = TvShizuku.status(context)
    }
    val shizukuReady = shizukuStatus == TvShizuku.Status.Ready
    val root = prefs?.let { it[SharedPrefsKeys.TV_ROOT_REPLACE] ?: true } ?: true
    val shizuku = prefs?.let { it[SharedPrefsKeys.TV_SHIZUKU_REPLACE] ?: true } ?: true

    val backFocus = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) = scope.launch {
        context.dataStore.updateData { it.toMutablePreferences().apply { this[key] = value } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TvSubScreenHeader(
                title = "Install Options",
                subtitle = "These options are stored separately for Shizuku and Root.",
                onBack = onBack,
                backFocusRequester = backFocus
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Shizuku",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (shizukuReady) "Ready" else "Unavailable — start Shizuku and grant permission first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        item { OptionRow("Replace existing", shizuku, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_REPLACE, !shizuku) } }
        item { OptionRow("Allow downgrade", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE) ?: false)) } }
        item { OptionRow("Grant all requested permissions", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_GRANT) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_GRANT, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_GRANT) ?: false)) } }
        item { OptionRow("Allow test APKs", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_TEST) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_TEST, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_TEST) ?: false)) } }
        item { OptionRow("Install for all users", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS) ?: false)) } }

        item {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Root",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (rootReady == true) "Ready" else "Unavailable — grant root access first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rootReady == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { OptionRow("Replace existing", root, rootReady == true) { set(SharedPrefsKeys.TV_ROOT_REPLACE, !root) } }
        item { OptionRow("Allow downgrade", prefs?.get(SharedPrefsKeys.TV_ROOT_DOWNGRADE) ?: false, rootReady == true) { set(SharedPrefsKeys.TV_ROOT_DOWNGRADE, !(prefs?.get(SharedPrefsKeys.TV_ROOT_DOWNGRADE) ?: false)) } }
        item { OptionRow("Grant all requested permissions", prefs?.get(SharedPrefsKeys.TV_ROOT_GRANT) ?: false, rootReady == true) { set(SharedPrefsKeys.TV_ROOT_GRANT, !(prefs?.get(SharedPrefsKeys.TV_ROOT_GRANT) ?: false)) } }
        item { OptionRow("Allow test APKs", prefs?.get(SharedPrefsKeys.TV_ROOT_TEST) ?: false, rootReady == true) { set(SharedPrefsKeys.TV_ROOT_TEST, !(prefs?.get(SharedPrefsKeys.TV_ROOT_TEST) ?: false)) } }
        item { OptionRow("Install for all users", prefs?.get(SharedPrefsKeys.TV_ROOT_ALL_USERS) ?: false, rootReady == true) { set(SharedPrefsKeys.TV_ROOT_ALL_USERS, !(prefs?.get(SharedPrefsKeys.TV_ROOT_ALL_USERS) ?: false)) } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val colors = ClickableSurfaceDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        scale = TvNoScale,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = colors
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Switch(checked = checked, enabled = enabled, onCheckedChange = { onClick() })
        }
    }
}
