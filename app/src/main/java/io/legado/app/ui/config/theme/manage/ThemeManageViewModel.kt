package io.legado.app.ui.config.theme.manage

import android.app.Application
import android.content.ClipData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.widget.ConfigTab
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import splitties.systemservices.clipboardManager

/**
 * 主题管理的 ViewModel 层
 * 
 * 架构决策（为什么这么写）：
 * 1. 严格遵循 UDF (单向数据流)：UI 的所有核心状态（列表 items、草稿 editDraft）必须在这里收拢，
 *    仅对外暴露只读的 StateFlow。绝对禁止 UI 层相互注册脏回调来“反向掏取”数据。
 * 2. 剥离直接的磁盘 IO：所有数据获取和保存操作依赖 [ThemeRepository]，彻底与静态单例解耦。
 * 3. 一次性事件防丢失与防重放：采用 Channel(BUFFERED) 收拢弹 Toast 等动作，利用 receiveAsFlow 消费，
 *    避免横竖屏切换时状态重建引发的重复弹窗。
 */
class ThemeManageViewModel(
    private val repository: ThemeRepository,
    application: Application
) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<ThemeItem>>(emptyList())
    val items: StateFlow<List<ThemeItem>> = _items.asStateFlow()

    private val _events = Channel<ThemeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _editDraft = MutableStateFlow<ThemeConfig.Config?>(null)
    val editDraft: StateFlow<ThemeConfig.Config?> = _editDraft.asStateFlow()

    init {
        execute { loadThemes() }
    }

    private fun loadThemes() {
        val itemList = repository.getThemes().mapIndexed { index, config ->
            ThemeItem(config = config, originalIndex = index)
        }
        _items.value = itemList
    }

    fun applyConfig(item: ThemeItem) {
        execute {
            withContext(Dispatchers.Main) {
                repository.applyTheme(item.config)
            }
            _events.send(ThemeEvent.Applied(item.config.themeName))
        }
    }

    fun deleteItem(item: ThemeItem) {
        val currentConfig = repository.getDurConfig()
        if (item.config.themeName == currentConfig.themeName &&
            item.config.isNightTheme == currentConfig.isNightTheme
        ) {
            _events.trySend(ThemeEvent.Toast(R.string.cannot_delete_current_theme))
            return
        }
        execute {
            repository.deleteConfig(item.originalIndex)
            loadThemes()
        }
    }

    fun shareItem(item: ThemeItem) {
        _events.trySend(ThemeEvent.ShareJson(GSON.toJson(item.config)))
    }

    fun copyItem(item: ThemeItem) {
        val json = GSON.toJson(item.config)
        val clipData = ClipData.newPlainText(null, json)
        clipboardManager.setPrimaryClip(clipData)
        _events.trySend(ThemeEvent.ToastMsg("${item.config.themeName}主题已拷贝"))
    }

    fun requestDeleteSelected(selectedIndices: Set<Int>) {
        if (selectedIndices.isEmpty()) {
            _events.trySend(ThemeEvent.Toast(R.string.select_theme))
            return
        }
        _events.trySend(ThemeEvent.DeleteConfirm)
    }

    fun executeDeleteSelected(selectedIndices: Set<Int>) {
        val currentConfig = repository.getDurConfig()
        val indices = selectedIndices
            .filterNot { idx ->
                val item = _items.value.getOrNull(idx)
                item != null &&
                item.config.themeName == currentConfig.themeName &&
                item.config.isNightTheme == currentConfig.isNightTheme
            }
            .sortedDescending()
        execute {
            indices.forEach { repository.deleteConfig(it) }
            loadThemes()
        }
    }

    fun toTopSelected(selectedIndices: Set<Int>) {
        if (selectedIndices.isEmpty()) {
            _events.trySend(ThemeEvent.Toast(R.string.select_theme))
            return
        }
        val positions = selectedIndices.sorted()
        execute {
            repository.toTopConfigs(positions)
            loadThemes()
        }
    }

    fun exportSelected(selectedIndices: Set<Int>) {
        val configs = selectedIndices
            .sorted()
            .mapNotNull { idx -> _items.value.getOrNull(idx)?.config }
        if (configs.isEmpty()) return
        _events.trySend(ThemeEvent.ShareJson(GSON.toJson(configs)))
    }

    fun importFromClipboard() {
        execute {
            val clipText = getApplication<Application>().getClipText()
            if (clipText.isNullOrBlank()) {
                _events.send(ThemeEvent.ImportEmpty)
                return@execute
            }
            val count = repository.addConfig(clipText)
            if (count > 0) {
                loadThemes()
                _events.send(ThemeEvent.ImportSuccess)
            } else {
                _events.send(ThemeEvent.ImportFailed)
            }
        }
    }

    fun startEdit(item: ThemeItem?): ThemeEditDraft {
        val config = item?.config?.copy() ?: newThemeConfig()
        _editDraft.value = config
        return ThemeEditDraft(
            config = config,
            isNew = item == null,
            editingIndex = item?.originalIndex ?: -1
        )
    }

    fun clearEditDraft() {
        _editDraft.value = null
    }

    fun saveEditedTheme(editingIndex: Int) {
        val draft = _editDraft.value ?: return
        execute {
            repository.saveTheme(draft, editingIndex)
            val current = repository.getDurConfig()
            if (current.themeName == draft.themeName && current.isNightTheme == draft.isNightTheme) {
                withContext(Dispatchers.Main) {
                    repository.applyTheme(draft)
                }
            }
            loadThemes()
            _events.send(ThemeEvent.Toast(R.string.success))
            clearEditDraft()
        }
    }

    fun updateDraftColor(colorKey: String, color: Int) {
        val currentDraft = _editDraft.value ?: return
        val hex = "#" + Integer.toHexString(color).padStart(8, '0').uppercase()
        _editDraft.value = when (colorKey) {
            "primaryColor" -> currentDraft.copy(primaryColor = hex)
            "accentColor" -> currentDraft.copy(accentColor = hex)
            "backgroundColor" -> currentDraft.copy(backgroundColor = hex)
            "bottomBackground" -> currentDraft.copy(bottomBackground = hex)
            else -> currentDraft
        }
    }

    fun updateDraftBlur(blur: Int) {
        val currentDraft = _editDraft.value ?: return
        _editDraft.value = currentDraft.copy(backgroundImgBlur = blur)
    }

    fun updateDraftBackgroundImage(path: String) {
        val currentDraft = _editDraft.value ?: return
        _editDraft.value = currentDraft.copy(backgroundImgPath = path)
    }
    
    fun updateDraftConfig(transform: (ThemeConfig.Config) -> ThemeConfig.Config) {
        val currentDraft = _editDraft.value ?: return
        _editDraft.value = transform(currentDraft)
    }

    private fun newThemeConfig(): ThemeConfig.Config {
        return repository.getDurConfig().copy(
            themeName = getNextThemeName(),
            isNightTheme = AppConfig.isNightTheme
        )
    }

    private fun getNextThemeName(): String {
        val base = getApplication<Application>().getString(R.string.add_theme)
        val usedNames = repository.getThemes()
            .filter { it.isNightTheme == AppConfig.isNightTheme }
            .map { it.themeName }
            .toSet()
        if (!usedNames.contains(base)) return base
        for (i in 2..999) {
            val name = "$base $i"
            if (!usedNames.contains(name)) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }
}

data class ThemeEditDraft(
    val config: ThemeConfig.Config,
    val isNew: Boolean,
    val editingIndex: Int
)