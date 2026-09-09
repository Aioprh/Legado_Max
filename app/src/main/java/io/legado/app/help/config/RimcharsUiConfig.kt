package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * Rimchars 风格发现/订阅页面独立开关。
 * 默认关闭，完全不改变原有页面行为。
 */
object RimcharsUiConfig {
    private const val DISCOVERY_KEY = "rimcharsStyleDiscoveryPage"
    private const val RSS_KEY = "rimcharsStyleRssPage"

    var discoveryEnabled: Boolean
        get() = appCtx.getPrefBoolean(DISCOVERY_KEY, false)
        set(value) = appCtx.putPrefBoolean(DISCOVERY_KEY, value)

    var rssEnabled: Boolean
        get() = appCtx.getPrefBoolean(RSS_KEY, false)
        set(value) = appCtx.putPrefBoolean(RSS_KEY, value)
}
