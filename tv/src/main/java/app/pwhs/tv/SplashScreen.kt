package app.pwhs.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Brand splash: logo + name + slogan, shown briefly on launch. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = rememberAppIcon(context.packageName, sizePx = 256)
    val fade by animateFloatAsState(
        targetValue = if (logo != null) 1f else 0f,
        animationSpec = tween(600),
        label = "splashFade",
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().alpha(fade),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            logo?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(128.dp).clip(RoundedCornerShape(28.dp)),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Universal Installer",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Install anything, anywhere",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
