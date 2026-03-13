package com.nqmgaming.universalinstaller.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.nqmgaming.universalinstaller.presentation.composable.AppScaffold
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs

@Composable
fun UniversalInstallerApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val start = NavGraphs.root.defaultStartDirection

    AppScaffold(
        modifier = modifier,
    ) {
        DestinationsNavHost(
            navController = navController,
            navGraph = NavGraphs.root,
            modifier = Modifier
                .fillMaxSize(),
            start = start,
        )
    }
}