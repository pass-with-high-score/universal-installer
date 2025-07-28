package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme
import com.nqmgaming.universalinstaller.util.extension.getDisplayName
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import org.koin.androidx.compose.koinViewModel
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber

@Destination<RootGraph>(start = true)
@Composable
fun InstallScreen(modifier: Modifier = Modifier, viewModel: InstallViewModel = koinViewModel()) {
    val error = viewModel.error.collectAsState().value
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsState().value
    InstallUi(
        modifier = modifier,
        onInstall = viewModel::installPackage,
        error = error.resolve(context),
        uiState = uiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    onInstall: (splitPackage: SplitPackage.Provider, fileName: String) -> Unit = { _, _ -> },
    error: String? = null,
    uiState: InstallUiState = InstallUiState()
) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }
    val bottomPadding by animateDpAsState(
        targetValue = if (startAnimation) 100.dp else 0.dp,
        animationSpec = spring()
    )
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    val result = remember { mutableStateOf<Uri?>(null) }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            result.value = it
        }

    fun getApksFromUri(uri: Uri): SplitPackage.Provider {
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        val displayName = context.contentResolver.getDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        Timber.d("Selected file: $uri, MIME type: $mimeType")
        return when {
            (mimeType == "application/vnd.android.package-archive" || extension == "apk") -> SingletonApkSequence(
                uri,
                context
            ).toSplitPackage()

            extension in listOf("apks", "xapk", "apkm", "zip") -> ZippedApkSplits.getApksForUri(
                uri,
                context
            )
                .validate()
                .toSplitPackage()
                .filterCompatible(context)

            else -> SplitPackage.empty()
        }
    }

    fun install(uri: Uri?) {
        if (uri == null) {
            return
        }
        val name = context.contentResolver.getDisplayName(uri)
        val apks = getApksFromUri(uri)
        onInstall(apks, name)
    }

    LaunchedEffect(result.value) {
        install(result.value)
    }

    Scaffold(
        modifier = modifier, topBar = {
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
                    filePickerLauncher.launch(
                        arrayOf("*/*")
                    )
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
                        contentDescription = "Install",
                    )
                    Text(
                        text = "Install",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )

                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
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
            LazyColumn {
                items(uiState.sessions.size) {
                    val session = uiState.sessions[it]
                    Column {
                        Text(
                            text = session.name,
                        )
                        Text(
                            text = session.id.toString(),
                        )
                        Text(
                            text = session.error.resolve(context),
                        )
                        Text(
                            text = session.isCancellable.toString(),
                        )

                    }
                }
                items(uiState.sessionsProgress.size) {
                    val session = uiState.sessionsProgress[it]
                    Column {
                        Text(
                            text = session.id.toString(),
                        )
                        Text(
                            text = session.currentProgress.toString(),
                        )
                        Text(
                            text = session.progressMax.toString(),
                        )

                    }
                }
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