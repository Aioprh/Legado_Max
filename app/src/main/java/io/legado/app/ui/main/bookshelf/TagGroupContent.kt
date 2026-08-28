package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R

/**
 * 标签列表内容区，展示当前选中分组的标签卡片，支持长按拖拽排序。
 *
 * 拖拽排序实现思路：
 * - 在 `onDrag` 中累积偏移量（[accumulatedOffset]），
 *   当偏移超过阈值（item 高度的一半）时与相邻 item 交换位置并重置偏移。
 * - [dragFromIndex] 记录被拖拽项的初始索引，[dragCurrentIndex] 记录当前位置。
 *   拖拽结束时两者不同则触发 [onReorderTags]。
 */
@Composable
internal fun TagGroupContent(
    group: BookshelfTagGroupUi,
    onAddTags: () -> Unit,
    onTagVisibilityChange: (String, Boolean) -> Unit,
    onManageBooks: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onRenameTag: (String) -> Unit,
    onReorderTags: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var tags by remember(group.groupId, group.tags) {
        mutableStateOf(group.tags)
    }
    val listState = rememberLazyListState()
    var dragFromIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }
    var accumulatedOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
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
        if (tags.isEmpty()) {
            item(key = "empty:${group.groupId}") {
                EmptyTagCard()
            }
        } else {
            items(tags, key = { it.name.lowercase() }) { tag ->
                val itemIndex = tags.indexOfFirst { it.name.equals(tag.name, ignoreCase = true) }
                val isBeingDragged = itemIndex == dragCurrentIndex && dragFromIndex >= 0
                TagCard(
                    tag = tag,
                    onVisibilityChange = { onTagVisibilityChange(tag.name, it) },
                    onManageBooks = { onManageBooks(tag.name) },
                    onDelete = { onDeleteTag(tag.name) },
                    onRename = { onRenameTag(tag.name) },
                    modifier = Modifier
                        .then(
                            if (isBeingDragged) {
                                Modifier.graphicsLayer {
                                    alpha = 0.9f
                                    translationY = accumulatedOffset
                                    shadowElevation = 8f
                                }
                            } else {
                                Modifier
                            }
                        )
                        .pointerInput(tags.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragFromIndex = itemIndex
                                    dragCurrentIndex = itemIndex
                                    accumulatedOffset = 0f
                                },
                                onDragEnd = {
                                    if (dragFromIndex >= 0 && dragCurrentIndex >= 0 &&
                                        dragFromIndex != dragCurrentIndex
                                    ) {
                                        val list = tags.map { it.name }
                                        onReorderTags(list)
                                    }
                                    dragFromIndex = -1
                                    dragCurrentIndex = -1
                                    accumulatedOffset = 0f
                                },
                                onDragCancel = {
                                    // 拖拽取消时恢复原始顺序
                                    if (dragFromIndex >= 0 && dragCurrentIndex >= 0 &&
                                        dragFromIndex != dragCurrentIndex
                                    ) {
                                        tags = group.tags
                                    }
                                    dragFromIndex = -1
                                    dragCurrentIndex = -1
                                    accumulatedOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedOffset += dragAmount.y
                                    // 获取当前 item 在屏幕上的高度（包含 spacing）
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index - 1 == dragCurrentIndex }
                                    val itemHeight = itemInfo?.size?.toFloat() ?: 120f
                                    val spacing = 8f // Arrangement.spacedBy(8.dp)
                                    val threshold = (itemHeight + spacing) / 2f
                                    // 向下拖拽：累积偏移超过阈值，与下方交换
                                    while (accumulatedOffset > threshold &&
                                        dragCurrentIndex < tags.size - 1
                                    ) {
                                        val list = tags.toMutableList()
                                        val tmp = list[dragCurrentIndex]
                                        list[dragCurrentIndex] = list[dragCurrentIndex + 1]
                                        list[dragCurrentIndex + 1] = tmp
                                        tags = list
                                        dragCurrentIndex++
                                        accumulatedOffset -= (itemHeight + spacing)
                                    }
                                    // 向上拖拽：累积偏移超过阈值，与上方交换
                                    while (accumulatedOffset < -threshold &&
                                        dragCurrentIndex > 0
                                    ) {
                                        val list = tags.toMutableList()
                                        val tmp = list[dragCurrentIndex]
                                        list[dragCurrentIndex] = list[dragCurrentIndex - 1]
                                        list[dragCurrentIndex - 1] = tmp
                                        tags = list
                                        dragCurrentIndex--
                                        accumulatedOffset += (itemHeight + spacing)
                                    }
                                }
                            )
                        }
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
