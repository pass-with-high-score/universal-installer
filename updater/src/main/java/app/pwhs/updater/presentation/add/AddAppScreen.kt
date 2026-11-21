package app.pwhs.updater.presentation.add

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.presentation.adaptive.MaxWidthContainer
import app.pwhs.core.ui.theme.Spacing
import app.pwhs.updater.domain.model.UpdateSourceType
import app.pwhs.updater.presentation.InstalledAppItem
import app.pwhs.updater.presentation.UpdatesViewModel
import app.pwhs.updater.presentation.dialog.AppPickerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAppScreen(
    viewModel: UpdatesViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String = "",
    initialCategory: String = "",
    initialIncludePrereleases: Boolean = false,
    initialTargetPackageName: String? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var categoryText by remember(initialCategory) { mutableStateOf(initialCategory) }
    var customRegexText by remember { mutableStateOf("") }
    var includePrereleases by remember(initialIncludePrereleases) { mutableStateOf(initialIncludePrereleases) }
    var selectedApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    val detectedSourceType = remember(urlText) {
        if (urlText.isBlank()) null else UpdateSourceType.fromUrl(urlText)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.updates_dialog_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaxWidthContainer(
                    maxWidth = 680.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.L, vertical = Spacing.M),
                ) {
                    Button(
                        onClick = {
                            viewModel.addTrackedAppFromUrl(
                                context = context,
                                url = urlText.trim(),
                                includePrereleases = includePrereleases,
                                targetPackageName = selectedApp?.packageName ?: initialTargetPackageName,
                                category = categoryText.trim().takeIf { it.isNotBlank() },
                                onSuccess = onBackClick,
                            )
                        },
                        enabled = urlText.isNotBlank() && !uiState.isAdding,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (uiState.isAdding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Text("Fetching Release Info…")
                        } else {
                            Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Text(stringResource(R.string.updates_dialog_add_btn))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        MaxWidthContainer(
            maxWidth = 680.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L, vertical = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            // Source URL Section
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "App Source URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        if (detectedSourceType != null && detectedSourceType != UpdateSourceType.UNKNOWN) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = detectedSourceType.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                )
                            }
                        }
                    }

                    Text(
                        text = "Paste a GitHub repository, GitLab, Codeberg, F-Droid package, or direct APK URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text(stringResource(R.string.updates_dialog_url_label)) },
                        placeholder = { Text("https://github.com/owner/repo") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Link, contentDescription = null)
                        },
                        trailingIcon = {
                            if (urlText.isNotBlank()) {
                                IconButton(onClick = { urlText = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        urlText = clip.trim()
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                        isError = !uiState.error.isNullOrBlank(),
                    )
                }
            }

            // Link to Installed App Section
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Text(
                        text = "Installed App Link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    if (selectedApp != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedApp!!.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${selectedApp!!.packageName} (v${selectedApp!!.versionName})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { selectedApp = null }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Optionally select an installed app to guarantee exact package matching and auto-version detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedButton(
                            onClick = { showAppPicker = true },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Text("Pick from installed apps")
                        }
                    }
                }
            }

            // Configuration & Preferences Section
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Text(
                        text = "App Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    // Category
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Category (Optional)") },
                        placeholder = { Text("e.g. Tools, Games, Social") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (uiState.categories.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                        ) {
                            uiState.categories.take(5).forEach { cat ->
                                SuggestionChip(
                                    onClick = { categoryText = cat },
                                    label = { Text(cat) },
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Pre-releases switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.updates_dialog_prerelease),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Include alpha/beta/RC versions in update checks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = includePrereleases,
                            onCheckedChange = { includePrereleases = it },
                        )
                    }

                    HorizontalDivider()

                    // Asset Regex Filter
                    OutlinedTextField(
                        value = customRegexText,
                        onValueChange = { customRegexText = it },
                        label = { Text("Asset Regex Filter (Optional)") },
                        placeholder = { Text("e.g. .*-arm64-v8a.*\\.apk") },
                        leadingIcon = {
                            Icon(Icons.Rounded.FilterAlt, contentDescription = null)
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            installedApps = uiState.installedApps,
            isLoading = uiState.isLoadingInstalledApps,
            onLoadApps = { viewModel.loadInstalledApps(context) },
            onAppSelected = { app ->
                selectedApp = app
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}
