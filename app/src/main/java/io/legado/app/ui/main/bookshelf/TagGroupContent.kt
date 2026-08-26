package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement

/**
 * 标签列表内容区，展示当前选中分组的标签卡片。
 */
@Composable
internal fun TagGroupContent(
    group: BookshelfTagGroupUi,
    onAddTags: () -> Unit,
    onTagVisibilityChange: (String, Boolean) -> Unit,
    onManageBooks: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary:${group.groupId}") {
            TagGroupSummaryCard(
                groupName = group.groupName,
                bookCount = group.books.size,
                tagCount = group.tags.size,
                onAddTags = onAddTags
            )
        }
        if (group.tags.isEmpty()) {
            item(key = "empty:${group.groupId}") {
                EmptyTagCard()
            }
        } else {
            items(group.tags, key = { it.name.lowercase() }) { tag ->
                TagCard(
                    tag = tag,
                    onVisibilityChange = { onTagVisibilityChange(tag.name, it) },
                    onManageBooks = { onManageBooks(tag.name) },
                    onDelete = { onDeleteTag(tag.name) }
                )
            }
        }
    }
}

@Composable
private fun TagGroupSummaryCard(
    groupName: String,
    bookCount: Int,
    tagCount: Int,
    onAddTags: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(
                    R.string.bookshelf_tag_group_summary,
                    bookCount,
                    tagCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(top = 8.dp)
            )
            androidx.compose.material3.Button(onClick = onAddTags) {
                Text(stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun EmptyTagCard() {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Text(
            text = stringResource(R.string.bookshelf_tag_empty_summary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
