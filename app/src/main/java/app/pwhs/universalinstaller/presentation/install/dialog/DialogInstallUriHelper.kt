package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.pwhs.universalinstaller.presentation.install.util.AppCacheManager
import app.pwhs.universalinstaller.presentation.install.InstallViewModel
import app.pwhs.universalinstaller.presentation.install.util.InstallApkSplitsHelper
import app.pwhs.universalinstaller.util.extension.getDisplayName

object DialogInstallUriHelper {

    fun collectIncomingUris(source: Intent?): List<Uri> {
        if (source == null) return emptyList()
        val out = mutableListOf<Uri>()

        // 1. Data URI (VIEW / INSTALL_PACKAGE)
        source.data?.takeIf { isSupportedScheme(it.scheme) }?.let(out::add)

        // 2. EXTRA_STREAM or EXTRA_TEXT (SEND / SEND_MULTIPLE)
        @Suppress("DEPRECATION")
        when (source.action) {
            Intent.ACTION_SEND -> {
                (source.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    ?.takeIf { isSupportedScheme(it.scheme) }
                    ?.let(out::add)

                val text = source.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                if (!text.isNullOrBlank()) {
                    val url = text.split("\\s+".toRegex()).find {
                        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                    }
                    if (url != null) {
                        runCatching { Uri.parse(url) }.getOrNull()?.let(out::add)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE ->
                source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.filterNotNull()
                    ?.filter { isSupportedScheme(it.scheme) }
                    ?.let(out::addAll)
        }

        // 3. ClipData (Alternative for some file managers)
        source.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                val u = clip.getItemAt(i).uri ?: continue
                if (isSupportedScheme(u.scheme)) out.add(u)
            }
        }

        return out.distinct()
    }

    private fun isSupportedScheme(scheme: String?): Boolean =
        scheme == "content" || scheme == "file" || scheme == "http" || scheme == "https"

    fun forwardIncomingUris(source: Intent?, target: Intent) {
        if (source == null) return
        val uris = collectIncomingUris(source)
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            target.data = uris.first()
        } else {
            val clip = ClipData.newRawUri("", uris.first())
            for (i in 1 until uris.size) {
                clip.addItem(ClipData.Item(uris[i]))
            }
            target.clipData = clip
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun parseAndPush(
        context: Context,
        uri: Uri,
        viewModel: InstallViewModel,
        onDownloadProgress: ((app.pwhs.core.network.DownloadProgress) -> Unit)? = null,
    ) {
        val originalDisplayName = context.contentResolver.getDisplayName(uri).ifBlank {
            uri.lastPathSegment?.substringAfterLast('/') ?: "package.apk"
        }

        val targetUri = if (uri.scheme == "http" || uri.scheme == "https") {
            val downloader = app.pwhs.core.network.NetworkApkDownloader(context)
            when (val result = downloader.download(uri.toString(), onDownloadProgress ?: {})) {
                is app.pwhs.core.network.DownloadResult.Success -> {
                    Uri.fromFile(result.file)
                }
                is app.pwhs.core.network.DownloadResult.Error -> {
                    throw java.io.IOException(result.message, result.throwable)
                }
                app.pwhs.core.network.DownloadResult.Cancelled -> {
                    return
                }
            }
        } else if (uri.scheme == "content" && !isAppInternalUri(context, uri)) {
            AppCacheManager.stageExternalUri(context, uri, originalDisplayName) ?: uri
        } else {
            uri
        }

        val displayName = if (targetUri.scheme == "file") {
            originalDisplayName
        } else {
            context.contentResolver.getDisplayName(targetUri).ifBlank { originalDisplayName }
        }

        if (!app.pwhs.universalinstaller.presentation.install.util.PackageFileFilter.isSupportedPackage(context, targetUri, displayName)) {
            android.widget.Toast.makeText(
                context,
                context.getString(app.pwhs.universalinstaller.R.string.install_unsupported_file),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            viewModel.dialogParseFailed(context.getString(app.pwhs.universalinstaller.R.string.install_unsupported_file))
            return
        }

        val ext = displayName.substringAfterLast('.', "").lowercase()
        val splitProvider = InstallApkSplitsHelper.buildSplitProvider(context, targetUri, ext)
        viewModel.parseApkInfo(context, targetUri, splitProvider, displayName)
    }

    private fun isAppInternalUri(context: Context, uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        return authority == "${context.packageName}.fileprovider"
    }

    suspend fun parseAndPushFile(
        context: Context,
        file: java.io.File,
        fileName: String,
        viewModel: InstallViewModel,
    ) {
        if (!app.pwhs.universalinstaller.presentation.install.util.PackageFileFilter.isSupportedPackageFile(file, fileName)) {
            android.widget.Toast.makeText(
                context,
                context.getString(app.pwhs.universalinstaller.R.string.install_unsupported_file),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            viewModel.dialogParseFailed(context.getString(app.pwhs.universalinstaller.R.string.install_unsupported_file))
            return
        }

        val targetUri = Uri.fromFile(file)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val splitProvider = InstallApkSplitsHelper.buildSplitProvider(context, targetUri, ext)
        viewModel.parseApkInfo(context, targetUri, splitProvider, fileName)
    }
}
