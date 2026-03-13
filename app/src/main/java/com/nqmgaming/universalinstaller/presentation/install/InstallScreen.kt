package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme
import com.nqmgaming.universalinstaller.util.extension.getDisplayName
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Destination<RootGraph>
@Composable
fun InstallScreen(modifier: Modifier = Modifier, fileUriString: String, navigator: DestinationsNavigator, viewModel: InstallViewModel = koinViewModel()) {
    val error by viewModel.error.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    InstallUi(
        modifier = modifier,
        passedUri = fileUriString,
        onParse = viewModel::parseApp,
        onInstall = { uri, isApks, name, deleteAfter -> viewModel.installPackage(uri, isApks, name, deleteAfter) },
        error = error,
        uiState = uiState,
        onBack = { navigator.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    passedUri: String? = null,
    onParse: (Uri) -> Unit = {},
    onInstall: (Uri, Boolean, String, Boolean) -> Unit = { _, _, _, _ -> },
    error: String? = null,
    uiState: InstallUiState = InstallUiState(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }
    val bottomPadding by animateDpAsState(
        targetValue = if (startAnimation) 100.dp else 0.dp,
        animationSpec = spring(), label = "fab_anim"
    )
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    
    val selectedUri = remember { mutableStateOf<Uri?>(null) }
    var deleteAfterInstall by remember { mutableStateOf(false) }
    
    LaunchedEffect(passedUri) {
        if (passedUri != null) {
            val uri = Uri.parse(passedUri)
            selectedUri.value = uri
            onParse(uri)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Install Detail",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                item {
                    uiState.parsedAppInfo?.let { appInfo ->
                        AppInfoCard(
                            appInfo = appInfo,
                            existingAppInfo = uiState.existingAppInfo,
                            deleteAfterInstall = deleteAfterInstall,
                            onDeleteCheckedChange = { deleteAfterInstall = it },
                            onInstallClick = {
                                selectedUri.value?.let { uri ->
                                    val name = context.contentResolver.getDisplayName(uri)
                                    onInstall(uri, appInfo.isApks, name, deleteAfterInstall)
                                }
                            }
                        )
                    }
                }

                items(uiState.sessionsProgress.size) { index ->
                    val progress = uiState.sessionsProgress[index]
                    val session = uiState.sessions.find { it.id == progress.id }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Installing: ${session?.name ?: "Unknown"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val progressFloat = if (progress.progressMax > 0) 
                                progress.currentProgress.toFloat() / progress.progressMax.toFloat() 
                                else 0f
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${progress.currentProgress} / ${progress.progressMax}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.End)
                            )
                            session?.error?.let { err ->
                                if (err.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoCard(
    appInfo: AppInfo, 
    existingAppInfo: ExistingAppInfo?, 
    deleteAfterInstall: Boolean,
    onDeleteCheckedChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appInfo.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap().asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(80.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Version: ${appInfo.versionName} (${appInfo.versionCode})",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Size: ${appInfo.size / (1024 * 1024)} MB",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            if (existingAppInfo != null) {
                val isDowngrade = appInfo.versionCode < existingAppInfo.versionCode
                val isSame = appInfo.versionCode == existingAppInfo.versionCode
                
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (isDowngrade) MaterialTheme.colorScheme.errorContainer 
                                         else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isDowngrade) "Warning: Downgrade!"
                                   else if (isSame) "Same Version Installed"
                                   else "Update Available",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isDowngrade) MaterialTheme.colorScheme.onErrorContainer 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Current: ${existingAppInfo.versionName} (${existingAppInfo.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDowngrade) MaterialTheme.colorScheme.onErrorContainer 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "New: ${appInfo.versionName} (${appInfo.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDowngrade) MaterialTheme.colorScheme.onErrorContainer 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                Text(
                    text = "New Installation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = deleteAfterInstall,
                    onCheckedChange = onDeleteCheckedChange
                )
                Text(
                    text = "Delete original file after installation",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onInstallClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Install App")
            }
        }
    }
}

@Preview
@Composable
private fun InstallScreenPreview() {
    UniversalInstallerTheme {
        InstallUi()
    }
}