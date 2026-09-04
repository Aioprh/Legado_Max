package io.legado.app.help.book

import android.content.Context

/** 智能标签 2.0 的本地配置。规则本身仍由 SmartTag 提供，用户只控制启用状态。 */
object SmartTagConfig {
    private const val PREFS = "smart_tag_v2"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_MAX = "max_tags"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isRuleVisible(context: Context, name: String): Boolean =
        prefs(context).getStringSet(KEY_VISIBLE, null)?.contains(name) ?: true

    fun setRuleVisible(context: Context, name: String, visible: Boolean) {
        val current = prefs(context).getStringSet(KEY_VISIBLE, null)?.toMutableSet()
            ?: SmartTag.ruleInfos.map { it.name }.toMutableSet()
        if (visible) current.add(name) else current.remove(name)
        prefs(context).edit().putStringSet(KEY_VISIBLE, current).apply()
    }

    fun maxTags(context: Context): Int = prefs(context).getInt(KEY_MAX, 6).coerceIn(1, 12)

    fun setMaxTags(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_MAX, value.coerceIn(1, 12)).apply()
    }

    fun enabledRuleNames(context: Context): Set<String> =
        SmartTag.ruleInfos.map { it.name }.filter { isRuleVisible(context, it) }.toSet()
}
