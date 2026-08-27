package io.legado.app.ui.main.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.help.book.BookTagManagement

/**
 * 标签管理 Screen 的回调集合，打包传递以避免参数过多。
 */
internal data class BookshelfTagManageCallbacks(
    val onBack: () -> Unit,
    val onShowAddTagDialog: (Long, String) -> Unit,
    val onAddTags: (Long, List<String>) -> Unit,
    val onTagVisibilityChange: (Long, String, Boolean) -> Unit,
    val onManageBooks: (BookshelfTagGroupUi, String) -> Unit,
    val onRequestDelete: (BookshelfTagGroupUi, String) -> Unit,
    val onConfirmDelete: (Long, String, String, List<BookTagInfo>) -> Unit,
    val onDismissDialog: () -> Unit,
    val onSaveAssignment: (BookTagAssignmentUi, Set<String>) -> Unit
)

/**
 * 标签管理主 Screen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfTagManageScreen(
    state: BookshelfTagManageUiState,
    callbacks: BookshelfTagManageCallbacks,
    modifier: Modifier = Modifier
) {
    var selectedGroupId by rememberSaveable {
        mutableLongStateOf(state.groups.firstOrNull()?.groupId ?: -1L)
    }
    LaunchedEffect(state.groups) {
        if (state.groups.isNotEmpty() &&
            state.groups.none { it.groupId == selectedGroupId }
        ) {
            selectedGroupId = state.groups.firstOrNull()?.groupId ?: -1L
        }
    }
    val selectedGroup = state.groups.firstOrNull { it.groupId == selectedGroupId }

    BackHandler(enabled = state.dialog != null) { callbacks.onDismissDialog() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bookshelf_tag_manage),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.groups.isNotEmpty()) {
                GroupSelector(
                    groups = state.groups,
                    selectedGroupId = selectedGroupId,
                    onSelect = { selectedGroupId = it }
                )
            }
            when {
                state.loading -> LoadingContent()
                selectedGroup == null -> EmptyContent()
                else -> TagGroupContent(
                    group = selectedGroup,
                    onAddTags = { callbacks.onShowAddTagDialog(selectedGroup.groupId, selectedGroup.groupName) },
                    onTagVisibilityChange = { tag, visible ->
                        callbacks.onTagVisibilityChange(selectedGroup.groupId, tag, visible)
                    },
                    onManageBooks = { tag -> callbacks.onManageBooks(selectedGroup, tag) },
                    onDeleteTag = { tag -> callbacks.onRequestDelete(selectedGroup, tag) }
                )
            }
        }
    }

    val dialog = state.dialog
    when (dialog) {
        is BookshelfTagDialogState.AddTags -> {
            val group = state.groups.firstOrNull { it.groupId == dialog.groupId }
            if (group != null) {
                val allTags = remember(state.groups) {
                    state.groups.flatMap { it.tags.map { item -> item.name } }
                }
                val reusableTags = remember(group.tags, allTags) {
                    BookTagManagement.reusableTags(
                        current = group.tags.map { it.name },
                        all = allTags
                    )
                }
                BookTagAddDialog(
                    group = group,
                    reusableTags = reusableTags,
                    onDismiss = callbacks.onDismissDialog,
                    onAdd = { tags ->
                        callbacks.onDismissDialog()
                        callbacks.onAddTags(group.groupId, tags)
                    }
                )
            }
        }
        is BookshelfTagDialogState.ManageBooks -> {
            BookTagAssignmentDialog(
                assignment = dialog.assignment,
                onDismiss = callbacks.onDismissDialog,
                onSave = { selected ->
                    callbacks.onSaveAssignment(dialog.assignment, selected)
                }
            )
        }
        is BookshelfTagDialogState.DeleteConfirm -> {
            AlertDialog(
                onDismissRequest = callbacks.onDismissDialog,
                title = { Text(stringResource(R.string.bookshelf_tag_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.bookshelf_tag_delete_message,
                            dialog.tag,
                            dialog.groupName
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            callbacks.onConfirmDelete(
                                dialog.groupId,
                                dialog.groupName,
                                dialog.tag,
                                dialog.books
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = callbacks.onDismissDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.bookshelf_tag_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
