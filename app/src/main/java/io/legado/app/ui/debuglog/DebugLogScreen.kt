package io.legado.app.ui.debuglog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.ui.debuglog.components.DebugCategoryTabs
import io.legado.app.ui.debuglog.components.DebugLogDetailDialog
import io.legado.app.ui.debuglog.components.DebugLogItem
import io.legado.app.ui.debuglog.viewmodel.DebugLogViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 调试日志主界面（Compose实现）
 */
@Composable
fun DebugLogScreen(
    viewModel: DebugLogViewModel = viewModel(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()

    Scaffold(
        topBar = {
            DebugLogTopBar(
                isPaused = isPaused,
                onPauseToggle = { viewModel.togglePause() },
                onClear = { viewModel.clearLogs() },
                onExport = { /* TODO: 导出逻辑 */ },
                onDismiss = onDismiss
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. 分类 Tab
            DebugCategoryTabs(
                selectedCategory = selectedCategory,
                categories = DebugCategory.entries.filter { it != DebugCategory.CHECK && it != DebugCategory.CRASH },
                onCategorySelected = viewModel::selectCategory
            )

            HorizontalDivider()

            // 2. 日志列表
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        LoadingIndicator()
                    }
                    uiState.isEmpty || filteredLogs.isEmpty() -> {
                        EmptyState(message = "暂无调试日志")
                    }
                    else -> {
                        DebugLogList(
                            logs = filteredLogs,
                            onLogClick = viewModel::selectLog,
                            onCopyLog = viewModel::copyLogDetail
                        )
                    }
                }

                // 3. 详情弹窗（按需渲染）
                if (uiState.selectedLog != null) {
                    DebugLogDetailDialog(
                        log = uiState.selectedLog!!,
                        onDismiss = { viewModel.clearSelection() },
                        onCopy = { viewModel.copyLogDetail(uiState.selectedLog!!) }
                    )
                }
            }
        }
    }
}

/**
 * 顶部工具栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugLogTopBar(
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    TopAppBar(
        title = { Text("调试日志") },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        },
        actions = {
            IconButton(onClick = onPauseToggle) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "继续" else "暂停"
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "清空"
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "导出"
                )
            }
        }
    )
}

/**
 * 加载中指示器
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 日志列表容器
 */
@Composable
private fun DebugLogList(
    logs: List<DebugEvent>,
    onLogClick: (DebugEvent) -> Unit,
    onCopyLog: (DebugEvent) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(
            count = logs.size,
            key = { index -> logs[index].id }
        ) { index ->
            val log = logs[index]
            DebugLogItem(
                log = log,
                onClick = onLogClick,
                onLongClick = onCopyLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
