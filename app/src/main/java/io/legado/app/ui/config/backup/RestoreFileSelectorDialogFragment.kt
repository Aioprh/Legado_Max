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

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is RestoreFileSelectorEvent.Toast -> appCtx.toastOnUi(event.message)
                    RestoreFileSelectorEvent.Dismiss -> dismiss()
                }
            }
        }

        if (uiState.files.isEmpty() && !uiState.isRestoring) return

        if (uiState.isRestoring) {
            RestoreProgressDialog(
                progress = uiState.restoreProgress,
                current = uiState.restoreCurrent,
                total = uiState.restoreTotal,
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
                arguments = Bundle().apply { putString(ARG_BACKUP_PATH, backupPath) }
            }
        }
    }
}

/**
 * 与普通恢复统一的进度界面：
 * 百分比 + 当前/总数 + 当前恢复项目。
 */
@Composable
private fun RestoreProgressDialog(
    progress: String,
    current: Int,
    total: Int,
    onCancel: () -> Unit
) {
    val safeTotal = total.coerceAtLeast(1)
    val safeCurrent = current.coerceIn(0, safeTotal)
    val percent = safeCurrent * 100 / safeTotal

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { safeCurrent.toFloat() / safeTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$percent%  ·  $safeCurrent/$safeTotal",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (progress.isNotEmpty()) progress else stringResource(R.string.fvd_restoring),
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
