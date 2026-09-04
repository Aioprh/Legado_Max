package io.legado.app.ui.main.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.help.book.BookTagManagement

internal data class BookshelfTagManageCallbacks(
    val onBack: () -> Unit,
    val onShowAddTagDialog: (Long, String) -> Unit,
    val onAddTags: (Long, List<String>) -> Unit,
    val onTagVisibilityChange: (Long, String, Boolean) -> Unit,
    val onManageBooks: (BookshelfTagGroupUi, String) -> Unit,
    val onRequestDelete: (BookshelfTagGroupUi, String) -> Unit,
    val onConfirmDelete: (Long, String, String, List<BookTagInfo>) -> Unit,
    val onDismissDialog: () -> Unit,
    val onSaveAssignment: (BookTagAssignmentUi, Set<String>) -> Unit,
    val onRequestRename: (BookshelfTagGroupUi, String) -> Unit,
    val onRenameTag: (Long, String, String, String) -> Unit,
    val onReorderTags: (Long, List<String>) -> Unit,
    val onSmartTagsEnabledChange: (Boolean) -> Unit,
    val onSmartTagVisibilityChange: (String, Boolean) -> Unit
)

@Composable
internal fun BookshelfTagManageScreen(
    state: BookshelfTagManageUiState,
    callbacks: BookshelfTagManageCallbacks,
    modifier: Modifier = Modifier
) {
    var selectedGroupId by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    LaunchedEffect(state.focusGroupId, state.groups) {
        if (state.groups.isNotEmpty() && state.groups.none { it.groupId == selectedGroupId }) {
            selectedGroupId = state.groups.firstOrNull { it.groupId == state.focusGroupId }?.groupId
                ?: state.groups.firstOrNull()?.groupId ?: -1L
        }
    }
    val selectedGroup = state.groups.firstOrNull { it.groupId == selectedGroupId }
    BackHandler(enabled = state.dialog != null) { callbacks.onDismissDialog() }

    ScaffoldWithSmartTags(
        state = state,
        callbacks = callbacks,
        selectedGroup = selectedGroup,
        selectedGroupId = selectedGroupId,
        onSelectGroup = { selectedGroupId = it },
        modifier = modifier
    )

    val dialog = state.dialog
    when (dialog) {
        is BookshelfTagDialogState.AddTags -> {
            val group = state.groups.firstOrNull { it.groupId == dialog.groupId }
            if (group != null) {
                val allTags = remember(state.groups) { state.groups.flatMap { it.tags.map { item -> item.name } } }
                val reusableTags = remember(group.tags, allTags) {
                    BookTagManagement.reusableTags(current = group.tags.map { it.name }, all = allTags)
                }
                BookTagAddDialog(group, reusableTags, callbacks.onDismissDialog) { tags ->
                    callbacks.onDismissDialog()
                    callbacks.onAddTags(group.groupId, tags)
                }
            }
        }
        is BookshelfTagDialogState.ManageBooks -> BookTagAssignmentDialog(
            assignment = dialog.assignment,
            onDismiss = callbacks.onDismissDialog,
            onSave = { callbacks.onSaveAssignment(dialog.assignment, it) }
        )
        is BookshelfTagDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = callbacks.onDismissDialog,
            title = { Text(stringResource(R.string.bookshelf_tag_delete_title)) },
            text = { Text(stringResource(R.string.bookshelf_tag_delete_message, dialog.tag, dialog.groupName)) },
            confirmButton = {
                TextButton(onClick = {
                    callbacks.onConfirmDelete(dialog.groupId, dialog.groupName, dialog.tag, dialog.books)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = callbacks.onDismissDialog) { Text(stringResource(R.string.cancel)) } }
        )
        is BookshelfTagDialogState.RenameTag -> BookTagRenameDialog(
            groupId = dialog.groupId,
            groupName = dialog.groupName,
            oldTag = dialog.oldTag,
            onDismiss = callbacks.onDismissDialog,
            onRename = { newTag ->
                callbacks.onDismissDialog()
                callbacks.onRenameTag(dialog.groupId, dialog.groupName, dialog.oldTag, newTag)
            }
        )
        null -> Unit
    }
}

@Composable
private fun ScaffoldWithSmartTags(
    state: BookshelfTagManageUiState,
    callbacks: BookshelfTagManageCallbacks,
    selectedGroup: BookshelfTagGroupUi?,
    selectedGroupId: Long,
    onSelectGroup: (Long) -> Unit,
    modifier: Modifier
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookshelf_tag_manage), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            SmartTagManageCard(
                enabled = state.smartTagsEnabled,
                tags = state.smartTags,
                onEnabledChange = callbacks.onSmartTagsEnabledChange,
                onTagVisibilityChange = callbacks.onSmartTagVisibilityChange
            )
            if (state.groups.isNotEmpty()) {
                GroupSelector(
                    groups = state.groups,
                    selectedGroupId = selectedGroupId,
                    onSelect = onSelectGroup
                )
            }
            when {
                state.loading -> LoadingContent()
                selectedGroup == null -> EmptyContent()
                else -> TagGroupContent(
                    group = selectedGroup,
                    onAddTags = { callbacks.onShowAddTagDialog(selectedGroup.groupId, selectedGroup.groupName) },
                    onTagVisibilityChange = { tag, visible -> callbacks.onTagVisibilityChange(selectedGroup.groupId, tag, visible) },
                    onManageBooks = { tag -> callbacks.onManageBooks(selectedGroup, tag) },
                    onDeleteTag = { tag -> callbacks.onRequestDelete(selectedGroup, tag) },
                    onRenameTag = { tag -> callbacks.onRequestRename(selectedGroup, tag) },
                    onReorderTags = { newOrder -> callbacks.onReorderTags(selectedGroup.groupId, newOrder) }
                )
            }
        }
    }
}

@Composable
private fun SmartTagManageCard(
    enabled: Boolean,
    tags: List<BookshelfTagItemUi>,
    onEnabledChange: (Boolean) -> Unit,
    onTagVisibilityChange: (String, Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("智能标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "根据书籍类型、阅读进度、章节数量和更新状态自动生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                tags.forEach { tag ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${tag.assignedCount} 本 · ${SmartTagDescription.description(tag.name)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tag.visible,
                            onCheckedChange = { onTagVisibilityChange(tag.name, it) }
                        )
                    }
                }
            }
        }
    }
}

private object SmartTagDescription {
    private val descriptions = mapOf(
        "有声" to "音频书籍", "漫画" to "图片/漫画", "视频" to "视频书籍", "本地" to "本地书籍",
        "网络书" to "网络书源", "更新异常" to "更新失败", "已读完" to "阅读完成", "在读" to "正在阅读",
        "未开始" to "尚未开始", "超长篇" to "1000章以上", "长篇" to "500章以上",
        "中长篇" to "200章以上", "短篇" to "少于50章", "有更新" to "检测到新章节",
        "不可更新" to "关闭自动更新", "有封面" to "存在封面", "有简介" to "存在简介"
    )
    fun description(name: String) = descriptions[name] ?: "自动识别"
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.bookshelf_tag_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
