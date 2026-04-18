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

    fun post(uri: Uri) {
        _pendingUri.value = uri
    }

    fun consume(): Uri? {
        val value = _pendingUri.value
        if (value != null) _pendingUri.value = null
        return value
    }
}
