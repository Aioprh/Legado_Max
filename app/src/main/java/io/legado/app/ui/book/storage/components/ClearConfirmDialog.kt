package io.legado.app.ui.book.storage.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog

// ### UI组件
// 7. ClearConfirmDialog.kt

// - 作用 ：清理确认对话框组件
// - 主要功能 ：
//   - ClearConfirmDialog - 单个缓存清理确认
//   - ClearAllConfirmDialog - 一键清理确认

@Composable
fun ClearConfirmDialog(
    targetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppConfirmDialog(
        title = stringResource(R.string.storage_clear_confirm_title),
        text = stringResource(R.string.storage_clear_confirm_msg, targetName),
        confirmText = stringResource(R.string.ok),
        destructive = true,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss
    )
}

@Composable
fun ClearAllConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppConfirmDialog(
        title = stringResource(R.string.storage_clear_all),
        text = stringResource(R.string.storage_clear_all_msg),
        confirmText = stringResource(R.string.ok),
        destructive = true,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss
    )
}
