package app.pwhs.universalinstaller

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot handoff for URIs delivered via `ACTION_VIEW` (Chrome downloads, file managers,
 * Gmail attachments). `MainActivity` posts the incoming URI here; the install screen
 * consumes it on first composition so the user lands on the APK info sheet instead of
 * the home screen.
 */
object IntentHandoff {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    /** Multi-file share (`ACTION_SEND_MULTIPLE`) or multi-pick from inside the app. */
    private val _pendingUris = MutableStateFlow<List<Uri>?>(null)
    val pendingUris: StateFlow<List<Uri>?> = _pendingUris.asStateFlow()

    /** Plain-text share (`ACTION_SEND` + `text/plain`) — typically a download URL pasted
     *  from a browser's share sheet. Consumed by InstallScreen which routes it through
     *  the existing download-from-URL flow. */
    private val _pendingDownloadUrl = MutableStateFlow<String?>(null)
    val pendingDownloadUrl: StateFlow<String?> = _pendingDownloadUrl.asStateFlow()

    fun post(uri: Uri) {
        _pendingUri.value = uri
    }

    fun postBatch(uris: List<Uri>) {
        _pendingUris.value = uris
    }

    fun postDownloadUrl(url: String) {
        _pendingDownloadUrl.value = url
    }

    fun consume(): Uri? {
        val value = _pendingUri.value
        if (value != null) _pendingUri.value = null
        return value
    }

    fun consumeBatch(): List<Uri>? {
        val value = _pendingUris.value
        if (value != null) _pendingUris.value = null
        return value
    }

    fun consumeDownloadUrl(): String? {
        val value = _pendingDownloadUrl.value
        if (value != null) _pendingDownloadUrl.value = null
        return value
    }
}
