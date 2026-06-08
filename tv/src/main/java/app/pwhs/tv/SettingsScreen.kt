package app.pwhs.tv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val REPO_URL = "https://github.com/pass-with-high-score/universal-installer"

/** Settings/About destination for the TV app. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = rememberAppIcon(context.packageName, sizePx = 128)
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // ── About ────────────────────────────────
        item {
            SectionHeader("About")
            Row(verticalAlignment = Alignment.CenterVertically) {
                logo?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(20.dp))
                }
                Column {
                    Text("Universal Installer", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Install anything, anywhere",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (version.isNotBlank()) {
                        Text(
                            "Version $version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Install ──────────────────────────────
        item { SectionHeader("Install") }
        item {
            ActionCard(
                title = "Allow installs from this app",
                subtitle = "Open the system 'unknown sources' setting",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
        item {
            InfoCard(title = "Receiver", value = "Port 8787 · token-guarded LAN upload")
        }

        // ── Device ───────────────────────────────
        item { SectionHeader("Device") }
        item {
            InfoCard(
                title = "This device",
                value = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
        }

        // ── Source ───────────────────────────────
        item { SectionHeader("Project") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(data = REPO_URL, modifier = Modifier.size(140.dp))
                Spacer(Modifier.width(24.dp))
                Column {
                    Text("Open source", style = MaterialTheme.typography.titleMedium)
                    Text(
                        REPO_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Scan to view the project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoCard(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
