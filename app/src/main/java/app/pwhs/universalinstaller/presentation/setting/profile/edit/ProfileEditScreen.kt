package app.pwhs.universalinstaller.presentation.setting.profile.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

@Composable
fun ProfileEditScreen(
    profileId: String?,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var name by remember { mutableStateOf("") }
    var installerPkg by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("Default") }
    
    // Flags
    var replaceExisting by remember { mutableStateOf<Boolean?>(null) }
    var allowTest by remember { mutableStateOf<Boolean?>(null) }
    var requestDowngrade by remember { mutableStateOf<Boolean?>(null) }
    var grantAllPermissions by remember { mutableStateOf<Boolean?>(null) }
    var bypassLowTargetSdk by remember { mutableStateOf<Boolean?>(null) }
    var allUsers by remember { mutableStateOf<Boolean?>(null) }

    // True from the moment Save is tapped until the DataStore write commits and we
    // finish the activity. Drives the spinner + disabled Save action so the user
    // can't double-tap (which would enqueue a second write / second finish).
    var isSaving by remember { mutableStateOf(false) }

    val originalProfile = remember(profileId, uiState.installerProfiles) {
        uiState.installerProfiles.find { it.id == profileId }
    }

    LaunchedEffect(originalProfile) {
        originalProfile?.let {
            name = it.name
            installerPkg = it.installerPackageName ?: ""
            backend = it.preferredBackend ?: "Default"
            replaceExisting = it.replaceExisting
            allowTest = it.allowTest
            requestDowngrade = it.requestDowngrade
            grantAllPermissions = it.grantAllPermissions
            bypassLowTargetSdk = it.bypassLowTargetSdk
            allUsers = it.allUsers
        }
    }

    ProfileEditUi(
        isNew = profileId == null,
        isSaving = isSaving,
        name = name,
        onNameChange = { name = it },
        installerPkg = installerPkg,
        onInstallerPkgChange = { installerPkg = it },
        backend = backend,
        onBackendChange = { backend = it },
        rootSupported = uiState.rootSupported,
        rootState = uiState.rootState,
        replaceExisting = replaceExisting,
        onReplaceExistingChange = { replaceExisting = it },
        allowTest = allowTest,
        onAllowTestChange = { allowTest = it },
        requestDowngrade = requestDowngrade,
        onRequestDowngradeChange = { requestDowngrade = it },
        grantAllPermissions = grantAllPermissions,
        onGrantAllPermissionsChange = { grantAllPermissions = it },
        bypassLowTargetSdk = bypassLowTargetSdk,
        onBypassLowTargetSdkChange = { bypassLowTargetSdk = it },
        allUsers = allUsers,
        onAllUsersChange = { allUsers = it },
        onBack = { (context as? android.app.Activity)?.finish() },
        onSave = {
            if (name.isNotBlank() && !isSaving) {
                isSaving = true
                val profile = (originalProfile ?: InstallerProfile(id = UUID.randomUUID().toString(), name = "")).copy(
                    name = name,
                    installerPackageName = installerPkg.ifBlank { null },
                    preferredBackend = if (backend == "Default") null else backend,
                    replaceExisting = replaceExisting,
                    allowTest = allowTest,
                    requestDowngrade = requestDowngrade,
                    grantAllPermissions = grantAllPermissions,
                    bypassLowTargetSdk = bypassLowTargetSdk,
                    allUsers = allUsers,
                )
                // finish() only after the write commits — see saveProfile's doc.
                viewModel.saveProfile(profile) {
                    (context as? android.app.Activity)?.finish()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditUi(
    isNew: Boolean,
    isSaving: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    installerPkg: String,
    onInstallerPkgChange: (String) -> Unit,
    backend: String,
    onBackendChange: (String) -> Unit,
    rootSupported: Boolean,
    rootState: app.pwhs.universalinstaller.presentation.install.controller.RootState,
    replaceExisting: Boolean?,
    onReplaceExistingChange: (Boolean?) -> Unit,
    allowTest: Boolean?,
    onAllowTestChange: (Boolean?) -> Unit,
    requestDowngrade: Boolean?,
    onRequestDowngradeChange: (Boolean?) -> Unit,
    grantAllPermissions: Boolean?,
    onGrantAllPermissionsChange: (Boolean?) -> Unit,
    bypassLowTargetSdk: Boolean?,
    onBypassLowTargetSdkChange: (Boolean?) -> Unit,
    allUsers: Boolean?,
    onAllUsersChange: (Boolean?) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 120.dp,
                title = {
                    Text(
                        text = if (isNew) stringResource(R.string.profile_create_title) else stringResource(R.string.profile_edit_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = name.isNotBlank() && !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.profile_save),
                                tint = if (name.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = navBarPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(
                    title = "General",
                    icon = Icons.Rounded.Badge
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = onNameChange,
                            label = { Text(stringResource(R.string.profile_name_label)) },
                            placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )

                        OutlinedTextField(
                            value = installerPkg,
                            onValueChange = onInstallerPkgChange,
                            label = { Text(stringResource(R.string.setting_shizuku_installer_label)) },
                            placeholder = { Text("com.android.vending") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.setting_install_mode_title),
                    icon = Icons.Rounded.Layers
                ) {
                    // Mirrors the main Settings install-mode picker: segmented row, Root
                    // dimmed when no su binary on this device, hidden entirely on store
                    // flavor (which has no libsu).
                    val rootSelectable = rootState == app.pwhs.universalinstaller.presentation.install.controller.RootState.READY ||
                        rootState == app.pwhs.universalinstaller.presentation.install.controller.RootState.DENIED ||
                        rootState == app.pwhs.universalinstaller.presentation.install.controller.RootState.UNKNOWN
                    val options = if (rootSupported) listOf("Default", "Shizuku", "Root")
                        else listOf("Default", "Shizuku")
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, b ->
                                SegmentedButton(
                                    selected = backend == b,
                                    onClick = { if (backend != b) onBackendChange(b) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    enabled = b != "Root" || rootSelectable,
                                    label = {
                                        Text(
                                            text = when (b) {
                                                "Default" -> stringResource(R.string.setting_install_mode_default)
                                                "Shizuku" -> stringResource(R.string.setting_install_mode_shizuku)
                                                else -> stringResource(R.string.setting_install_mode_root)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.dialog_menu_title),
                    icon = Icons.AutoMirrored.Rounded.List
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_replace), replaceExisting, onReplaceExistingChange)
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_allow_test), allowTest, onAllowTestChange)
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_downgrade), requestDowngrade, onRequestDowngradeChange)
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_grant_permissions), grantAllPermissions, onGrantAllPermissionsChange)
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_bypass_sdk), bypassLowTargetSdk, onBypassLowTargetSdkChange)
                        ProfileFlagItem(stringResource(R.string.setting_shizuku_all_users), allUsers, onAllUsersChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFlagItem(
    label: String,
    checked: Boolean?,
    onCheckedChange: (Boolean?) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            val state = when (checked) {
                true -> androidx.compose.ui.state.ToggleableState.On
                false -> androidx.compose.ui.state.ToggleableState.Off
                null -> androidx.compose.ui.state.ToggleableState.Indeterminate
            }
            androidx.compose.material3.TriStateCheckbox(
                state = state,
                onClick = null
            )
        },
        modifier = Modifier.clickable {
            onCheckedChange(
                when (checked) {
                    true -> false
                    false -> null
                    null -> true
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
