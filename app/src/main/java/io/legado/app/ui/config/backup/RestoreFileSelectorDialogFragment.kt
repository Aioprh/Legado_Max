package io.legado.app.ui.config.backup

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState
import io.legado.app.ui.config.ValidationErrorDetailDialog
import io.legado.app.ui.widget.components.dialog.BaseComposeDialogFragment
import io.legado.app.ui.widget.components.dialog.MultiSelectDialogContent
import io.legado.app.ui.widget.components.dialog.MultiSelectGroup
import io.legado.app.ui.widget.components.dialog.MultiSelectItem
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 恢复文件选择器 DialogFragment。
 *
 * 复用通用多选弹窗 [MultiSelectDialogContent] 渲染文件列表 + 验证状态，
 * 所有业务逻辑委托给 [RestoreFileSelectorViewModel]。
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
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

        // 备份文件平铺为单个空名分组，复用多选弹窗样式但不渲染分组头
        val groups = remember(uiState.files) {
            listOf(
                MultiSelectGroup(
                    name = "",
                    items = uiState.files.map { file ->
                        MultiSelectItem(
                            key = file.fileName,
                            title = file.displayName,
                            size = BackupInfoHelper.formatSize(file.size),
                            rawSize = file.size,
                            group = ""
                        )
                    }
                )
            )
        }

        MultiSelectDialogContent(
            title = stringResource(R.string.fvd_title),
            groups = groups,
            selectedKeys = uiState.selectedKeys,
            totalSizeCalculator = viewModel::formatTotalSize,
            onSelectionChange = viewModel::onSelectionChange,
            onDismiss = { dismiss() },
            onSelectAll = viewModel::selectAll,
            onDeselectAll = viewModel::deselectAll,
            headerAction = {
                FilledTonalButton(onClick = { viewModel.validateFiles(backupPath) }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.fvd_detect_format))
                }
            },
            itemTrailing = { item ->
                ValidationStatusTrailing(
                    result = uiState.validationResults[item.key],
                    onInfoClick = { viewModel.showValidationDetail(item.key) }
                )
            },
            onConfirm = { viewModel.restoreSelected(backupPath) }
        )

        uiState.detailResult?.let { result ->
            ValidationErrorDetailDialog(
                result = result,
                onDismiss = { viewModel.hideValidationDetail() }
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
 * 行尾验证状态图标。
 *
 * VALID 显示对勾，WARNING/ERROR 额外提供查看详情入口，VALIDATING 显示等待动画。
 */
@Composable
private fun ValidationStatusTrailing(
    result: ValidationResult?,
    onInfoClick: () -> Unit
) {
    when (result?.state) {
        ValidationState.VALID -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.fvd_valid),
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp)
        )

        ValidationState.WARNING -> {
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.fvd_view_details),
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.fvd_warning),
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
        }

        ValidationState.ERROR -> {
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.fvd_view_details),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(R.string.fvd_invalid),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }

        ValidationState.VALIDATING -> Icon(
            imageVector = Icons.Default.HourglassEmpty,
            contentDescription = stringResource(R.string.fvd_validating),
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )

        else -> {}
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