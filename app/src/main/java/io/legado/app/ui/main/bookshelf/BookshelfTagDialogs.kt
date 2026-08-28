package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement

private enum class BookSelectionFilter { All, Selected, Unselected }

/**
 * 添加标签对话框。
 */
@Composable
internal fun BookTagAddDialog(
    group: BookshelfTagGroupUi,
    reusableTags: List<String>,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTagInput by rememberSaveable(group.groupId) { mutableStateOf("") }
    var query by rememberSaveable(group.groupId) { mutableStateOf("") }
    var selectedTags by remember(group.groupId, reusableTags) {
        mutableStateOf(emptySet<String>())
    }
    val visibleTags = remember(reusableTags, query) {
        val normalizedQuery = query.trim()
        reusableTags.filter {
            normalizedQuery.isEmpty() || it.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val newTags = remember(newTagInput) { BookTagHelper.parse(newTagInput) }
    val tagsToAdd = remember(reusableTags, selectedTags, newTags) {
        BookTagManagement.mergeTags(
            configured = reusableTags.filter { it in selectedTags },
            existing = newTags
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .widthIn(max = 620.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.bookshelf_tag_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.bookshelf_tag_add_group, group.groupName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.bookshelf_tag_new_label)) },
                    placeholder = { Text(stringResource(R.string.bookshelf_tag_new_hint)) }
                )
                if (reusableTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.bookshelf_tag_search_existing)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.bookshelf_tag_reusable_summary,
                            reusableTags.size,
                            selectedTags.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    reusableTags.isEmpty() -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.bookshelf_tag_no_reusable),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    visibleTags.isEmpty() -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.bookshelf_tag_no_matching_existing),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(visibleTags, key = { it.lowercase() }) { tag ->
                                val selected = tag in selectedTags
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedTags = if (selected) {
                                                selectedTags - tag
                                            } else {
                                                selectedTags + tag
                                            }
                                        }
                                    )
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    androidx.compose.material3.Button(
                        onClick = { if (tagsToAdd.isNotEmpty()) onAdd(tagsToAdd) },
                        modifier = Modifier.weight(1f),
                        enabled = tagsToAdd.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
        }
    }
}

/**
 * 管理书籍对话框，支持搜索、筛选、勾选书籍来分配标签。
 */
@Composable
internal fun BookTagAssignmentDialog(
    assignment: BookTagAssignmentUi,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable(assignment.groupId, assignment.tag) { mutableStateOf("") }
    var filter by rememberSaveable(assignment.groupId, assignment.tag) {
        mutableStateOf(BookSelectionFilter.All)
    }
    var selectedUrls by remember(assignment.groupId, assignment.tag, assignment.books) {
        mutableStateOf(assignment.initiallySelectedUrls)
    }
    val visibleBooks = remember(assignment.books, selectedUrls, query, filter) {
        assignment.books.asSequence()
            .filter { book ->
                query.isBlank() ||
                    book.name.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true)
            }
            .filter { book ->
                when (filter) {
                    BookSelectionFilter.All -> true
                    BookSelectionFilter.Selected -> book.bookUrl in selectedUrls
                    BookSelectionFilter.Unselected -> book.bookUrl !in selectedUrls
                }
            }
            .sortedWith(
                compareByDescending<BookTagInfo> { it.bookUrl in selectedUrls }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .widthIn(max = 700.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${assignment.groupName} · ${assignment.tag}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.bookshelf_tag_search_book)) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        text = stringResource(R.string.bookshelf_tag_filter_all),
                        selected = filter == BookSelectionFilter.All,
                        modifier = Modifier.weight(1f),
                        onClick = { filter = BookSelectionFilter.All }
                    )
                    FilterChip(
                        text = stringResource(R.string.bookshelf_tag_filter_selected),
                        selected = filter == BookSelectionFilter.Selected,
                        modifier = Modifier.weight(1f),
                        onClick = { filter = BookSelectionFilter.Selected }
                    )
                    FilterChip(
                        text = stringResource(R.string.bookshelf_tag_filter_unselected),
                        selected = filter == BookSelectionFilter.Unselected,
                        modifier = Modifier.weight(1f),
                        onClick = { filter = BookSelectionFilter.Unselected }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.bookshelf_tag_selected_count,
                            selectedUrls.size,
                            assignment.books.size
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        selectedUrls = selectedUrls + visibleBooks.map { it.bookUrl }
                    }) {
                        Text(stringResource(R.string.bookshelf_tag_select_results))
                    }
                    TextButton(onClick = {
                        selectedUrls = selectedUrls - visibleBooks.map { it.bookUrl }.toSet()
                    }) {
                        Text(stringResource(R.string.bookshelf_tag_clear_results))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (visibleBooks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.bookshelf_tag_no_matching_books),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visibleBooks, key = { it.bookUrl }) { book ->
                            val selected = book.bookUrl in selectedUrls
                            val tags = BookTagHelper.parse(book.customTag)
                            val description = listOfNotNull(
                                book.author.takeIf { it.isNotBlank() },
                                tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                            ).joinToString("  ·  ")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        selectedUrls = if (selected) {
                                            selectedUrls - book.bookUrl
                                        } else {
                                            selectedUrls + book.bookUrl
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (description.isNotEmpty()) {
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    androidx.compose.material3.Button(
                        onClick = { onSave(selectedUrls) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.bookshelf_tag_save))
                    }
                }
            }
        }
    }
}

/**
 * 重命名标签对话框。
 */
@Composable
internal fun BookTagRenameDialog(
    groupId: Long,
    groupName: String,
    oldTag: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by rememberSaveable(groupId, oldTag) { mutableStateOf(oldTag) }
    val canRename = newName.trim().isNotBlank() &&
        !newName.trim().equals(oldTag, ignoreCase = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.bookshelf_tag_rename_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.bookshelf_tag_rename_group, groupName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.bookshelf_tag_rename_label)) },
                    isError = newName.trim().isBlank()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (canRename) onRename(newName.trim()) },
                enabled = canRename
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
