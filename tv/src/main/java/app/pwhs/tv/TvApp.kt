package app.pwhs.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.pwhs.core.data.AppRepository
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Top-level TV shell: an Install | Manage tab strip built from plain tv-material3 buttons
 * (deliberately NOT tv-material3 TabRow — same alpha cohort that crashed via tv-foundation;
 * Button/Surface are the components we've verified work).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp(repo: AppRepository, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }

    Surface(modifier = modifier.fillMaxSize(), shape = RectangleShape) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TabButton("Install", selected = tab == 0) { tab = 0 }
                TabButton("Manage", selected = tab == 1) { tab = 1 }
                TabButton("Settings", selected = tab == 2) { tab = 2 }
            }
            when (tab) {
                0 -> ReceiveScreen(modifier = Modifier.weight(1f))
                1 -> ManageScreen(repo = repo, modifier = Modifier.weight(1f))
                else -> SettingsScreen(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
