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
    val uiState by viewModel.uiState.collectAsState()
    val topBarColors = pageTopBarColors()

    val eventFlow = viewModel.events
    androidx.compose.runtime.LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is ThemeEvent.Toast -> onToast(event.resId)
                is ThemeEvent.ToastMsg -> onToastMsg(event.msg)
                is ThemeEvent.ImportSuccess -> onImportFromClipboard()
                is ThemeEvent.ImportEmpty -> onImportEmpty()
                is ThemeEvent.ImportFailed -> onImportFailed()
                is ThemeEvent.ShareJson -> onShareJson(event.json)
                is ThemeEvent.Recreate -> onRecreate()
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm()
                is ThemeEvent.Applied -> {}
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
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
                        Text(
                            text = stringResource(R.string.select_all),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        // 点击切换全选/取消全选 — 用 IconButton 包装
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
            ThemeTabRow(
                selectedTab = uiState.tab,
                onTabClick = { viewModel.switchTab(it) }
            )

            if (uiState.visibleItems.isEmpty()) {
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
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // 编辑弹窗
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