package io.legado.app.ui.book.read.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

object HighlightRuleStore {

    fun defaultPresetRules(context: Context): List<HighlightRule> {
        return createDefaultRules(context)
    }

    fun load(context: Context): MutableList<HighlightRule> {
        val stored = context.getPrefString(PreferKey.highlightRuleItems)
        val rules = GSON.fromJsonArray<HighlightRule>(stored).getOrNull()?.toMutableList()
        if (!rules.isNullOrEmpty()) {
            val merged = ensureBuiltinRules(rules, context)
            HighlightRuleGroupStore.ensureFromRules(context, merged)
            if (merged.size != rules.size) {
                save(context, merged)
            }
            return merged.toMutableList()
        }
        return mutableListOf()
    }

    fun loadEnabled(context: Context): List<HighlightRule> {
        return load(context).filter { it.enabled && it.pattern.isNotBlank() }
    }

    fun save(context: Context, rules: List<HighlightRule>) {
        val normalized = rules.map { it.copy(group = it.group.ifBlank { HighlightRuleGroupStore.DEFAULT_GROUP }) }
        context.putPrefString(PreferKey.highlightRuleItems, GSON.toJson(normalized))
        HighlightRuleGroupStore.ensureFromRules(context, normalized)
    }

    fun reset(context: Context): MutableList<HighlightRule> {
        val defaults = createDefaultRules(context)
        save(context, defaults)
        return defaults.toMutableList()
    }

    private fun createDefaultRules(context: Context): List<HighlightRule> {
        return listOf(
            HighlightRule(
                id = "dialog_default",
                name = "对话高亮",
                pattern = "[“\"]([^”\"\\n]{1,120})[”\"]|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』",
                sampleText = "她轻声说：\u201C今晚就出发。\u201D",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleDialog, true),
                textColor = 0xFFFF8C00.toInt()
            ),
            HighlightRule(
                id = "book_title_default",
                name = "书名号高亮",
                pattern = "《[^》\\n]{1,80}》",
                sampleText = "最近在重读《百年孤独》，节奏很稳。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleBookTitle, true),
                underlineMode = 3,
                underlineColor = 0xFF63C37D.toInt()
            ),
            HighlightRule(
                id = "bracket_note_default",
                name = "括号标注高亮",
                pattern = "（[^）\\n]{1,80}）|\\([^\\)\\n]{1,80}\\)|【[^】\\n]{1,80}】",
                sampleText = "他停了一下（像是忽然想起什么）。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleBracketNote, true),
                textColor = 0xFF8F959E.toInt(),
                underlineMode = 2,
                underlineColor = 0xFF5A8DEE.toInt()
            ),
            HighlightRule(
                id = "title_emphasis_default",
                name = "标题强调",
                pattern = "(?m)^(第[0-9零一二三四五六七八九十百千两0123456789IVXLCDMivxlcdm]{1,12}[章节回卷部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,40}$",
                sampleText = "第一章 雨夜来客",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = true,
                textColor = 0xFF333333.toInt(),
                underlineMode = 4,
                underlineColor = 0xFF7C5634.toInt()
            ),
            HighlightRule(
                id = "thought_default",
                name = "心理活动",
                pattern = "（[^）]*?(想|寻思|暗道|心道|心里|想着|思量|思忖|盘算|盘算着)[^）]*?）",
                sampleText = "她心中暗道：（这究竟是怎么回事？）",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF9370DB.toInt(),
                underlineMode = 1,
                underlineColor = 0xFF9370DB.toInt()
            ),
            HighlightRule(
                id = "narrator_default",
                name = "旁白说明",
                pattern = "（以下\\S{0,20}省略|省略\\S{0,20}内容|[^\\n]{0,20}的情景不再赘述|[^\\n]{0,20}的情况不再多说）",
                sampleText = "（中间的情节不再赘述）",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF708090.toInt()
            ),
            HighlightRule(
                id = "emphasis_default",
                name = "着重强调",
                pattern = "[*＊]{1,2}[^*\\n]{1,50}[*＊]{1,2}",
                sampleText = "**这是重点内容**，需要特别关注。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFFDC143C.toInt(),
                underlineMode = 1,
                underlineColor = 0xFFDC143C.toInt()
            ),
            HighlightRule(
                id = "poetry_default",
                name = "诗词引用",
                pattern = "[\\n]([七五言绝句律诗词牌曲牌][^\\n]{0,60}[^\\n]{10,50}[^\\n]{0,20}[，。！？])\\n",
                sampleText = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF2F4F4F.toInt(),
                underlineMode = 3,
                underlineColor = 0xFF2F4F4F.toInt()
            ),
            HighlightRule(
                id = "ellipsis_default",
                name = "省略语",
                pattern = "x{2,}|\\*{2,}|\\.{2,}",
                sampleText = "他xxx地笑了笑。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF8B8B8B.toInt()
            ),
            HighlightRule(
                id = "number_default",
                name = "数字金额",
                pattern = "[0-9零一二三四五六七八九十百千万亿]+[元块美元英镑]|[0-9]+[%％]",
                sampleText = "原价100元，现在只要50元。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "english_default",
                name = "英文单词",
                pattern = "[a-zA-Z]{2,}[a-zA-Z0-9'-]*",
                sampleText = "Hello World，你好世界！",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "date_time_default",
                name = "时间日期",
                pattern = "[0-9零一二三四五六七八九十]+年[0-9零一二三四五六七八九十]+月[0-9零一二三四五六七八九十]*日?|[0-9]+点[0-9零一二三四五六七八九十]*分?",
                sampleText = "2024年5月1日，上午10点30分",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF20B2AA.toInt()
            )
        )
    }

    private fun ensureBuiltinRules(
        rules: List<HighlightRule>,
        context: Context,
    ): List<HighlightRule> {
        val builtins = createDefaultRules(context)
        val ids = rules.mapTo(HashSet()) { it.id }
        val merged = rules.map {
            if (it.group.isBlank()) it.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP) else it
        }.toMutableList()
        builtins.forEach { builtin ->
            if (!ids.contains(builtin.id)) {
                merged.add(builtin)
            }
        }
        return merged
    }
}
