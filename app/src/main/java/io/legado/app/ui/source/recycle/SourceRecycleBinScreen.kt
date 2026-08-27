package io.legado.app.ui.source.recycle

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.source.recycle.components.SourceRecycleBinItem
import io.legado.app.ui.source.recycle.components.SourceRecycleDropdownMenu
import io.legado.app.ui.source.recycle.components.SourceRecycleDropdownMenuItem
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import kotlinx.coroutines.launch

@Composable
fun SourceRecycleBinScreen(
    viewModel: SourceRecycleBinViewModel,
    onBackClick: () -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // 瞬态状态（§4.2 上限 3 个）：菜单展开态保留本地；
    // searchQuery 为可空（null = 关闭，"" = 展开且为空），用 rememberSaveable 支持进程重建恢复（§4.2）
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var itemMenuExpanded by remember { mutableStateOf<Long?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val showSearch = searchQuery != null
    val filterLabel = stringResource(filter.labelRes)
    val displayedItems = remember(items, searchQuery) {
        val query = searchQuery?.trim().orEmpty()
        if (query.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                    item.key.contains(query, ignoreCase = true) ||
                    item.groupName.orEmpty().contains(query, ignoreCase = true) ||
                    item.payload.contains(query, ignoreCase = true)
            }
        }
    }
    val selectedItems = remember(displayedItems, selectedIds) {
        displayedItems.filter { it.id in selectedIds }
    }
    val isSelectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(items) {
        viewModel.pruneInvalidSelection(items)
    }

    val secondaryTextColor = pageSecondaryTextColor()

    // 返回键拦截：有 Dialog 时先关闭 Dialog，无则正常返回（state-events.md §4.5）
    val hasDialog = dialog != null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(hasDialog, backDispatcher) {
        val callback = object : OnBackPressedCallback(hasDialog) {
            override fun handleOnBackPressed() = viewModel.dismissDialog()
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 统一顶栏（theme-styles.md §14.2）；选择态切换标题/返回图标
            AppPageTopBar(
                title = if (isSelectionMode) {
                    stringResource(R.string.selected, selectedItems.size)
                } else {
                    stringResource(R.string.source_recycle_bin)
                },
                subtitle = if (isSelectionMode) {
                    stringResource(R.string.select_count, selectedItems.size, items.size)
                } else {
                    stringResource(R.string.source_recycle_bin_count, filterLabel, items.size)
                },
                onBackClick = {
                    if (isSelectionMode) {
                        viewModel.clearSelection()
                    } else {
                        onBackClick()
                    }
                },
                backIcon = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                backContentDescription = stringResource(
                    if (isSelectionMode) R.string.cancel else R.string.back
                )
            ) {
                if (isSelectionMode) {
                        IconButton(
                            enabled = selectedItems.isNotEmpty(),
                            onClick = {
                                val targets = selectedItems
                                coroutineScope.launch {
                                    // R3：suspend 获取冲突结果后设置 Dialog 状态（§4.5）
                                    viewModel.showDialog(
                                        if (viewModel.hasConflict(targets)) {
                                            RecycleBinDialogState.ConflictConfirm(targets)
                                        } else {
                                            RecycleBinDialogState.RestoreConfirm(targets)
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.RestoreFromTrash,
                                contentDescription = stringResource(R.string.restore)
                            )
                        }
                        IconButton(
                            enabled = selectedItems.isNotEmpty(),
                            onClick = {
                                viewModel.showDialog(
                                    RecycleBinDialogState.DeleteConfirm(selectedItems)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.delete_forever)
                            )
                        }
                        Box {
                            IconButton(onClick = { actionMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                            SourceRecycleDropdownMenu(
                                expanded = actionMenuExpanded,
                                onDismissRequest = { actionMenuExpanded = false }
                            ) {
                                SourceRecycleDropdownMenuItem(
                                    text = stringResource(
                                        if (selectedIds.size == displayedItems.size) {
                                            R.string.un_select_all
                                        } else {
                                            R.string.select_all
                                        }
                                    ),
                                    selected = selectedIds.size == displayedItems.size,
                                    onClick = {
                                        if (selectedIds.size == displayedItems.size) {
                                            viewModel.clearSelection()
                                        } else {
                                            viewModel.setSelected(
                                                displayedItems.mapTo(linkedSetOf()) { it.id }
                                            )
                                        }
                                        actionMenuExpanded = false
                                    }
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                searchQuery = if (searchQuery == null) "" else null
                            }
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                        Box {
                            IconButton(onClick = { filterMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.filter)
                                )
                            }
                            SourceRecycleDropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false }
                            ) {
                                SourceRecycleBinFilter.entries.forEach {
                                    SourceRecycleDropdownMenuItem(
                                        text = stringResource(it.labelRes),
                                        selected = it == filter,
                                        onClick = {
                                            viewModel.setFilter(it)
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { actionMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                            SourceRecycleDropdownMenu(
                                expanded = actionMenuExpanded,
                                onDismissRequest = { actionMenuExpanded = false }
                            ) {
                                SourceRecycleDropdownMenuItem(
                                    text = stringResource(
                                        if (enabled) {
                                            R.string.disable_source_recycle_bin
                                        } else {
                                            R.string.enable_source_recycle_bin
                                        }
                                    ),
                                    selected = enabled,
                                    onClick = {
                                        viewModel.setEnabled(!enabled)
                                        actionMenuExpanded = false
                                    }
                                )
                                SourceRecycleDropdownMenuItem(
                                    text = stringResource(R.string.select_all),
                                    enabled = displayedItems.isNotEmpty(),
                                    onClick = {
                                        viewModel.setSelected(
                                            displayedItems.mapTo(linkedSetOf()) { it.id }
                                        )
                                        actionMenuExpanded = false
                                    }
                                )
                                SourceRecycleDropdownMenuItem(
                                    text = stringResource(R.string.clear),
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null
                                        )
                                    },
                                    destructive = true,
                                    enabled = items.isNotEmpty(),
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.showDialog(RecycleBinDialogState.ClearAll)
                                    }
                                )
                            }
                        }
                    }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(visible = showSearch && !isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery.orEmpty(),
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (!searchQuery.isNullOrEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    singleLine = true
                )
            }

            if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.source_recycle_bin_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = secondaryTextColor
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        val selected = item.id in selectedIds
                        SourceRecycleBinItem(
                            item = item,
                            selected = selected,
                            secondaryTextColor = secondaryTextColor,
                            menuExpanded = itemMenuExpanded == item.id,
                            onToggleSelected = {
                                itemMenuExpanded = null
                                viewModel.toggleSelected(item.id)
                            },
                            onMenuOpen = { itemMenuExpanded = item.id },
                            onMenuDismiss = { itemMenuExpanded = null },
                            onRestoreClick = {
                                itemMenuExpanded = null
                                coroutineScope.launch {
                                    // R3：suspend 获取冲突结果后设置 Dialog 状态（§4.5）
                                    viewModel.showDialog(
                                        if (viewModel.hasConflict(item)) {
                                            RecycleBinDialogState.ConflictConfirm(listOf(item))
                                        } else {
                                            RecycleBinDialogState.RestoreConfirm(listOf(item))
                                        }
                                    )
                                }
                            },
                            onDeleteClick = {
                                itemMenuExpanded = null
                                viewModel.showDialog(
                                    RecycleBinDialogState.DeleteConfirm(listOf(item))
                                )
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 确认对话框统一由 ViewModel Dialog 状态条件渲染（§4.5）；
    // 单个/批量操作复用同一状态（items.size == 1 时显示单条文案）
    when (val state = dialog) {
        is RecycleBinDialogState.RestoreConfirm -> AppConfirmDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = stringResource(R.string.restore),
            text = if (state.items.size == 1) {
                stringResource(R.string.source_recycle_bin_restore_msg, state.items.first().name)
            } else {
                stringResource(R.string.source_recycle_bin_batch_restore_msg, state.items.size)
            },
            confirmText = stringResource(R.string.restore),
            onConfirm = {
                viewModel.restore(state.items, overwrite = false)
                viewModel.dismissDialog()
            }
        )
        is RecycleBinDialogState.ConflictConfirm -> AppConfirmDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = stringResource(R.string.source_recycle_bin_conflict_title),
            text = if (state.items.size == 1) {
                stringResource(R.string.source_recycle_bin_conflict_msg, state.items.first().name)
            } else {
                stringResource(R.string.source_recycle_bin_batch_conflict_msg, state.items.size)
            },
            confirmText = stringResource(R.string.overwrite),
            onConfirm = {
                viewModel.restore(state.items, overwrite = true)
                viewModel.dismissDialog()
            }
        )
        is RecycleBinDialogState.DeleteConfirm -> AppConfirmDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = stringResource(R.string.delete_forever),
            text = if (state.items.size == 1) {
                stringResource(R.string.source_recycle_bin_delete_msg, state.items.first().name)
            } else {
                stringResource(R.string.source_recycle_bin_batch_delete_msg, state.items.size)
            },
            confirmText = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(state.items)
                viewModel.dismissDialog()
            }
        )
        is RecycleBinDialogState.ClearAll -> AppConfirmDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = stringResource(R.string.source_recycle_bin_clear_title),
            text = stringResource(R.string.source_recycle_bin_clear_msg),
            confirmText = stringResource(R.string.clear),
            destructive = true,
            onConfirm = {
                viewModel.clearAll()
                viewModel.dismissDialog()
            }
        )
        null -> Unit
    }
}
