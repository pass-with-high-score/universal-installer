package app.pwhs.universalinstaller.util.extension

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile

fun ContentResolver.getDisplayName(uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.toFile().name
    }
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
        if (cursor == null || !cursor.moveToFirst()) {
            return ""
        }
        return cursor.getString(0)
    }
}