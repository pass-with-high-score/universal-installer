package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R

private const val TELEGRAM_URL = "https://t.me/blockads_android_chat"
private const val GITHUB_URL = "https://github.com/pass-with-high-score/universal-installer"

/**
 * Top-left header action buttons linking to the Telegram community and GitHub repository.
 */
@Composable
fun CommunityActionButtons(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { uriHandler.openUriSafely(context, TELEGRAM_URL) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_telegram),
                contentDescription = stringResource(R.string.setting_telegram_title),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = { uriHandler.openUriSafely(context, GITHUB_URL) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = stringResource(R.string.setting_github_title),
                modifier = Modifier.size(22.dp),
                tint = LocalContentColor.current,
            )
        }
    }
}
