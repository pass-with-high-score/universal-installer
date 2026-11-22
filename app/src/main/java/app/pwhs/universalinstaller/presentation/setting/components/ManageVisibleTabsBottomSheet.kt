package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.BottomBarItem
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVisibleTabsBottomSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hiddenTabs by produceState(initialValue = app.pwhs.universalinstaller.presentation.composable.HiddenTabsCache.current, context) {
        context.dataStore.data.collect { prefs ->
            val tabs = prefs[PreferencesKeys.HIDDEN_BOTTOM_TABS] ?: emptySet()
            app.pwhs.universalinstaller.presentation.composable.HiddenTabsCache.current = tabs
            value = tabs
        }
    }

    val availableTabs = remember {
        BottomBarItem.entries.filter { it.activityClass != null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_manage_visible_tabs_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = stringResource(R.string.setting_manage_visible_tabs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            SettingsCardGroup(
                title = "",
                items = availableTabs.map { tab ->
                    {
                        val isVisible = tab.isAlwaysVisible || tab.name !in hiddenTabs
                        SettingsSwitchRow(
                            title = stringResource(tab.label),
                            subtitle = if (tab.isAlwaysVisible) {
                                stringResource(R.string.setting_manage_visible_tabs_always_on)
                            } else null,
                            icon = tab.icon,
                            checked = isVisible,
                            enabled = !tab.isAlwaysVisible,
                            onCheckedChange = { checked ->
                                if (!tab.isAlwaysVisible) {
                                    scope.launch {
                                        context.dataStore.edit { prefs ->
                                            val current = prefs[PreferencesKeys.HIDDEN_BOTTOM_TABS] ?: emptySet()
                                            val updated = if (checked) current - tab.name else current + tab.name
                                            app.pwhs.universalinstaller.presentation.composable.HiddenTabsCache.current = updated
                                            prefs[PreferencesKeys.HIDDEN_BOTTOM_TABS] = updated
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}
