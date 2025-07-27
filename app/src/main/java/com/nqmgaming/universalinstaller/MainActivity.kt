package com.nqmgaming.universalinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nqmgaming.universalinstaller.app.UniversalInstallerApp
import com.nqmgaming.universalinstaller.ui.theme.UniversalInstallerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversalInstallerTheme {
                UniversalInstallerApp()
            }
        }
    }
}
