package app.pwhs.tv.presentation.manage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.formatSize
import app.pwhs.tv.rememberAppIcon

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var reloadTick by remember { mutableIntStateOf(0) }
    var focusedApp by remember { mutableStateOf<InstalledApp?>(null) }
    val context = LocalContext.current

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { reloadTick++ }

    LaunchedEffect(reloadTick) {
        viewModel.loadApps()
    }

    LaunchedEffect(uiState.filteredApps, uiState.isLoading) {
        if (!uiState.isLoading && focusedApp == null && uiState.filteredApps.isNotEmpty()) {
            focusedApp = uiState.filteredApps.first()
        }
    }

    Row(modifier = modifier.fillMaxSize().background(Color(0xFF101214))) {
        // Left Column: Apps List (with Sidebar Background)
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .background(Color(0xFF181A1C)) // Darker sidebar background
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(Modifier.height(24.dp))

            // Search and Filter Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    placeholder = { Text(stringResource(R.string.tv_manage_search_placeholder), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray
                    )
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFilterChip(
                    selected = uiState.filter == AppFilter.User,
                    label = stringResource(R.string.tv_manage_filter_user),
                    onClick = { viewModel.setFilter(AppFilter.User) }
                )
                SmallFilterChip(
                    selected = uiState.filter == AppFilter.System,
                    label = stringResource(R.string.tv_manage_filter_system),
                    onClick = { viewModel.setFilter(AppFilter.System) }
                )
                SmallFilterChip(
                    selected = uiState.filter == AppFilter.Disabled,
                    label = stringResource(R.string.tv_manage_filter_disabled),
                    onClick = { viewModel.setFilter(AppFilter.Disabled) }
                )
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.isLoading) {
                    items(8) { LoadingRow() }
                } else if (uiState.filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.tv_manage_empty),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    items(uiState.filteredApps, key = { it.packageName }) { app ->
                        AppListRow(
                            app = app,
                            isSelected = focusedApp?.packageName == app.packageName,
                            onFocus = { focusedApp = app }
                        )
                    }
                }
            }
        }

        // Right Column: Details & Actions (Main Content Background)
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .background(Color(0xFF101214))
                .padding(horizontal = 48.dp, vertical = 48.dp)
        ) {
            AnimatedContent(
                targetState = focusedApp,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "detailTransition"
            ) { app ->
                if (app != null && !uiState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Text(
                            text = "Version ${app.versionName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(40.dp))

                        // Actions - Modern flat list style
                        ActionItem(label = "Open") {
                            runCatching {
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                                    context.startActivity(intent)
                                }
                            }
                        }
                        
                        ActionItem(label = "Uninstall") {
                            uninstallLauncher.launch(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                            )
                        }

                        ActionItem(label = "Extract APK") {
                            viewModel.extractApp(app.packageName, app.appName)
                        }
                        
                        ActionItem(label = "System settings") {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                        
                        // Metadata Breakdown
                        InfoBlock(label = "Storage used", value = formatSize(context, app.sizeBytes))
                        if (app.isSystemApp) InfoBlock(label = "Type", value = "System Application")
                        if (!app.enabled) InfoBlock(label = "Status", value = "Disabled")
                        
                        // Async Status (Extraction)
                        val extractState = uiState.extractState
                        if (extractState !is ExtractState.Idle) {
                            Spacer(Modifier.height(24.dp))
                            when (extractState) {
                                is ExtractState.Running -> if (extractState.packageName == app.packageName) {
                                    Text(
                                        text = "Extracting... ${(extractState.bytesCopied * 100 / extractState.totalBytes).toInt()}%",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                is ExtractState.Done -> if (extractState.appName == app.appName) {
                                    Text(text = "Extracted ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                }
                                is ExtractState.Error -> if (extractState.appName == app.appName) {
                                    Text(text = "Error: ${extractState.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                }
                                else -> {}
                            }
                        }
                        
                        Spacer(Modifier.height(64.dp))
                    }
                } else {
                    // Hero loading state or placeholder
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (uiState.isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ShimmerBox(modifier = Modifier.size(120.dp), shape = RoundedCornerShape(20.dp))
                                Spacer(Modifier.height(24.dp))
                                ShimmerBox(modifier = Modifier.width(200.dp).height(32.dp), shape = RoundedCornerShape(8.dp))
                                Spacer(Modifier.height(8.dp))
                                ShimmerBox(modifier = Modifier.width(140.dp).height(24.dp), shape = RoundedCornerShape(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppListRow(
    app: InstalledApp,
    isSelected: Boolean,
    onFocus: () -> Unit
) {
    val icon = rememberAppIcon(app.packageName, sizePx = 120)
    val shape = RoundedCornerShape(12.dp)
    
    Surface(
        onClick = { /* Detail pane updates on focus */ },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .onFocusChanged { if (it.isFocused) onFocus() },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f), // Minimal scale to avoid overlap
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = if (isSelected) Color.White else Color.LightGray,
            focusedContentColor = Color.Black
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Text(text = app.appName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionItem(
    label: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
            focusedContentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SmallFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = CircleShape
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(shape),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.05f),
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
        ),
        modifier = Modifier.height(32.dp).clip(shape)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(16.dp))
        ShimmerBox(modifier = Modifier.width(140.dp).height(20.dp), shape = RoundedCornerShape(4.dp))
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = alpha * 0.15f)),
    )
}
