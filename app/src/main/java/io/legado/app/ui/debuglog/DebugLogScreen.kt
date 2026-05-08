package io.legado.app.ui.debuglog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.ui.debuglog.components.DebugCategoryTabs
import io.legado.app.ui.debuglog.components.DebugLogDetailDialog
import io.legado.app.ui.debuglog.components.DebugLogItem
import io.legado.app.ui.debuglog.viewmodel.DebugLogViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.utils.share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    viewModel: DebugLogViewModel = viewModel(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
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
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                        IconButton(onClick = { viewModel.togglePause() }) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "继续" else "暂停"
                            )
                        }
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清空"
                            )
                        }
                        IconButton(onClick = { 
                            val exportedText = viewModel.exportFilteredLogs()
                            context.share(exportedText, "导出调试日志")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "导出"
                            )
                        }
                    }
                )
                
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery ?: "",
                        onValueChange = { viewModel.setSearchQuery(it.ifBlank { null }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("搜索日志内容...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (!searchQuery.isNullOrBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                viewModel.setSearchQuery(searchQuery)
                            }
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DebugCategoryTabs(
                selectedCategory = selectedCategory,
                categories = DebugCategory.entries.filter { it != DebugCategory.CHECK && it != DebugCategory.CRASH },
                onCategorySelected = viewModel::selectCategory
            )

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        LoadingIndicator()
                    }
                    uiState.isEmpty || filteredLogs.isEmpty() -> {
                        EmptyState(
                            message = if (searchQuery.isNullOrBlank()) "暂无调试日志" 
                                     else "未找到匹配的日志"
                        )
                    }
                    else -> {
                        DebugLogList(
                            logs = filteredLogs,
                            onLogClick = viewModel::selectLog,
                            onCopyLog = viewModel::copyLogDetail
                        )
                    }
                }

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
