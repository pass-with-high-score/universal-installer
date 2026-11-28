package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pwhs.core.domain.model.VtScanPhase
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus

internal fun VtResult?.isScanInProgress(): Boolean = this?.status in setOf(
    VtStatus.SCANNING,
    VtStatus.UPLOADING,
    VtStatus.QUEUED,
    VtStatus.ANALYZING,
)

internal enum class ScanStepVisualState { DONE, ACTIVE, PENDING }

internal data class ScanUiProgress(
    val stepIndex: Int,
    val totalSteps: Int,
    val fraction: Float,
    val isDeepScan: Boolean,
)

internal fun computeScanUiProgress(vt: VtResult?, hasSha256: Boolean): ScanUiProgress {
    if (vt == null) return ScanUiProgress(1, 2, 0.1f, false)
    val status = vt.status
    val phase = vt.scanPhase
    val isDeep = status in setOf(VtStatus.UPLOADING, VtStatus.QUEUED, VtStatus.ANALYZING) ||
        phase in setOf(VtScanPhase.UPLOADING, VtScanPhase.QUEUED, VtScanPhase.ANALYZING)

    return when {
        status == VtStatus.CLEAN || status == VtStatus.MALICIOUS || status == VtStatus.SUSPICIOUS -> {
            val total = if (isDeep) 4 else 2
            ScanUiProgress(total, total, 1f, isDeep)
        }
        status == VtStatus.UPLOADING || phase == VtScanPhase.UPLOADING -> {
            val uploadFrac = (vt.uploadProgress.coerceIn(0, 100) / 100f)
            ScanUiProgress(3, 4, 0.30f + 0.40f * uploadFrac, true)
        }
        status == VtStatus.QUEUED || phase == VtScanPhase.QUEUED -> {
            ScanUiProgress(4, 4, 0.76f, true)
        }
        status == VtStatus.ANALYZING || phase == VtScanPhase.ANALYZING -> {
            val analyzeFrac = (vt.uploadProgress.coerceIn(0, 100) / 100f)
            ScanUiProgress(4, 4, 0.80f + 0.18f * analyzeFrac, true)
        }
        phase == VtScanPhase.LOOKUP_CACHE || (status == VtStatus.SCANNING && hasSha256) -> {
            ScanUiProgress(2, 2, 0.65f, false)
        }
        phase == VtScanPhase.FALLBACK -> {
            ScanUiProgress(2, 2, 0.55f, false)
        }
        else -> {
            ScanUiProgress(1, 2, 0.22f, false)
        }
    }
}

@Composable
internal fun activeScanStepLabel(vt: VtResult?, hasSha256: Boolean): String {
    if (vt == null) return stringResource(R.string.vt_scan_step_hashing)
    return when {
        vt.status == VtStatus.UPLOADING || vt.scanPhase == VtScanPhase.UPLOADING ->
            if (vt.uploadProgress > 0) stringResource(R.string.vt_scan_step_upload_progress, vt.uploadProgress)
            else stringResource(R.string.vt_scan_step_upload)
        vt.status == VtStatus.QUEUED || vt.scanPhase == VtScanPhase.QUEUED ->
            vt.stageDescription.ifBlank { stringResource(R.string.vt_scan_step_queued) }
        vt.status == VtStatus.ANALYZING || vt.scanPhase == VtScanPhase.ANALYZING ->
            vt.stageDescription.ifBlank { stringResource(R.string.vt_scan_step_analyzing) }
        vt.scanPhase == VtScanPhase.FALLBACK ->
            stringResource(R.string.vt_scan_step_fallback)
        vt.scanPhase == VtScanPhase.LOOKUP_CACHE || (vt.status == VtStatus.SCANNING && hasSha256) ->
            stringResource(R.string.vt_scan_step_lookup)
        else ->
            stringResource(R.string.vt_scan_step_hashing)
    }
}

@Composable
internal fun resolveVtErrorMessage(errorMessage: String?): String = when {
    errorMessage.isNullOrBlank() -> stringResource(R.string.apk_info_vt_desc_error_default)
    errorMessage == VirusDetecService.NETWORK_ERROR_TAG -> stringResource(R.string.apk_info_vt_network_error)
    else -> errorMessage
}
