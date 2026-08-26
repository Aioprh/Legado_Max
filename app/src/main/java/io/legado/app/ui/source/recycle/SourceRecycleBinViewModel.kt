package io.legado.app.ui.source.recycle

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceRecycleBinHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 回收站确认 Dialog 状态
 * （state-events.md §4.5：Dialog 由 ViewModel 状态条件渲染）
 * 单个操作的弹窗复用列表形式（size == 1）
 */
sealed interface RecycleBinDialogState {
    /** 确认恢复（无冲突） */
    data class RestoreConfirm(val items: List<SourceRecycleBin>) : RecycleBinDialogState

    /** 恢复时存在同名源，确认是否覆盖 */
    data class ConflictConfirm(val items: List<SourceRecycleBin>) : RecycleBinDialogState

    /** 确认彻底删除 */
    data class DeleteConfirm(val items: List<SourceRecycleBin>) : RecycleBinDialogState

    /** 确认清空回收站 */
    data object ClearAll : RecycleBinDialogState
}

/** 回收站 UI 事件 */
sealed interface RecycleBinEvent {
    data class Toast(@StringRes val msgRes: Int) : RecycleBinEvent
}

class SourceRecycleBinViewModel(application: Application) : BaseViewModel(application) {

    private val _filter = MutableStateFlow(SourceRecycleBinFilter.ALL)
    val filter: StateFlow<SourceRecycleBinFilter> = _filter.asStateFlow()
    private val _enabled = MutableStateFlow(AppConfig.sourceRecycleBinEnabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 确认 Dialog 状态（§4.5） */
    private val _dialog = MutableStateFlow<RecycleBinDialogState?>(null)
    val dialog: StateFlow<RecycleBinDialogState?> = _dialog.asStateFlow()

    /** 批量选中项（批量操作需要，提升到 ViewModel，§4.2） */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Toast 事件：允许丢事件（连续操作只提示最新一条），用 CONFLATED 通道（§4.1）
    private val _toasts = Channel<RecycleBinEvent.Toast>(Channel.CONFLATED)
    val toasts: Flow<RecycleBinEvent.Toast> = _toasts.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<SourceRecycleBin>> = _filter.flatMapLatest { filter ->
        if (filter.type == null) {
            appDb.sourceRecycleBinDao.flowAll()
        } else {
            appDb.sourceRecycleBinDao.flowByType(filter.type)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            SourceRecycleBinHelp.cleanupExpired()
        }
    }

    fun setFilter(filter: SourceRecycleBinFilter) {
        _filter.value = filter
    }

    fun setEnabled(enabled: Boolean) {
        AppConfig.sourceRecycleBinEnabled = enabled
        _enabled.value = enabled
    }

    /** 请求弹出确认 Dialog（§4.5） */
    fun showDialog(dialog: RecycleBinDialogState) {
        _dialog.value = dialog
    }

    /** 关闭当前 Dialog */
    fun dismissDialog() {
        _dialog.value = null
    }

    fun toggleSelected(id: Long) {
        _selectedIds.update { ids -> if (id in ids) ids - id else ids + id }
    }

    fun setSelected(ids: Set<Long>) {
        _selectedIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** 剔除已不在列表中的选中项（列表刷新后调用） */
    fun pruneInvalidSelection(items: List<SourceRecycleBin>) {
        val validIds = items.mapTo(linkedSetOf()) { it.id }
        _selectedIds.update { ids -> ids.filterTo(linkedSetOf()) { it in validIds } }
    }

    /** 是否存在同名源（R3：回调改 suspend，由调用方协程获取结果） */
    suspend fun hasConflict(item: SourceRecycleBin): Boolean =
        withContext(Dispatchers.IO) { SourceRecycleBinHelp.hasConflict(item) }

    /** 列表中任意一项是否存在同名源 */
    suspend fun hasConflict(items: List<SourceRecycleBin>): Boolean =
        withContext(Dispatchers.IO) { items.any { SourceRecycleBinHelp.hasConflict(it) } }

    fun restore(items: List<SourceRecycleBin>, overwrite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { SourceRecycleBinHelp.restore(it, overwrite) }
            _toasts.trySend(RecycleBinEvent.Toast(R.string.source_recycle_bin_restored))
            removeSelected(items.map { it.id })
        }
    }

    fun delete(items: List<SourceRecycleBin>) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.sourceRecycleBinDao.delete(*items.toTypedArray())
            _toasts.trySend(RecycleBinEvent.Toast(R.string.source_recycle_bin_deleted))
            removeSelected(items.map { it.id })
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.sourceRecycleBinDao.deleteAll()
            _toasts.trySend(RecycleBinEvent.Toast(R.string.source_recycle_bin_cleared))
            _selectedIds.value = emptySet()
        }
    }

    private fun removeSelected(ids: List<Long>) {
        val idSet = ids.toSet()
        _selectedIds.update { current -> current - idSet }
    }
}

enum class SourceRecycleBinFilter(
    val labelRes: Int,
    val type: String?
) {
    ALL(R.string.all, null),
    BOOK_SOURCE(R.string.book_source, SourceRecycleBinHelp.TYPE_BOOK_SOURCE),
    RSS_SOURCE(R.string.rss_source, SourceRecycleBinHelp.TYPE_RSS_SOURCE),
    REPLACE_RULE(R.string.replace_rule, SourceRecycleBinHelp.TYPE_REPLACE_RULE),
    TXT_TOC_RULE(R.string.txt_toc_rule, SourceRecycleBinHelp.TYPE_TXT_TOC_RULE),
    HTTP_TTS(R.string.speak_engine, SourceRecycleBinHelp.TYPE_HTTP_TTS),
    DICT_RULE(R.string.dict_rule, SourceRecycleBinHelp.TYPE_DICT_RULE),
    HIGHLIGHT_RULE(R.string.highlight_rule_config, SourceRecycleBinHelp.TYPE_HIGHLIGHT_RULE),
    SEARCH_ENGINE(R.string.search_engine_rule, SourceRecycleBinHelp.TYPE_SEARCH_ENGINE)
}
