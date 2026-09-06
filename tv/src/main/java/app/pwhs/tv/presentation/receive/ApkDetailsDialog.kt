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
import androidx.compose.ui.window.DialogProperties
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val installFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { installFocus.requestFocus() } }

        Surface(
            modifier = Modifier.width(680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                // Header: App Icon (72dp) + Name & Package + Split tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "APK",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isBundle) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "SPLIT BUNDLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (pkg.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Metadata cards: 2x2 balanced grid with API chip badges
                val minVer = getAndroidVersion(minSdk)
                val targetVer = getAndroidVersion(targetSdk)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailMetaItem(
                            label = stringResource(R.string.tv_details_version),
                            value = version,
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetaItem(
                            label = stringResource(R.string.tv_details_size),
                            value = formatSize(context, size),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailMetaItem(
                            label = stringResource(R.string.tv_details_min_sdk),
                            value = if (minSdk > 0) (if (minVer != null) "Android $minVer+" else "API $minSdk") else "—",
                            subValue = if (minSdk > 0 && minVer != null) "API $minSdk" else null,
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetaItem(
                            label = stringResource(R.string.tv_details_target_sdk),
                            value = if (targetSdk > 0) (if (targetVer != null) "Android $targetVer" else "API $targetSdk") else "—",
                            subValue = if (targetSdk > 0 && targetVer != null) "API $targetSdk" else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons: Install & Close on main row, Delete APK below
                val btnShape = RoundedCornerShape(14.dp)
                val hasDelete = apkItem is TvApkItem.Local && onDelete != null

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onInstall(uri, isBundle, name, size) },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .clip(btnShape)
                            .focusRequester(installFocus),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    ) {
                        Text(
                            text = if (isInstalling) stringResource(R.string.tv_receive_installing_plain)
                            else stringResource(R.string.tv_receive_install),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    ) {
                        Text(
                            stringResource(R.string.tv_manage_action_close),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (hasDelete) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(btnShape),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.error,
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
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getAndroidVersion(sdk: Int): String? = when (sdk) {
    21 -> "5.0"
    22 -> "5.1"
    23 -> "6.0"
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> null
}

@OptIn(ExperimentalTvMaterial3Api::class)
private fun String.isBundleName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+", "zip")

