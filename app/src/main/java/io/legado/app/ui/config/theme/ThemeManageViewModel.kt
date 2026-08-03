package io.legado.app.ui.config.theme

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class ThemeManageViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ThemeManageUiState())
    val uiState: StateFlow<ThemeManageUiState> = _uiState.asStateFlow()

    private val _events = Channel<ThemeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadThemes()
    }

    // ── 数据加载 ──────────────────────────────────────────

    fun loadThemes() {
        ThemeConfig.configList // 触发 lazy init
        val items = ThemeConfig.configList.mapIndexed { index, config ->
            ThemeItem(config = config, originalIndex = index)
        }
        _uiState.update { state ->
            state.copy(
                allItems = items,
                visibleItems = items.filter { it.config.isNightTheme == state.tab.isNight }
            )
        }
    }

    // ── Tab 切换 ──────────────────────────────────────────

    fun switchTab(tab: ThemeTab) {
        _uiState.update { state ->
            if (state.tab == tab) return@update state
            state.copy(
                tab = tab,
                multiSelect = MultiSelectState(),
                visibleItems = state.allItems.filter { it.config.isNightTheme == tab.isNight }
            )
        }
    }

    // ── 多选 ──────────────────────────────────────────────

    fun enterMultiSelect(originalIndex: Int) {
        _uiState.update { state ->
            state.copy(
                multiSelect = MultiSelectState(
                    active = true,
                    selectedOriginalIndices = setOf(originalIndex)
                )
            )
        }
    }

    fun exitMultiSelect() {
        _uiState.update { state -> state.copy(multiSelect = MultiSelectState()) }
    }

    fun toggleSelection(originalIndex: Int) {
        _uiState.update { state ->
            val current = state.multiSelect.selectedOriginalIndices
            val updated = if (originalIndex in current) current - originalIndex else current + originalIndex
            if (updated.isEmpty()) {
                state.copy(multiSelect = MultiSelectState())
            } else {
                state.copy(multiSelect = state.multiSelect.copy(selectedOriginalIndices = updated))
            }
        }
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            if (state.allVisibleSelected) {
                state.copy(multiSelect = MultiSelectState())
            } else {
                val all = state.visibleItems.map { it.originalIndex }.toSet()
                state.copy(multiSelect = state.multiSelect.copy(selectedOriginalIndices = all))
            }
        }
    }

    // ── 批量操作 ──────────────────────────────────────────

    fun requestDeleteSelected() {
        if (_uiState.value.multiSelect.isEmpty) {
            _events.trySend(ThemeEvent.Toast(R.string.select_theme))
            return
        }
        _events.trySend(ThemeEvent.DeleteConfirm)
    }

    fun executeDeleteSelected() {
        val indices = _uiState.value.multiSelect.selectedOriginalIndices.sortedDescending()
        execute {
            indices.forEach { ThemeConfig.delConfig(it) }
            exitMultiSelect()
            loadThemes()
        }
    }

    fun toTopSelected() {
        if (_uiState.value.multiSelect.isEmpty) {
            _events.trySend(ThemeEvent.Toast(R.string.select_theme))
            return
        }
        val positions = _uiState.value.multiSelect.selectedOriginalIndices.sorted()
        execute {
            ThemeConfig.toTopConfigs(positions)
            exitMultiSelect()
            loadThemes()
        }
    }

    fun exportSelected() {
        val state = _uiState.value
        val configs = state.multiSelect.selectedOriginalIndices
            .sorted()
            .mapNotNull { idx -> state.allItems.getOrNull(idx)?.config }
        if (configs.isEmpty()) return
        _events.trySend(ThemeEvent.ShareJson(GSON.toJson(configs)))
        exitMultiSelect()
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

    // ── 单项操作 ──────────────────────────────────────────

    fun applyConfig(item: ThemeItem) {
        ThemeConfig.applyConfig(getApplication(), item.config)
        val newTab = if (item.config.isNightTheme) ThemeTab.NIGHT else ThemeTab.DAY
        _uiState.update { state ->
            state.copy(
                tab = newTab,
                visibleItems = state.allItems.filter { it.config.isNightTheme == newTab.isNight }
            )
        }
        _events.trySend(ThemeEvent.Applied(item.config.themeName))
        _events.trySend(ThemeEvent.Recreate)
    }

    fun deleteItem(item: ThemeItem) {
        execute {
            ThemeConfig.delConfig(item.originalIndex)
            loadThemes()
        }
    }

    fun shareItem(item: ThemeItem) {
        _events.trySend(ThemeEvent.ShareJson(GSON.toJson(item.config)))
    }

    // ── 编辑弹窗 ──────────────────────────────────────────

    fun openEditDialog(sourceItem: ThemeItem? = null) {
        val config = sourceItem?.config?.copy() ?: newThemeConfig()
        _uiState.update { state ->
            state.copy(
                editDialog = EditDialogState(
                    visible = true,
                    isNew = sourceItem == null,
                    editingIndex = sourceItem?.originalIndex ?: -1,
                    draft = config
                )
            )
        }
    }

    fun closeEditDialog() {
        _uiState.update { state -> state.copy(editDialog = EditDialogState()) }
    }

    fun updateDraft(transform: (ThemeConfig.Config) -> ThemeConfig.Config) {
        _uiState.update { state ->
            val draft = state.editDialog.draft ?: return@update state
            state.copy(editDialog = state.editDialog.copy(draft = transform(draft)))
        }
    }

    fun saveEditedTheme() {
        val state = _uiState.value
        val config = state.editDialog.draft ?: return
        val targetIndex = state.editDialog.editingIndex

        execute {
            if (targetIndex >= 0) {
                ThemeConfig.configList[targetIndex] = config
            } else {
                ThemeConfig.configList.add(config)
            }
            ThemeConfig.save()
            loadThemes()
            closeEditDialog()

            val current = ThemeConfig.getDurConfig(getApplication())
            if (current.themeName == config.themeName && current.isNightTheme == config.isNightTheme) {
                ThemeConfig.applyConfig(getApplication(), config)
                _events.trySend(ThemeEvent.Recreate)
            }
            _events.trySend(ThemeEvent.Toast(R.string.success))
        }
    }

    // ── 背景图处理（由 Activity 回调） ────────────────────

    fun onBackgroundImageSelected(path: String) {
        updateDraft { it.copy(backgroundImgPath = path) }
    }

    // ── 颜色选择处理（由 Activity ColorPickerDialog 回调） ──

    fun onColorSelected(colorKey: String, color: Int) {
        // ColorPickerDialog 关闭了alpha滑块，但保险起见补齐8位
        val hex = "#" + Integer.toHexString(color).padStart(8, '0').uppercase()
        updateDraft { draft ->
            when (colorKey) {
                "primaryColor" -> draft.copy(primaryColor = hex)
                "accentColor" -> draft.copy(accentColor = hex)
                "backgroundColor" -> draft.copy(backgroundColor = hex)
                "bottomBackground" -> draft.copy(bottomBackground = hex)
                else -> draft
            }
        }
    }

    // ── 虚化值处理（由 Activity NumberPickerDialog 回调） ──

    fun onBlurSelected(blur: Int) {
        updateDraft { it.copy(backgroundImgBlur = blur) }
    }

    // ── 内部 ──────────────────────────────────────────────

    private fun newThemeConfig(): ThemeConfig.Config {
        val app = getApplication<Application>()
        return ThemeConfig.getDurConfig(app).copy(
            themeName = getNextThemeName(),
            isNightTheme = _uiState.value.tab.isNight
        )
    }

    private fun getNextThemeName(): String {
        val base = getApplication<Application>().getString(R.string.add_theme)
        val usedNames = ThemeConfig.configList
            .filter { it.isNightTheme == _uiState.value.tab.isNight }
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