package app.pwhs.tv.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.tv.install.TvShizuku
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.map
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
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) = scope.launch {
        context.dataStore.updateData { it.toMutablePreferences().apply { this[key] = value } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 48.dp), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Surface(onClick = onBack, scale = app.pwhs.tv.TvNoScale) { Icon(Icons.Default.ArrowBack, "Back") } }
        item { Text("Install options", style = MaterialTheme.typography.displaySmall) }
        item { Text("These options are stored separately for Shizuku and Root.", style = MaterialTheme.typography.bodyLarge) }
        item { Text("Shizuku", style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (shizukuReady) "Ready" else "Unavailable — start Shizuku and grant permission first", style = MaterialTheme.typography.bodyMedium) }
        item { OptionRow("Replace existing", shizuku, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_REPLACE, !shizuku) } }
        item { OptionRow("Allow downgrade", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_DOWNGRADE) ?: false)) } }
        item { OptionRow("Grant all requested permissions", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_GRANT) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_GRANT, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_GRANT) ?: false)) } }
        item { OptionRow("Allow test APKs", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_TEST) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_TEST, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_TEST) ?: false)) } }
        item { OptionRow("Install for all users", prefs?.get(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS) ?: false, shizukuReady) { set(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS, !(prefs?.get(SharedPrefsKeys.TV_SHIZUKU_ALL_USERS) ?: false)) } }
        item { Text("Root", style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (rootReady == true) "Ready" else "Unavailable — grant root access first", style = MaterialTheme.typography.bodyMedium) }
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
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    val colors = ClickableSurfaceDefaults.colors(
            containerColor = surface,
            focusedContainerColor = surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = surface.copy(alpha = .22f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
        )
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
            Switch(checked = checked, enabled = enabled, onCheckedChange = { onClick() })
        }
    }
    if (enabled) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), scale = app.pwhs.tv.TvNoScale, colors = colors) { content() }
    } else {
        Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) { content() }
    }
}
