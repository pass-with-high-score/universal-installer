package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.launch

private val VIRUSTOTAL_API_KEY_FORMAT = Regex("[0-9a-fA-F]{64}")
private const val VIRUSTOTAL_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

/**
 * An in-place bottom sheet for configuring VirusTotal.
 *
 * Before installation, if no API key is configured, walks the user through a quick 3-step story:
 * 1. Danger of unverified sideloaded APKs.
 * 2. Universal Installer's free VirusTotal solution.
 * 3. Adding their free personal API key.
 *
 * Once viewed, subsequent invocations skip straight to step 3.
 * Supports a "Don't ask again" option so the user is never prompted before install if they opt out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusTotalSetupSheet(
    initialKey: String = "",
    onDismiss: () -> Unit,
    onSaveAndScan: ((String) -> Unit)? = null,
    onProceedInstall: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val storedPrefs by context.dataStore.data.collectAsState(initial = null)
    val hasSeenStory = storedPrefs?.get(SharedPrefsKeys.VIRUSTOTAL_ONBOARDING_SEEN) ?: false

    var currentStep by remember(hasSeenStory) {
        mutableIntStateOf(if (hasSeenStory) 3 else 1)
    }

    var dontAskAgain by remember { mutableStateOf(false) }

    var apiKey by remember(initialKey) { mutableStateOf(initialKey) }
    val trimmed = apiKey.trim()
    val looksValid = VIRUSTOTAL_API_KEY_FORMAT.matches(trimmed)

    LaunchedEffect(Unit) {
        AnalyticsHelper.logVirusTotalPromptShown(
            isFirstTime = !hasSeenStory,
            source = if (onProceedInstall != null) "pre_install" else "manual"
        )
        AnalyticsHelper.logVirusTotalStoryStep(currentStep)
    }

    fun markStorySeen(persistDontAskAgain: Boolean = false) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.VIRUSTOTAL_ONBOARDING_SEEN] = true
                prefs[PreferencesKeys.VIRUSTOTAL_ONBOARDING_SEEN] = true
                if (persistDontAskAgain && dontAskAgain) {
                    prefs[SharedPrefsKeys.VIRUSTOTAL_DONT_ASK_AGAIN] = true
                    prefs[PreferencesKeys.VIRUSTOTAL_DONT_ASK_AGAIN] = true
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            targetValue != SheetValue.Hidden
        },
    )

    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsHelper.logVirusTotalAction("dismiss", dontAskAgain = dontAskAgain)
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
                .animateContentSize()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // Top bar with close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(onClick = {
                    AnalyticsHelper.logVirusTotalAction("dismiss", dontAskAgain = dontAskAgain)
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 40)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "VirusTotalStoryStep",
            ) { step ->
                when (step) {
                    1 -> {
                        // Step 1: Warning about sideloaded APK hazards
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            VirusTotalStoryPage(
                                badgeText = stringResource(R.string.virustotal_story_step1_badge),
                                titleText = stringResource(R.string.virustotal_story_step1_title),
                                descText = stringResource(R.string.virustotal_story_step1_desc),
                                animation = { VirusTotalDangerAnimation() },
                            )

                            VirusTotalStoryIndicators(currentStep = 1)

                            Spacer(modifier = Modifier.height(8.dp))

                            VirusTotalStoryActions(
                                onProceedInstall = onProceedInstall,
                                onSkip = {
                                    markStorySeen(persistDontAskAgain = false)
                                    AnalyticsHelper.logVirusTotalAction("skip_and_install")
                                    onDismiss()
                                    onProceedInstall?.invoke()
                                },
                                onNext = {
                                    AnalyticsHelper.logVirusTotalAction("next")
                                    currentStep = 2
                                    AnalyticsHelper.logVirusTotalStoryStep(2)
                                },
                            )
                        }
                    }

                    2 -> {
                        // Step 2: Solution with VirusTotal
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            VirusTotalStoryPage(
                                badgeText = stringResource(R.string.virustotal_story_step2_badge),
                                titleText = stringResource(R.string.virustotal_story_step2_title),
                                descText = stringResource(R.string.virustotal_story_step2_desc),
                                animation = { VirusTotalSolutionAnimation() },
                            )

                            VirusTotalStoryIndicators(currentStep = 2)

                            Spacer(modifier = Modifier.height(8.dp))

                            VirusTotalStoryActions(
                                onProceedInstall = onProceedInstall,
                                onSkip = {
                                    markStorySeen(persistDontAskAgain = false)
                                    AnalyticsHelper.logVirusTotalAction("skip_and_install")
                                    onDismiss()
                                    onProceedInstall?.invoke()
                                },
                                onNext = {
                                    markStorySeen()
                                    AnalyticsHelper.logVirusTotalAction("next")
                                    currentStep = 3
                                    AnalyticsHelper.logVirusTotalStoryStep(3)
                                },
                            )
                        }
                    }

                    else -> {
                        // Step 3: Enter API Key
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_virustotal_logo),
                                    contentDescription = "VirusTotal",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.virustotal_story_step3_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.virustotal_story_step3_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (!hasSeenStory) {
                                VirusTotalStoryIndicators(
                                    currentStep = 3,
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                )
                            }

                            // Input field with Paste button
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text(stringResource(R.string.setting_vt_api_key_title)) },
                                placeholder = { Text(stringResource(R.string.virustotal_setup_key_hint)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService<ClipboardManager>()
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                        if (!clipText.isNullOrBlank()) {
                                            apiKey = clipText
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentPaste,
                                            contentDescription = stringResource(R.string.virustotal_setup_paste),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    if (trimmed.isNotEmpty() && !looksValid) {
                                        Text(
                                            text = stringResource(R.string.virustotal_setup_key_malformed),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                isError = trimmed.isNotEmpty() && !looksValid,
                            )

                            // Link to get key
                            TextButton(
                                onClick = {
                                    AnalyticsHelper.logVirusTotalAction("get_api_key")
                                    uriHandler.openUriSafely(context, VIRUSTOTAL_API_KEY_URL)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.virustotal_setup_get_free_key),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            // Don't ask again toggle with note
                            if (onProceedInstall != null) {
                                VirusTotalDontAskAgainSection(
                                    checked = dontAskAgain,
                                    onCheckedChange = { dontAskAgain = it },
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action buttons: [ Bỏ qua & Cài đặt ] and [ Lưu & Quét ngay ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        markStorySeen(persistDontAskAgain = true)
                                        AnalyticsHelper.logVirusTotalAction("skip_and_install", dontAskAgain = dontAskAgain)
                                        onDismiss()
                                        onProceedInstall?.invoke()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = if (onProceedInstall != null) {
                                            stringResource(R.string.virustotal_story_skip_and_install)
                                        } else {
                                            stringResource(R.string.virustotal_setup_later)
                                        }
                                    )
                                }

                                Button(
                                    onClick = {
                                        val finalKey = trimmed
                                        markStorySeen(persistDontAskAgain = false)
                                        AnalyticsHelper.logVirusTotalAction("save_and_scan", hasKey = true)
                                        scope.launch {
                                            context.dataStore.edit { prefs ->
                                                prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = finalKey
                                                prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = finalKey
                                            }
                                            onDismiss()
                                            onSaveAndScan?.invoke(finalKey)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = trimmed.isNotEmpty(),
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (onSaveAndScan != null) R.string.virustotal_setup_save_and_scan
                                            else R.string.virustotal_setup_save_only
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for [VirusTotalSetupSheet].
 */
@Composable
fun VirusTotalSetupDialog(
    initialKey: String = "",
    onDismiss: () -> Unit,
    onSaveAndScan: ((String) -> Unit)? = null,
    onProceedInstall: (() -> Unit)? = null,
) = VirusTotalSetupSheet(
    initialKey = initialKey,
    onDismiss = onDismiss,
    onSaveAndScan = onSaveAndScan,
    onProceedInstall = onProceedInstall,
)
