package app.pwhs.universalinstaller.wearos.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

@Composable
fun LanguageScreen(selectedTag: String, onSelect: (String) -> Unit) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()

        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(stringResource(R.string.settings_language))
                    }
                }
                items(AppLocale.TAGS.size) { index ->
                    val tag = AppLocale.TAGS[index]
                    RadioButton(
                        selected = tag == selectedTag,
                        onSelect = { onSelect(tag) },
                        label = {
                            Text(
                                text = if (tag == AppLocale.SYSTEM) {
                                    stringResource(R.string.settings_language_system)
                                } else {
                                    AppLocale.displayName(tag)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun LanguageScreenPreview() {
    UniversalInstallerTheme { LanguageScreen(selectedTag = "vi", onSelect = {}) }
}
