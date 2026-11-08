package app.pwhs.universalinstaller.presentation.install.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.model.FalsePositiveRequest
import app.pwhs.core.network.VirusDetecService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReportFalsePositiveDialog(
    sha256: String,
    packageName: String,
    appName: String,
    onDismiss: () -> Unit,
    virusDetecService: VirusDetecService = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isReasonValid = reason.trim().length >= 5

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Báo cáo nhận diện nhầm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Gửi báo cáo khiếu nại nếu bạn tin rằng ứng dụng này an toàn và việc gắn cờ mã độc là do nhầm lẫn (False Positive).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = appName.ifBlank { packageName },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (sha256.isNotBlank()) {
                            Text(
                                text = "SHA: ${sha256.take(16)}...",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        errorMessage = null
                    },
                    label = { Text("Lý do khiếu nại *") },
                    placeholder = { Text("Mô tả lý do APK này an toàn (tối thiểu 5 ký tự)...") },
                    isError = reason.isNotBlank() && !isReasonValid,
                    supportingText = {
                        if (reason.isNotBlank() && !isReasonValid) {
                            Text("Cần tối thiểu 5 ký tự (hiện có ${reason.trim().length})")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSubmitting,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email liên hệ (Tùy chọn)") },
                    placeholder = { Text("Để nhận thông báo khi Quản trị viên duyệt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isReasonValid) return@Button
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        val req = FalsePositiveRequest(
                            sha256 = sha256,
                            packageName = packageName,
                            appName = appName,
                            reason = reason.trim(),
                            contactEmail = email.trim(),
                        )
                        virusDetecService.submitFalsePositive(req)
                            .onSuccess { res ->
                                isSubmitting = false
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { "Đã gửi báo cáo thẩm định tới Quản trị viên!" },
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                            .onFailure { err ->
                                isSubmitting = false
                                errorMessage = err.message ?: "Gửi báo cáo thất bại, vui lòng thử lại."
                            }
                    }
                },
                enabled = isReasonValid && !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Gửi khiếu nại")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("Hủy")
            }
        },
    )
}
