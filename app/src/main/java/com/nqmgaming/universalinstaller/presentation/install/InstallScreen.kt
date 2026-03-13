package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme
import com.nqmgaming.universalinstaller.util.extension.getDisplayName
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Destination<RootGraph>
@Composable
fun InstallScreen(modifier: Modifier = Modifier, viewModel: InstallViewModel = koinViewModel()) {
    val error by viewModel.error.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    InstallUi(
        modifier = modifier,
        onParse = viewModel::parseApp,
        onInstall = { uri, isApks, name -> viewModel.installPackage(uri, isApks, name) },
        error = error,
        uiState = uiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    onParse: (Uri) -> Unit = {},
    onInstall: (Uri, Boolean, String) -> Unit = { _, _, _ -> },
    error: String? = null,
    uiState: InstallUiState = InstallUiState()
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
    
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
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
                        text = "Universal Installer",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding),
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*")) // Can refine mime types later
                },
                shape = RoundedCornerShape(25.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_apk_install),
                        contentDescription = "Pick File",
                    )
                    Text(
                        text = "Select APK/APKS",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
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
                            onInstallClick = {
                                selectedUri.value?.let { uri ->
                                    val name = context.contentResolver.getDisplayName(uri)
                                    onInstall(uri, appInfo.isApks, name)
                                }
                            }
                        )
                    }
                }

                items(uiState.sessionsProgress.size) { index ->
                    val progress = uiState.sessionsProgress[index]
                    val session = uiState.sessions.find { it.id == progress.id }
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text("Installing: ${session?.name ?: "Unknown"}")
                        Text("Progress: ${progress.currentProgress} / ${progress.progressMax}")
                        session?.error?.let { err ->
                            if (err.isNotBlank()) {
                                Text("Error: $err", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoCard(appInfo: AppInfo, onInstallClick: () -> Unit) {
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