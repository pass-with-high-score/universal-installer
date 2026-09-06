package app.pwhs.tv.presentation.receive

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.receiver.ReceivedApk
import app.pwhs.tv.presentation.receive.components.ApkOverviewPane
import app.pwhs.tv.presentation.receive.components.ApkPermissionsPane
import app.pwhs.tv.presentation.receive.components.ConfirmDeleteDialog
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
    var showPermissions by remember { mutableStateOf(false) }

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

    val permissions: List<String> = remember(apkItem) {
        val metaPerms = when (apkItem) {
            is TvApkItem.Received -> apkItem.apk.metadata?.permissions
            is TvApkItem.Local -> apkItem.apk.metadata?.permissions
        }
        if (!metaPerms.isNullOrEmpty()) {
            metaPerms
        } else {
            runCatching {
                val path = when (apkItem) {
                    is TvApkItem.Received -> apkItem.apk.path
                    is TvApkItem.Local -> apkItem.apk.uri
                }
                if (path.startsWith("/")) {
                    val flags = PackageManager.GET_PERMISSIONS
                    val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageArchiveInfo(
                            path,
                            PackageManager.PackageInfoFlags.of(flags.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageArchiveInfo(path, flags)
                    }
                    pi?.requestedPermissions?.toList().orEmpty()
                } else emptyList()
            }.getOrDefault(emptyList())
        }
    }

    BackHandler(enabled = showPermissions) {
        showPermissions = false
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
        onDismissRequest = {
            if (showPermissions) showPermissions = false else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val installFocus = remember { FocusRequester() }
        LaunchedEffect(showPermissions) {
            if (!showPermissions) {
                runCatching { installFocus.requestFocus() }
            }
        }

        Surface(
            modifier = Modifier.width(680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            AnimatedContent(
                targetState = showPermissions,
                label = "permissionsTransition"
            ) { isPermissionsState ->
                if (isPermissionsState) {
                    ApkPermissionsPane(
                        name = name,
                        permissions = permissions,
                        isInstalling = isInstalling,
                        icon = icon,
                        onInstall = { onInstall(uri, isBundle, name, size) },
                        onBack = { showPermissions = false }
                    )
                } else {
                    ApkOverviewPane(
                        name = name,
                        pkg = pkg,
                        version = version,
                        size = size,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        isBundle = isBundle,
                        isInstalling = isInstalling,
                        hasDelete = apkItem is TvApkItem.Local && onDelete != null,
                        icon = icon,
                        installFocus = installFocus,
                        onInstall = { onInstall(uri, isBundle, name, size) },
                        onShowPermissions = { showPermissions = true },
                        onDelete = { showDeleteConfirm = true },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

private fun String.isBundleName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+", "zip")
