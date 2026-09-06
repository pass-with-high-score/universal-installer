package app.pwhs.tv.presentation.settings

import app.pwhs.tv.presentation.settings.components.SettingsCard
import app.pwhs.tv.presentation.settings.components.TitleValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.install.TvShizuku

@Composable
internal fun ShizukuSetting(model: ShizukuSettingsViewModel = viewModel()) {
    val enabled by model.enabled.collectAsState()
    val status by model.status.collectAsState()
    val message by model.message.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycle.addObserver(observer)
        model.refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    SettingsCard(onClick = model::toggle) {
        TitleValue(
            stringResource(R.string.tv_shizuku_title),
            stringResource(if (enabled) R.string.tv_shizuku_on else R.string.tv_shizuku_off),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(message ?: when (status) {
                TvShizuku.Status.Missing -> R.string.tv_shizuku_missing
                TvShizuku.Status.Stopped -> R.string.tv_shizuku_stopped
                TvShizuku.Status.Unsupported -> R.string.tv_shizuku_unsupported
                TvShizuku.Status.PermissionRequired -> R.string.tv_shizuku_permission
                TvShizuku.Status.Ready -> R.string.tv_shizuku_ready
            }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
