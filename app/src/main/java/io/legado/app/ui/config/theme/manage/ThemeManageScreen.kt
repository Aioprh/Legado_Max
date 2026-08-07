package io.legado.app.ui.config.theme.manage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
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
 * 主题管理主屏幕（Compose，组合模式重构版）。
 *
 * 通过组合通用零件组装：
 * - [rememberConfigManageState]：通用状态 Holder（Tab/多选/编辑弹窗）
 * - [ConfigManageScaffold]：通用 Scaffold + TopAppBar 骨架
 * - [DayNightPager]：通用日/夜 Tab + Pager 联动
 * - [ConfigList]：通用列表 + 空状态
 * - [ConfigMultiSelectBar]：通用多选底栏（可配置操作项）
 * - [ThemeCard]：主题专用卡片
 * - [ThemeEditDialog]：主题专用编辑弹窗
 *
 * ViewModel 只负责数据操作，不管理 UI 交互状态。
 *
 * ## Activity 回调注册
 * 颜色选择 / 虚化值 / 背景图选择的结果需要从 Activity 平台侧回传到 Compose 层，
 * 通过 `register*` 系列参数注册回调实现。
 */
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
    onBlurClick: (currentBlur: Int) -> Unit = {},
    // Activity 回调注册：平台侧结果回传
    registerOnColorResult: ((String, Int) -> Unit) -> Unit = {},
    registerOnBlurResult: ((Int) -> Unit) -> Unit = {},
    registerOnBackgroundImageResult: ((String) -> Unit) -> Unit = {},
    registerGetDraftBgPath: (() -> String?) -> Unit = {},
    registerGetSelectedIndices: (() -> Set<Int>) -> Unit = {}
) {
    // ── 通用状态（组合） ──────────────────────────────────
    val initialTab = if (AppConfig.isNightTheme) ConfigTab.NIGHT else ConfigTab.DAY
    val state = rememberConfigManageState(initialTab)

    // ── 数据订阅 ──────────────────────────────────────────
    val allItems by viewModel.items.collectAsState()
    val appliedThemeTemplate = stringResource(R.string.applied_theme_config)
    val themeSummary = stringResource(R.string.theme_summary)

    val context = androidx.compose.ui.platform.LocalContext.current
    // remember 缓存当前主题配置，避免每次 recomposition 重复读取 SharedPreferences（8次 getPref*）
    val currentConfig = remember { ThemeConfig.getDurConfig(context) }

    // ── 编辑弹窗草稿（主题专用，由 Screen 层持有） ─────────
    var editDraft by remember { mutableStateOf<ThemeConfig.Config?>(null) }

    // ── 注册回调：供 Activity 获取/回传数据 ────────────────
    DisposableEffect(Unit) {
        registerOnColorResult { key, color ->
            editDraft?.let { draft ->
                editDraft = viewModel.onColorSelected(key, color, draft)
            }
        }
        registerOnBlurResult { blur ->
            editDraft?.let { draft ->
                editDraft = viewModel.onBlurSelected(blur, draft)
            }
        }
        registerOnBackgroundImageResult { path ->
            editDraft?.let { draft ->
                editDraft = viewModel.onBackgroundImageSelected(path, draft)
            }
        }
        registerGetDraftBgPath { editDraft?.backgroundImgPath }
        registerGetSelectedIndices { state.multiSelect.selectedIndices.toSet() }
        onDispose { }
    }

    // ── 一次性事件收集 ────────────────────────────────────
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
                is ThemeEvent.Recreate -> onRecreate()
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm()
                is ThemeEvent.Applied -> onToastMsg(appliedThemeTemplate.format(event.themeName))
            }
        }
    }

    // ── 当前 Tab 的可见条目 ───────────────────────────────
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

    // ── 组装 Scaffold ────────────────────────────────────
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
        // ── 组装日/夜 Pager ───────────────────────────────
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
                                val draft = viewModel.createDraft(item)
                                editDraft = draft.config
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
                                val draft = viewModel.createDraft(item)
                                editDraft = draft.config
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

    // ── 主题专用编辑弹窗 ──────────────────────────────────
    if (state.editDialog.visible && editDraft != null) {
        ThemeEditDialog(
            draft = editDraft!!,
            isNew = state.editDialog.isNew,
            onDismiss = {
                state.closeEditDialog()
                editDraft = null
            },
            onSave = {
                viewModel.saveEditedTheme(editDraft!!, state.editDialog.editingIndex)
                state.closeEditDialog()
                editDraft = null
            },
            onSelectImage = onSelectImage,
            onUpdateDraft = { transform ->
                editDraft = transform(editDraft!!)
            },
            onColorClick = onColorClick,
            onBlurClick = onBlurClick
        )
    }
}

// ── 预览 ────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ThemeManageScreenPreview() {
    MaterialTheme {
        ThemeManageScreen(
            onBackClick = {},
            onImportFromClipboard = {},
            onImportEmpty = {},
            onImportFailed = {},
            onSelectImage = {},
            onShareJson = {},
            onRecreate = {},
            onDeleteConfirm = {},
            onToast = {},
            onToastMsg = {},
            onColorClick = { _, _ -> },
            onBlurClick = {}
        )
    }
}
