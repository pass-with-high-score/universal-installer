package app.pwhs.tv.presentation.settings

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.TvNoScale
import app.pwhs.tv.presentation.settings.components.TvSubScreenHeader
import app.pwhs.tv.util.LocaleHelper

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTag = remember { LocaleHelper.getStoredLanguage(context) }
    val backFocus = remember { FocusRequester() }

    // D-pad Back returns to Settings instead of bubbling to the Activity and exiting the app.
    BackHandler(onBack = onBack)

    // Entering this screen removes the Settings card that had focus, so land focus on the Back button.
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    val languages = remember {
        listOf(
            "" to "System Default",
            "en" to "English",
            "vi" to "Tiếng Việt",
            "zh" to "中文",
            "es" to "Español",
            "pt" to "Português",
            "ru" to "Русский",
            "hi" to "हिन्दी",
            "ar" to "العربية",
            "tr" to "Türkçe",
            "bn" to "বাংলা",
            "fa" to "فارسی"
        ).sortedBy { if (it.first.isEmpty()) "" else it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TvSubScreenHeader(
                title = stringResource(R.string.tv_language_title),
                onBack = onBack,
                backFocusRequester = backFocus
            )
        }

        items(languages) { (tag, name) ->
            val isSelected = currentTag == tag
            val shape = RoundedCornerShape(12.dp)
            Surface(
                onClick = {
                    if (!isSelected) {
                        LocaleHelper.setAppLanguage(context, tag)
                        // API 33+ recreates automatically when applicationLocales changes; pre-33
                        // we recreate ourselves so LocaleHelper.wrap() re-applies on the new context.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            (context as? Activity)?.recreate()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape),
                scale = TvNoScale,
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
