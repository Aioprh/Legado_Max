package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean

/**
 * 高亮规则的内置预置规则工厂。
 *
 * 只负责根据当前旧版开关偏好生成默认规则列表，不处理持久化、
 * 导入导出或运行时匹配。
 */
object HighlightRuleDefaultRules {

    fun create(context: Context): List<HighlightRule> {
        return listOf(
            HighlightRule(
                id = "dialog_default",
                name = "对话高亮",
                pattern = "“[^”\\n]{1,120}”|\"[^\"\\n]{1,120}\"|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』",
                sampleText = "她轻声说：\"今晚就出发。\"\n他说：“明天见。”",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleDialog, true),
                textColor = 0xFFFF8C00.toInt()
            ),
            HighlightRule(
                id = "book_title_default",
                name = "书名号高亮",
                pattern = "《[^》\\n]{1,80}》",
                sampleText = "最近在重读《百年孤独》，节奏依然很稳。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleBookTitle, true),
                underlineMode = 3,
                underlineWidth = 0.5f,
                underlineColor = 0xFF63C37D.toInt()
            ),
            HighlightRule(
                id = "bracket_note_default",
                name = "括号标注高亮",
                pattern = "（[^（）\\n]{1,80}）|\\([^()\\n]{1,80}\\)|【[^】\\n]{1,80}】|\\[[^\\]\\n]{1,80}]",
                sampleText = "他停了一下（像是忽然想起了什么）。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = context.getPrefBoolean(PreferKey.highlightRuleBracketNote, true),
                textColor = 0xFF8F959E.toInt(),
                underlineMode = 2,
                underlineWidth = 0.5f,
                underlineColor = 0xFF5A8DEE.toInt()
            ),
            HighlightRule(
                id = "title_emphasis_default",
                name = "标题强调",
                pattern = "(?m)^\\s{0,2}(?:第[0-9零〇一二两三四五六七八九十百千万IVXLCDMivxlcdm]{1,12}[章节卷回部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,40}$",
                sampleText = "第一章 雨夜来客",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                targetScope = HighlightRule.TARGET_TITLE,
                enabled = true,
                textColor = 0xFF333333.toInt(),
                underlineMode = 4,
                underlineColor = 0xFF7C5634.toInt()
            ),
            HighlightRule(
                id = "thought_default",
                name = "心理活动",
                pattern = "（[^）\\n]{0,40}(?:心想|暗道|心道|想到|寻思着|琢磨|嘀咕)[^）\\n]{0,40}）",
                sampleText = "她心中一紧（暗道不对，这里一定有问题）。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF9370DB.toInt(),
                underlineMode = 1,
                underlineWidth = 0.5f,
                underlineColor = 0xFF9370DB.toInt()
            ),
            HighlightRule(
                id = "narrator_default",
                name = "旁白说明",
                pattern = "(?:未完待续|待续|下文再表|按：|注：)[^\\n]{0,40}|（(?:注|旁白|作者有话说)[:：][^）\\n]{0,40}）",
                sampleText = "（注：此处时间线与前文同步）",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF708090.toInt()
            ),
            HighlightRule(
                id = "emphasis_default",
                name = "重点强调",
                pattern = "(?:\\*\\*|__)[^\\n*_]{1,40}(?:\\*\\*|__)|(?:!!!|！？|\\?!)[^\\n]{0,20}",
                sampleText = "**这是重点内容**，需要特别注意。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFFDC143C.toInt(),
                underlineMode = 1,
                underlineColor = 0xFFDC143C.toInt()
            ),
            HighlightRule(
                id = "poetry_default",
                name = "诗词引用",
                pattern = "(?m)^[\\p{IsHan}，。！？；：、]{5,24}$",
                sampleText = "床前明月光，\n疑是地上霜。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF2F4F4F.toInt(),
                underlineMode = 3,
                underlineWidth = 0.5f,
                underlineColor = 0xFF2F4F4F.toInt()
            ),
            HighlightRule(
                id = "ellipsis_default",
                name = "省略停顿",
                pattern = "…{2,}|\\.{3,}|—{2,}|-{3,}",
                sampleText = "他沉默了很久……最后还是点了头。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF8B8B8B.toInt()
            ),
            HighlightRule(
                id = "number_default",
                name = "数字金额",
                pattern = "(?:¥|￥)?\\d+(?:\\.\\d+)?(?:元|块|万|千|百|亿|%|％)|[零〇一二两三四五六七八九十百千万亿]+(?:元|块|万|千|百|亿)",
                sampleText = "原价100元，现在只要50元。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "english_default",
                name = "英文单词",
                pattern = "\\b[A-Za-z]{2,}[A-Za-z0-9'-]*\\b",
                sampleText = "Hello World，你好世界。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "date_time_default",
                name = "时间日期",
                pattern = "(?:\\d{2,4}|[零〇一二两三四五六七八九十]{2,4})年(?:\\d{1,2}|[正一二三四五六七八九十冬腊])月(?:\\d{1,2}|[一二三四五六七八九十廿三])?[日号]?|\\b\\d{1,2}:\\d{2}\\b|(?:[0-1]?\\d|2[0-3])点(?:[0-5]?\\d分?)?",
                sampleText = "2024年8月12日，上午10:30出发。",
                group = HighlightRuleGroupStore.DEFAULT_GROUP,
                enabled = false,
                textColor = 0xFF20B2AA.toInt()
            )
        )
    }
}