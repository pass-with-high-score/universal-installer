package app.pwhs.universalinstaller.presentation.install.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import app.pwhs.universalinstaller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object SourceFileDeleter {

    fun deleteSourceFile(context: Context, uri: Uri): Boolean =
        app.pwhs.core.util.SourceFileDeleter.deleteSourceFile(context, uri)


    suspend fun deleteSourceFileAndWarn(context: Context, uri: Uri) {
        val fileSize = resolveUriFileSize(context, uri)
        if (deleteSourceFile(context, uri)) {
            Timber.d("Deleted source file: $uri")
            app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordSourceFileDeleted(context, fileSize)
            return
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.install_delete_source_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun resolveUriFileSize(context: Context, uri: Uri): Long = runCatching {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).length() } ?: 0L
            ContentResolver.SCHEME_CONTENT -> DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
            else -> 0L
        }
    }.getOrDefault(0L)
}
