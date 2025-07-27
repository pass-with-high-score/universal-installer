package com.nqmgaming.universalinstaller.presentation.uninstall

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

@Destination<RootGraph>
@Composable
fun UninstallScreen(modifier: Modifier = Modifier) {
    UninstallUi(modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UninstallUi(modifier: Modifier = Modifier) {
    var startAnimation by remember { mutableStateOf(false) }
    val bottomPadding by animateDpAsState(
        targetValue = if (startAnimation) 100.dp else 0.dp,
        animationSpec = spring()
    )
    LaunchedEffect(Unit) {
        startAnimation = true
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
                onClick = {},
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Uninstall",
                    )
                    Text(
                        text = "Uninstall",
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
            Text(text = "Uninstall Screen")

        }
    }
}

@Preview
@Composable
private fun UninstallScreenPreview() {
    UniversalInstallerTheme {
        UninstallUi()
    }
}