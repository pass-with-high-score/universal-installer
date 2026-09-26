package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.util.AppCacheManager
import kotlinx.coroutines.launch

/**
 * Compact card showing internal-storage usage and quick cache clearing. Stats come from `/data`
 * (where APKs install), not emulated external — that's what actually matters for install success.
 */
@Composable
internal fun StorageCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(StorageUtil.getStorageStats()) }
    var cacheBytes by remember { mutableStateOf(AppCacheManager.getCacheSizeBytes(context)) }
    var isClearing by remember { mutableStateOf(false) }

    val onClearCache = {
        if (!isClearing) {
            isClearing = true
            scope.launch {
                val freed = AppCacheManager.clearCache(context)
                stats = StorageUtil.getStorageStats()
                cacheBytes = AppCacheManager.getCacheSizeBytes(context)
                isClearing = false
                val msg = if (freed > 0) {
                    context.getString(
                        R.string.install_storage_cache_cleared,
                        Formatter.formatShortFileSize(context, freed),
                    )
                } else {
                    context.getString(R.string.install_storage_cache_empty)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val progress = stats.progress

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.install_storage_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                FilledTonalButton(
                    onClick = onClearCache,
                    enabled = !isClearing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = if (cacheBytes > 0) {
                            stringResource(
                                R.string.install_storage_clear_cache_with_size,
                                Formatter.formatShortFileSize(context, cacheBytes),
                            )
                        } else {
                            stringResource(R.string.install_storage_clear_cache)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.install_storage_value,
                        Formatter.formatShortFileSize(context, stats.freeBytes),
                        Formatter.formatShortFileSize(context, stats.totalBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    progress >= 0.9f -> MaterialTheme.colorScheme.error
                    progress >= 0.75f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
