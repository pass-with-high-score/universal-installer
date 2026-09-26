package app.pwhs.universalinstaller.presentation.manage

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import app.pwhs.universalinstaller.presentation.manage.BatchExtractState
import app.pwhs.universalinstaller.presentation.manage.ExtractMode
import app.pwhs.universalinstaller.presentation.manage.ExtractState
import app.pwhs.universalinstaller.presentation.manage.PrivilegedActionResult
import app.pwhs.universalinstaller.presentation.manage.launchShareIntent
import app.pwhs.universalinstaller.util.extension.getDisplayName

@Composable
internal fun ManageSnackbars(
    batchExtractState: BatchExtractState,
    privilegedActionResult: PrivilegedActionResult?,
    extractState: ExtractState,
    snackbarHostState: SnackbarHostState,
    onOpenBackups: () -> Unit,
    onDismissBatchExtractResult: () -> Unit,
    onDismissPrivilegedResult: () -> Unit,
    onDismissExtractResult: () -> Unit,
) {
    val context = LocalContext.current
    val resource = LocalResources.current

    LaunchedEffect(batchExtractState) {
        val s = batchExtractState as? BatchExtractState.Done ?: return@LaunchedEffect
        val msg = if (s.failed == 0) {
            resource.getString(R.string.manage_batch_extract_done, s.success)
        } else {
            resource.getString(R.string.manage_batch_extract_partial, s.success, s.failed)
        }
        val res = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = resource.getString(R.string.extract_done_action_open),
            withDismissAction = true,
        )
        if (res == SnackbarResult.ActionPerformed) onOpenBackups()
        onDismissBatchExtractResult()
    }

    LaunchedEffect(privilegedActionResult) {
        val result = privilegedActionResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = result.message, withDismissAction = true)
        onDismissPrivilegedResult()
    }

    LaunchedEffect(extractState) {
        when (val s = extractState) {
            is ExtractState.Done -> {
                val fileName = if (s.uri.scheme == "file") {
                    java.io.File(s.uri.path!!).name
                } else {
                    context.contentResolver.getDisplayName(s.uri)
                }
                when (s.mode) {
                    ExtractMode.Backup -> {
                        val res = snackbarHostState.showSnackbar(
                            message = resource.getString(R.string.extract_done, fileName),
                            actionLabel = resource.getString(R.string.extract_done_action_open),
                            withDismissAction = true,
                        )
                        if (res == SnackbarResult.ActionPerformed) onOpenBackups()
                    }
                    ExtractMode.Share -> {
                        val launched = launchShareIntent(context, s.uri, s.appName)
                        if (!launched) {
                            snackbarHostState.showSnackbar(
                                message = resource.getString(
                                    R.string.manage_action_share_failed,
                                    "no app accepts the share",
                                ),
                                withDismissAction = true,
                            )
                        }
                    }
                    ExtractMode.Server -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resource.getString(R.string.manage_action_added_to_server, fileName),
                            actionLabel = resource.getString(R.string.manage_action_open_sync),
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val intent = Intent(context, SyncActivity::class.java)
                            context.startActivity(intent)
                        }
                    }
                    ExtractMode.Reinstall -> {
                        val installUri = if (s.uri.scheme == "file") {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${app.pwhs.universalinstaller.BuildConfig.APPLICATION_ID}.fileprovider",
                                java.io.File(s.uri.path!!),
                            )
                        } else {
                            s.uri
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(installUri, "application/vnd.android.package-archive")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setPackage(app.pwhs.universalinstaller.BuildConfig.APPLICATION_ID)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            snackbarHostState.showSnackbar(
                                message = "Couldn't launch Universal Installer",
                                withDismissAction = true,
                            )
                        }
                    }
                }
                onDismissExtractResult()
            }
            is ExtractState.Error -> {
                val msg = when (s.mode) {
                    ExtractMode.Share ->
                        resource.getString(R.string.manage_action_share_failed, s.message)
                    ExtractMode.Backup ->
                        resource.getString(R.string.extract_failed, s.message)
                    ExtractMode.Server ->
                        "Failed to add to server: ${s.message}"
                    ExtractMode.Reinstall ->
                        "Failed to extract for reinstall: ${s.message}"
                }
                snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                onDismissExtractResult()
            }
            else -> Unit
        }
    }
}
