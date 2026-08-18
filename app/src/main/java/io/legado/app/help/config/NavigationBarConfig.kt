package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.util.LruCache
import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import com.google.gson.JsonArray
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import splitties.init.appCtx

/**
 * 底栏导航栏配置数据类。
 *
 * 定义了底部导航栏的外观和行为属性，包括布局模式、视觉效果、
 * 背景色/透明度、边框样式以及自定义图标。每个配置项可以是
 * 内置（默认日间/夜间）或用户自定义的。
 *
 * @property id 唯一标识符，内置配置以 "builtin_" 前缀开头
 * @property name 配置名称，用于 UI 展示
 * @property isNight 是否为夜间模式配置
 * @property isBuiltin 是否为内置（不可删除）配置
 * @property layoutMode 布局模式：[LAYOUT_FLOATING] / [LAYOUT_STANDARD] / [LAYOUT_SIDEBAR]
 * @property effectMode 视觉效果：[EFFECT_SOLID] / [EFFECT_GLASS] / [EFFECT_FROSTED]
 * @property backgroundColor 自定义背景色，null 表示跟随主题
 * @property opacity 背景透明度（0-100）
 * @property borderColor 边框颜色
 * @property borderAlpha 边框透明度（0-100）
 * @property wallpaperPath 壁纸图片路径（当前未使用）
 * @property sidebarBackgroundPath 侧栏背景路径（侧栏模式专用）
 * @property sidebarGravity 侧栏停靠方向："start" 或 "end"
 * @property icons 自定义图标映射，key 格式为 "{itemKey}_{state}"
 * @property updatedAt 最后更新时间戳
 */
@Keep
data class NavigationBarConfig(
    var id: String,
    var name: String,
    var isNight: Boolean,
    var isBuiltin: Boolean = false,
    var layoutMode: String = LAYOUT_FLOATING,
    var effectMode: String = EFFECT_GLASS,
    var backgroundColor: Int? = null,
    var opacity: Int = 76,
    var borderColor: Int? = null,
    var borderAlpha: Int = 100,
    var wallpaperPath: String? = null,
    var sidebarBackgroundPath: String? = null,
    var sidebarGravity: String = "start",
    var icons: Map<String, String> = emptyMap(),
    var updatedAt: Long = System.currentTimeMillis()
) {

    /** 底栏导航项元数据：key 用于图标存储，menuId 对应菜单资源 ID */
    data class NavItem(
        val key: String,
        @StringRes val titleRes: Int,
        @IdRes val menuId: Int,
        @DrawableRes val defaultIconRes: Int
    )

    fun toJson(): String = GSON.toJson(this)

    fun copySelf(): NavigationBarConfig = copy(icons = icons.toMap())

    /** 获取布局模式的本地化显示文本 */
    fun getLayoutModeText(context: Context = appCtx): String = when (layoutMode) {
        LAYOUT_STANDARD -> context.getString(R.string.nav_bar_layout_standard)
        LAYOUT_SIDEBAR -> context.getString(R.string.nav_bar_layout_sidebar)
        else -> context.getString(R.string.nav_bar_layout_floating)
    }

    /** 获取效果模式的本地化显示文本 */
    fun getEffectModeText(context: Context = appCtx): String = when (effectMode) {
        EFFECT_SOLID -> context.getString(R.string.nav_bar_effect_solid)
        EFFECT_FROSTED -> context.getString(R.string.nav_bar_effect_frosted)
        else -> context.getString(R.string.nav_bar_effect_glass)
    }

    companion object {
        /** 布局模式：浮动底栏 */
        const val LAYOUT_FLOATING = "floating"
        /** 布局模式：标准底栏 */
        const val LAYOUT_STANDARD = "standard"
        /** 布局模式：侧边栏 */
        const val LAYOUT_SIDEBAR = "sidebar"
        /** 效果模式：纯色 */
        const val EFFECT_SOLID = "solid"
        /** 效果模式：玻璃质感 */
        const val EFFECT_GLASS = "glass"
        /** 效果模式：磨砂 */
        const val EFFECT_FROSTED = "frosted"
        /** 图标状态：常规 */
        const val STATE_NORMAL = "normal"
        /** 图标状态：选中 */
        const val STATE_SELECTED = "selected"

        private const val PREF_KEY_ACTIVE_DAY = "activeDayNavBarId"
        private const val PREF_KEY_ACTIVE_NIGHT = "activeNightNavBarId"
        private const val PREF_KEY_CUSTOM_CONFIGS = "customNavBarConfigs"

        /** 图标 Bitmap 缓存，避免每次重新解析 SVG/PNG 文件 */
        private val iconBitmapCache = LruCache<String, Bitmap>(64)

        /** 清空图标缓存（配置变更时调用） */
        fun clearIconCache() {
            iconBitmapCache.evictAll()
        }

        /** 生成图标缓存 key：基于文件路径、最后修改时间和大小 */
        private fun iconCacheKey(path: String): String {
            val file = java.io.File(path)
            return "${file.absolutePath}|${file.lastModified()}|${file.length()}"
        }

        /** 底栏导航项列表：首页、书架、发现、RSS、我的 */
        val items = listOf(
            NavItem("homepage", R.string.homepage, R.id.menu_homepage, R.drawable.ic_bottom_home),
            NavItem("bookshelf", R.string.bookshelf, R.id.menu_bookshelf, R.drawable.ic_bottom_books),
            NavItem("discovery", R.string.discovery, R.id.menu_discovery, R.drawable.ic_bottom_explore),
            NavItem("rss", R.string.rss, R.id.menu_rss, R.drawable.ic_bottom_rss_feed),
            NavItem("my", R.string.my, R.id.menu_my_config, R.drawable.ic_bottom_person)
        )

        fun fromJson(json: String): NavigationBarConfig {
            return GSON.fromJsonObject<NavigationBarConfig>(json).getOrThrow()
        }

        fun createDefaultDay(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_day",
                name = appCtx.getString(R.string.nav_bar_default_day_name),
                isNight = false,
                isBuiltin = true,
                layoutMode = LAYOUT_STANDARD,
                effectMode = EFFECT_SOLID,
                opacity = 30,
                updatedAt = 0L
            )
        }

        fun createDefaultNight(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_night",
                name = appCtx.getString(R.string.nav_bar_default_night_name),
                isNight = true,
                isBuiltin = true,
                layoutMode = LAYOUT_STANDARD,
                effectMode = EFFECT_SOLID,
                opacity = 30,
                updatedAt = 0L
            )
        }

        /**
         * 加载所有底栏配置：内置配置（日间/夜间）+ 用户自定义配置。
         * 支持从旧版 JSON 对象格式自动迁移到数组格式。
         */
        fun loadConfigs(context: Context): MutableList<NavigationBarConfig> {
            val configs = mutableListOf(createDefaultDay(), createDefaultNight())
            val stored = context.getPrefString(PREF_KEY_CUSTOM_CONFIGS)
            var shouldMigrate = false
            when {
                stored.isNullOrBlank() -> Unit
                stored.trimStart().startsWith("[") -> {
                    configs.addAll(parseConfigArray(stored))
                }
                else -> {
                    parseLegacyConfigObjects(stored)
                        .mapNotNull { json -> runCatching { fromJson(json) }.getOrNull() }
                        .also {
                            configs.addAll(it)
                            shouldMigrate = it.isNotEmpty()
                        }
                }
            }
            if (shouldMigrate) {
                saveConfigs(context, configs)
            }
            return configs
        }

        /** 保存用户自定义配置到 SharedPreferences（内置配置不入库） */
        fun saveConfigs(context: Context, configs: List<NavigationBarConfig>) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(
                    PREF_KEY_CUSTOM_CONFIGS,
                    GSON.toJson(configs.filter { !it.isBuiltin })
                )
            }
        }

        private fun parseConfigArray(stored: String): List<NavigationBarConfig> {
            val array = runCatching {
                GSON.fromJson(stored, JsonArray::class.java)
            }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                runCatching {
                    GSON.fromJson(element, NavigationBarConfig::class.java)
                }.getOrNull()
            }
        }

        /**
         * 解析旧版 JSON 对象格式（多个独立的 JSON 对象拼接成的字符串）。
         * 通过手动字符扫描提取每个 `{}` 块，兼容历史数据。
         */
        private fun parseLegacyConfigObjects(stored: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            var start = -1
            var inString = false
            var escaped = false
            stored.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                when {
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    !inString && char == '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            result.add(stored.substring(start, index + 1))
                            start = -1
                        }
                    }
                }
            }
            return result
        }

        /** 获取当前激活的底栏配置 ID */
        fun activeId(context: Context, isNight: Boolean): String? {
            return context.getPrefString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY)
        }

        /** 设置当前激活的底栏配置 ID */
        fun setActiveId(context: Context, isNight: Boolean, id: String?) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY, id.orEmpty())
            }
        }

        /** 获取当前激活的底栏配置，若未设置则返回该模式下的第一个配置 */
        fun activeConfig(context: Context, isNight: Boolean): NavigationBarConfig {
            val configs = loadConfigs(context)
            val activeId = activeId(context, isNight)
            return configs.firstOrNull { it.isNight == isNight && it.id == activeId }
                ?: configs.first { it.isNight == isNight }
        }

        /** 生成当前配置的签名摘要，用于缓存失效判断 */
        fun currentSignature(context: Context, isNight: Boolean): String {
            val config = activeConfig(context, isNight)
            val iconSignature = config.icons.entries
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}:${it.value}" }
            // 内置配置使用固定 updatedAt（0L），避免每次 loadConfigs 重新创建实例
            // 导致时间戳变化、签名不一致，进而触发重复重建主题/图标/背景。
            val stableUpdatedAt = if (config.isBuiltin) 0L else config.updatedAt
            return listOf(
                isNight,
                config.id,
                config.layoutMode,
                config.effectMode,
                config.backgroundColor,
                config.opacity,
                config.borderColor,
                config.borderAlpha,
                stableUpdatedAt,
                iconSignature
            ).joinToString("|")
        }

        /** 应用指定配置：设置激活 ID、应用主题、发送事件总线通知 */
        fun applyConfig(context: Context, config: NavigationBarConfig, recreate: Boolean = false) {
            setActiveId(context, config.isNight, config.id)
            ThemeConfig.applyTheme(context)
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, config.isNight)
            if (recreate) postEvent(EventBus.RECREATE, "")
        }

        /** 根据配置的透明度计算最终底栏背景色（内置与自定义配置统一处理） */
        fun resolveBottomColor(baseColor: Int, config: NavigationBarConfig): Int {
            val alpha = config.opacity.coerceIn(0, 100) / 100f
            return ColorUtils.withAlpha(config.backgroundColor ?: baseColor, alpha)
        }

        /**
         * 将自定义图标应用到菜单项。
         * @return true 表示至少有一项使用了自定义图标
         */
        fun applyToMenu(menu: Menu, context: Context, isNight: Boolean, bgColor: Int? = null): Boolean {
            val config = activeConfig(context, isNight)
            var hasCustom = false
            items.forEach { item ->
                val normal = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                val selected = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_SELECTED)])
                if (normal != null || selected != null) hasCustom = true
                menu.findItem(item.menuId)?.icon = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_checked), selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor))
                    addState(intArrayOf(android.R.attr.state_selected), selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor))
                    addState(intArrayOf(), normal ?: defaultDrawable(context, item.defaultIconRes, false, bgColor))
                }
            }
            return hasCustom
        }

        /** 获取指定导航项的预览 Drawable（优先自定义图标，回退到默认） */
        fun previewDrawable(context: Context, config: NavigationBarConfig, item: NavItem, selected: Boolean, bgColor: Int? = null): Drawable? {
            val state = if (selected) STATE_SELECTED else STATE_NORMAL
            return loadIconDrawable(context, config.icons[iconKey(item.key, state)])
                ?: loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                ?: defaultDrawable(context, item.defaultIconRes, selected, bgColor)
        }

        /** 生成图标存储 key："{itemKey}_{state}" */
        fun iconKey(itemKey: String, state: String): String = "${itemKey}_$state"

        private fun loadIconDrawable(context: Context, path: String?): Drawable? {
            if (path.isNullOrBlank()) return null
            val file = java.io.File(path)
            // SVG 文件需要用 SvgUtils 解析，Drawable.createFromPath 不支持 SVG
            if (file.extension.equals("svg", ignoreCase = true)) {
                val targetSize = (context.resources.displayMetrics.density * 48).toInt()
                val cacheKey = iconCacheKey(path)
                val bitmap = synchronized(iconBitmapCache) {
                    iconBitmapCache[cacheKey]?.takeIf { !it.isRecycled }
                        ?: SvgUtils.createBitmapFromFile(path, targetSize, targetSize)?.also {
                            iconBitmapCache.put(cacheKey, it)
                        }
                }
                return bitmap?.let { BitmapDrawable(context.resources, it) }
            }
            return Drawable.createFromPath(path)
        }

        private fun defaultDrawable(context: Context, @DrawableRes resId: Int, selected: Boolean, bgColor: Int? = null): Drawable {
            val drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
            val bg = bgColor ?: ThemeStore.bottomBackground(context)
            val textIsDark = ColorUtils.isColorLight(bg)
            val color = if (selected) ThemeStore.accentColor(context) else context.getSecondaryTextColor(textIsDark)
            DrawableCompat.setTint(drawable, color)
            return drawable
        }
    }
}
