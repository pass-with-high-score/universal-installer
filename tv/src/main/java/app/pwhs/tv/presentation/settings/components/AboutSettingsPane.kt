package app.pwhs.tv.presentation.settings.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.util.openUriSafely
import app.pwhs.tv.R
import app.pwhs.tv.ui.components.QrCode

private const val TELEGRAM_URL = "https://t.me/blockads_android"
private const val GITHUB_URL = "https://github.com/pass-with-high-score/universal-installer"
private const val WEBSITE_URL = "https://universal-installer.pwhs.app/"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.pwhs.universalinstaller"
private const val SPONSOR_URL = "https://github.com/sponsors/pass-with-high-score"
private const val PRIVACY_URL = "https://universal-installer.pwhs.app/privacy"
private const val LICENSE_URL = "https://github.com/pass-with-high-score/universal-installer/blob/main/LICENSE"

data class LinkDialogInfo(
    val title: String,
    val url: String,
    val subtitle: String? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LinkQrDialog(
    info: LinkDialogInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(460.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    QrCode(data = info.url, modifier = Modifier.fillMaxSize())
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.tv_settings_scan_qr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = info.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(closeFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Done",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            context.openUriSafely(info.url)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.tv_settings_open_on_tv),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AboutSettingsPane(
    version: String,
    deviceInfo: String,
    modifier: Modifier = Modifier
) {
    var activeLinkDialog by remember { mutableStateOf<LinkDialogInfo?>(null) }

    activeLinkDialog?.let { info ->
        LinkQrDialog(info = info, onDismiss = { activeLinkDialog = null })
    }

    val openInNewTrailing: @Composable () -> Unit = {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Identity Header
        SettingsCard(onClick = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        stringResource(R.string.tv_settings_app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.tv_settings_app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (version.isNotBlank()) {
                        Text(
                            stringResource(R.string.tv_settings_version, version),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Section: Community & Support
        Text(
            stringResource(R.string.tv_settings_section_community),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_telegram_title),
            subtitle = stringResource(R.string.tv_settings_telegram_subtitle),
            iconPainter = painterResource(R.drawable.ic_telegram),
            iconTint = Color(0xFF2AABEE),
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "Telegram Community",
                    url = TELEGRAM_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_github_title),
            subtitle = stringResource(R.string.tv_settings_github_subtitle),
            iconPainter = painterResource(R.drawable.ic_github),
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "GitHub Project",
                    url = GITHUB_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_website_title),
            subtitle = stringResource(R.string.tv_settings_website_subtitle),
            icon = Icons.Default.Public,
            iconTint = Color(0xFF38BDF8),
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "Official Website",
                    url = WEBSITE_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        // Section: Feedback & Rating
        Text(
            stringResource(R.string.tv_settings_section_feedback),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_rate_title),
            subtitle = stringResource(R.string.tv_settings_rate_subtitle),
            iconPainter = painterResource(R.drawable.ic_google_play_circle),
            iconTint = Color(0xFF01875F),
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "Rate Universal Installer",
                    url = PLAY_STORE_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_sponsor_title),
            subtitle = stringResource(R.string.tv_settings_sponsor_subtitle),
            icon = Icons.Default.Favorite,
            iconTint = Color(0xFFF43F5E),
            badgeText = "SPONSOR",
            badgeColor = MaterialTheme.colorScheme.errorContainer,
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "Sponsor on GitHub",
                    url = SPONSOR_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        // Section: Device & System
        Text(
            stringResource(R.string.tv_settings_section_device),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_device_title),
            subtitle = deviceInfo,
            icon = Icons.Default.Tv,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = {}
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_cpu_arch),
            subtitle = Build.SUPPORTED_ABIS.joinToString(", "),
            icon = Icons.Default.Memory,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = {}
        )

        // Section: Legal & Privacy
        Text(
            "Legal & Privacy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_privacy_title),
            subtitle = stringResource(R.string.tv_settings_privacy_subtitle),
            icon = Icons.Default.Security,
            iconTint = Color(0xFF10B981),
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "Privacy Policy",
                    url = PRIVACY_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        TvSettingRow(
            title = stringResource(R.string.tv_settings_license_title),
            subtitle = stringResource(R.string.tv_settings_license_subtitle),
            icon = Icons.Default.Description,
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = {
                activeLinkDialog = LinkDialogInfo(
                    title = "License",
                    url = LICENSE_URL
                )
            },
            trailingContent = openInNewTrailing
        )

        Spacer(Modifier.height(16.dp))
    }
}
