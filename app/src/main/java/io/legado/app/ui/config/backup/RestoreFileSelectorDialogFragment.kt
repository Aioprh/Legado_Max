package io.legado.app.ui.config.backup

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.widget.components.dialog.BaseComposeDialogFragment
import io.legado.app.ui.config.FileValidationDialog
import io.legado.app.ui.config.ValidationErrorDetailDialog
import io.legado.app.help.storage.ValidationResult
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 恢复文件选择器 DialogFragment。
 *
 * 使用 Compose 渲染文件列表 + 验证状态，所有业务逻辑委托给 [RestoreFileSelectorViewModel]。
 * 生命周期由系统管理，配置变更时自动重建。
 *
 * 需要在 arguments 中传入：
 * - "backupPath": String — 已解压的备份目录路径
 */
class RestoreFileSelectorDialogFragment : BaseComposeDialogFragment() {

    private val viewModel by viewModels<RestoreFileSelectorViewModel>()

    override fun onFragmentCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val backupPath = arguments?.getString(ARG_BACKUP_PATH)
        if (backupPath.isNullOrEmpty()) {
            dismiss()
            return
        }
        viewModel.loadFiles(backupPath)
    }

    @Composable
    override fun DialogContent() {
        val backupPath = remember { arguments?.getString(ARG_BACKUP_PATH) ?: "" }
        val uiState by viewModel.uiState.collectAsState()
        var showErrorDialog by remember { mutableStateOf<ValidationResult?>(null) }

        // 收集一次性事件
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is RestoreFileSelectorEvent.Toast -> appCtx.toastOnUi(event.message)
                    RestoreFileSelectorEvent.Dismiss -> dismiss()
                }
            }
        }

        // 文件列表为空时不显示
        if (uiState.files.isEmpty() && !uiState.isRestoring) {
            return
        }

        // 恢复进行中，显示进度对话框
        if (uiState.isRestoring) {
            RestoreProgressDialog(
                progress = uiState.restoreProgress,
                onCancel = { dismiss() }
            )
            return
        }

        FileValidationDialog(
            files = uiState.files,
            validationResults = uiState.validationResults,
            onValidate = { viewModel.validateFiles(backupPath) },
            onConfirm = { selectedFiles ->
                if (selectedFiles.isEmpty()) {
                    appCtx.toastOnUi(R.string.fvd_select_at_least_one)
                    return@FileValidationDialog
                }
                viewModel.restoreSelected(backupPath, selectedFiles)
            },
            onDismiss = { dismiss() },
            onInfoClick = { result -> showErrorDialog = result }
        )

        showErrorDialog?.let { result ->
            ValidationErrorDetailDialog(
                result = result,
                onDismiss = { showErrorDialog = null }
            )
        }
    }

    companion object {
        private const val ARG_BACKUP_PATH = "backupPath"

        fun newInstance(backupPath: String): RestoreFileSelectorDialogFragment {
            return RestoreFileSelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BACKUP_PATH, backupPath)
                }
            }
        }
    }
}

/**
 * 恢复进度提示，简单的等待对话框。
 */
@Composable
private fun RestoreProgressDialog(
    progress: String,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (progress.isNotEmpty()) progress
                    else stringResource(R.string.fvd_restoring),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}