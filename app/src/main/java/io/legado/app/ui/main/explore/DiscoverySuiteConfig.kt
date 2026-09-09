package io.legado.app.ui.main.explore

import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.util.UUID

data class DiscoverySuiteConfig(val suites: List<DiscoverySuite> = emptyList())

data class DiscoverySuite(
    val id: String = "",
    val name: String = "",
    val alias: String = "",
    val opacityMultiplier: Float = 1f,
    val order: Int = 0,
    val widgets: List<DiscoverySuiteWidget> = emptyList()
) { val displayName: String get() = alias.ifBlank { name } }

data class DiscoverySuiteWidget(
    val id: String = "",
    val type: String = DiscoverySuiteWidgetType.RandomBooks.value,
    val title: String = "",
    val targets: List<DiscoverySuiteWidgetTarget> = emptyList(),
    val displayLimit: Int = 12,
    val order: Int = 0
)

data class DiscoverySuiteWidgetTarget(val sourceUrl: String = "", val tagUrl: String = "", val title: String = "")

enum class DiscoverySuiteWidgetType(val value: String) {
    RandomBooks("random_books"), TagBar("tag_bar"), HorizontalBooks("horizontal_books"), WaterfallBooks("waterfall_books");
    companion object { fun sanitize(value: String) = values().firstOrNull { it.value == value }?.value ?: RandomBooks.value }
}

object DiscoverySuiteStore {
    private const val KEY = "maxDiscoverySuiteConfig"
    private const val SELECTED = "maxSelectedDiscoverySuiteId"
    fun load(): DiscoverySuiteConfig {
        val raw = appCtx.getPrefString(KEY).orEmpty()
        return if (raw.isBlank()) DiscoverySuiteConfig() else runCatching { GSON.fromJson(raw, DiscoverySuiteConfig::class.java) ?: DiscoverySuiteConfig() }.getOrDefault(DiscoverySuiteConfig())
    }
    fun save(config: DiscoverySuiteConfig) { appCtx.putPrefString(KEY, GSON.toJson(config)) }
    fun selectedSuiteId(): String = appCtx.getPrefString(SELECTED).orEmpty()
    fun setSelectedSuiteId(id: String) = appCtx.putPrefString(SELECTED, id)
    fun newSuite(name: String): DiscoverySuite = DiscoverySuite(newId("suite"), name.trim().take(40).ifBlank { "发现套件" }, order = Int.MAX_VALUE)
    fun newWidget(title: String, type: String = DiscoverySuiteWidgetType.RandomBooks.value) = DiscoverySuiteWidget(newId("widget"), DiscoverySuiteWidgetType.sanitize(type), title.trim().take(60).ifBlank { "推荐" }, order = Int.MAX_VALUE)
    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID()}"
}

fun DiscoverySuiteWidget.validTargets() = targets.filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }
fun DiscoverySuiteWidget.cacheSignature() = "$type|$title|${targets.joinToString(";") { "${it.sourceUrl}|${it.tagUrl}" }}"
fun DiscoverySuiteWidgetTarget.key() = "$sourceUrl\n$tagUrl"
