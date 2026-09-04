package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Immutable
import io.legado.app.data.dao.BookTagInfo

@Immutable
data class BookshelfTagItemUi(
    val name: String,
    val assignedCount: Int,
    val visible: Boolean
)

@Immutable
data class BookshelfTagGroupUi(
    val groupId: Long,
    val groupName: String,
    val books: List<BookTagInfo>,
    val tags: List<BookshelfTagItemUi>
)

@Immutable
data class BookTagAssignmentUi(
    val groupId: Long,
    val groupName: String,
    val tag: String,
    val books: List<BookTagInfo>,
    val initiallySelectedUrls: Set<String>
)

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

data class BookshelfTagManageUiState(
    val loading: Boolean = true,
    val focusGroupId: Long = -1L,
    val groups: List<BookshelfTagGroupUi> = emptyList(),
    val smartTags: List<BookshelfTagItemUi> = emptyList(),
    val smartTagsEnabled: Boolean = true,
    val dialog: BookshelfTagDialogState? = null
)
