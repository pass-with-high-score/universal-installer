package app.pwhs.universalinstaller.presentation.manage.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.install.ApkExtractor
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.util.CustomTabsHelper
import app.pwhs.universalinstaller.presentation.manage.BatchExtractState
import app.pwhs.universalinstaller.presentation.manage.ExtractMode
import app.pwhs.universalinstaller.presentation.manage.ExtractState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object ManageExtractHelper {

    suspend fun scanVirusTotal(context: Context, app: InstalledApp) = withContext(Dispatchers.IO) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
            val baseApkFile = File(appInfo.sourceDir)
            if (baseApkFile.exists()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                baseApkFile.inputStream().use { input ->
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        digest.update(buffer, 0, bytes)
                        bytes = input.read(buffer)
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

                withContext(Dispatchers.Main) {
                    CustomTabsHelper.openUrl(
                        context,
                        "https://www.virustotal.com/gui/file/$sha256/detection",
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan VT for ${app.packageName}")
        }
    }

    suspend fun extractApp(
        context: Context,
        packageName: String,
        appName: String,
        mode: ExtractMode,
        outputDir: File?,
        onProgress: (Long, Long) -> Unit,
    ): ExtractState {
        val useConfiguredPath = mode == ExtractMode.Backup
        val result = performExtract(
            context = context,
            packageName = packageName,
            cacheOutputDir = outputDir,
            useConfiguredPath = useConfiguredPath,
            onProgress = onProgress,
        )
        return when (result) {
            is ApkExtractor.Result.Success -> ExtractState.Done(appName, result.uri, mode)
            is ApkExtractor.Result.Failure -> ExtractState.Error(appName, result.message, mode)
        }
    }

    suspend fun extractBatch(
        context: Context,
        packages: List<String>,
        apps: List<InstalledApp>,
        onProgress: (BatchExtractState.Running) -> Unit,
    ): BatchExtractState.Done {
        val lookup = apps.associateBy { it.packageName }
        var success = 0
        var failed = 0
        val total = packages.size
        packages.forEachIndexed { index, pkg ->
            val name = lookup[pkg]?.appName ?: pkg
            onProgress(
                BatchExtractState.Running(
                    completed = index,
                    total = total,
                    currentName = name,
                    bytesCopied = 0L,
                    totalBytes = 1L,
                )
            )
            val result = performExtract(
                context = context,
                packageName = pkg,
                cacheOutputDir = null,
                useConfiguredPath = true,
            ) { bytes, totalBytes ->
                onProgress(
                    BatchExtractState.Running(
                        completed = index,
                        total = total,
                        currentName = name,
                        bytesCopied = bytes,
                        totalBytes = totalBytes,
                    )
                )
            }
            when (result) {
                is ApkExtractor.Result.Success -> success++
                is ApkExtractor.Result.Failure -> {
                    failed++
                    Timber.w("Bulk extract failed for $pkg: ${result.message}")
                }
            }
        }
        return BatchExtractState.Done(success = success, failed = failed)
    }

    suspend fun performExtract(
        context: Context,
        packageName: String,
        cacheOutputDir: File?,
        useConfiguredPath: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): ApkExtractor.Result {
        val prefs = context.dataStore.data.first()
        val customPathUri = prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH]
        val template = prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] ?: "{name}-{version}"
        val splitFormat = if (prefs[PreferencesKeys.APK_EXTRACTOR_SPLIT_FORMAT] == "xapk") {
            ApkExtractor.SplitFormat.XAPK
        } else {
            ApkExtractor.SplitFormat.APKS
        }
        val includeObb = prefs[PreferencesKeys.APK_EXTRACTOR_INCLUDE_OBB] ?: false
        return ApkExtractor.extract(
            context = context,
            packageName = packageName,
            outputDir = resolveOutputDir(
                context = context,
                cacheOutputDir = cacheOutputDir,
                customPathUri = customPathUri,
                useConfiguredPath = useConfiguredPath,
            ),
            filenameTemplate = template,
            splitFormat = splitFormat,
            includeObb = includeObb,
            onProgress = onProgress,
        )
    }

    private fun resolveOutputDir(
        context: Context,
        cacheOutputDir: File?,
        customPathUri: String?,
        useConfiguredPath: Boolean,
    ): DocumentFile? {
        if (!useConfiguredPath) {
            return cacheOutputDir?.apply { mkdirs() }?.let { DocumentFile.fromFile(it) }
        }

        val configured = resolveConfiguredOutputDir(context, customPathUri)
        if (
            configured != null &&
            configured.exists() &&
            configured.isDirectory &&
            configured.canWrite()
        ) {
            return configured
        }

        val fallback = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DEFAULT_EXTRACTED_SUBFOLDER,
        ).apply { mkdirs() }
        return DocumentFile.fromFile(fallback)
    }

    fun resolveConfiguredOutputDir(context: Context, path: String?): DocumentFile? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("content://")) {
            DocumentFile.fromTreeUri(context, Uri.parse(path))
        } else {
            val dir = File(path).apply { if (!exists()) mkdirs() }
            if (dir.isDirectory) DocumentFile.fromFile(dir) else null
        }
    }

    private const val DEFAULT_EXTRACTED_SUBFOLDER = "UniversalInstaller/Extracted"
}
