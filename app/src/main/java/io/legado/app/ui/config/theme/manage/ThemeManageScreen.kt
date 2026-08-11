package io.legado.app.ui.config.theme.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.theme.manage.components.ThemeCard
import io.legado.app.ui.config.theme.manage.components.ThemeEditDialog
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.ConfigTab
import io.legado.app.ui.config.widget.DayNightPager
import io.legado.app.ui.config.widget.ImportFromClipboardAction
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberConfigManageState

@Composable
fun ThemeManageScreen(
    viewModel: ThemeManageViewModel,
    onBackClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportEmpty: () -> Unit,
    onImportFailed: () -> Unit,
    onSelectImage: () -> Unit,
    onShareJson: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
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
    val dayItems = remember(allItems) { allItems.filter { !it.config.isNightTheme } }
    val nightItems = remember(allItems) { allItems.filter { it.config.isNightTheme } }
    val visibleItems = if (state.tab == ConfigTab.DAY) dayItems else nightItems
    val visibleKeys = remember(visibleItems) { visibleItems.map { it.key } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentConfig = remember(allItems, context) {
        ThemeConfig.getDurConfig(context)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ThemeEvent.Toast -> onToast(event.resId)
                is ThemeEvent.ToastMsg -> onToastMsg(event.msg)
                is ThemeEvent.ImportSuccess -> onImportFromClipboard()
                is ThemeEvent.ImportEmpty -> onImportEmpty()
                is ThemeEvent.ImportFailed -> onImportFailed()
                is ThemeEvent.ShareJson -> onShareJson(event.json)
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm()
                is ThemeEvent.Applied -> onToastMsg(appliedThemeTemplate.format(event.themeName))
            }
        }
    }

    ConfigManageScaffold(
        title = stringResource(R.string.theme_list),
        isMultiSelectMode = state.isMultiSelectMode,
        onBackClick = onBackClick,
        onExitMultiSelect = state::exitMultiSelect,
        actions = {
            if (state.isMultiSelectMode) {
                SelectAllAction(
                    isAllSelected = state.isAllSelected(visibleKeys),
                    onSelectAll = { state.selectAllVisible(visibleKeys) }
                )
            } else {
                ImportFromClipboardAction(viewModel::importFromClipboard)
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
                                viewModel.toTopSelected(state.multiSelect.selectedKeys.toSet())
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.exportSelected(state.multiSelect.selectedKeys.toSet())
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.requestDeleteSelected(state.multiSelect.selectedKeys.toSet())
                            }
                        )
                    )
                )
            } else {
                ThemeAddBottomBar(
                    onClick = {
                        val draft = viewModel.startNew(state.tab.isNight)
                        state.openEditDialog(isNew = true, editingKey = draft.editingKey)
                    }
                )
            }
        }
    ) { contentPadding ->
        DayNightPager(
            state = state,
            onTabChange = state::switchTab,
            summaryText = themeSummary,
            scrollEnabled = !state.isMultiSelectMode,
            contentPadding = contentPadding,
            dayContent = {
                ThemeList(dayItems, state, currentConfig, viewModel)
            },
            nightContent = {
                ThemeList(nightItems, state, currentConfig, viewModel)
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
                viewModel.saveEditedTheme(state.editDialog.editingKey)
                state.closeEditDialog()
            },
            onSelectImage = onSelectImage,
            onUpdateDraft = viewModel::updateDraftConfig,
            onColorClick = onColorClick,
            onBlurClick = onBlurClick
        )
    }
}

@Composable
private fun ThemeAddBottomBar(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = colorResource(R.color.background_add_button),
        border = BorderStroke(1.dp, colorResource(R.color.border_add_button))
    ) {
        Text(
            text = stringResource(R.string.add_theme),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ThemeList(
    items: List<ThemeItem>,
    state: io.legado.app.ui.config.widget.ConfigManageState,
    currentConfig: ThemeConfig.Config,
    viewModel: ThemeManageViewModel
) {
    ConfigList(
        items = items,
        itemKey = { it.key },
        itemContent = { item ->
            ThemeCard(
                item = item,
                isMultiSelectMode = state.isMultiSelectMode,
                isSelected = item.key in state.multiSelect.selectedKeys,
                isCurrent = item.config.themeName == currentConfig.themeName &&
                    item.config.isNightTheme == currentConfig.isNightTheme,
                onApply = { viewModel.applyConfig(item) },
                onEdit = {
                    val draft = viewModel.startEdit(item)
                    state.openEditDialog(draft.isNew, draft.editingKey)
                },
                onShare = { viewModel.shareItem(item) },
                onDelete = { viewModel.deleteItem(item) },
                onCopy = { viewModel.copyItem(item) },
                onLongClick = { state.enterMultiSelect(item.key) },
                onToggleSelect = { state.toggleSelection(item.key) }
            )
        }
    )
}