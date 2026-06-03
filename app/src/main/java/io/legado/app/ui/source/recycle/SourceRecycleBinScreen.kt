package io.legado.app.ui.source.recycle

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.source.SourceRecycleBinHelp
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageTopBarContainerColor
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
    val items by viewModel.items.collectAsState(initial = emptyList())
    val filter by viewModel.filter.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var itemMenuExpanded by remember { mutableStateOf<Long?>(null) }
    var restoreTarget by remember { mutableStateOf<SourceRecycleBin?>(null) }
    var conflictTarget by remember { mutableStateOf<SourceRecycleBin?>(null) }
    var deleteTarget by remember { mutableStateOf<SourceRecycleBin?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchRestoreDialog by remember { mutableStateOf(false) }
    var showBatchConflictDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val filterLabel = stringResource(filter.labelRes)
    val selectedItems = remember(items, selectedIds) {
        items.filter { it.id in selectedIds }
    }
    val isSelectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(items) {
        val validIds = items.mapTo(linkedSetOf()) { it.id }
        selectedIds = selectedIds.filterTo(linkedSetOf()) { it in validIds }
    }

    val topBarColor = pageTopBarContainerColor()
    val secondaryTextColor = pageSecondaryTextColor()

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
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                            Text(
                                text = stringResource(
                                    if (enabled) {
                                        R.string.source_recycle_bin_enabled
                                    } else {
                                        R.string.source_recycle_bin_disabled
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.88f),
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = emptySet()
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
                                viewModel.checkConflict(selectedItems) { hasConflict ->
                                    if (hasConflict) {
                                        showBatchConflictDialog = true
                                    } else {
                                        showBatchRestoreDialog = true
                                    }
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
                            onClick = { showBatchDeleteDialog = true }
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
                                        if (selectedIds.size == items.size) {
                                            R.string.un_select_all
                                        } else {
                                            R.string.select_all
                                        }
                                    ),
                                    selected = selectedIds.size == items.size,
                                    onClick = {
                                        selectedIds = if (selectedIds.size == items.size) {
                                            emptySet()
                                        } else {
                                            items.mapTo(linkedSetOf()) { it.id }
                                        }
                                        actionMenuExpanded = false
                                    }
                                )
                            }
                        }
                    } else {
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
                                    enabled = items.isNotEmpty(),
                                    onClick = {
                                        selectedIds = items.mapTo(linkedSetOf()) { it.id }
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
                                        showClearDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val selected = item.id in selectedIds
                    SourceRecycleBinItem(
                        item = item,
                        selected = selected,
                        secondaryTextColor = secondaryTextColor,
                        menuExpanded = itemMenuExpanded == item.id,
                        onToggleSelected = {
                            itemMenuExpanded = null
                            selectedIds = selectedIds.toMutableSet().apply {
                                if (!add(item.id)) remove(item.id)
                            }
                        },
                        onMenuOpen = { itemMenuExpanded = item.id },
                        onMenuDismiss = { itemMenuExpanded = null },
                        onRestoreClick = {
                            itemMenuExpanded = null
                            viewModel.checkConflict(item) { hasConflict ->
                                if (hasConflict) {
                                    conflictTarget = item
                                } else {
                                    restoreTarget = item
                                }
                            }
                        },
                        onDeleteClick = {
                            itemMenuExpanded = null
                            deleteTarget = item
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    restoreTarget?.let { item ->
        SourceRecycleConfirmDialog(
            onDismissRequest = { restoreTarget = null },
            title = stringResource(R.string.restore),
            text = stringResource(R.string.source_recycle_bin_restore_msg, item.name),
            confirmText = stringResource(R.string.restore),
            onConfirm = {
                viewModel.restore(item, overwrite = false)
                restoreTarget = null
                selectedIds = selectedIds - item.id
            }
        )
    }

    conflictTarget?.let { item ->
        SourceRecycleConfirmDialog(
            onDismissRequest = { conflictTarget = null },
            title = stringResource(R.string.source_recycle_bin_conflict_title),
            text = stringResource(R.string.source_recycle_bin_conflict_msg, item.name),
            confirmText = stringResource(R.string.overwrite),
            onConfirm = {
                viewModel.restore(item, overwrite = true)
                conflictTarget = null
                selectedIds = selectedIds - item.id
            }
        )
    }

    deleteTarget?.let { item ->
        SourceRecycleConfirmDialog(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.delete_forever),
            text = stringResource(R.string.source_recycle_bin_delete_msg, item.name),
            confirmText = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(item)
                deleteTarget = null
                selectedIds = selectedIds - item.id
            }
        )
    }

    if (showClearDialog) {
        SourceRecycleConfirmDialog(
            onDismissRequest = { showClearDialog = false },
            title = stringResource(R.string.source_recycle_bin_clear_title),
            text = stringResource(R.string.source_recycle_bin_clear_msg),
            confirmText = stringResource(R.string.clear),
            destructive = true,
            onConfirm = {
                viewModel.clearAll()
                showClearDialog = false
            }
        )
    }

    if (showBatchRestoreDialog) {
        SourceRecycleConfirmDialog(
            onDismissRequest = { showBatchRestoreDialog = false },
            title = stringResource(R.string.restore),
            text = stringResource(
                R.string.source_recycle_bin_batch_restore_msg,
                selectedItems.size
            ),
            confirmText = stringResource(R.string.restore),
            onConfirm = {
                viewModel.restore(selectedItems, overwrite = false)
                selectedIds = emptySet()
                showBatchRestoreDialog = false
            }
        )
    }

    if (showBatchConflictDialog) {
        SourceRecycleConfirmDialog(
            onDismissRequest = { showBatchConflictDialog = false },
            title = stringResource(R.string.source_recycle_bin_conflict_title),
            text = stringResource(
                R.string.source_recycle_bin_batch_conflict_msg,
                selectedItems.size
            ),
            confirmText = stringResource(R.string.overwrite),
            onConfirm = {
                viewModel.restore(selectedItems, overwrite = true)
                selectedIds = emptySet()
                showBatchConflictDialog = false
            }
        )
    }

    if (showBatchDeleteDialog) {
        SourceRecycleConfirmDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = stringResource(R.string.delete_forever),
            text = stringResource(
                R.string.source_recycle_bin_batch_delete_msg,
                selectedItems.size
            ),
            confirmText = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(selectedItems)
                selectedIds = emptySet()
                showBatchDeleteDialog = false
            }
        )
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
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onToggleSelected),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
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
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_type_group,
                        typeLabel(item.type),
                        item.groupName.orEmpty().ifBlank { stringResource(R.string.no_group) }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_time_left,
                        formatTime(item.deletedAt),
                        remainingDays(item.expireAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1
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
private fun SourceRecycleConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun typeLabel(type: String): String {
    return when (type) {
        SourceRecycleBinHelp.TYPE_BOOK_SOURCE -> stringResource(R.string.book_source)
        SourceRecycleBinHelp.TYPE_RSS_SOURCE -> stringResource(R.string.rss_source)
        SourceRecycleBinHelp.TYPE_REPLACE_RULE -> stringResource(R.string.replace_rule)
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
