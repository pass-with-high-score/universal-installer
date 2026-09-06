package app.pwhs.universalinstaller.presentation.setting.security.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.pwhs.universalinstaller.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val PIN_DEFAULT_LENGTH = 4

sealed interface PinDialogMode {
    /** Sets up a new PIN (Enter -> Confirm) */
    data object Setup : PinDialogMode

    /** Verifies existing PIN (e.g. for install, uninstall, or settings access) */
    data class Verify(
        val titleRes: Int = R.string.pin_dialog_title_verify,
        val descRes: Int = R.string.pin_dialog_desc_install,
    ) : PinDialogMode

    /** Changes existing PIN (Verify old -> Enter new -> Confirm new) */
    data object Change : PinDialogMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDialog(
    mode: PinDialogMode,
    onDismiss: () -> Unit,
    /** For Verify mode: returns true if PIN is correct, false otherwise */
    onVerifyPin: ((String) -> Boolean)? = null,
    /** For Setup and Change modes: called with the confirmed new PIN */
    onPinConfirmed: ((String) -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var currentPin by remember { mutableStateOf("") }
    var firstEnteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Step state:
    // Setup: 0 = Enter New PIN, 1 = Confirm New PIN
    // Change: 0 = Enter Old PIN, 1 = Enter New PIN, 2 = Confirm New PIN
    // Verify: 0 = Enter PIN
    var step by remember { mutableIntStateOf(0) }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerError(msg: String) {
        errorMessage = msg
        isError = true
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -20f at 50
                    20f at 100
                    -15f at 150
                    15f at 200
                    -10f at 250
                    10f at 300
                    -5f at 350
                    0f at 400
                },
            )
            delay(500)
            currentPin = ""
            isError = false
        }
    }

    val title = when (mode) {
        PinDialogMode.Setup -> {
            if (step == 0) stringResource(R.string.pin_dialog_title_setup)
            else stringResource(R.string.pin_dialog_title_confirm)
        }
        is PinDialogMode.Verify -> stringResource(mode.titleRes)
        PinDialogMode.Change -> {
            when (step) {
                0 -> stringResource(R.string.pin_dialog_title_verify)
                1 -> stringResource(R.string.pin_dialog_title_setup)
                else -> stringResource(R.string.pin_dialog_title_confirm)
            }
        }
    }

    val description = when (mode) {
        PinDialogMode.Setup -> {
            if (step == 0) stringResource(R.string.pin_dialog_desc_setup)
            else stringResource(R.string.pin_dialog_desc_confirm)
        }
        is PinDialogMode.Verify -> stringResource(mode.descRes)
        PinDialogMode.Change -> {
            when (step) {
                0 -> stringResource(R.string.pin_dialog_desc_verify_current)
                1 -> stringResource(R.string.pin_dialog_desc_setup)
                else -> stringResource(R.string.pin_dialog_desc_confirm)
            }
        }
    }

    val mismatchErrorStr = stringResource(R.string.pin_dialog_error_mismatch)
    val incorrectErrorStr = stringResource(R.string.pin_dialog_error_incorrect)

    fun onPinComplete(pin: String) {
        when (mode) {
            PinDialogMode.Setup -> {
                if (step == 0) {
                    firstEnteredPin = pin
                    currentPin = ""
                    errorMessage = null
                    step = 1
                } else {
                    if (pin == firstEnteredPin) {
                        onPinConfirmed?.invoke(pin)
                        onSuccess?.invoke()
                    } else {
                        triggerError(mismatchErrorStr)
                        step = 0
                        firstEnteredPin = ""
                    }
                }
            }
            is PinDialogMode.Verify -> {
                val correct = onVerifyPin?.invoke(pin) ?: false
                if (correct) {
                    onSuccess?.invoke()
                } else {
                    triggerError(incorrectErrorStr)
                }
            }
            PinDialogMode.Change -> {
                when (step) {
                    0 -> {
                        val correct = onVerifyPin?.invoke(pin) ?: false
                        if (correct) {
                            currentPin = ""
                            errorMessage = null
                            step = 1
                        } else {
                            triggerError(incorrectErrorStr)
                        }
                    }
                    1 -> {
                        firstEnteredPin = pin
                        currentPin = ""
                        errorMessage = null
                        step = 2
                    }
                    else -> {
                        if (pin == firstEnteredPin) {
                            onPinConfirmed?.invoke(pin)
                            onSuccess?.invoke()
                        } else {
                            triggerError(mismatchErrorStr)
                            step = 1
                            firstEnteredPin = ""
                        }
                    }
                }
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier.padding(horizontal = 24.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Dots indicator
                PinDotsIndicator(
                    pinLength = currentPin.length,
                    maxDigits = PIN_DEFAULT_LENGTH,
                    hasError = isError,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error text
                Box(
                    modifier = Modifier.height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (errorMessage != null && isError) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Numeric Keypad
                PinNumericKeypad(
                    enabled = !isError,
                    onDigitClick = { digit ->
                        if (currentPin.length < PIN_DEFAULT_LENGTH) {
                            val newPin = currentPin + digit
                            currentPin = newPin
                            if (newPin.length == PIN_DEFAULT_LENGTH) {
                                onPinComplete(newPin)
                            }
                        }
                    },
                    onDeleteClick = {
                        if (currentPin.isNotEmpty()) {
                            currentPin = currentPin.dropLast(1)
                            errorMessage = null
                            isError = false
                        }
                    },
                    onClearClick = {
                        currentPin = ""
                        errorMessage = null
                        isError = false
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
