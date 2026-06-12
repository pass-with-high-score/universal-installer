package app.pwhs.universalinstaller.presentation.sync

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.base.BaseActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * Phone-side of the TV install flow: scan the TV's QR (open-source ZXing scanner), pick an
 * APK, and upload it to the TV over the LAN. The TV then confirms + installs it.
 */
class SendToTvActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme { SendToTvScreen(onBack = { finish() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendToTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanned by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned = it; status = null }
    }
    val apkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = scanned
        if (uri != null && target != null) {
            uploading = true
            status = context.getString(R.string.tv_sync_status_sending)
            scope.launch {
                val name = queryDisplayName(context, uri)
                val result = TvUploadClient.upload(context, target, uri, name)
                uploading = false
                status = when (result) {
                    is TvUploadClient.Result.Success -> context.getString(R.string.tv_sync_status_sent)
                    is TvUploadClient.Result.Failure -> context.getString(R.string.tv_sync_status_failed, result.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tv_sync_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tv_sync_hero_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))

                val target = scanned
                if (target == null) {
                    Text(
                        stringResource(R.string.tv_sync_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setPrompt(context.getString(R.string.tv_sync_prompt_scan))
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(true)
                                    .setCaptureActivity(CustomScannerActivity::class.java)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, null)
                        Spacer(Modifier.height(0.dp))
                        Text("  " + stringResource(R.string.tv_sync_btn_scan))
                    }
                } else {
                    Text(
                        stringResource(R.string.tv_sync_status_connected, Uri.parse(target).host ?: "TV"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { if (!uploading) apkLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uploading,
                    ) { Text(if (uploading) stringResource(R.string.tv_sync_status_sending) else stringResource(R.string.tv_sync_btn_choose_apk)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scanned = null; status = null },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uploading,
                    ) { Text(stringResource(R.string.tv_sync_btn_rescan)) }
                }

                status?.let {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "app.apk"
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "app.apk"
}
