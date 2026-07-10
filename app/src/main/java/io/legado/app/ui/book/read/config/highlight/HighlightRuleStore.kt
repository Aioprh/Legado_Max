package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import java.io.File

/**
 * 高亮规则的数据存储。
 *
 * 保留历史调用入口，负责规则持久化、缓存、备份数据组装和旧数据清洗；
 * 默认规则、背景图文件和 UI 访问入口分别下沉到专门对象。
 */
object HighlightRuleStore {

    const val backupFileName = "highlightRule.json"
    const val backupBgDirName = "highlightRuleBg"

    /**
     * 高亮规则备份文件的完整数据结构。
     */
    data class BackupData(
        val rules: List<HighlightRule> = emptyList(),
        val groups: List<String> = emptyList(),
        val currentGroup: String = "",
        val dialogEnabled: Boolean = true,
        val bookTitleEnabled: Boolean = true,
        val bracketNoteEnabled: Boolean = true,
    )

    @Volatile
    private var cachedRules: List<HighlightRule>? = null

    fun defaultPresetRules(context: Context): List<HighlightRule> {
        return createDefaultRules(context)
    }

    fun load(context: Context): MutableList<HighlightRule> {
        cachedRules?.let { return it.toMutableList() }
        val stored = context.getPrefString(PreferKey.highlightRuleItems)
        if (stored.isNullOrBlank()) {
            return mutableListOf()
        }
        val rules = GSON.fromJsonArray<HighlightRule>(stored).getOrNull()?.toMutableList()
        if (rules != null) {
            val normalized = normalizeRules(rules, context)
            if (normalized != rules) {
                save(context, normalized)
            } else {
                HighlightRuleGroupStore.ensureFromRules(context, normalized)
            }
            cachedRules = normalized
            return normalized.toMutableList()
        }
        return mutableListOf()
    }

    fun loadEnabled(context: Context): List<HighlightRule> {
        return load(context).filter { it.enabled && it.pattern.isNotBlank() }
    }

    fun save(context: Context, rules: List<HighlightRule>) {
        val json = GSON.toJson(rules)
        context.putPrefString(PreferKey.highlightRuleItems, json)
        cachedRules = rules
        HighlightRuleGroupStore.ensureFromRules(context, rules)
    }

    fun reset(context: Context): MutableList<HighlightRule> {
        val defaultRules = createDefaultRules(context)
        save(context, defaultRules)
        return defaultRules.toMutableList()
    }

    fun sanitizeRule(rule: HighlightRule, fallbackGroup: String = HighlightRuleGroupStore.DEFAULT_GROUP): HighlightRule {
        return rule.copy(
            name = rule.name.trim(),
            pattern = rule.pattern.trim(),
            sampleText = rule.sampleText.trim(),
            group = rule.group.takeIf { it.isNotBlank() } ?: fallbackGroup,
            scope = rule.scope?.trim()?.takeIf { it.isNotBlank() },
            excludeScope = rule.excludeScope?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    fun backupData(context: Context): BackupData {
        val rules = load(context)
        val groups = HighlightRuleGroupStore.load(context)
        val currentGroup = context.getPrefString(PreferKey.highlightRuleCurrentGroup) ?: ""
        val dialogEnabled = context.getPrefBoolean(PreferKey.highlightRuleDialog, true)
        val bookTitleEnabled = context.getPrefBoolean(PreferKey.highlightRuleBookTitle, true)
        val bracketNoteEnabled = context.getPrefBoolean(PreferKey.highlightRuleBracketNote, true)

        return BackupData(
            rules = rules,
            groups = groups,
            currentGroup = currentGroup,
            dialogEnabled = dialogEnabled,
            bookTitleEnabled = bookTitleEnabled,
            bracketNoteEnabled = bracketNoteEnabled,
        )
    }

    fun restoreBackupData(
        context: Context,
        backupData: BackupData,
        restoreBackgroundFiles: (Context, String?) -> Unit,
    ) {
        save(context, backupData.rules)
        HighlightRuleGroupStore.save(context, backupData.groups)
        context.putPrefBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
        val groups = HighlightRuleGroupStore.load(context)
        context.putPrefString(
            PreferKey.highlightRuleCurrentGroup,
            backupData.currentGroup.takeIf { groups.contains(it) } ?: ""
        )
        restoreBackgroundFiles(context, backupData.rules.mapNotNull { it.bgImage }.distinct().joinToString("\n"))
    }

    fun getUsedBgImageFiles(context: Context): List<File> {
        return HighlightRuleBackgroundManager.getUsedFiles(context, load(context))
    }

    private fun createDefaultRules(context: Context): List<HighlightRule> {
        return HighlightRuleDefaultRules.create(context)
    }

    private fun normalizeRules(
        rules: List<HighlightRule>,
        context: Context,
    ): List<HighlightRule> {
        val builtins = createDefaultRules(context).associateBy { it.id }
        return rules.map { rule ->
            val safeRule = sanitizeRule(rule)
            val normalizedGroup = safeRule.group
            val builtin = builtins[safeRule.id]
            val base = if (builtin != null && shouldRefreshBuiltin(safeRule)) {
                builtin.copy(
                    enabled = safeRule.enabled,
                    group = normalizedGroup,
                    targetScope = normalizeTargetScope(safeRule.targetScope, builtin.targetScope),
                    textColor = safeRule.textColor ?: builtin.textColor,
                    underlineMode = safeRule.underlineMode.takeIf { it != 0 } ?: builtin.underlineMode,
                    underlineColor = safeRule.underlineColor ?: builtin.underlineColor,
                    underlineWidth = safeRule.underlineWidth.takeIf { it != 1f } ?: builtin.underlineWidth,
                    underlineSvgPath = safeRule.underlineSvgPath ?: builtin.underlineSvgPath,
                    bgImage = safeRule.bgImage ?: builtin.bgImage,
                    bgColor = safeRule.bgColor ?: builtin.bgColor,
                    bgImageFit = safeRule.bgImageFit.takeIf { it != 0 } ?: builtin.bgImageFit,
                    bgImageScale = safeRule.bgImageScale.takeIf { it != 1f } ?: builtin.bgImageScale,
                    scope = safeRule.scope,
                    excludeScope = safeRule.excludeScope,
                )
            } else {
                safeRule
            }
            base
        }
    }

    private fun shouldRefreshBuiltin(rule: HighlightRule): Boolean {
        return rule.name.isBlank() ||
                rule.pattern.isBlank() ||
                (rule.textColor == null && rule.underlineMode == 0 && rule.bgColor == null && rule.bgImage.isNullOrBlank())
    }

    private fun normalizeTargetScope(ruleScope: Int, builtinScope: Int): Int {
        return if (ruleScope in 0..2) ruleScope else builtinScope
    }
}