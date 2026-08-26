package io.legado.app.ui.file

import androidx.compose.foundation.background
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import java.io.File

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManageScreen(
    viewModel: FileManageViewModel = viewModel(),
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
            Column(modifier = Modifier.background(topBarColor)) {
                // 标题栏
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.file_manage),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
                // 搜索栏
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    hint = "${stringResource(R.string.screen)} • ${stringResource(R.string.file_manage)}"
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
 * 搜索栏组件
 * 样式：圆角背景，左侧搜索图标，右侧输入框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    val containerColor = pageCardContainerColor()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索图标
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 输入框
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                // 占位提示文字
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                // 实际输入框
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )
            }
        }
    }
}

/**
 * 路径导航条
 * 显示当前路径，支持点击跳转
 * 格式：root > folder1 > folder2 > ...
 */
@Composable
private fun PathBreadcrumb(
    subDocs: List<File>,
    onRootClick: () -> Unit,
    onPathClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val containerColor = pageCardContainerColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(containerColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 根目录项
        PathItem(
            text = "root",
            onClick = onRootClick
        )
        
        // 子目录项
        subDocs.forEachIndexed { index, file ->
            PathItem(
                text = file.name,
                onClick = { onPathClick(index) }
            )
        }
    }
}

/**
 * 单个路径项
 * 格式：文本 + 箭头图标
 */
@Composable
private fun PathItem(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false)
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier
                .width(20.dp)
                .height(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 文件列表
 * 使用 LazyColumn 实现滚动列表
 */
@Composable
private fun FileList(
    files: List<File>,
    lastDir: File?,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 0.dp)
    ) {
        items(files, key = { it.absolutePath }) { file ->
            FileItem(
                file = file,
                isParentDir = file == lastDir,
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) }
            )
        }
    }
}

/**
 * 单个文件项
 * 显示图标 + 文件名
 * 图标类型：上级目录(..)、文件夹、普通文件（Material Icons，禁止手写 Bitmap 解码 theme-styles.md §7.3）
 */
@Composable
private fun FileItem(
    file: File,
    isParentDir: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 根据类型选择图标与语义色
        val (icon, tint) = when {
            isParentDir -> Icons.Default.SubdirectoryArrowLeft to MaterialTheme.colorScheme.onSurfaceVariant  // 返回上级图标
            file.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.primary                     // 文件夹图标
            else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant               // 普通文件图标
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = if (isParentDir) ".." else file.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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
