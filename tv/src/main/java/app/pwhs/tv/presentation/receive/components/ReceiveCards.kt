package app.pwhs.tv.presentation.receive.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.receiver.ConnectedClient
import app.pwhs.core.receiver.ReceivedApk
import app.pwhs.core.receiver.ReceivingProgress
import app.pwhs.core.receiver.ReceiverStatus
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.tv.R
import app.pwhs.tv.TvButtonNoScale
import app.pwhs.tv.TvNoScale
import app.pwhs.tv.formatSize
import app.pwhs.tv.ui.components.QrCode

@Composable
fun ReceiveContent(
    status: ReceiverStatus,
    connectedClient: ConnectedClient?,
    pending: ReceivedApk?,
    receivingProgress: ReceivingProgress?,
    installingLabel: String?,
    installFocus: FocusRequester,
    onInstall: (ReceivedApk) -> Unit,
    onDismissPending: () -> Unit,
    onDisconnectClient: () -> Unit,
    onOpenUrlDownload: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (pending != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.tv_receive_step3),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                HeroPendingCard(
                    apk = pending,
                    isInstalling = installingLabel != null,
                    installFocus = installFocus,
                    onInstall = { onInstall(pending) },
                    onDismiss = onDismissPending
                )
            }
        } else if (receivingProgress != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ReceivingProgressCard(receivingProgress)
            }
        } else if (connectedClient != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ConnectedDeviceCard(connectedClient, onDisconnect = onDisconnectClient)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val s = status) {
                    is ReceiverStatus.Running -> {
                        if (s.ip == "0.0.0.0") {
                            NoNetworkState()
                        } else {
                            CenteredHeroCard(url = s.url, ip = s.ip, port = s.port)
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                onClick = onOpenUrlDownload,
                                scale = TvNoScale,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.tv_receive_card_url_download_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(R.string.tv_receive_card_url_download_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalContentColor.current.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ReceiverStatus.Stopped -> {
                        val ctx = LocalContext.current
                        LaunchedEffect(Unit) {
                            app.pwhs.core.receiver.TvReceiver.start(ctx)
                        }
                        Text(
                            stringResource(R.string.tv_receive_starting),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroPendingCard(
    apk: ReceivedApk,
    isInstalling: Boolean,
    installFocus: FocusRequester,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val meta = apk.metadata
    val shape = RoundedCornerShape(28.dp)
    LaunchedEffect(Unit) { runCatching { installFocus.requestFocus() } }
    Surface(
        onClick = onInstall,
        scale = TvNoScale,
        modifier = Modifier.width(600.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = meta?.icon
            val imageBitmap = remember(icon) { icon?.asImageBitmap() }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("APK", style = MaterialTheme.typography.displayMedium)
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(meta?.appName ?: apk.fileName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (meta != null) {
                        val installedPkgInfo = remember(meta.packageName) {
                            runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.packageManager.getPackageInfo(meta.packageName, PackageManager.PackageInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.packageManager.getPackageInfo(meta.packageName, 0)
                                }
                            }.getOrNull()
                        }
                        val installedVersionCode = installedPkgInfo?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
                            else @Suppress("DEPRECATION") it.versionCode.toLong()
                        }

                        if (installedVersionCode != null) {
                            val isDowngrade = meta.versionCode < installedVersionCode
                            val isUpdate = meta.versionCode > installedVersionCode
                            val isSame = meta.versionCode == installedVersionCode

                            if (isDowngrade) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.found_chip_older),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            } else if (isUpdate) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.found_chip_update),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else if (isSame) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.found_chip_installed),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.found_chip_new),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                if (meta != null) {
                    Text("${meta.packageName} · v${meta.versionName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatSize(context, apk.sizeBytes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val btnShape = CircleShape
                    Button(
                        onClick = onInstall,
                        scale = TvButtonNoScale,
                        modifier = Modifier
                            .focusRequester(installFocus)
                            .weight(1f),
                        shape = ButtonDefaults.shape(btnShape)
                    ) {
                        Text(if (isInstalling) stringResource(R.string.tv_receive_installing_plain) else stringResource(R.string.tv_receive_install), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = onDismiss,
                        scale = TvButtonNoScale,
                        modifier = Modifier.weight(1f),
                        shape = ButtonDefaults.shape(btnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    ) {
                        Text(stringResource(R.string.tv_receive_dismiss), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}


