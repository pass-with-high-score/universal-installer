package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import kotlinx.coroutines.launch

private const val TELEGRAM_SUPPORT_URL = "https://t.me/blockads_android_chat"
private const val GITHUB_REPO_URL = "https://github.com/pass-with-high-score/universal-installer"
private const val GITHUB_NEW_ISSUE_URL = "$GITHUB_REPO_URL/issues/new"
const val ERRORS_DOCS_URL = "https://universal-installer.pwhs.app/errors"

object InstallSupportHelper {
    fun resolveErrorDocUrl(errorMessage: String): String {
        val upper = errorMessage.uppercase()
        return when {
            "UPDATE_INCOMPATIBLE" in upper || "SIGNATURE" in upper ->
                "$ERRORS_DOCS_URL/install-failed-update-incompatible"

            "VERSION_DOWNGRADE" in upper || "DOWNGRADE" in upper ->
                "$ERRORS_DOCS_URL/install-failed-version-downgrade"

            "ALREADY_EXISTS" in upper ->
                "$ERRORS_DOCS_URL/install-failed-already-exists"

            "NO_MATCHING_ABIS" in upper || "CPU_ABI" in upper || ("ABI" in upper && "NOT SUPPORTED" in upper) ->
                "$ERRORS_DOCS_URL/install-failed-no-matching-abis"

            "INSUFFICIENT_STORAGE" in upper || "NO SPACE" in upper || ("STORAGE" in upper && "FULL" in upper) ->
                "$ERRORS_DOCS_URL/install-failed-insufficient-storage"

            "DEPRECATED_SDK_VERSION" in upper || "OLDER_SDK" in upper ->
                "$ERRORS_DOCS_URL/install-failed-deprecated-sdk-version"

            "MIUI" in upper || "HYPEROS" in upper ->
                "$ERRORS_DOCS_URL/miui-optimization-blocks-install"

            "PARSE" in upper || "PARSING" in upper ->
                "$ERRORS_DOCS_URL/there-was-a-problem-parsing-the-package"

            "INVALID_APK" in upper || "NO_CERTIFICATES" in upper || "BAD_SIGNATURE" in upper ->
                "$ERRORS_DOCS_URL/install-failed-invalid-apk"

            "INSTALL_FAILED" in upper || "FAILED" in upper ->
                "$ERRORS_DOCS_URL/app-not-installed"

            else -> ERRORS_DOCS_URL
        }
    }

    fun buildErrorReport(
        target: DialogTarget?,
        errorMessage: String,
    ): String {
        val appName = target?.appName?.takeIf { it.isNotBlank() } ?: "Unknown App"
        val pkg = target?.packageName?.takeIf { it.isNotBlank() } ?: "Unknown"
        return buildString {
            appendLine("=== Universal Installer Issue Report ===")
            appendLine("• App: $appName ($pkg)")
            appendLine("• App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("• Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            if (Build.DISPLAY.isNotBlank()) {
                appendLine("• ROM/Build: ${Build.DISPLAY}")
            }
            appendLine("----------------------------------------")
            appendLine("• Error Message:")
            appendLine(errorMessage.ifBlank { "(No detailed error message)" })
            appendLine("========================================")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallSupportBottomSheet(
    target: DialogTarget?,
    errorMessage: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val copiedToast = stringResource(R.string.dialog_support_telegram_copied)

    val reportText = InstallSupportHelper.buildErrorReport(target, errorMessage)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SupportAgent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dialog_support_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.dialog_support_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error preview card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (target != null && target.appName.isNotBlank()) {
                        Text(
                            text = target.appName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = errorMessage.ifBlank { stringResource(R.string.dialog_failed_title) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Telegram Community
            SupportOptionCard(
                icon = painterResource(R.drawable.ic_telegram),
                title = stringResource(R.string.dialog_support_telegram_title),
                subtitle = stringResource(R.string.dialog_support_telegram_sub),
                badgeText = stringResource(R.string.dialog_support_telegram_badge),
                onClick = {
                    scope.launch {
                        val clip = ClipData.newPlainText("install-error-report", reportText)
                        clipboard.setClipEntry(ClipEntry(clip))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_LONG).show()
                        context.openUriSafely(TELEGRAM_SUPPORT_URL)
                        onDismiss()
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. GitHub Issue
            SupportOptionCard(
                icon = painterResource(R.drawable.ic_github),
                title = stringResource(R.string.dialog_support_github_title),
                subtitle = stringResource(R.string.dialog_support_github_sub),
                onClick = {
                    val issueTitle = Uri.encode("[Bug]: Install failed - ${target?.appName ?: target?.packageName ?: "Unknown"}")
                    val issueBody = Uri.encode(reportText)
                    val url = "$GITHUB_NEW_ISSUE_URL?title=$issueTitle&body=$issueBody"
                    context.openUriSafely(url)
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Share Error Details
            SupportOptionCard(
                icon = rememberVectorPainter(Icons.Rounded.Share),
                title = stringResource(R.string.dialog_support_share_title),
                subtitle = stringResource(R.string.dialog_support_share_sub),
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, reportText)
                        putExtra(Intent.EXTRA_SUBJECT, "Universal Installer Error Report")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. FAQ / Troubleshooting
            SupportOptionCard(
                icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.MenuBook),
                title = stringResource(R.string.dialog_support_faq_title),
                subtitle = stringResource(R.string.dialog_support_faq_sub),
                onClick = {
                    val targetUrl = InstallSupportHelper.resolveErrorDocUrl(errorMessage)
                    context.openUriSafely(targetUrl)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SupportOptionCard(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
