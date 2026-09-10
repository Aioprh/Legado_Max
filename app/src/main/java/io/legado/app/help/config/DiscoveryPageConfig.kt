package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * 发现页模式配置。
 *
 * 这里保留旧的 DiscoverySuite 开关作为兼容层；Modern Discovery 完整移植后
 * 再统一切换到 discoveryPageMode 三态配置。
 */
object DiscoveryPageMode {
    const val CLASSIC = 0
    const val MODERN = 1
    const val SUITE = 2
}

/** 兼容现有 Max 发现套件实现。 */
var AppConfig.enableDiscoverySuite: Boolean
    get() = appCtx.getPrefBoolean("enableDiscoverySuite", false)
    set(value) = appCtx.putPrefBoolean("enableDiscoverySuite", value)

/** 新版发现页模式，默认保持原版发现。 */
var AppConfig.discoveryPageMode: Int
    get() = when {
        AppConfig.enableDiscoverySuite -> DiscoveryPageMode.SUITE
        else -> appCtx.getSharedPreferences("legado", 0)
            .getInt("discoveryPageMode", DiscoveryPageMode.CLASSIC)
    }
    set(value) {
        appCtx.getSharedPreferences("legado", 0)
            .edit()
            .putInt("discoveryPageMode", value)
            .apply()
    }

val AppConfig.usingModernDiscovery: Boolean
    get() = discoveryPageMode == DiscoveryPageMode.MODERN
