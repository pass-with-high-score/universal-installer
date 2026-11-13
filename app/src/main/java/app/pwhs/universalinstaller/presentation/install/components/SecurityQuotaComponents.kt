package app.pwhs.universalinstaller.presentation.install.components

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VtQuotaStatus
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingActivity
import kotlinx.coroutines.launch

private const val VIRUSTOTAL_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

/**
 * Displays the current AppShield daily free scan quota (`remaining/dailyLimit`) or
 * Unlimited status badge (when using Universal Installer Pro or a personal VirusTotal API key).
 */
@Composable
fun AppShieldDailyQuotaBadge(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val quotaFlow = remember(context) { VirusTotalKeyManager.observeQuotaStatus(context) }
    val quota by quotaFlow.collectAsState(initial = VtQuotaStatus())

    val containerColor = when {
        quota.isUnlimited -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        quota.remainingToday == 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }
    val contentColor = when {
        quota.isUnlimited -> MaterialTheme.colorScheme.onPrimaryContainer
        quota.remainingToday == 0 -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when {
        quota.isProUser -> Icons.Rounded.WorkspacePremium
        quota.hasPersonalKey -> Icons.Rounded.Verified
        else -> Icons.Rounded.Security
    }
    val label = when {
        quota.isProUser -> "AppShield Pro · Quét không giới hạn"
        quota.hasPersonalKey -> "VirusTotal Key cá nhân · Dự phòng không giới hạn"
        quota.remainingToday > 0 -> "AppShield miễn phí: còn ${quota.remainingToday}/${quota.dailyLimit} lượt hôm nay"
        else -> "Đã hết ${quota.dailyLimit}/${quota.dailyLimit} lượt AppShield miễn phí hôm nay"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
            if (!quota.isUnlimited && quota.dailyLimit > 0) {
                val progress = (quota.remainingToday.toFloat() / quota.dailyLimit.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (quota.remainingToday == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = contentColor.copy(alpha = 0.16f),
                )
            }
        }
    }
}

/**
 * Displays both options when free daily AppShield quota is exhausted (or key is missing/invalid):
 * 1. Enter personal VirusTotal API key (Free / Open-source backup)
 * 2. Upgrade to Universal Installer Pro (Zero-config unlimited AppShield)
 */
@Composable
fun SecurityQuotaExhaustedSection(
    onAddKey: () -> Unit,
    onGetKey: () -> Unit,
    onUpgradeProClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Mở khóa quét không giới hạn",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        // Option 1: Personal VirusTotal API key (Free / Open-source)
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Dùng VirusTotal API Key cá nhân (Miễn phí)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Phù hợp cho người dùng open-source hoặc dự phòng khi hết lượt AppShield miễn phí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onAddKey,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Nhập API Key",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = onGetKey,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Lấy Key miễn phí",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // Option 2: Upgrade to Pro
        if (onUpgradeProClick != null) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Nâng cấp Universal Installer Pro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Quét AppShield không giới hạn, không cần cấu hình API key, kèm hỗ trợ riêng 1-1.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Button(
                        onClick = onUpgradeProClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Nâng cấp Pro",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick inline dialog for pasting/saving a personal VirusTotal API key without leaving the install sheet.
 */
@Composable
fun VirusTotalQuickKeyDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val savedKey = prefs?.get(SharedPrefsKeys.VIRUSTOTAL_API_KEY).orEmpty()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    val trimmed = keyInput.trim()
    val isValidLength = trimmed.isEmpty() || trimmed.length == 64

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "VirusTotal API Key cá nhân",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập API Key miễn phí từ virustotal.com (64 ký tự hex) để quét dự phòng không giới hạn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key (64 ký tự)") },
                    singleLine = true,
                    isError = !isValidLength,
                    supportingText = if (!isValidLength) {
                        { Text("API Key phải đúng 64 ký tự (${trimmed.length}/64)") }
                    } else null,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!text.isNullOrBlank()) {
                                    keyInput = text
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Dán",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri(VIRUSTOTAL_API_KEY_URL) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Lấy Key miễn phí", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            context.startActivity(Intent(context, VirusTotalSettingActivity::class.java))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Cài đặt nâng cao", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = trimmed.length == 64,
                onClick = {
                    scope.launch {
                        context.dataStore.edit { p ->
                            p[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = trimmed
                            p[SharedPrefsKeys.SECURITY_AUTO_FALLBACK] = true
                        }
                        onSaved()
                    }
                },
            ) {
                Text("Lưu & Quét ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
