package io.legado.app.ui.main.bookshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.book.SmartTag
import io.legado.app.help.book.SmartTagConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfTagManageViewModel(application: Application) : AndroidViewModel(application) {
    private var focusGroupId: Long = BookGroup.IdAll
    private val _uiState = MutableStateFlow(BookshelfTagManageUiState())
    val uiState: StateFlow<BookshelfTagManageUiState> = _uiState.asStateFlow()

    fun setFocusGroupId(groupId: Long) {
        focusGroupId = groupId
        loadTags()
    }

    fun loadTags() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val books = appDb.bookDao.allTagInfos
                val allBooks = appDb.bookDao.flowAll().first()
                val groups = appDb.bookGroupDao.all
                    .filter { it.groupId != BookGroup.IdRoot }
                    .sortedBy { it.order }
                val userGroupMask = groups.asSequence()
                    .filter { it.groupId > 0 }
                    .fold(0L) { acc, group -> acc or group.groupId }
                val configuredMap = AppConfig.bookshelfGroupTags.toMutableMap()
                var configuredChanged = false
                val hiddenMap = AppConfig.bookshelfHiddenTags
                val result = groups.map { group ->
                    val groupBooks = booksInGroup(group, books, userGroupMask)
                    val existingTags = groupBooks.flatMap { BookTagHelper.parse(it.customTag) }
                    val configuredTags = configuredMap[group.groupId].orEmpty()
                    val tags = BookTagManagement.mergeTags(configuredTags, existingTags)
                    if (configuredTags != tags) {
                        configuredMap[group.groupId] = tags
                        configuredChanged = true
                    }
                    val hiddenTags = hiddenMap[group.groupId].orEmpty()
                    BookshelfTagGroupUi(
                        groupId = group.groupId,
                        groupName = group.groupName,
                        books = groupBooks,
                        tags = tags.map { tag ->
                            BookshelfTagItemUi(
                                name = tag,
                                assignedCount = groupBooks.count { BookTagHelper.has(it.customTag, tag) },
                                visible = hiddenTags.none { it.equals(tag, ignoreCase = true) }
                            )
                        }
                    )
                }
                if (configuredChanged) AppConfig.bookshelfGroupTags = configuredMap

                // 管理页直接使用引擎中的完整规则列表，包含持久化自定义规则。
                val smartTagItems = SmartTag.allRuleInfos(getApplication()).map { rule ->
                    BookshelfTagItemUi(
                        name = rule.name,
                        assignedCount = allBooks.count {
                            SmartTag.names(it, getApplication(), Int.MAX_VALUE).contains(rule.name)
                        },
                        visible = SmartTagConfig.isRuleVisible(getApplication(), rule.name)
                    )
                }
                Triple(result, smartTagItems, SmartTagConfig.isEnabled(getApplication()))
            }
            _uiState.value = _uiState.value.copy(
                groups = data.first,
                smartTags = data.second,
                smartTagsEnabled = data.third,
                focusGroupId = focusGroupId,
                loading = false
            )
        }
    }

    fun setSmartTagsEnabled(enabled: Boolean) {
        SmartTagConfig.setEnabled(getApplication(), enabled)
        _uiState.value = _uiState.value.copy(smartTagsEnabled = enabled)
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    fun setSmartTagVisible(tag: String, visible: Boolean) {
        SmartTagConfig.setRuleVisible(getApplication(), tag, visible)
        _uiState.value = _uiState.value.copy(
            smartTags = _uiState.value.smartTags.map {
                if (it.name == tag) it.copy(visible = visible) else it
            }
        )
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    fun showAddTagDialog(groupId: Long, groupName: String) {
        _uiState.value = _uiState.value.copy(dialog = BookshelfTagDialogState.AddTags(groupId, groupName))
    }

    fun addTags(groupId: Long, tags: List<String>) {
        val newTags = BookTagManagement.mergeTags(emptyList(), tags)
        if (newTags.isEmpty()) return
        val map = AppConfig.bookshelfGroupTags.toMutableMap()
        map[groupId] = BookTagManagement.mergeTags(map[groupId].orEmpty(), newTags)
        AppConfig.bookshelfGroupTags = map
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
        loadTags()
    }

    fun setTagVisible(groupId: Long, tag: String, visible: Boolean) {
        val map = AppConfig.bookshelfHiddenTags.toMutableMap()
        val tags = map[groupId].orEmpty().toMutableSet()
        tags.removeAll { it.equals(tag, ignoreCase = true) }
        if (!visible) tags.add(tag)
        if (tags.isEmpty()) map.remove(groupId) else map[groupId] = tags
        AppConfig.bookshelfHiddenTags = map
        _uiState.value = _uiState.value.copy(groups = _uiState.value.groups.map { group ->
            if (group.groupId != groupId) group else group.copy(tags = group.tags.map { item ->
                if (item.name.equals(tag, ignoreCase = true)) item.copy(visible = visible) else item
            })
        })
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    fun startManageBooks(group: BookshelfTagGroupUi, tag: String) {
        _uiState.value = _uiState.value.copy(dialog = BookshelfTagDialogState.ManageBooks(
            BookTagAssignmentUi(
                groupId = group.groupId,
                groupName = group.groupName,
                tag = tag,
                books = group.books,
                initiallySelectedUrls = group.books.asSequence()
                    .filter { BookTagHelper.has(it.customTag, tag) }
                    .mapTo(linkedSetOf()) { it.bookUrl }
            )
        ))
    }

    fun confirmDeleteTag(group: BookshelfTagGroupUi, tag: String) {
        _uiState.value = _uiState.value.copy(dialog = BookshelfTagDialogState.DeleteConfirm(
            groupId = group.groupId, groupName = group.groupName, tag = tag, books = group.books
        ))
    }

    fun confirmRenameTag(group: BookshelfTagGroupUi, tag: String) {
        _uiState.value = _uiState.value.copy(dialog = BookshelfTagDialogState.RenameTag(
            groupId = group.groupId, groupName = group.groupName, oldTag = tag
        ))
    }

    fun dismissDialog() { _uiState.value = _uiState.value.copy(dialog = null) }

    fun saveAssignment(assignment: BookTagAssignmentUi, selectedUrls: Set<String>) {
        dismissDialog()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDb.withTransaction {
                    assignment.books.forEach { book ->
                        val write = BookTagManagement.updateTag(
                            book.customTag, assignment.tag, book.bookUrl in selectedUrls
                        ) ?: return@forEach
                        appDb.bookDao.updateCustomTag(book.bookUrl, write.customTag)
                    }
                }
            }
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
            loadTags()
        }
    }

    fun executeDeleteTag(groupId: Long, groupName: String, tag: String, books: List<BookTagInfo>) {
        dismissDialog()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDb.withTransaction {
                    books.forEach { book ->
                        val write = BookTagManagement.updateTag(book.customTag, tag, false)
                            ?: return@forEach
                        appDb.bookDao.updateCustomTag(book.bookUrl, write.customTag)
                    }
                }
                val hiddenMap = AppConfig.bookshelfHiddenTags.toMutableMap()
                hiddenMap[groupId] = hiddenMap[groupId].orEmpty().filterNot { it.equals(tag, true) }.toSet()
                if (hiddenMap[groupId].isNullOrEmpty()) hiddenMap.remove(groupId)
                AppConfig.bookshelfHiddenTags = hiddenMap
                val tagMap = AppConfig.bookshelfGroupTags.toMutableMap()
                tagMap[groupId] = tagMap[groupId].orEmpty().filterNot { it.equals(tag, true) }
                AppConfig.bookshelfGroupTags = tagMap
            }
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
            loadTags()
        }
    }

    fun executeRenameTag(groupId: Long, groupName: String, oldTag: String, newTag: String) {
        dismissDialog()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDb.withTransaction {
                    booksInGroupForRename(groupId).forEach { book ->
                        if (!BookTagHelper.has(book.customTag, oldTag)) return@forEach
                        val tags = BookTagHelper.parse(book.customTag).toMutableList()
                        val idx = tags.indexOfFirst { it.equals(oldTag, true) }
                        if (idx >= 0) {
                            tags[idx] = newTag
                            appDb.bookDao.updateCustomTag(
                                book.bookUrl,
                                BookTagHelper.join(tags.distinctBy { it.lowercase(java.util.Locale.ROOT) })
                            )
                        }
                    }
                }
                val tagMap = AppConfig.bookshelfGroupTags.toMutableMap()
                val tags = tagMap[groupId].orEmpty().toMutableList()
                val idx = tags.indexOfFirst { it.equals(oldTag, true) }
                if (idx >= 0) {
                    tags[idx] = newTag
                    tagMap[groupId] = tags.distinctBy { it.lowercase(java.util.Locale.ROOT) }
                    AppConfig.bookshelfGroupTags = tagMap
                }
                val hiddenMap = AppConfig.bookshelfHiddenTags.toMutableMap()
                val hiddenTags = hiddenMap[groupId].orEmpty().toMutableList()
                val hiddenIdx = hiddenTags.indexOfFirst { it.equals(oldTag, true) }
                if (hiddenIdx >= 0) {
                    hiddenTags[hiddenIdx] = newTag
                    hiddenMap[groupId] = hiddenTags.toSet()
                    AppConfig.bookshelfHiddenTags = hiddenMap
                }
            }
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
            loadTags()
        }
    }

    fun reorderTags(groupId: Long, newOrder: List<String>) {
        val tagMap = AppConfig.bookshelfGroupTags.toMutableMap()
        val currentTags = tagMap[groupId].orEmpty()
        val remaining = currentTags.filterNot { tag -> newOrder.any { it.equals(tag, true) } }
        tagMap[groupId] = newOrder + remaining
        AppConfig.bookshelfGroupTags = tagMap
        _uiState.value = _uiState.value.copy(groups = _uiState.value.groups.map { group ->
            if (group.groupId != groupId) group else group.copy(
                tags = newOrder.mapNotNull { tag -> group.tags.firstOrNull { it.name.equals(tag, true) } } +
                    group.tags.filterNot { item -> newOrder.any { it.equals(item.name, true) } }
            )
        })
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    private suspend fun booksInGroupForRename(groupId: Long): List<BookTagInfo> {
        val books = appDb.bookDao.allTagInfos
        val groups = appDb.bookGroupDao.all.filter { it.groupId != BookGroup.IdRoot }
        val userGroupMask = groups.asSequence().filter { it.groupId > 0 }.fold(0L) { acc, group -> acc or group.groupId }
        val group = groups.firstOrNull { it.groupId == groupId } ?: return emptyList()
        return booksInGroup(group, books, userGroupMask)
    }

    private fun booksInGroup(group: BookGroup, books: List<BookTagInfo>, userGroupMask: Long): List<BookTagInfo> = when (group.groupId) {
        BookGroup.IdAll -> books
        BookGroup.IdLocal -> books.filter { it.type and BookType.local > 0 }
        BookGroup.IdAudio -> books.filter { it.type and BookType.audio > 0 }
        BookGroup.IdVideo -> books.filter { it.type and BookType.video > 0 }
        BookGroup.IdError -> books.filter { it.type and BookType.updateError > 0 }
        BookGroup.IdNetNone -> books.filter {
            it.type and BookType.audio == 0 && it.type and BookType.video == 0 &&
                it.type and BookType.local == 0 && (it.group and userGroupMask) == 0L
        }
        BookGroup.IdLocalNone -> books.filter {
            it.type and BookType.audio == 0 && it.type and BookType.video == 0 &&
                it.type and BookType.local > 0 && (it.group and userGroupMask) == 0L
        }
        else -> if (group.groupId > 0) books.filter { it.group and group.groupId > 0 } else emptyList()
    }
}
