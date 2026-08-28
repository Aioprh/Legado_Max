package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Immutable
import io.legado.app.data.dao.BookTagInfo

/**
 * 单个标签项的 UI 状态。
 */
@Immutable
data class BookshelfTagItemUi(
    val name: String,
    val assignedCount: Int,
    val visible: Boolean
)

/**
 * 一个分组及其标签的 UI 状态。
 */
@Immutable
data class BookshelfTagGroupUi(
    val groupId: Long,
    val groupName: String,
    val books: List<BookTagInfo>,
    val tags: List<BookshelfTagItemUi>
)

/**
 * 书籍标签分配操作的 UI 状态。
 */
@Immutable
data class BookTagAssignmentUi(
    val groupId: Long,
    val groupName: String,
    val tag: String,
    val books: List<BookTagInfo>,
    val initiallySelectedUrls: Set<String>
)

/**
 * Dialog 状态，用于条件渲染。
 */
sealed interface BookshelfTagDialogState {
    data class AddTags(val groupId: Long, val groupName: String) : BookshelfTagDialogState
    data class ManageBooks(val assignment: BookTagAssignmentUi) : BookshelfTagDialogState
    data class DeleteConfirm(
        val groupId: Long,
        val groupName: String,
        val tag: String,
        val books: List<BookTagInfo>
    ) : BookshelfTagDialogState
    data class RenameTag(
        val groupId: Long,
        val groupName: String,
        val oldTag: String
    ) : BookshelfTagDialogState
}

/**
 * 标签管理整体 UI 状态。
 */
data class BookshelfTagManageUiState(
    val loading: Boolean = true,
    val focusGroupId: Long = -1L,
    val groups: List<BookshelfTagGroupUi> = emptyList(),
    val dialog: BookshelfTagDialogState? = null
)
