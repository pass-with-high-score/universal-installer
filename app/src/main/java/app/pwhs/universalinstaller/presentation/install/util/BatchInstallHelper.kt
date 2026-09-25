package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.presentation.install.BatchApkEntry
import app.pwhs.universalinstaller.presentation.install.BatchInstallState
import app.pwhs.universalinstaller.util.extension.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object BatchInstallHelper {

    suspend fun parseBatchUrisWithAckpine(
        context: Context,
        uris: List<Uri>,
        useMerge: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): List<BatchApkEntry> = parseBatchUris(
        context = context,
        uris = uris,
        useMerge = useMerge,
        onProgress = onProgress,
        parseSingleForBatch = { uri, displayName, extension ->
            val splitProvider = InstallApkSplitsHelper.buildSplitProvider(context, uri, extension)
            var splitUris = emptyList<Uri>()
            val info = InstallApkSplitsHelper.extractApkInfoAndCacheUris(context, uri, splitProvider, displayName, false) { splitUris = it }
            info to splitUris
        }
    )

    suspend fun parseBatchUris(
        context: Context,
        uris: List<Uri>,
        useMerge: Boolean,
        onProgress: (Int, Int) -> Unit,
        parseSingleForBatch: suspend (Uri, String, String) -> Pair<ApkInfo, List<Uri>>,
    ): List<BatchApkEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<BatchApkEntry>()
        uris.forEachIndexed { index, uri ->
            val displayName = context.contentResolver.getDisplayName(uri)
            if (!PackageFileFilter.isSupportedPackage(context, uri, displayName)) {
                entries += BatchApkEntry(
                    uri = uri,
                    fileName = displayName,
                    apkInfo = ApkInfo(
                        appName = displayName.substringBeforeLast('.'),
                        packageName = "Unknown",
                        versionName = "",
                        versionCode = 0L,
                        icon = null,
                        minSdkVersion = 0,
                        targetSdkVersion = 0,
                        fileSizeBytes = 0,
                        permissions = emptyList(),
                    ),
                    splitUris = emptyList(),
                    selected = false,
                    parseError = "Unsupported file format",
                )
                onProgress(index + 1, uris.size)
                return@forEachIndexed
            }
            val extension = displayName.substringAfterLast('.', "").lowercase()
            try {
                val (info, splitUris) = parseSingleForBatch(uri, displayName, extension)
                entries += BatchApkEntry(
                    uri = uri,
                    fileName = displayName,
                    apkInfo = info,
                    splitUris = splitUris,
                    selected = splitUris.isNotEmpty(),
                    parseError = if (splitUris.isEmpty()) "No installable splits found" else null,
                )
            } catch (t: Throwable) {
                Timber.e(t, "Batch parse failed for $uri")
                entries += BatchApkEntry(
                    uri = uri,
                    fileName = displayName,
                    apkInfo = ApkInfo(
                        appName = displayName.substringBeforeLast('.'),
                        packageName = "Unknown",
                        versionName = "",
                        versionCode = 0L,
                        icon = null,
                        minSdkVersion = 0,
                        targetSdkVersion = 0,
                        fileSizeBytes = 0,
                        permissions = emptyList(),
                    ),
                    splitUris = emptyList(),
                    selected = false,
                    parseError = t.message ?: "Parse failed",
                )
            }
            onProgress(index + 1, uris.size)
        }

        val dupLabel = context.getString(R.string.batch_install_dup_package)
        val mergedLabel = context.getString(R.string.batch_install_merged_splits)
        processBatchEntries(entries, useMerge, dupLabel, mergedLabel)
    }

    fun processBatchEntries(
        entries: List<BatchApkEntry>,
        useMerge: Boolean,
        dupLabel: String,
        mergedLabel: String,
    ): List<BatchApkEntry> {
        if (useMerge) {
            return entries.groupBy { "${it.apkInfo.packageName}_${it.apkInfo.versionCode}" }
                .map { (_, group) ->
                    if (group.size > 1 && group.all { it.parseError == null }) {
                        val representative = group.find { g ->
                            g.apkInfo.splitEntries.any { it.type == SplitType.Base }
                        } ?: group.first()

                        val allSplitUris = group.flatMap { it.splitUris }.distinct()
                        representative.copy(
                            splitUris = allSplitUris,
                            conflictLabel = mergedLabel,
                            apkInfo = representative.apkInfo.copy(
                                splitCount = allSplitUris.size,
                                fileSizeBytes = group.sumOf { it.apkInfo.fileSizeBytes }
                            )
                        )
                    } else {
                        group.first()
                    }
                }
        } else {
            val seen = mutableSetOf<String>()
            return entries.map { e ->
                when {
                    e.parseError != null -> e
                    e.apkInfo.packageName.isBlank() || e.apkInfo.packageName == "Unknown" -> e
                    e.apkInfo.packageName in seen -> e.copy(
                        selected = false,
                        conflictLabel = dupLabel,
                    )
                    else -> {
                        seen += e.apkInfo.packageName
                        e
                    }
                }
            }
        }
    }

    fun toggleBatchSelection(
        currentState: BatchInstallState,
        uri: Uri,
    ): BatchInstallState {
        val ready = currentState as? BatchInstallState.Ready ?: return currentState
        return BatchInstallState.Ready(
            ready.entries.map { e ->
                if (e.uri == uri && e.parseError == null) e.copy(selected = !e.selected) else e
            }
        )
    }

    fun setBatchAllSelected(
        currentState: BatchInstallState,
        selected: Boolean,
    ): BatchInstallState {
        val ready = currentState as? BatchInstallState.Ready ?: return currentState
        return BatchInstallState.Ready(
            ready.entries.map { e ->
                if (e.parseError == null) e.copy(selected = selected) else e
            }
        )
    }

    fun saveBatchDetail(
        currentState: BatchInstallState,
        uri: Uri,
        newSplitUris: List<Uri>,
    ): BatchInstallState {
        val ready = currentState as? BatchInstallState.Ready ?: return currentState
        val newEntries = ready.entries.map { entry ->
            if (entry.uri == uri) {
                entry.copy(splitUris = newSplitUris)
            } else {
                entry
            }
        }
        return BatchInstallState.Ready(newEntries)
    }
}
