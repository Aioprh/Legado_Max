/**
 * 首页配置管理
 *
 * 文件作用：提供首页相关配置的读写访问。
 * 主要功能：
 * - 管理首页布局模式配置（通过 SharedPreferences 持久化）
 * - 管理首页书源隐藏配置
 *
 * 使用单例对象实现，全局共享同一份配置数据。
 */
package io.legado.app.ui.main.homepage

import io.legado.app.utils.GSON
import java.util.Calendar

import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import splitties.init.appCtx

/**
 * 首页配置单例对象
 *
 * 通过 SharedPreferences 实现配置的持久化存储，
 * 提供首页布局模式和书源隐藏状态的读写访问。
 */
object HomepageConfig {

    /**
     * 首页布局模式
     *
     * 0 表示默认布局，其他值对应不同的布局模式。
     * 通过 SharedPreferences 持久化存储。
     */
    var homepageLayoutMode: Int
        get() = appCtx.defaultSharedPreferences.getInt(PreferKey.homepageLayoutMode, 0)
        set(value) = appCtx.defaultSharedPreferences.edit().putInt(PreferKey.homepageLayoutMode, value).apply()

    /**
     * 首页隐藏的书源列表
     *
     * 存储被用户隐藏的书源标识，多个书源以特定分隔符连接。
     * 默认为空字符串，表示不隐藏任何书源。
     */
    var homepageSourceHidden: String
        get() = appCtx.defaultSharedPreferences.getString(PreferKey.homepageSourceHidden, "") ?: ""
        set(value) = appCtx.defaultSharedPreferences.edit().putString(PreferKey.homepageSourceHidden, value).apply()

    /**
     * 首页预加载模式
     *
     * 0 = 仅加载当前书源集的模块（默认）
     * 1 = 加载当前书源集 + 相邻书源集（前后各一个）的模块
     *
     * 仅在分源Tab布局模式下生效。
     */
    var homepagePreload: Int
        get() = appCtx.defaultSharedPreferences.getInt(PreferKey.homepagePreload, 0)
        set(value) = appCtx.defaultSharedPreferences.edit().putInt(PreferKey.homepagePreload, value).apply()
    /**
     * 判断模块是否满足可选显示条件。
     * layoutConfig.showIf 示例：
     * {"showIf":{"minHour":8,"maxHour":23,"days":[1,2,3,4,5,6,7],"portraitOnly":true}}
     */
    fun isModuleVisible(layoutConfig: String?): Boolean {
        if (layoutConfig.isNullOrBlank()) return true
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val root = GSON.fromJson(layoutConfig, Map::class.java) as? Map<*, *> ?: return@runCatching true
            val condition = root["showIf"] as? Map<*, *> ?: return@runCatching true
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            val minHour = condition["minHour"]?.toString()?.toIntOrNull()
            val maxHour = condition["maxHour"]?.toString()?.toIntOrNull()
            if (minHour != null && hour < minHour) return@runCatching false
            if (maxHour != null && hour > maxHour) return@runCatching false
            val days = (condition["days"] as? List<*>)?.mapNotNull { it.toString().toIntOrNull() }
            if (!days.isNullOrEmpty() && day !in days) return@runCatching false
            true
        }.getOrDefault(true)
    }
}
