package io.legado.app.ui.source.recycle

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.source.SourceRecycleBinHelp
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceRecycleBinScreen(
    viewModel: SourceRecycleBinViewModel = viewModel(),
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

    val topBarColor = pageTopBarContainerColor()
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    if (isSelectionMode) {
                        Column {
                            Text(
                                text = stringResource(R.string.selected, selectedItems.size),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.select_count,
                                    selectedItems.size,
                                    items.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.88f),
                                maxLines = 1
                            )
                        }
                    } else {
                        Column {
                            Text(
                                text = stringResource(R.string.source_recycle_bin),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.source_recycle_bin_count,
                                    filterLabel,
                                    items.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.clearSelection()
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            if (isSelectionMode) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = stringResource(
                                if (isSelectionMode) R.string.cancel else R.string.back
                            )
                        )
                    }
                },
                actions = {
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
            )
        }
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

@Composable
private fun SourceRecycleBinItem(
    item: SourceRecycleBin,
    selected: Boolean,
    secondaryTextColor: Color,
    menuExpanded: Boolean,
    onToggleSelected: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onToggleSelected),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.key },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_type_group,
                        typeLabel(item.type),
                        item.groupName.orEmpty().ifBlank { stringResource(R.string.no_group) }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_time_left,
                        formatTime(item.deletedAt),
                        remainingDays(item.expireAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
            Box {
                IconButton(onClick = onMenuOpen) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                }
                SourceRecycleDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onMenuDismiss
                ) {
                    SourceRecycleDropdownMenuItem(
                        text = stringResource(R.string.restore),
                        leadingIcon = {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                        },
                        onClick = onRestoreClick
                    )
                    SourceRecycleDropdownMenuItem(
                        text = stringResource(R.string.delete_forever),
                        leadingIcon = {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                        },
                        destructive = true,
                        onClick = onDeleteClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRecycleDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content
    )
}

@Composable
private fun SourceRecycleDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        selected -> primaryColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onClick = onClick,
        modifier = modifier.background(
            if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else Color.Transparent
        ),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = primaryColor
                )
            }
        },
        colors = MenuDefaults.itemColors(
            textColor = textColor,
            leadingIconColor = textColor,
            trailingIconColor = primaryColor,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    )
}

@Composable
private fun typeLabel(type: String): String {
    return when (type) {
        SourceRecycleBinHelp.TYPE_BOOK_SOURCE -> stringResource(R.string.book_source)
        SourceRecycleBinHelp.TYPE_RSS_SOURCE -> stringResource(R.string.rss_source)
        SourceRecycleBinHelp.TYPE_REPLACE_RULE -> stringResource(R.string.replace_rule)
        SourceRecycleBinHelp.TYPE_TXT_TOC_RULE -> stringResource(R.string.txt_toc_rule)
        SourceRecycleBinHelp.TYPE_HTTP_TTS -> stringResource(R.string.speak_engine)
        SourceRecycleBinHelp.TYPE_DICT_RULE -> stringResource(R.string.dict_rule)
        SourceRecycleBinHelp.TYPE_HIGHLIGHT_RULE -> stringResource(R.string.highlight_rule_config)
        SourceRecycleBinHelp.TYPE_SEARCH_ENGINE -> stringResource(R.string.search_engine_rule)
        else -> type
    }
}

private fun formatTime(time: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
}

private fun remainingDays(expireAt: Long): Long {
    val millis = expireAt - System.currentTimeMillis()
    return TimeUnit.MILLISECONDS.toDays(millis).coerceAtLeast(0)
}
