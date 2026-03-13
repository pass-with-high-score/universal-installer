package com.nqmgaming.universalinstaller.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.domain.repository.FileScannerRepository
import com.nqmgaming.universalinstaller.domain.repository.LocalAppFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class ScannerUiState(
    val isLoading: Boolean = false,
    val files: List<LocalAppFile> = emptyList(),
    val error: String? = null
)

class ScannerViewModel(
    private val repository: FileScannerRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _files = MutableStateFlow<List<LocalAppFile>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ScannerUiState> = combine(
        _isLoading, _files, _error
    ) { isLoading, files, error ->
        ScannerUiState(isLoading, files, error)
    }.stateIn(viewModelScope, SharingStarted.Lazily, ScannerUiState())

    init {
        scanFiles()
    }

    fun scanFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                repository.scanFiles().collect { scannedFiles ->
                    _files.value = scannedFiles
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan files")
                _error.value = "Failed to load files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
