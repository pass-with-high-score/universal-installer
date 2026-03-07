package com.nqmgaming.universalinstaller.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.nqmgaming.universalinstaller.presentation.composable.BottomBar
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs

@Composable
fun UniversalInstallerApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val start = NavGraphs.root.defaultStartDirection

    // Single top-level Scaffold with bottom bar only.
    // Each screen provides its own topBar and FAB via its own Scaffold.
    // We pass bottom bar padding so screens know to avoid overlapping.
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { BottomBar(navController) },
        ) { innerPadding ->
            DestinationsNavHost(
                navController = navController,
                navGraph = NavGraphs.root,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                start = start,
            )
        }
    }
}