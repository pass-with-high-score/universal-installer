package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.runtime.Composable

@Composable
fun dialogInnerWidget(
    params: DialogInnerParams
): @Composable (() -> Unit)? =
    if (params.content == null) null
    else {
        {
            params.content.invoke()
        }
    }
