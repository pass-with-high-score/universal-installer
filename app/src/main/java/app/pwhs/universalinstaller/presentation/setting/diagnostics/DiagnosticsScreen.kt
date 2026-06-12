package app.pwhs.universalinstaller.presentation.setting.diagnostics

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, OTHER }

private data class LogEntry(
    val time: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
)

private fun parseLogLine(line: String): LogEntry {
    // Exact match for adb -v threadtime: 05-09 08:48:18.385   929   969 I HfLooper: message
    val threadTimeRegex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?)\s*:\s*(.*)$""")
    val match = threadTimeRegex.find(line.trim())

    return if (match != null) {
        val (dateTime, pid, tid, levelChar, tag, message) = match.destructured
        LogEntry(
            time = dateTime.substringAfterLast(" ").trim(),
            level = when (levelChar) {
                "V" -> LogLevel.VERBOSE
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E", "F" -> LogLevel.ERROR
                else -> LogLevel.OTHER
            },
            tag = tag.trim(),
            message = message,
            raw = line
        )
    } else {
        // Fallback: search for any HH:mm:ss.SSS
        val timeRegex = Regex("""(\d{2}:\d{2}:\d{2}\.\d{3})""")
        val extractedTime = timeRegex.find(line)?.value ?: ""
        
        val level = when {
            line.contains(" E ") || line.contains(" F ") || line.contains("E/") -> LogLevel.ERROR
            line.contains(" W ") || line.contains("W/") -> LogLevel.WARN
            line.contains(" I ") || line.contains("I/") -> LogLevel.INFO
            line.contains(" D ") || line.contains("D/") -> LogLevel.DEBUG
            line.contains(" V ") || line.contains("V/") -> LogLevel.VERBOSE
            else -> LogLevel.OTHER
        }
        
        LogEntry(extractedTime, level, "", line, line)
    }
}

@Composable
private fun levelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.VERBOSE -> Color(0xFFBBBBBB)
        LogLevel.DEBUG -> Color(0xFF3382DD)
        LogLevel.INFO -> Color(0xFF2BBAC5)
        LogLevel.WARN -> Color(0xFFFF9100)
        LogLevel.ERROR -> Color(0xFFFF5353)
        LogLevel.OTHER -> Color(0xFFEEEEEE)
    }
}

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
        val text = when (selectedTab) {
            0 -> Diagnostics.buildFullReport(context, sessionLines.joinToString("\n"))
            else -> "$deviceInfo\n\n$crashText"
        }
        scope.launch {
            val clip = android.content.ClipData.newPlainText("diagnostics", text)
            clipboard.setClipEntry(ClipEntry(clip))
            snackbarHost.showSnackbar(
                message = context.getString(R.string.diagnostics_copied),
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun shareReport() {
        val report = Diagnostics.buildFullReport(context, sessionLines.joinToString("\n"))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Universal Installer Diagnostics Report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.diagnostics_share))
        )
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
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = { throw RuntimeException("Test Crash - ${System.currentTimeMillis()}") }) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = "Test Crash",
                                tint = MaterialTheme.colorScheme.error
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

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.diagnostics_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogItem(entry: LogEntry) {
    val color = levelColor(entry.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (entry.time.isNotEmpty()) {
            Text(
                text = entry.time,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFFAAAAAA)
                ),
                modifier = Modifier.width(80.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        Text(
            text = when (entry.level) {
                LogLevel.VERBOSE -> "V"
                LogLevel.DEBUG -> "D"
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
                LogLevel.OTHER -> "?"
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            ),
            modifier = Modifier.width(10.dp)
        )

        Spacer(Modifier.width(4.dp))

        if (entry.tag.isNotEmpty()) {
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = color.copy(alpha = 0.7f)
                ),
                modifier = Modifier.width(80.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
        }

        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = if (entry.level == LogLevel.OTHER) Color(0xFFEEEEEE) else color
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LogList(lines: List<String>, listState: LazyListState) {
    if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
        EmptyLogsState(stringResource(R.string.diagnostics_no_session_logs))
        return
    }

    val parsed = remember(lines) {
        lines.map { parseLogLine(it) }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TIME", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA), modifier = Modifier.width(80.dp))
                Spacer(Modifier.width(4.dp))
                Text("L", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA), modifier = Modifier.width(10.dp))
                Spacer(Modifier.width(4.dp))
                Text("TAG", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA), modifier = Modifier.width(80.dp))
                Spacer(Modifier.width(4.dp))
                Text("MESSAGE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
            }
            HorizontalDivider(color = Color(0xFF333333))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(parsed, key = { index, _ -> index }) { _, entry ->
                    LogItem(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun CrashContent(text: String, onClearRequest: () -> Unit) {
    val noCrashes = text.isBlank() || text.trim() == "No crash logs"

    Column(Modifier.fillMaxSize()) {
        if (noCrashes) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyLogsState(stringResource(R.string.diagnostics_no_crashes))
            }
        } else {
            val lines = remember(text) { text.lines() }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF121212),
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(lines) { line ->
                        val entry = remember(line) { parseLogLine(line) }
                        if (entry.time.isNotEmpty() || entry.tag.isNotEmpty()) {
                            LogItem(entry = entry)
                        } else {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp,
                                    fontSize = 10.sp,
                                ),
                                color = when {
                                    line.contains("Exception") || line.contains("CRASH") || line.contains("Error") ->
                                        Color(0xFFFF5353)
                                    line.startsWith("=") ->
                                        MaterialTheme.colorScheme.primary
                                    else ->
                                        Color(0xFFEEEEEE)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onClearRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diagnostics_clear_crashes))
            }
        }
    }
}

@Composable
private fun EmptyLogsState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
