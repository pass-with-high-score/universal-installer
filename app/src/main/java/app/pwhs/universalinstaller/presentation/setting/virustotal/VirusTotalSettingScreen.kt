package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import org.koin.androidx.compose.koinViewModel

private const val VIRUSTOTAL_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusTotalSettingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VirusTotalSettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Quét bảo mật APK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = navBarPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 1. Master Enable / Disable Toggle ────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!uiState.scanningDisabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setScanningDisabled(!uiState.scanningDisabled) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (!uiState.scanningDisabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(32.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bảo vệ & Quét mã độc",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (!uiState.scanningDisabled) {
                                    "Đang bật bảo vệ tự động khi cài đặt APK"
                                } else {
                                    "Đã tắt hoàn toàn tính năng quét bảo mật"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = !uiState.scanningDisabled,
                            onCheckedChange = { viewModel.setScanningDisabled(!it) },
                        )
                    }
                }
            }

            if (!uiState.scanningDisabled) {
                // ── 2. Primary Scanner Selection ─────────────────────────────
                item {
                    Text(
                        text = "Bộ quét chính",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ScannerOptionItem(
                                title = "AppShield (Tích hợp sẵn • Khuyên dùng)",
                                subtitle = "Công nghệ quét đám mây tích hợp sẵn (3 lượt miễn phí/ngày, không giới hạn với Pro).",
                                isSelected = uiState.primaryScanner != "virustotal",
                                onClick = { viewModel.setPrimaryScanner("virusdetec") },
                            )
                            ScannerOptionItem(
                                title = "VirusTotal",
                                subtitle = "Đối chiếu hơn 70 bộ máy diệt virus quốc tế bằng khóa API cá nhân của bạn.",
                                isSelected = uiState.primaryScanner == "virustotal",
                                onClick = { viewModel.setPrimaryScanner("virustotal") },
                            )
                        }
                    }
                }

                // ── 3. Orchestration & Fallback Options ─────────────────────
                item {
                    Text(
                        text = "Cơ chế dự phòng & Ngoại tuyến",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tự động chuyển bộ quét dự phòng",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Tự động chuyển sang ${if (uiState.primaryScanner == "virustotal") "AppShield" else "VirusTotal"} khi hết lượt miễn phí hoặc gặp lỗi máy chủ.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = uiState.autoFallbackEnabled,
                                    onCheckedChange = { viewModel.setAutoFallbackEnabled(it) },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Phân tích tĩnh ngoại tuyến (DEX)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Kiểm tra quyền hạn và trình theo dõi Exodus cục bộ không cần mạng.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = uiState.offlineChecksEnabled,
                                    onCheckedChange = { viewModel.setOfflineChecksEnabled(it) },
                                )
                            }
                        }
                    }
                }

                // ── 4. Personal VirusTotal API Key ───────────────────────────
                item {
                    Text(
                        text = "Khóa API VirusTotal (Tùy chọn)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ApiKeySection(
                            apiKey = uiState.apiKey,
                            onApiKeyChange = { viewModel.setApiKey(it) },
                            onPasteClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                                if (clipText.isNotEmpty()) {
                                    viewModel.setApiKey(clipText)
                                }
                            },
                            onGetFreeKeyClick = {
                                runCatching { uriHandler.openUri(VIRUSTOTAL_API_KEY_URL) }
                            },
                        )
                    }
                }

                // ── 5. Quota Status Card ─────────────────────────────────────
                item {
                    val quota = uiState.quotaStatus
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hạn mức AppShield (Tích hợp sẵn)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (quota.isProUser) {
                                        "Không giới hạn lượt quét (Đang kích hoạt gói Pro)"
                                    } else {
                                        "Đã dùng ${quota.usedToday}/${quota.dailyLimit} lượt miễn phí hôm nay" +
                                            if (quota.hasPersonalKey) " • Đã có khóa VirusTotal dự phòng" else ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
