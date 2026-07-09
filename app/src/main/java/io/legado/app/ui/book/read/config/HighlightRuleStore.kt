package io.legado.app.ui.book.read.config

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
 * 高亮规则的兼容存储门面。
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
        val normalized = rules.map {
            sanitizeRule(it).copy(
                targetScope = normalizeTargetScope(it.targetScope)
            )
        }
        cachedRules = normalized
        context.putPrefString(PreferKey.highlightRuleItems, GSON.toJson(normalized))
        HighlightRuleGroupStore.ensureFromRules(context, normalized)
        HighlightRuleBackgroundManager.cleanupUnused(context, normalized)
    }

    fun reset(context: Context): MutableList<HighlightRule> {
        cachedRules = null
        val defaults = createDefaultRules(context)
        save(context, defaults)
        return defaults.toMutableList()
    }

    fun createBackupData(context: Context): BackupData {
        return BackupData(
            rules = load(context),
            groups = HighlightRuleGroupStore.load(context),
            currentGroup = context.getPrefString(PreferKey.highlightRuleCurrentGroup).orEmpty(),
            dialogEnabled = context.getPrefBoolean(PreferKey.highlightRuleDialog, true),
            bookTitleEnabled = context.getPrefBoolean(PreferKey.highlightRuleBookTitle, true),
            bracketNoteEnabled = context.getPrefBoolean(PreferKey.highlightRuleBracketNote, true),
        )
    }

    fun restoreBackupData(
        context: Context,
        backupData: BackupData,
        backupRootPath: String? = null,
    ) {
        HighlightRuleGroupStore.save(context, backupData.groups)
        val rules = backupData.rules.map { rule ->
            val safeRule = sanitizeRule(rule)
            val restoredBgImage = HighlightRuleBackgroundManager.restoreFromBackup(
                context,
                backupRootPath,
                safeRule.bgImage
            )
            safeRule.copy(bgImage = restoredBgImage)
        }
        save(context, rules)
        context.putPrefBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
        context.putPrefBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
        val groups = HighlightRuleGroupStore.load(context)
        context.putPrefString(
            PreferKey.highlightRuleCurrentGroup,
            backupData.currentGroup.takeIf { groups.contains(it) }.orEmpty()
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
                    bgImageFit = safeRule.bgImageFit.takeIf { it != 0 } ?: builtin.bgImageFit,
                    bgImageScale = safeRule.bgImageScale.takeIf { it != 1f } ?: builtin.bgImageScale
                )
            } else {
                safeRule.copy(
                    targetScope = normalizeTargetScope(safeRule.targetScope)
                )
            }
            HighlightRuleBackgroundManager.migrateToInternal(context, base)
        }
    }

    fun sanitizeRule(
        rule: HighlightRule,
        fallbackGroup: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    ): HighlightRule {
        val name = runCatching { rule.name }.getOrNull().orEmpty()
        val pattern = runCatching { rule.pattern }.getOrNull().orEmpty()
        val sampleText = runCatching { rule.sampleText }.getOrNull().orEmpty()
        val group = runCatching { rule.group }.getOrNull().orEmpty().ifBlank { fallbackGroup }
        val id = runCatching { rule.id }.getOrNull().orEmpty().ifBlank {
            buildSanitizedRuleId(name, pattern, sampleText, group)
        }
        val underlineSvgPath = runCatching { rule.underlineSvgPath }.getOrNull()
        val bgColor = runCatching { rule.bgColor }.getOrNull()
        val bgImage = runCatching { rule.bgImage }.getOrNull()?.takeIf { it.isNotBlank() }
        // 书籍作用域字段，旧数据缺失时默认为空（对所有书籍生效）
        val scope = runCatching { rule.scope }.getOrNull()?.takeIf { it.isNotBlank() }
        val excludeScope = runCatching { rule.excludeScope }.getOrNull()?.takeIf { it.isNotBlank() }
        return HighlightRule(
            id = id,
            name = name,
            pattern = pattern,
            sampleText = sampleText,
            group = group,
            targetScope = normalizeTargetScope(runCatching { rule.targetScope }.getOrDefault(HighlightRule.TARGET_ALL)),
            enabled = runCatching { rule.enabled }.getOrDefault(true),
            textColor = runCatching { rule.textColor }.getOrNull(),
            underlineMode = runCatching { rule.underlineMode }.getOrDefault(0).coerceIn(0, 5),
            underlineColor = runCatching { rule.underlineColor }.getOrNull(),
            underlineWidth = runCatching { rule.underlineWidth }.getOrDefault(1f).coerceIn(0.1f, 10f),
            underlineOffset = runCatching { rule.underlineOffset }.getOrDefault(2f).coerceIn(0f, 20f),
            underlineSvgPath = underlineSvgPath,
            bgColor = bgColor,
            bgImage = bgImage,
            bgImageFit = runCatching { rule.bgImageFit }.getOrDefault(0).coerceIn(0, 2),
            bgImageScale = runCatching { rule.bgImageScale }.getOrDefault(1f).coerceIn(0.1f, 5f),
            scope = scope,
            excludeScope = excludeScope,
        )
    }

    private fun buildSanitizedRuleId(
        name: String,
        pattern: String,
        sampleText: String,
        group: String,
    ): String {
        val seed = listOf(name, pattern, sampleText, group).joinToString("|")
        return "${System.currentTimeMillis()}_${seed.hashCode().toUInt().toString(16)}"
    }

    private fun normalizeTargetScope(value: Int, fallback: Int = HighlightRule.TARGET_ALL): Int {
        return when (value) {
            HighlightRule.TARGET_ALL,
            HighlightRule.TARGET_TITLE,
            HighlightRule.TARGET_BODY -> value
            else -> fallback
        }
    }

    private fun shouldRefreshBuiltin(rule: HighlightRule): Boolean {
        if (rule.id !in builtinIds) return false
        val inspectText = buildString {
            append(rule.name)
            append(rule.pattern)
            append(rule.sampleText)
        }
        return garbledMarkers.any { inspectText.contains(it) } ||
            legacyBuiltinPatterns[rule.id] == rule.pattern
    }

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

    private val garbledMarkers = listOf("锛", "銆", "鈥", "瀵", "涔", "鏍", "鐪", "鏈", "绗")
}
