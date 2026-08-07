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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import splitties.systemservices.clipboardManager

/**
 * 主题管理 ViewModel（组合模式重构版）。
 *
 * 仅负责主题数据的加载、增删改查和一次性事件发送。
 * 通用的 Tab/多选/编辑弹窗状态由 [ConfigManageState] 在 Composable 层持有，
 * ViewModel 不再管理这些 UI 交互状态。
 *
 * 数据操作通过 BaseViewModel.execute 在协程中执行，
 * 一次性事件通过 Channel 向上抛给 Activity 处理。
 */
class ThemeManageViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<ThemeItem>>(emptyList())
    val items: StateFlow<List<ThemeItem>> = _items.asStateFlow()

    private val _events = Channel<ThemeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadThemes()
    }

    // ── 数据加载 ──────────────────────────────────────────

    fun loadThemes() {
        ThemeConfig.configList // 触发 lazy init
        val itemList = ThemeConfig.configList.mapIndexed { index, config ->
            ThemeItem(config = config, originalIndex = index)
        }
        _items.value = itemList
    }

    /**
     * 获取指定 Tab 下的可见条目。
     */
    fun getItemsForTab(tab: ConfigTab): List<ThemeItem> {
        return _items.value.filter { it.config.isNightTheme == tab.isNight }
    }

    // ── 单项操作 ──────────────────────────────────────────

    fun applyConfig(item: ThemeItem) {
        ThemeConfig.applyConfig(getApplication(), item.config)
        _events.trySend(ThemeEvent.Applied(item.config.themeName))
        _events.trySend(ThemeEvent.Recreate)
    }

    fun deleteItem(item: ThemeItem) {
        // 阻止删除正在使用的主题
        val currentConfig = ThemeConfig.getDurConfig(getApplication())
        if (item.config.themeName == currentConfig.themeName
            && item.config.isNightTheme == currentConfig.isNightTheme
        ) {
            _events.trySend(ThemeEvent.Toast(R.string.cannot_delete_current_theme))
            return
        }
        execute {
            ThemeConfig.delConfig(item.originalIndex)
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

    // ── 批量操作 ──────────────────────────────────────────

    fun requestDeleteSelected(selectedIndices: Set<Int>) {
        if (selectedIndices.isEmpty()) {
            _events.trySend(ThemeEvent.Toast(R.string.select_theme))
            return
        }
        _events.trySend(ThemeEvent.DeleteConfirm)
    }

    fun executeDeleteSelected(selectedIndices: Set<Int>) {
        val currentConfig = ThemeConfig.getDurConfig(getApplication())
        val indices = selectedIndices
            .filterNot { idx ->
                val item = _items.value.getOrNull(idx)
                item != null
                    && item.config.themeName == currentConfig.themeName
                    && item.config.isNightTheme == currentConfig.isNightTheme
            }
            .sortedDescending()
        execute {
            indices.forEach { ThemeConfig.delConfig(it) }
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
            ThemeConfig.toTopConfigs(positions)
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
                _events.trySend(ThemeEvent.ImportEmpty)
                return@execute
            }
            val count = ThemeConfig.addConfig(clipText)
            if (count > 0) {
                loadThemes()
                _events.trySend(ThemeEvent.ImportSuccess)
            } else {
                _events.trySend(ThemeEvent.ImportFailed)
            }
        }
    }

    // ── 编辑弹窗：数据操作 ────────────────────────────────

    /**
     * 创建编辑弹窗的草稿数据。
     * 返回 (draft, isNew, editingIndex) 三元组，由调用方传入 ConfigManageState.openEditDialog。
     */
    fun createDraft(sourceItem: ThemeItem?): ThemeEditDraft {
        val config = sourceItem?.config?.copy() ?: newThemeConfig()
        return ThemeEditDraft(
            config = config,
            isNew = sourceItem == null,
            editingIndex = sourceItem?.originalIndex ?: -1
        )
    }

    fun saveEditedTheme(draft: ThemeConfig.Config, editingIndex: Int) {
        execute {
            if (editingIndex >= 0) {
                ThemeConfig.configList[editingIndex] = draft
            } else {
                ThemeConfig.configList.add(draft)
            }
            ThemeConfig.save()
            loadThemes()

            val current = ThemeConfig.getDurConfig(getApplication())
            if (current.themeName == draft.themeName && current.isNightTheme == draft.isNightTheme) {
                ThemeConfig.applyConfig(getApplication(), draft)
                _events.trySend(ThemeEvent.Recreate)
            }
            _events.trySend(ThemeEvent.Toast(R.string.success))
        }
    }

    // ── 背景图 / 颜色 / 虚化回调（由 Activity 触发） ──────

    fun onColorSelected(colorKey: String, color: Int, currentDraft: ThemeConfig.Config): ThemeConfig.Config {
        val hex = "#" + Integer.toHexString(color).padStart(8, '0').uppercase()
        return when (colorKey) {
            "primaryColor" -> currentDraft.copy(primaryColor = hex)
            "accentColor" -> currentDraft.copy(accentColor = hex)
            "backgroundColor" -> currentDraft.copy(backgroundColor = hex)
            "bottomBackground" -> currentDraft.copy(bottomBackground = hex)
            else -> currentDraft
        }
    }

    fun onBlurSelected(blur: Int, currentDraft: ThemeConfig.Config): ThemeConfig.Config {
        return currentDraft.copy(backgroundImgBlur = blur)
    }

    fun onBackgroundImageSelected(path: String, currentDraft: ThemeConfig.Config): ThemeConfig.Config {
        return currentDraft.copy(backgroundImgPath = path)
    }

    // ── 内部 ──────────────────────────────────────────────

    private fun newThemeConfig(): ThemeConfig.Config {
        val app = getApplication<Application>()
        return ThemeConfig.getDurConfig(app).copy(
            themeName = getNextThemeName(),
            isNightTheme = AppConfig.isNightTheme
        )
    }

    private fun getNextThemeName(): String {
        val base = getApplication<Application>().getString(R.string.add_theme)
        val usedNames = ThemeConfig.configList
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

/**
 * 编辑弹窗草稿数据（由 ViewModel 创建，传递给 Screen 层组装）。
 */
data class ThemeEditDraft(
    val config: ThemeConfig.Config,
    val isNew: Boolean,
    val editingIndex: Int
)
