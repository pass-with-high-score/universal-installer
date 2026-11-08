package app.pwhs.universalinstaller.presentation.setting.diagnostics

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var selectedTab by remember { mutableIntStateOf(0) }

    var sessionLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var crashText by remember { mutableStateOf("") }
    var isLoadingLogs by remember { mutableStateOf(true) }
    var isLoadingCrash by remember { mutableStateOf(true) }

    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val isDebug = BuildConfig.DEBUG &&
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val deviceInfo = remember { Diagnostics.buildDeviceInfo(context) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val raw = Diagnostics.readSessionLogs()
            val lines = raw.lines()
            val crashRaw = Diagnostics.getCrashLogs(context)
            withContext(Dispatchers.Main) {
                sessionLines = lines
                isLoadingLogs = false
                crashText = crashRaw
                isLoadingCrash = false
            }
        }
    }

    fun copyCurrentTab() {
        scope.launch(Dispatchers.IO) {
            val text = when (selectedTab) {
                0 -> Diagnostics.buildFullReport(context, sessionLines.joinToString("\n"))
                else -> "${Diagnostics.buildDeviceInfo(context)}\n\n$crashText"
            }
            withContext(Dispatchers.Main) {
                val clip = android.content.ClipData.newPlainText("diagnostics", text)
                clipboard.setClipEntry(ClipEntry(clip))
                snackbarHost.showSnackbar(
                    message = context.getString(R.string.diagnostics_copied),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    fun shareReport() {
        scope.launch(Dispatchers.IO) {
            Diagnostics.shareReportFile(context, sessionLines.joinToString("\n"))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                actions = {
                    if (isDebug) {
                        IconButton(onClick = { throw RuntimeException("Test Crash - ${System.currentTimeMillis()}") }) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = "Test Crash",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = ::shareReport) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.diagnostics_share),
                        )
                    }
                    IconButton(onClick = ::copyCurrentTab) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.diagnostics_copy),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = deviceInfo,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.diagnostics_tab_session),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.diagnostics_tab_crashes),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                when {
                    selectedTab == 0 && isLoadingLogs -> LoadingState()
                    selectedTab == 0 -> LogList(lines = sessionLines, listState = listState)
                    selectedTab == 1 && isLoadingCrash -> LoadingState()
                    selectedTab == 1 -> CrashContent(
                        text = crashText,
                        onClearRequest = { showClearDialog = true },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    stringResource(R.string.diagnostics_clear_confirm_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.diagnostics_clear_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Diagnostics.clearCrashLogs(context)
                        crashText = "No crash logs"
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.diagnostics_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.diagnostics_cancel))
                }
            },
        )
    }
}
