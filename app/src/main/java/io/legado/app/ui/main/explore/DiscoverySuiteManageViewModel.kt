package io.legado.app.ui.main.explore

import android.app.Application
import io.legado.app.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 套件管理页 UI 状态。
 */
data class DiscoverySuiteManageUiState(
    val suites: List<DiscoverySuite> = emptyList(),
    val selectedSuiteId: String = ""
)

/**
 * 套件管理 ViewModel。
 *
 * 所有增删改都直接作用于 [DiscoverySuiteStore]（持久化为 Preference JSON），
 * 并通过 [reload] 刷新 UI；同时维护当前选中套件。
 */
class DiscoverySuiteManageViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(DiscoverySuiteManageUiState())
    val uiState: StateFlow<DiscoverySuiteManageUiState> get() = _uiState

    fun reload() {
        val config = DiscoverySuiteStore.load()
        val selected = DiscoverySuiteStore.selectedSuiteId()
            .takeIf { id -> config.suites.any { it.id == id } }
            ?: config.suites.firstOrNull()?.id.orEmpty()
        _uiState.value = DiscoverySuiteManageUiState(
            suites = config.suites,
            selectedSuiteId = selected
        )
    }

    fun createSuite(name: String, alias: String) {
        val created = DiscoverySuiteStore.newSuite(name).copy(alias = alias)
        transformConfig({
            it.copy(suites = it.suites + created)
        }, setCurrent = created.id)
    }

    fun renameSuite(id: String, name: String, alias: String) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id == id) suite.copy(name = name, alias = alias) else suite
                }
            )
        }
    }

    fun setOpacity(id: String, multiplier: Float) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id == id) suite.copy(opacityMultiplier = multiplier) else suite
                }
            )
        }
    }

    fun setCurrent(id: String) {
        DiscoverySuiteStore.setSelectedSuiteId(id)
        reload()
    }

    fun deleteSuite(id: String) {
        val selected = _uiState.value.selectedSuiteId
        var nextSelected: String? = null
        transformConfig({ config ->
            val remaining = config.suites.filterNot { it.id == id }
            if (selected == id) {
                nextSelected = remaining.firstOrNull()?.id.orEmpty()
            }
            config.copy(suites = remaining)
        }, setCurrent = nextSelected)
    }

    fun addWidget(suiteId: String, widget: DiscoverySuiteWidget) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id == suiteId) {
                        suite.copy(widgets = suite.widgets + widget)
                    } else {
                        suite
                    }
                }
            )
        }
    }

    fun updateWidget(suiteId: String, updated: DiscoverySuiteWidget) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id == suiteId) {
                        suite.copy(
                            widgets = suite.widgets.map { w ->
                                if (w.id == updated.id) updated else w
                            }
                        )
                    } else {
                        suite
                    }
                }
            )
        }
    }

    fun moveWidget(suiteId: String, widgetId: String, delta: Int) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id != suiteId) {
                        suite
                    } else {
                        val list = suite.widgets.toMutableList()
                        val index = list.indexOfFirst { it.id == widgetId }
                        if (index < 0) {
                            suite
                        } else {
                            val target = index + delta
                            if (target !in list.indices) {
                                suite
                            } else {
                                val element = list.removeAt(index)
                                list.add(target, element)
                                suite.copy(widgets = list)
                            }
                        }
                    }
                }
            )
        }
    }

    fun deleteWidget(suiteId: String, widgetId: String) {
        transformConfig { config ->
            config.copy(
                suites = config.suites.map { suite ->
                    if (suite.id == suiteId) {
                        suite.copy(widgets = suite.widgets.filterNot { it.id == widgetId })
                    } else {
                        suite
                    }
                }
            )
        }
    }

    /** 从存储读取→变换→保存→更新选中→刷新 UI。 */
    private fun transformConfig(
        transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig,
        setCurrent: String? = null
    ) {
        val current = DiscoverySuiteStore.load()
        DiscoverySuiteStore.save(transform(current))
        setCurrent?.let { DiscoverySuiteStore.setSelectedSuiteId(it) }
        reload()
    }
}