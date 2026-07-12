package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.RegexCache
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

    /**
     * 清除内存缓存，强制下次 load 从 SharedPreferences 重新读取。
     *
     * 同时清除正则表达式缓存，避免旧规则残留。
     * 用于备份恢复后确保缓存与持久化数据一致。
     */
    fun clearCache() {
        cachedRules = null
        RegexCache.clear()
    }

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
        // 规则变更后清除正则缓存，避免旧 pattern 残留
        RegexCache.clear()
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
        backupRootPath: String? = null,
    ) {
        // 从备份目录恢复背景图文件，并更新规则中的 bgImage 路径
        val restoredRules = if (backupRootPath != null) {
            backupData.rules.map { rule ->
                val restoredPath = HighlightRuleBackgroundManager.restoreFromBackup(
                    context, backupRootPath, rule.bgImage
                )
                if (restoredPath != null && restoredPath != rule.bgImage) {
                    rule.copy(bgImage = restoredPath)
                } else {
                    rule
                }
            }
        } else {
            backupData.rules
        }
        save(context, restoredRules)
        HighlightRuleGroupStore.save(context, backupData.groups)
        context.putPrefBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
        val groups = HighlightRuleGroupStore.load(context)
        context.putPrefString(
            PreferKey.highlightRuleCurrentGroup,
            backupData.currentGroup.takeIf { groups.contains(it) } ?: ""
        )
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
        // 仅对内置规则 ID 执行检查，用户自定义规则不受影响
        if (rule.id !in builtinIds) return false
        // 检查条件：
        // 1. name 或 pattern 为空（数据丢失）
        // 2. 文本包含乱码标记（编码问题）
        // 3. pattern 匹配旧版遗留正则（需升级到当前版本）
        // 4. sampleText 匹配旧版遗留样本文本（需升级到当前版本）
        // 不再因"无样式"而刷新——用户可能故意清除内置规则的样式
        val inspectText = rule.name + rule.pattern + rule.sampleText
        return rule.name.isBlank() ||
                rule.pattern.isBlank() ||
                garbledMarkers.any { inspectText.contains(it) } ||
                legacyBuiltinPatterns[rule.id] == rule.pattern ||
                legacyBuiltinSampleTexts[rule.id] == rule.sampleText
    }

    /** 内置规则 ID 集合 */
    private val builtinIds = setOf(
        "dialog_default",
        "book_title_default",
        "bracket_note_default",
        "title_emphasis_default",
        "thought_default",
        "narrator_default",
        "emphasis_default",
        "poetry_default",
        "ellipsis_default",
        "number_default",
        "english_default",
        "date_time_default"
    )

    /**
     * 旧版遗留正则表达式映射。
     *
     * 当用户 SharedPreferences 中存储的内置规则 pattern 与此映射中的旧版 pattern
     * 完全匹配时，说明该规则尚未升级到当前版本，需要刷新为最新默认值。
     */
    private val legacyBuiltinPatterns = mapOf(
        "dialog_default" to "[“\"]([^”\"\\n]{1,120})[”\"]|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』",
        "book_title_default" to "《[^》\\n]{1,80}》",
        "bracket_note_default" to "（[^）\\n]{1,80}）|\\([^\\)\\n]{1,80}\\)|【[^】\\n]{1,80}】",
        "title_emphasis_default" to "(?m)^(第[0-9零一二三四五六七八九十百千两0123456789IVXLCDMivxlcdm]{1,12}[章节回卷部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,40}$",
        "thought_default" to "（[^）]*?(想道|暗道|心道|心里|想着|思量|思忖|盘算|盘算着)[^）]*?）",
        "narrator_default" to "（以下\\S{0,20}省略|省略\\S{0,20}内容|[^\\n]{0,20}的情景不再赘述|[^\\n]{0,20}的情况不再多说）",
        "emphasis_default" to "[*！]{1,2}[^*\\n]{1,50}[*！]{1,2}",
        "poetry_default" to "[\\n]([七五言绝句律诗词牌曲牌][^\\n]{0,60}[^\\n]{10,50}[^\\n]{0,20}[，。！？])\\n",
        "ellipsis_default" to "x{2,}|\\*{2,}|\\.{2,}",
        "number_default" to "[0-9零一二三四五六七八九十百千万亿]+[元块美元英镑]|[0-9]+[%％]",
        "english_default" to "[a-zA-Z]{2,}[a-zA-Z0-9'-]*",
        "date_time_default" to "[0-9零一二三四五六七八九十]+年[0-9零一二三四五六七八九十]+月[0-9零一二三四五六七八九十]*日?|[0-9]+点[0-9零一二三四五六七八九十]*分?"
    )

    /** 乱码标记，用于检测旧数据编码问题 */
    private val garbledMarkers = listOf("锛", "銆", "鈥", "瀵", "涔", "鏍", "鐪", "鏈", "绗")

    /**
     * 旧版遗留样本文本映射。
     *
     * 当用户 SharedPreferences 中存储的内置规则 sampleText 与此映射中的旧版值
     * 完全匹配时，说明该规则尚未升级到当前版本，需要刷新为最新默认值。
     * 主要用于修复重构时 \\n 被错误地当作字面字符而非换行符的问题。
     */
    private val legacyBuiltinSampleTexts = mapOf(
        "poetry_default" to "床前明月光，\\n疑是地上霜。"
    )

    private fun normalizeTargetScope(ruleScope: Int, builtinScope: Int): Int {
        return if (ruleScope in 0..2) ruleScope else builtinScope
    }
}