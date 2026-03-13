package com.nqmgaming.universalinstaller.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nqmgaming.universalinstaller.domain.installer.InstallMethod
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingsScreen(
    navigator: DestinationsNavigator,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Installation Method",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Choose the default method used for installing applications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            InstallMethodOption(
                title = "Standard (Ackpine Session API)",
                description = "Standard Android installation using PackageInstaller. Requires user confirmation for each app.",
                method = InstallMethod.STANDARD,
                selectedMethod = uiState.selectedInstallMethod,
                onSelected = { viewModel.updateInstallMethod(it) }
            )

            InstallMethodOption(
                title = "Shizuku (Silent Install)",
                description = if (uiState.isShizukuAvailable) "Seamless background installation without prompts." else "Shizuku is not running or not supported.",
                method = InstallMethod.SHIZUKU,
                selectedMethod = uiState.selectedInstallMethod,
                enabled = uiState.isShizukuAvailable,
                onSelected = { viewModel.updateInstallMethod(it) }
            )

            InstallMethodOption(
                title = "Root (Superuser)",
                description = if (uiState.isRootAvailable) "Background installation using root privileges." else "Root access not detected.",
                method = InstallMethod.ROOT,
                selectedMethod = uiState.selectedInstallMethod,
                enabled = uiState.isRootAvailable,
                onSelected = { viewModel.updateInstallMethod(it) }
            )
        }
    }
}

@Composable
fun InstallMethodOption(
    title: String,
    description: String,
    method: InstallMethod,
    selectedMethod: InstallMethod,
    enabled: Boolean = true,
    onSelected: (InstallMethod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelected(method) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = method == selectedMethod,
            enabled = enabled,
            onClick = { onSelected(method) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            val alpha = if (enabled) 1f else 0.5f
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}
