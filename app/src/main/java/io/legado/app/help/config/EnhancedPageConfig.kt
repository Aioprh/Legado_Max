package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * Optional Explore/RSS page enhancements.
 * Disabled by default so the existing Max behaviour remains unchanged.
 */
object EnhancedPageConfig {
    private const val ENHANCED_EXPLORE_PAGE = "enhancedExplorePage"
    private const val ENHANCED_RSS_PAGE = "enhancedRssPage"

    var enhancedExplorePage: Boolean
        get() = appCtx.getPrefBoolean(ENHANCED_EXPLORE_PAGE, false)
        set(value) = appCtx.putPrefBoolean(ENHANCED_EXPLORE_PAGE, value)

    var enhancedRssPage: Boolean
        get() = appCtx.getPrefBoolean(ENHANCED_RSS_PAGE, false)
        set(value) = appCtx.putPrefBoolean(ENHANCED_RSS_PAGE, value)
}
