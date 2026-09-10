package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

object DiscoveryPageMode {
    const val CLASSIC = 0
    const val MODERN = 1
    const val SUITE = 2
}

/** 保留旧配置键，兼容已有安装数据。 */
var AppConfig.enableDiscoverySuite: Boolean
    get() = appCtx.getPrefBoolean("enableDiscoverySuite", false)
    set(value) = appCtx.putPrefBoolean("enableDiscoverySuite", value)

/** 新版发现独立配置；旧版“新发现”开关也映射到这里，避免升级后失效。 */
var AppConfig.enableModernDiscovery: Boolean
    get() = appCtx.getPrefBoolean("enableModernDiscovery", false) || AppConfig.enableDiscoverySuite
    set(value) = appCtx.putPrefBoolean("enableModernDiscovery", value)

var AppConfig.discoveryPageMode: Int
    get() = when {
        AppConfig.enableModernDiscovery -> DiscoveryPageMode.MODERN
        else -> DiscoveryPageMode.CLASSIC
    }
    set(value) {
        when (value) {
            DiscoveryPageMode.MODERN -> AppConfig.enableModernDiscovery = true
            else -> {
                AppConfig.enableModernDiscovery = false
                AppConfig.enableDiscoverySuite = false
            }
        }
    }

val AppConfig.usingModernDiscovery: Boolean
    get() = enableModernDiscovery
