package io.legado.app.ui.main.explore

import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.util.UUID

data class DiscoverySuiteConfig(val suites: List<DiscoverySuite> = emptyList())
data class DiscoverySuite(val id: String = "", val name: String = "", val alias: String = "", val order: Int = 0, val widgets: List<DiscoverySuiteWidget> = emptyList()) { val displayName: String get() = alias.ifBlank { name } }
data class DiscoverySuiteWidget(val id: String = "", val type: String = DiscoverySuiteWidgetType.RandomBooks.value, val title: String = "", val targets: List<DiscoverySuiteWidgetTarget> = emptyList(), val sourceUrls: List<String> = emptyList(), val tagUrls: List<String> = emptyList(), val displayLimit: Int = 12, val order: Int = 0)
data class DiscoverySuiteWidgetTarget(val sourceUrl: String = "", val tagUrl: String = "", val title: String = "")

enum class DiscoverySuiteWidgetType(val value: String) { RandomBooks("random_books"), TagBar("tag_bar"), RankButtons("rank_buttons"), BookList("book_list"), HorizontalBooks("horizontal_books"), RankedList("ranked_list"), WaterfallBooks("waterfall_books"); companion object { fun sanitize(v: String) = entries.firstOrNull { it.value == v }?.value ?: RandomBooks.value } }

object DiscoverySuiteStore {
    private const val CONFIG_KEY = "discovery_suite_config"
    private const val SELECTED_KEY = "discovery_selected_suite"
    fun load(): DiscoverySuiteConfig {
        val raw = appCtx.getPrefString(CONFIG_KEY).orEmpty()
        if (raw.isBlank() || raw.length > 96 * 1024) return DiscoverySuiteConfig()
        return runCatching { GSON.fromJson(raw, DiscoverySuiteConfig::class.java) ?: DiscoverySuiteConfig() }.getOrDefault(DiscoverySuiteConfig())
    }
    fun save(config: DiscoverySuiteConfig) { appCtx.putPrefString(CONFIG_KEY, GSON.toJson(config.sanitize())) }
    fun selectedSuiteId() = appCtx.getPrefString(SELECTED_KEY).orEmpty()
    fun setSelectedSuiteId(id: String) = appCtx.putPrefString(SELECTED_KEY, id.take(64))
    fun newSuite(name: String) = DiscoverySuite("suite-${UUID.randomUUID()}", name.trim().take(40).ifBlank { "发现套件" }, order = Int.MAX_VALUE)
    fun newWidget(title: String, type: String = DiscoverySuiteWidgetType.RandomBooks.value) = DiscoverySuiteWidget("widget-${UUID.randomUUID()}", DiscoverySuiteWidgetType.sanitize(type), title.trim().take(60).ifBlank { "书籍" }, displayLimit = 12, order = Int.MAX_VALUE)
    private fun DiscoverySuiteConfig.sanitize() = copy(suites = suites.asSequence().filter { it.id.isNotBlank() }.distinctBy { it.id }.sortedBy { it.order }.take(20).mapIndexed { si, s -> s.copy(id = s.id.take(64), name = s.name.trim().take(40).ifBlank { "发现套件 ${si + 1}" }, alias = s.alias.trim().take(40), order = si, widgets = s.widgets.asSequence().filter { it.id.isNotBlank() }.distinctBy { it.id }.sortedBy { it.order }.take(50).mapIndexed { wi, w -> w.copy(id = w.id.take(64), type = DiscoverySuiteWidgetType.sanitize(w.type), title = w.title.trim().take(60).ifBlank { "书籍 ${wi + 1}" }, displayLimit = w.displayLimit.coerceIn(1, 60), order = wi) }.toList()) }.toList())
}
