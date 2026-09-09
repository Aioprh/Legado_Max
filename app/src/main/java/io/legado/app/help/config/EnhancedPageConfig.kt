package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * Optional modern Explore/RSS pages.
 * Disabled by default so existing Max behaviour is unchanged.
 */
object EnhancedPageConfig {
    var enhancedExplorePage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enhancedExplorePage, false)
        set(value) { appCtx.putPrefBoolean(PreferKey.enhancedExplorePage, value) }

    var enhancedRssPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enhancedRssPage, false)
        set(value) { appCtx.putPrefBoolean(PreferKey.enhancedRssPage, value) }
}
