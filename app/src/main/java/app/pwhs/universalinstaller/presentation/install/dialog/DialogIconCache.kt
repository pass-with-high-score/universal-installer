package app.pwhs.universalinstaller.presentation.install.dialog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Process-wide LRU cache for dialog target icons, keyed on the icon file path.
 *
 * The install dialog decodes a target's icon from a cached PNG on disk every time it
 * shows. Re-opening the dialog for the same APK (e.g. Retry, or a quick second tap)
 * would otherwise re-hit [BitmapFactory.decodeFile] on the main thread. Holding a few
 * decoded bitmaps makes those repeats instant.
 *
 * Capped by total byte size rather than entry count — icons vary in resolution and a
 * handful of large ones shouldn't be able to balloon memory.
 */
object DialogIconCache {

    // 4 MiB is enough for dozens of typical launcher icons while staying negligible
    // against the app heap. Eviction drops the least-recently-used entry; we never
    // recycle() since callers may still hold a reference via asImageBitmap().
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Returns the decoded bitmap for [iconPath], decoding (and caching) on first miss.
     * Returns null when the path is null/blank, the file is missing, or decode fails.
     */
    fun get(iconPath: String?): Bitmap? {
        val path = iconPath?.takeIf { it.isNotBlank() } ?: return null
        cache.get(path)?.let { return it }
        val decoded = runCatching {
            path.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
        }.getOrNull() ?: return null
        cache.put(path, decoded)
        return decoded
    }
}
