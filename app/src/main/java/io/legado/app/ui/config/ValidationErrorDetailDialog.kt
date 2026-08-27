package io.legado.app.ui.config

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.legado.app.R
import io.legado.app.help.storage.ValidationResult

/**
 * 备份文件验证详情弹窗。
 *
 * 展示单个文件的验证结果：提示信息、详情、缺失字段以及是否可恢复。
 */
@Composable
fun ValidationErrorDetailDialog(
    result: ValidationResult,
    onDismiss: () -> Unit
) {
    val message = buildAnnotatedString {
        append(result.message)
        if (result.details.isNotBlank()) {
            append("\n\n${result.details}")
        }
        if (result.missingFields.isNotEmpty()) {
            append("\n\n${stringResource(R.string.fvd_missing_fields, result.missingFields.joinToString(", "))}")
        }
        append("\n\n")
        withStyle(SpanStyle(color = Color.Unspecified)) {
            if (result.canRestore) {
                append("✅ ")
            } else {
                append("❌ ")
            }
        }
        if (result.canRestore) {
            append(stringResource(R.string.fvd_can_restore))
        } else {
            append(stringResource(R.string.fvd_cannot_restore))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        title = {
            Text(text = result.fileName)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
