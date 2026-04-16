package app.pwhs.universalinstaller.presentation.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

@Destination<RootGraph>
@Composable
fun SyncScreen(modifier: Modifier = Modifier) {
    SyncUi(modifier = modifier)
}

@Composable
private fun SyncUi(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(text = "Sync Screen")
    }
}

@Preview
@Composable
private fun SyncScreenPreview() {
    UniversalInstallerTheme {
        SyncUi()
    }
}