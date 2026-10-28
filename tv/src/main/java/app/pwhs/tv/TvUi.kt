package app.pwhs.tv

import android.content.Context
import android.content.pm.PackageManager
import android.util.DisplayMetrics
import android.util.LruCache
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-wide in-memory cache for decoded TV launcher icons (up to 120 apps). */
private val appIconCache = LruCache<String, ImageBitmap>(120)

/**
 * Focus scales with 1.0f (no zoom/scale-up) for content elements like Netflix, Spotify, and HBO Max.
 * Only the primary side navigation rail retains subtle zoom scaling.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
val TvNoScale: ClickableSurfaceScale
    @Composable get() = ClickableSurfaceDefaults.scale(focusedScale = 1f)

@OptIn(ExperimentalTvMaterial3Api::class)
val TvButtonNoScale: ButtonScale
    @Composable get() = ButtonDefaults.scale(focusedScale = 1f)

/**
 * High-contrast card border for TV (visible border when resting, vibrant accent border when focused).
 * Solves washed-out / invisible cards on light background.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
val TvCardBorder: ClickableSurfaceBorder
    @Composable get() = ClickableSurfaceDefaults.border(
        border = Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ),
        focusedBorder = Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        ),
        disabledBorder = Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        )
    )

/**
 * Standard Surface colors for TV cards and list rows with crisp contrast in both Light & Dark modes.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
val TvCardColors: ClickableSurfaceColors
    @Composable get() = ClickableSurfaceDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

/** Human-readable byte size. */
fun formatSize(context: Context, bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> context.getString(R.string.tv_common_size_gb, gb)
        mb >= 1.0 -> context.getString(R.string.tv_common_size_mb, mb)
        kb >= 1.0 -> context.getString(R.string.tv_common_size_kb, kb)
        else -> context.getString(R.string.tv_common_size_bytes, bytes)
    }
}

/** Check if a key code corresponds to D-Pad confirm/click on Android TV. */
fun isDpadConfirmKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
}

/**
 * Loads an app's launcher icon off the main thread with fast LruCache lookup.
 * Returns cached bitmap immediately to eliminate white flashes on LazyColumn scrolling.
 */
@Composable
fun rememberAppIcon(packageName: String, sizePx: Int = 128): ImageBitmap? {
    val cached = remember(packageName, sizePx) { appIconCache.get("$packageName:$sizePx") }
    if (cached != null) return cached

    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName, key2 = sizePx) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val drawable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    pm.getResourcesForApplication(appInfo).getDrawableForDensity(
                        appInfo.icon,
                        DisplayMetrics.DENSITY_XHIGH,
                        null
                    )
                } else {
                    pm.getApplicationIcon(appInfo)
                }
                drawable?.toBitmap(sizePx, sizePx)?.asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) {
            appIconCache.put("$packageName:$sizePx", loaded)
        }
        value = loaded
    }
    return icon
}
