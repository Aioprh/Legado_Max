package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

object DiscoveryPageMode {
    const val CLASSIC = 0
    const val MODERN = 1
    const val SUITE = 2
}

var AppConfig.enableDiscoverySuite: Boolean
    get() = appCtx.getPrefBoolean("enableDiscoverySuite", false)
    set(value) = appCtx.putPrefBoolean("enableDiscoverySuite", value)

/** 新版发现独立开关：默认关闭，不影响首页。 */
var AppConfig.enableModernDiscovery: Boolean
    get() = appCtx.getPrefBoolean("enableModernDiscovery", false)
    set(value) = appCtx.putPrefBoolean("enableModernDiscovery", value)

var AppConfig.discoveryPageMode: Int
    get() = when {
        AppConfig.enableDiscoverySuite -> DiscoveryPageMode.SUITE
        AppConfig.enableModernDiscovery -> DiscoveryPageMode.MODERN
        else -> DiscoveryPageMode.CLASSIC
    }
    set(value) {
        when (value) {
            DiscoveryPageMode.MODERN -> {
                AppConfig.enableModernDiscovery = true
                AppConfig.enableDiscoverySuite = false
            }
            DiscoveryPageMode.SUITE -> {
                AppConfig.enableDiscoverySuite = true
                AppConfig.enableModernDiscovery = false
            }
            else -> {
                AppConfig.enableModernDiscovery = false
                AppConfig.enableDiscoverySuite = false
            }
        }
    }

val AppConfig.usingModernDiscovery: Boolean
    get() = enableModernDiscovery
