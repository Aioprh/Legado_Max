package io.legado.app.help.book

import android.content.Context
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray

/** 智能标签 2.0 的本地配置。 */
object SmartTagConfig {
    private const val PREFS = "smart_tag_v2"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_MAX = "max_tags"
    private const val KEY_CUSTOM_RULES = "custom_rules"

    data class CustomRule(
        val id: String,
        val name: String,
        val field: String,
        val operator: String,
        val value: String,
        val enabled: Boolean = true
    )

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

    fun customRules(context: Context): List<CustomRule> =
        prefs(context).getString(KEY_CUSTOM_RULES, null)?.let { json ->
            runCatching { GSON.fromJsonArray<CustomRule>(json) }.getOrNull()
        }?.orEmpty()

    fun saveCustomRules(context: Context, rules: List<CustomRule>) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_RULES, GSON.toJson(rules))
            .apply()
    }

    fun upsertCustomRule(context: Context, rule: CustomRule) {
        saveCustomRules(context, customRules(context).filterNot { it.id == rule.id } + rule)
    }

    fun deleteCustomRule(context: Context, id: String) {
        saveCustomRules(context, customRules(context).filterNot { it.id == id })
    }

    fun allRuleNames(context: Context): List<String> =
        SmartTag.ruleInfos.map { it.name } + customRules(context).filter { it.enabled }.map { it.name }

    fun enabledRuleNames(context: Context): Set<String> =
        allRuleNames(context).filter { isRuleVisible(context, it) }.toSet()
}
