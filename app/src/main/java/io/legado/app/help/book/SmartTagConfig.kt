package io.legado.app.help.book

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
            ?: allRuleNames(context).toMutableSet()
        if (visible) current.add(name) else current.remove(name)
        prefs(context).edit().putStringSet(KEY_VISIBLE, current).apply()
    }

    fun maxTags(context: Context): Int = prefs(context).getInt(KEY_MAX, 6).coerceIn(1, 12)

    fun setMaxTags(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_MAX, value.coerceIn(1, 12)).apply()
    }

    fun customRules(context: Context): List<CustomRule> {
        val raw = prefs(context).getString(KEY_CUSTOM_RULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(CustomRule(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        field = o.optString("field"),
                        operator = o.optString("operator"),
                        value = o.optString("value"),
                        enabled = o.optBoolean("enabled", true)
                    ))
                }
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveCustomRules(context: Context, rules: List<CustomRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("field", rule.field)
                put("operator", rule.operator)
                put("value", rule.value)
                put("enabled", rule.enabled)
            })
        }
        prefs(context).edit().putString(KEY_CUSTOM_RULES, array.toString()).apply()
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
