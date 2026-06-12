package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.runtime.Composable

data class DialogInnerParams(
    val id: String,
    val content: (@Composable () -> Unit)? = null
)

private val emptyInnerParams = DialogInnerParams("empty")

data class DialogParams(
    val icon: DialogInnerParams = emptyInnerParams,
    val title: DialogInnerParams = emptyInnerParams,
    val subtitle: DialogInnerParams = emptyInnerParams,
    val text: DialogInnerParams = emptyInnerParams,
    val content: DialogInnerParams = emptyInnerParams,
    val buttons: DialogInnerParams = emptyInnerParams
)
