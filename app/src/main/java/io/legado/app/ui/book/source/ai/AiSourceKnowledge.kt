package io.legado.app.ui.book.source.ai

/**
 * AI 书源生成的本地规则知识库。
 *
 * 这是对 legadoSkill 中最值得常驻客户端的“规则知识层”的轻量移植：
 * 不把整套 Markdown 知识库塞进 APK，而是保留会直接影响生成正确率的高价值规则，
 * 供静态预检、修复提示和后续 AI Prompt 增强共同使用。
 */
object AiSourceKnowledge {

    data class Hint(
        val id: String,
        val title: String,
        val rule: String,
        val repair: String
    )

    val hints: List<Hint> = listOf(
        Hint(
            "json-list",
            "JSON 接口列表规则",
            "JSON/接口响应的 bookList、chapterList 等列表字段应直接使用 JSONPath，不要对整个响应使用 @js:JSON.parse(this)。",
            "把 @js:JSON.parse(this) 改成真实 JSONPath，例如 $.data.list、$.Data.CardList[*].Body[*].ItemData。"
        ),
        Hint(
            "no-book-id",
            "Legado 变量边界",
            "本版规则 JS 中不存在 bookId/chapterId 变量；ID 应从当前 JSON 元素、book.bookUrl 或 chapter.url 中取得。",
            "删除 {{bookId}}/{{chapterId}} 及裸 bookId/chapterId，改为 JSONPath 取 ID，或从 book.bookUrl/chapter.url 用安全正则提取。"
        ),
        Hint(
            "safe-match",
            "match 空值安全",
            "JavaScript String.match() 可能返回 null，不能直接访问 [1] 等下标。",
            "使用 (url.match(/.../)||[])[1]||''，避免 TypeError。"
        ),
        Hint(
            "absolute-url",
            "URL 完整性",
            "搜索、详情、目录、正文最终解析出的 URL 必须是合法 http/https URL，不能出现空 host、空参数或裸相对地址。",
            "优先让 bookUrl/chapterUrl 返回完整 URL；相对地址需要由站点实际规则补全，不要凭空拼接域名。"
        ),
        Hint(
            "toc-pagination",
            "目录分页",
            "目录只有第一页会导致书源表现为“只加载最新一章/少量章节”，nextTocUrl 与分页规则必须依据真实接口验证。",
            "检查 nextTocUrl、分页参数和章节去重；至少验证首章、中间章、末章的 URL 都不同且可访问。"
        ),
        Hint(
            "content-chain",
            "正文链路",
            "目录能解析不代表正文能解析；chapterUrl 必须真正指向正文接口/页面，不能把目录 URL 当正文 URL。",
            "从真实章节对象中取正文 ID/URL，并对实际 chapter.url 执行正文解析验证。"
        ),
        Hint(
            "selector-stability",
            "选择器稳定性",
            "禁止依赖脆弱的 :contains()、:first-child、:last-child 等高风险写法；优先使用稳定 class/id、属性和 JSONPath。",
            "改用稳定属性选择器或 Legado 原生选择器语法，并以真实 HTML/JSON 结构为依据。"
        ),
        Hint(
            "regex-pair",
            "正则配对",
            "## 正则必须成对出现，且内部正则必须可编译。",
            "检查 ## 数量为偶数，并确保中间正则可以被 Kotlin Regex 编译。"
        ),
        Hint(
            "review-safe",
            "段评规则安全",
            "段评气泡只能在真实探测到接口或用户明确开启段评探测时生成，不得虚构接口。",
            "没有真实段评接口证据时保持空规则；有接口时先验证统计接口再生成气泡。"
        )
    )

    /** 返回静态规则摘要，供 AI/校验层使用。 */
    fun compactRules(): String = hints.joinToString("\n") {
        "- [${it.id}] ${it.rule}"
    }

    /** 根据书源文本快速筛选最相关的修复提示。 */
    fun relevantRepairHints(sourceText: String, max: Int = 5): List<Hint> {
        val text = sourceText.lowercase()
        val scored = hints.map { hint ->
            var score = 0
            when (hint.id) {
                "json-list" -> if (text.contains("@js:json.parse(this)")) score += 10
                "no-book-id" -> if (Regex("(?<![.$])\\b(bookId|chapterId)\\b").containsMatchIn(sourceText)) score += 10
                "safe-match" -> if (Regex("\\.match\\s*\\([^)]*\\)\\s*\\[").containsMatchIn(sourceText)) score += 10
                "absolute-url" -> if (text.contains("bookurl") || text.contains("chapterurl")) score += 2
                "toc-pagination" -> if (text.contains("nexttocurl")) score += 4
                "content-chain" -> if (text.contains("chapterurl") && text.contains("rulecontent")) score += 3
                "selector-stability" -> if (text.contains(":contains(") || text.contains(":first-child") || text.contains(":last-child")) score += 10
                "regex-pair" -> if (text.contains("##")) score += 3
                "review-safe" -> if (text.contains("showparagraphcomments") || text.contains("dp:")) score += 4
            }
            hint to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(max)
        return scored.map { it.first }
    }
}
