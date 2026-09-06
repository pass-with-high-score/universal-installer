package app.pwhs.tv.presentation.receive

import app.pwhs.tv.presentation.receive.components.ConfirmDeleteDialog
import app.pwhs.tv.presentation.receive.components.DetailMetaItem
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.receiver.ReceivedApk
import app.pwhs.tv.R
import app.pwhs.tv.formatSize
import java.io.File

sealed interface TvApkItem {
    data class Received(val apk: ReceivedApk) : TvApkItem
    data class Local(val apk: ApkFile) : TvApkItem
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ApkDetailsDialog(
    apkItem: TvApkItem,
    isInstalling: Boolean,
    onDismiss: () -> Unit,
    onInstall: (Uri, Boolean, String, Long) -> Unit,
    onDelete: ((ApkFile) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val name: String
    val pkg: String
    val version: String
    val size: Long
    val icon: androidx.compose.ui.graphics.ImageBitmap?
    val uri: Uri
    val isBundle: Boolean
    val minSdk: Int
    val targetSdk: Int

    when (apkItem) {
        is TvApkItem.Received -> {
            val meta = apkItem.apk.metadata
            name = meta?.appName ?: apkItem.apk.fileName
            pkg = meta?.packageName ?: ""
            version = meta?.versionName ?: ""
            size = apkItem.apk.sizeBytes
            icon = meta?.icon?.asImageBitmap()
            uri = Uri.fromFile(File(apkItem.apk.path))
            isBundle = apkItem.apk.fileName.isBundleName()
            minSdk = meta?.minSdk ?: 0
            targetSdk = meta?.targetSdk ?: 0
        }
        is TvApkItem.Local -> {
            val meta = apkItem.apk.metadata
            name = meta?.appName ?: apkItem.apk.displayName
            pkg = meta?.packageName ?: ""
            version = meta?.versionName ?: ""
            size = apkItem.apk.sizeBytes
            icon = meta?.icon?.asImageBitmap()
            uri = Uri.parse(apkItem.apk.uri)
            isBundle = apkItem.apk.isBundle
            minSdk = meta?.minSdk ?: 0
            targetSdk = meta?.targetSdk ?: 0
        }
    }

    if (showDeleteConfirm && apkItem is TvApkItem.Local) {
        ConfirmDeleteDialog(
            apkName = name,
            onConfirm = {
                showDeleteConfirm = false
                onDelete?.invoke(apkItem.apk)
                onDismiss()
            },
            onCancel = { showDeleteConfirm = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("APK", style = MaterialTheme.typography.displaySmall)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (pkg.isNotBlank()) {
                    Text(
                        pkg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailMetaItem(label = "Version", value = version, modifier = Modifier.weight(1f))
                    DetailMetaItem(label = "Size", value = formatSize(context, size), modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailMetaItem(label = "Min SDK", value = "Android $minSdk", modifier = Modifier.weight(1f))
                    DetailMetaItem(label = "Target SDK", value = "Android $targetSdk", modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(32.dp))

                val btnShape = RoundedCornerShape(14.dp)
                Button(
                    onClick = { onInstall(uri, isBundle, name, size) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(btnShape),
                    shape = ButtonDefaults.shape(btnShape)
                ) {
                    Text(
                        if (isInstalling) stringResource(R.string.tv_receive_installing_plain)
                        else stringResource(R.string.tv_receive_install),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (apkItem is TvApkItem.Local && onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            focusedContentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.tv_receive_delete_apk),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(btnShape),
                    shape = ButtonDefaults.shape(btnShape),
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        stringResource(R.string.tv_manage_action_close),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
private fun String.isBundleName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+", "zip")

