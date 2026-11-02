package app.pwhs.tv.presentation.settings

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.core.util.ShizukuStarterHelper
import app.pwhs.tv.R
import app.pwhs.tv.install.TvShizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class ShizukuSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    val enabled = context.dataStore.data.map { it[TvShizuku.enabledKey] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _status = MutableStateFlow(TvShizuku.status(context))
    val status: kotlinx.coroutines.flow.StateFlow<TvShizuku.Status> = _status.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private var requesting = false
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener { refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == TvShizuku.PERMISSION_REQUEST && requesting) {
            requesting = false
            refresh()
            if (grant == PackageManager.PERMISSION_GRANTED) {
                setEnabled(true)
                showToast(context.getString(R.string.tv_shizuku_on))
            } else {
                _message.value = R.string.tv_shizuku_denied
                showToast(context.getString(R.string.tv_shizuku_denied))
            }
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
    }

    fun refresh() {
        _status.value = TvShizuku.status(context)
    }

    fun toggle() {
        _message.value = null
        refresh()
        if (enabled.value) {
            setEnabled(false)
            showToast(context.getString(R.string.tv_shizuku_off))
            return
        }

        when (status.value) {
            TvShizuku.Status.Ready -> {
                setEnabled(true)
                showToast(context.getString(R.string.tv_shizuku_on))
            }

            TvShizuku.Status.PermissionRequired -> {
                if (requesting) return
                requesting = true
                showToast(context.getString(R.string.tv_shizuku_permission))
                try {
                    Shizuku.requestPermission(TvShizuku.PERMISSION_REQUEST)
                } catch (_: Exception) {
                    requesting = false
                    _message.value = R.string.tv_shizuku_error
                    showToast(context.getString(R.string.tv_shizuku_error))
                }
            }

            TvShizuku.Status.Stopped -> {
                viewModelScope.launch {
                    var startedViaRoot = false
                    if (RootShell.isAvailable()) {
                        val starterCmd = ShizukuStarterHelper.buildStarterScript(context)
                        RootShell.exec(starterCmd)
                        delay(1200)
                        refresh()
                        if (status.value == TvShizuku.Status.Ready) {
                            setEnabled(true)
                            showToast(context.getString(R.string.tv_shizuku_on))
                            startedViaRoot = true
                        } else if (status.value == TvShizuku.Status.PermissionRequired) {
                            requesting = true
                            try {
                                Shizuku.requestPermission(TvShizuku.PERMISSION_REQUEST)
                            } catch (_: Exception) {
                                requesting = false
                            }
                            startedViaRoot = true
                        }
                    }

                    if (!startedViaRoot) {
                        _message.value = R.string.tv_shizuku_stopped
                        showToast(context.getString(R.string.tv_shizuku_stopped))
                        val launchIntent = ShizukuStarterHelper.getManagerLaunchIntent(context)
                        if (launchIntent != null) {
                            runCatching { context.startActivity(launchIntent) }
                        }
                    }
                }
            }

            TvShizuku.Status.Missing -> {
                _message.value = R.string.tv_shizuku_missing
                showToast(context.getString(R.string.tv_shizuku_missing))
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=moe.shizuku.privileged.api")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching {
                    context.startActivity(marketIntent)
                }.onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }
                }
            }

            TvShizuku.Status.Unsupported -> {
                _message.value = R.string.tv_shizuku_unsupported
                showToast(context.getString(R.string.tv_shizuku_unsupported))
            }
        }
    }

    private fun showToast(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setEnabled(value: Boolean) {
        viewModelScope.launch { context.dataStore.edit { it[TvShizuku.enabledKey] = value } }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
    }
}
