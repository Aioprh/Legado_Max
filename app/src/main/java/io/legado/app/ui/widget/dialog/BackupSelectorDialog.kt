package io.legado.app.ui.widget.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.widget.components.dialog.BaseComposeDialogFragment
import io.legado.app.ui.widget.components.dialog.MultiSelectDialogContent

/**
 * 备份选择器弹窗。
 *
 * 这个文件只负责 Compose 弹窗展示和用户事件转发，具体加载、选择和保存逻辑交给 ViewModel。
 */
class BackupSelectorDialog : BaseComposeDialogFragment() {

    private val viewModel by viewModels<BackupSelectorViewModel>()

    @Composable
    override fun DialogContent() {
        BackupSelectorDialogContent(
            viewModel = viewModel,
            onDismiss = { dismiss() }
        )
    }
}

@Composable
fun BackupSelectorDialogContent(
    viewModel: BackupSelectorViewModel,
    onDismiss: () -> Unit
) {
    // 保持 Composable 只负责渲染；加载、选择和持久化都放在 ViewModel 中处理。
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        BackupSelectorUiState.Loading -> {
            BackupSelectorLoadingDialog(onDismiss = onDismiss)
        }

        is BackupSelectorUiState.Content -> {
            MultiSelectDialogContent(
                title = stringResource(R.string.backup_selector),
                groups = state.groups,
                selectedKeys = state.selectedKeys,
                totalSizeCalculator = viewModel::formatTotalSize,
                onSelectionChange = viewModel::onSelectionChange,
                onDismiss = {
                    viewModel.saveSelection()
                    onDismiss()
                },
                onSelectAll = viewModel::selectAll,
                onDeselectAll = viewModel::deselectAll
            )
        }
    }
}

@Composable
private fun BackupSelectorLoadingDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = pageCardContainerColor()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
