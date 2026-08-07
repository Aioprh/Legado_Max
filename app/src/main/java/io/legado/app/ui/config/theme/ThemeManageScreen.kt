package io.legado.app.ui.config.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.ui.config.theme.components.MultiSelectBottomBar
import io.legado.app.ui.config.theme.components.ThemeCard
import io.legado.app.ui.config.theme.components.ThemeEditDialog
import io.legado.app.ui.config.theme.components.ThemeTabRow
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors

/**
 * 主题管理主屏幕（Compose）。
 *
 * 订阅 ViewModel 的 UiState 渲染主题列表、Tab切换、多选操作栏、编辑弹窗。
 * 一次性事件（Toast、分享、Recreate等）通过 LaunchedEffect 收集并回调给 Activity。
 *
 * ## 架构
 * - **状态驱动**：所有 UI 状态由 [ThemeManageViewModel.uiState] 驱动
 * - **事件转发**：ViewModel 的一次性事件通过 Channel 向上转发，由 Activity 处理
 * - **双模式**：普通模式（应用/编辑/分享/删除）与多选模式（置顶/导出/批量删除）
 *
 * ## 参数说明
 * - `onToast`：显示资源 ID 类型的 Toast（如"请选择主题"）
 * - `onToastMsg`：显示动态字符串 Toast（如"已应用主题: xxx"）
 * - `onRecreate`：通知 Activity 重建以应用新主题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManageScreen(
    viewModel: ThemeManageViewModel = viewModel(),
    onBackClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportEmpty: () -> Unit,
    onImportFailed: () -> Unit,
    onSelectImage: () -> Unit,
    onShareJson: (String) -> Unit,
    onRecreate: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onToast: (Int) -> Unit = {},
    onToastMsg: (String) -> Unit = {},
    onColorClick: (colorKey: String, currentColor: String) -> Unit = { _, _ -> },
    onBlurClick: (currentBlur: Int) -> Unit = {}
) {
    // 订阅 UI 状态
    val uiState by viewModel.uiState.collectAsState()
    val topBarColors = pageTopBarColors()

    // 收集一次性事件，转发给 Activity 处理
    val eventFlow = viewModel.events
    androidx.compose.runtime.LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is ThemeEvent.Toast -> onToast(event.resId) // 资源ID Toast（如"请选择主题"）
                is ThemeEvent.ToastMsg -> onToastMsg(event.msg) // 动态字符串 Toast
                is ThemeEvent.ImportSuccess -> onImportFromClipboard() // 导入成功后刷新列表
                is ThemeEvent.ImportEmpty -> onImportEmpty()
                is ThemeEvent.ImportFailed -> onImportFailed()
                is ThemeEvent.ShareJson -> onShareJson(event.json) // 分享 JSON 给外部应用
                is ThemeEvent.Recreate -> onRecreate() // 应用主题后重建 Activity
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm()
                is ThemeEvent.Applied -> onToastMsg(stringResource(R.string.applied_theme_config, event.themeName))
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 顶部导航栏：标题 + 返回/全选按钮 + 导入按钮
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = stringResource(R.string.theme_list),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                },
                navigationIcon = {
                    // 返回按钮：多选模式下退出多选，否则返回上一页
                    IconButton(onClick = {
                        if (uiState.isMultiSelectMode) {
                            viewModel.exitMultiSelect()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.isMultiSelectMode) {
                        // 多选模式：全选/取消全选切换按钮
                        Text(
                            text = stringResource(R.string.select_all),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Text(
                                text = if (uiState.allVisibleSelected)
                                    stringResource(R.string.cancel)
                                else
                                    stringResource(R.string.select_all),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        // 普通模式：从剪贴板导入主题
                        IconButton(onClick = { viewModel.importFromClipboard() }) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.top_bar_import_clipboard)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 多选操作栏：置顶 / 导出 / 删除
            if (uiState.isMultiSelectMode) {
                MultiSelectBottomBar(
                    selectedCount = uiState.selectedCount,
                    onToTop = { viewModel.toTopSelected() },
                    onExport = { viewModel.exportSelected() },
                    onDelete = { viewModel.requestDeleteSelected() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 日间/夜间主题切换 Tab
            ThemeTabRow(
                selectedTab = uiState.tab,
                onTabClick = { viewModel.switchTab(it) }
            )

            if (uiState.visibleItems.isEmpty()) {
                // 空状态提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 主题卡片列表
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.visibleItems,
                        key = { it.originalIndex }
                    ) { item ->
                        ThemeCard(
                            item = item,
                            isMultiSelectMode = uiState.isMultiSelectMode,
                            isSelected = item.originalIndex in uiState.multiSelect.selectedOriginalIndices,
                            onApply = { viewModel.applyConfig(item) },
                            onEdit = { viewModel.openEditDialog(item) },
                            onShare = { viewModel.shareItem(item) },
                            onDelete = { viewModel.deleteItem(item) },
                            onLongClick = { viewModel.enterMultiSelect(item.originalIndex) },
                            onToggleSelect = { viewModel.toggleSelection(item.originalIndex) }
                        )
                    }
                    item {
                        // 底部留出安全距离，避免被系统导航栏遮挡
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // 编辑/新建主题弹窗
    if (uiState.editDialog.visible) {
        ThemeEditDialog(
            draft = uiState.editDialog.draft,
            isNew = uiState.editDialog.isNew,
            onDismiss = { viewModel.closeEditDialog() },
            onSave = { viewModel.saveEditedTheme() },
            onSelectImage = onSelectImage,
            onUpdateDraft = { viewModel.updateDraft(it) },
            onColorClick = onColorClick,
            onBlurClick = onBlurClick
        )
    }
}