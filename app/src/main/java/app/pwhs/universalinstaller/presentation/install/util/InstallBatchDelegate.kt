package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.BatchInstallState
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstallBatchDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val backendFactory: app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory? = null,
    private val resolveController: suspend (String?) -> BaseInstallController,
    private val onStorageInsufficient: (Long) -> Unit = {},
) {
    private val _batchState = MutableStateFlow<BatchInstallState>(BatchInstallState.Idle)
    val batchState: StateFlow<BatchInstallState> = _batchState.asStateFlow()

    private val _batchDetailUri = MutableStateFlow<Uri?>(null)
    val batchDetailUri: StateFlow<Uri?> = _batchDetailUri.asStateFlow()

    private var batchParseJob: Job? = null

    fun parseBatch(context: Context, uris: List<Uri>, mergeSplits: Boolean) {
        if (uris.size <= 1) return
        _batchState.value = BatchInstallState.Parsing(
            uris = uris,
            processed = 0,
            total = uris.size,
        )
        batchParseJob?.cancel()
        batchParseJob = scope.launch {
            val entries = BatchInstallHelper.parseBatchUrisWithAckpine(
                context = context,
                uris = uris,
                useMerge = mergeSplits,
                onProgress = { processed, total ->
                    _batchState.value = BatchInstallState.Parsing(uris, processed, total)
                },
            )
            _batchState.value = BatchInstallState.Ready(entries)
        }
    }

    fun toggleBatchSelection(uri: Uri) {
        _batchState.value = BatchInstallHelper.toggleBatchSelection(_batchState.value, uri)
    }

    fun setBatchAllSelected(selected: Boolean) {
        _batchState.value = BatchInstallHelper.setBatchAllSelected(_batchState.value, selected)
    }

    fun dismissBatchInstall() {
        _batchState.value = BatchInstallState.Idle
    }

    fun openBatchDetail(uri: Uri) {
        _batchDetailUri.value = uri
    }

    fun closeBatchDetail() {
        _batchDetailUri.value = null
    }

    fun saveBatchDetail(uri: Uri, newSplitUris: List<Uri>) {
        _batchState.value = BatchInstallHelper.saveBatchDetail(_batchState.value, uri, newSplitUris)
        _batchDetailUri.value = null
    }

    fun confirmBatchInstall(selectedProfileId: String?) {
        val ready = _batchState.value as? BatchInstallState.Ready ?: return
        val picked = ready.entries.filter { it.selected && it.splitUris.isNotEmpty() }
        _batchState.value = BatchInstallState.Idle
        if (picked.isEmpty()) return

        if (!StorageUtil.hasSufficientStorage()) {
            onStorageInsufficient(0L)
            return
        }

        scope.launch {
            InstallExecutionCoordinator.executeBatchInstall(
                application = application,
                scope = scope,
                picked = picked,
                currentProfileId = selectedProfileId,
                backendFactory = backendFactory,
                resolveActiveController = resolveController,
            )
        }
    }

    fun skipBatchParseAndInstall() {
        val parsing = _batchState.value as? BatchInstallState.Parsing ?: return
        val uris = parsing.uris
        batchParseJob?.cancel()
        batchParseJob = null
        _batchState.value = BatchInstallState.Idle

        if (!StorageUtil.hasSufficientStorage()) {
            onStorageInsufficient(0L)
            return
        }

        scope.launch {
            InstallExecutionCoordinator.executeSkipBatch(
                application = application,
                scope = scope,
                uris = uris,
                resolveActiveController = { resolveController(null) },
            )
        }
    }
}
