package app.pwhs.updater.presentation.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.util.Locale

object InstallerUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    fun launchInstallerForFile(context: Context, file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }
        val isTv = runCatching {
            Class.forName("app.pwhs.tv.presentation.install.TvDialogInstallActivity")
            true
        }.getOrDefault(false)
        val targetClass = if (isTv) {
            "app.pwhs.tv.presentation.install.TvDialogInstallActivity"
        } else {
            "app.pwhs.universalinstaller.presentation.install.DialogInstallActivity"
        }

        fun createIntent(className: String? = null) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            className?.let { setClassName(context.packageName, it) }
            clipData = ClipData.newRawUri("package", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { context.startActivity(createIntent(targetClass)) }
            .recoverCatching { context.startActivity(createIntent()) }
            .onFailure { e -> Timber.e(e, "Failed to launch installer for file: ${file.absolutePath}") }
    }
}
