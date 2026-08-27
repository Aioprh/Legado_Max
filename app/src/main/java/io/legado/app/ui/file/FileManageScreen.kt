package io.legado.app.ui.file

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.file.components.FileList
import io.legado.app.ui.file.components.PathBreadcrumb
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppSearchBar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog

/**
 * 文件管理主界面 (Compose 版本)
 *
 * 功能：
 * - 显示文件和文件夹列表
 * - 支持进入子目录、返回上级目录
 * - 路径导航条可点击跳转
 * - 搜索过滤文件
 * - 点击文件可打开，长按可删除
 */
@Composable
fun FileManageScreen(
    viewModel: FileManageViewModel,
    initialPath: String? = null,
    onBackClick: () -> Unit
) {
    // 从 ViewModel 收集状态
    val files by viewModel.files.collectAsStateWithLifecycle()
    val subDocs by viewModel.subDocsFlow.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // UI 状态（承载删除确认 Dialog 显隐，state-events.md §4.5）
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val topBarColor = pageTopBarContainerColor()

    LaunchedEffect(initialPath) {
        initialPath?.let { viewModel.openPath(it) }
    }

    // 返回键拦截：有 Dialog 时先关闭 Dialog，无则正常返回（§4.5）
    val hasDialog = uiState.dialog != null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(hasDialog, backDispatcher) {
        val callback = object : OnBackPressedCallback(hasDialog) {
            override fun handleOnBackPressed() = viewModel.dismissDialog()
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    // 删除确认对话框（UiState 条件渲染）
    when (val dialog = uiState.dialog) {
        is FileDialogState.DeleteConfirm -> DeleteConfirmDialog(
            fileName = dialog.file.name,
            onConfirm = { viewModel.confirmDelete(dialog.file) },
            onDismiss = { viewModel.dismissDialog() }
        )
        null -> Unit
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 顶栏与搜索栏连成一体，AppPageTopBar 容器取透明（theme-styles.md §14.2）
            Column(modifier = Modifier.background(topBarColor)) {
                AppPageTopBar(
                    title = stringResource(R.string.file_manage),
                    onBackClick = onBackClick,
                    containerColor = Color.Transparent
                ) {
                    // 用其他文件管理器打开当前路径
                    IconButton(onClick = { viewModel.openWithChooser() }) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = stringResource(R.string.open_with)
                        )
                    }
                }
                // 搜索栏
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    hint = "${stringResource(R.string.screen)} • ${stringResource(R.string.file_manage)}",
                    showClearButton = false
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 路径导航条
            PathBreadcrumb(
                subDocs = subDocs,
                onRootClick = { viewModel.goToRoot() },
                onPathClick = { index -> viewModel.goToPath(index) }
            )

            // 文件列表或空提示
            if (files.isEmpty()) {
                EmptyMessage()
            } else {
                FileList(
                    files = files,
                    lastDir = viewModel.lastDir,
                    onFileClick = { file ->
                        when {
                            file == viewModel.lastDir -> viewModel.gotoLastDir()  // 点击 ".." 返回上级
                            file.isDirectory -> viewModel.enterDir(file)         // 进入文件夹
                            else -> viewModel.openFile(file)                     // 打开文件
                        }
                    },
                    onFileLongClick = { file ->
                        if (file != viewModel.lastDir) {
                            viewModel.requestDelete(file)
                        }
                    }
                )
            }
        }
    }
}

/**
 * 空提示
 * 当文件列表为空时显示
 */
@Composable
private fun EmptyMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 删除确认对话框
 */
@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppConfirmDialog(
        title = stringResource(R.string.delete),
        text = stringResource(R.string.file_delete_confirm, fileName),
        confirmText = stringResource(R.string.delete),
        destructive = true,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss
    )
}
