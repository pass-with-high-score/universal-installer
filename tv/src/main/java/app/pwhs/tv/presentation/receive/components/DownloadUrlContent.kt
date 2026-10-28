package app.pwhs.tv.presentation.receive.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.core.receiver.ReceivingProgress
import app.pwhs.tv.R
import app.pwhs.tv.TvButtonNoScale
import app.pwhs.tv.TvNoScale
import org.json.JSONArray

private const val PREFS_NAME = "tv_url_download_history"
private const val KEY_HISTORY = "recent_urls"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadUrlContent(
    isDownloading: Boolean,
    receivingProgress: ReceivingProgress?,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var inputUrl by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }

    val textFieldFocusRequester = remember { FocusRequester() }
    val downloadBtnFocusRequester = remember { FocusRequester() }
    val recentUrls = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)
        if (json != null) {
            runCatching {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    if (item.isNotBlank() && !recentUrls.contains(item)) {
                        recentUrls.add(item)
                    }
                }
            }
        }
    }

    fun saveRecentUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        recentUrls.remove(trimmed)
        recentUrls.add(0, trimmed)
        while (recentUrls.size > 8) {
            recentUrls.removeAt(recentUrls.lastIndex)
        }
        val array = JSONArray()
        recentUrls.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    fun clearRecentUrls() {
        recentUrls.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    fun submitDownload(url: String) {
        val target = url.trim()
        if (target.isNotBlank()) {
            saveRecentUrl(target)
            onDownload(target)
        }
    }

    val isShortcode = remember(inputUrl) {
        inputUrl.trim().matches(Regex("^[0-9]{4,7}$"))
    }

    val inputShape = RoundedCornerShape(16.dp)

    Box(modifier = modifier.fillMaxSize()) {
        if (isDownloading && receivingProgress != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ReceivingProgressCard(receivingProgress)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.tv_url_download_dialog_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.tv_url_download_dialog_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // URL Input Area (Unified single-container card)
                item {
                    if (!isEditing) {
                        Surface(
                            onClick = { isEditing = true },
                            scale = TvNoScale,
                            shape = ClickableSurfaceDefaults.shape(inputShape),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    shape = inputShape
                                )
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isBoxFocused = it.isFocused }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isBoxFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = inputUrl.ifEmpty { stringResource(R.string.tv_url_download_placeholder) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = if (inputUrl.isNotEmpty()) FontFamily.Monospace else FontFamily.Default,
                                    color = if (inputUrl.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), inputShape)
                                .border(2.dp, MaterialTheme.colorScheme.secondary, inputShape)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(14.dp))
                                BasicTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            isEditing = false
                                            submitDownload(inputUrl)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(textFieldFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                                                isEditing = false
                                                true
                                            } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                                isEditing = false
                                                focusManager.clearFocus()
                                                true
                                            } else false
                                        }
                                )
                            }
                            LaunchedEffect(isEditing) {
                                textFieldFocusRequester.requestFocus()
                            }
                        }
                    }
                }

                // Type Hint
                if (inputUrl.isNotBlank()) {
                    item {
                        Text(
                            text = if (isShortcode) stringResource(R.string.tv_url_download_shortcode_hint) else stringResource(R.string.tv_url_download_url_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isShortcode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Action Buttons Row (Download, Paste, Clear)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { submitDownload(inputUrl) },
                            enabled = inputUrl.isNotBlank() && !isDownloading,
                            scale = TvButtonNoScale,
                            shape = ButtonDefaults.shape(CircleShape),
                            colors = ButtonDefaults.colors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.focusRequester(downloadBtnFocusRequester)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isDownloading) stringResource(R.string.tv_url_download_in_progress) else stringResource(R.string.tv_url_download_btn),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    inputUrl = clipText
                                }
                            },
                            scale = TvButtonNoScale,
                            shape = ButtonDefaults.shape(CircleShape),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tv_url_download_paste))
                        }

                        if (inputUrl.isNotEmpty()) {
                            Button(
                                onClick = { inputUrl = "" },
                                scale = TvButtonNoScale,
                                shape = ButtonDefaults.shape(CircleShape),
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.error,
                                    focusedContentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tv_url_download_clear))
                            }
                        }
                    }
                }

                // Quick Chips Row
                item {
                    val chips = listOf("https://", "aftv.news/", ".apk", "github.com")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chips.forEach { chip ->
                            Surface(
                                onClick = {
                                    inputUrl = when {
                                        chip.startsWith("http") -> if (inputUrl.startsWith("http")) inputUrl else chip + inputUrl
                                        chip.endsWith("/") -> chip + inputUrl.removePrefix("https://").removePrefix("http://")
                                        chip.startsWith(".") -> if (inputUrl.endsWith(chip)) inputUrl else inputUrl + chip
                                        else -> if (inputUrl.contains(chip)) inputUrl else inputUrl + chip
                                    }
                                },
                                scale = TvNoScale,
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Recent History Section
                recentUrlsSection(
                    recentUrls = recentUrls,
                    onSelectUrl = { url ->
                        inputUrl = url
                        submitDownload(url)
                    },
                    onClearHistory = { clearRecentUrls() }
                )
            }
        }
    }
}
