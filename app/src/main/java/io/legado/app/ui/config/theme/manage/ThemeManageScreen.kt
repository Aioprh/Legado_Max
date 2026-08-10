package io.legado.app.ui.config.theme.manage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigTab
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.DayNightPager
import io.legado.app.ui.config.widget.ImportFromClipboardAction
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberConfigManageState
import io.legado.app.ui.config.theme.manage.components.ThemeCard
import io.legado.app.ui.config.theme.manage.components.ThemeEditDialog

/**
 * 主题管理主界面 (Compose)
 * 
 * 架构规范：
 * 本组件设计为无状态木偶组件 (Stateless Component)。
 * 核心原则：
 * 1. collectAsState 订阅 ViewModel 吐出的单向状态流，负责纯粹的 UI 映射。
 * 2. 接收用户的所有点击/滑动交互，直接通过 lambda 抛给父级 Activity/ViewModel 处理，
 *    组件自身绝对不能截留并修改状态。
 */
@Composable
fun ThemeManageScreen(
    viewModel: ThemeManageViewModel,
    onBackClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportEmpty: () -> Unit,
    onImportFailed: () -> Unit,
    onSelectImage: () -> Unit,
    onShareJson: (String) -> Unit,
    onDeleteConfirm: (Set<Int>) -> Unit,
    onToast: (Int) -> Unit = {},
    onToastMsg: (String) -> Unit = {},
    onColorClick: (colorKey: String, currentColor: String) -> Unit = { _, _ -> },
    onBlurClick: (currentBlur: Int) -> Unit = {}
) {
    val initialTab = if (AppConfig.isNightTheme) ConfigTab.NIGHT else ConfigTab.DAY
    val state = rememberConfigManageState(initialTab)

    val allItems by viewModel.items.collectAsState()
    val editDraft by viewModel.editDraft.collectAsState()
    val appliedThemeTemplate = stringResource(R.string.applied_theme_config)
    val themeSummary = stringResource(R.string.theme_summary)

    val context = androidx.compose.ui.platform.LocalContext.current
    // 从仓库读取当前配置以供 UI 显示
    val currentConfig = remember(allItems) { 
        io.legado.app.help.config.ThemeConfig.getDurConfig(context) 
    }

    val eventFlow = viewModel.events
    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is ThemeEvent.Toast -> onToast(event.resId)
                is ThemeEvent.ToastMsg -> onToastMsg(event.msg)
                is ThemeEvent.ImportSuccess -> onImportFromClipboard()
                is ThemeEvent.ImportEmpty -> onImportEmpty()
                is ThemeEvent.ImportFailed -> onImportFailed()
                is ThemeEvent.ShareJson -> onShareJson(event.json)
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm(state.multiSelect.selectedIndices.toSet())
                is ThemeEvent.Applied -> onToastMsg(appliedThemeTemplate.format(event.themeName))
            }
        }
    }

    val dayItems = remember(allItems) {
        allItems.filter { !it.config.isNightTheme }
    }
    val nightItems = remember(allItems) {
        allItems.filter { it.config.isNightTheme }
    }
    val visibleItems = if (state.tab == ConfigTab.DAY) dayItems else nightItems
    val visibleIndices = remember(visibleItems) {
        visibleItems.map { it.originalIndex }
    }

    ConfigManageScaffold(
        title = stringResource(R.string.theme_list),
        isMultiSelectMode = state.isMultiSelectMode,
        onBackClick = onBackClick,
        onExitMultiSelect = { state.exitMultiSelect() },
        actions = {
            if (state.isMultiSelectMode) {
                SelectAllAction(
                    isAllSelected = state.isAllSelected(visibleIndices),
                    onSelectAll = { state.selectAllVisible(visibleIndices) }
                )
            } else {
                ImportFromClipboardAction {
                    viewModel.importFromClipboard()
                }
            }
        },
        bottomBar = {
            if (state.isMultiSelectMode) {
                ConfigMultiSelectBar(
                    selectedCount = state.selectedCount,
                    actions = listOf(
                        MultiSelectAction(
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = stringResource(R.string.to_top),
                            onClick = {
                                viewModel.toTopSelected(state.multiSelect.selectedIndices.toSet())
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.exportSelected(state.multiSelect.selectedIndices.toSet())
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.requestDeleteSelected(state.multiSelect.selectedIndices.toSet())
                            }
                        )
                    )
                )
            }
        }
    ) { contentPadding ->
        DayNightPager(
            state = state,
            onTabChange = { state.switchTab(it) },
            summaryText = themeSummary,
            scrollEnabled = !state.isMultiSelectMode,
            contentPadding = contentPadding,
            dayContent = {
                ConfigList(
                    items = dayItems,
                    itemKey = { it.originalIndex },
                    itemContent = { item ->
                        ThemeCard(
                            item = item,
                            isMultiSelectMode = state.isMultiSelectMode,
                            isSelected = item.originalIndex in state.multiSelect.selectedIndices,
                            isCurrent = item.config.themeName == currentConfig.themeName
                                && !item.config.isNightTheme == !currentConfig.isNightTheme,
                            onApply = { viewModel.applyConfig(item) },
                            onEdit = {
                                val draft = viewModel.startEdit(item)
                                state.openEditDialog(draft.isNew, draft.editingIndex)
                            },
                            onShare = { viewModel.shareItem(item) },
                            onDelete = { viewModel.deleteItem(item) },
                            onCopy = { viewModel.copyItem(item) },
                            onLongClick = { state.enterMultiSelect(item.originalIndex) },
                            onToggleSelect = { state.toggleSelection(item.originalIndex) }
                        )
                    }
                )
            },
            nightContent = {
                ConfigList(
                    items = nightItems,
                    itemKey = { it.originalIndex },
                    itemContent = { item ->
                        ThemeCard(
                            item = item,
                            isMultiSelectMode = state.isMultiSelectMode,
                            isSelected = item.originalIndex in state.multiSelect.selectedIndices,
                            isCurrent = item.config.themeName == currentConfig.themeName
                                && item.config.isNightTheme == currentConfig.isNightTheme,
                            onApply = { viewModel.applyConfig(item) },
                            onEdit = {
                                val draft = viewModel.startEdit(item)
                                state.openEditDialog(draft.isNew, draft.editingIndex)
                            },
                            onShare = { viewModel.shareItem(item) },
                            onDelete = { viewModel.deleteItem(item) },
                            onCopy = { viewModel.copyItem(item) },
                            onLongClick = { state.enterMultiSelect(item.originalIndex) },
                            onToggleSelect = { state.toggleSelection(item.originalIndex) }
                        )
                    }
                )
            }
        )
    }

    if (state.editDialog.visible && editDraft != null) {
        ThemeEditDialog(
            draft = editDraft!!,
            isNew = state.editDialog.isNew,
            onDismiss = {
                state.closeEditDialog()
                viewModel.clearEditDraft()
            },
            onSave = {
                viewModel.saveEditedTheme(state.editDialog.editingIndex)
                state.closeEditDialog()
            },
            onSelectImage = onSelectImage,
            onUpdateDraft = { transform ->
                viewModel.updateDraftConfig(transform)
            },
            onColorClick = onColorClick,
            onBlurClick = onBlurClick
        )
    }
}