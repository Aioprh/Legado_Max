package io.legado.app.ui.book.source.ai

/**
 * AI 书源真实验证的结构化问题。
 *
 * 与纯文本 summary 不同，code/stage/severity/repairHint 可直接用于：
 * 1. 自动修复提示词；
 * 2. UI 展示与筛选；
 * 3. 后续多章节验证；
 * 4. 统计最常见的书源生成失败类型。
 */
data class AiSourceValidationIssue(
    val code: Code,
    val stage: Stage,
    val message: String,
    val repairHint: String = "",
    val chapterIndex: Int? = null,
    val url: String? = null,
    val severity: Severity = Severity.ERROR
) {
    enum class Severity { WARNING, ERROR }

    enum class Stage {
        JSON,
        SEARCH,
        BOOK_INFO,
        TOC,
        CONTENT,
        EXPLORE,
        REVIEW
    }

    enum class Code {
        INVALID_JSON,
        EMPTY_SEARCH,
        SEARCH_FAILED,
        BOOK_INFO_FAILED,
        TOC_FAILED,
        TOC_TOO_FEW,
        DUPLICATE_CHAPTER_URL,
        INVALID_CHAPTER_URL,
        CONTENT_FAILED,
        EMPTY_CONTENT,
        SAME_CONTENT_URL,
        SAME_CONTENT,
        EXPLORE_FAILED,
        REVIEW_RULE_MISSING
    }
}

/**
 * 不依赖 WebBook 的轻量真实验证辅助工具。
 * WebBook 负责实际请求和解析，本类负责把多章节结果归一化成稳定的错误类型。
 */
object AiSourceValidation {

    data class ChapterSample(
        val index: Int,
        val title: String,
        val url: String,
        val contentLength: Int,
        val contentFingerprint: String = ""
    )

    /**
     * 从目录中选择首章、中间章、末章进行交叉验证。
     * 少于 3 章时全部返回；同一章节不会重复。
     */
    fun selectChapterIndexes(size: Int): List<Int> {
        if (size <= 0) return emptyList()
        if (size == 1) return listOf(0)
        if (size == 2) return listOf(0, 1)
        return listOf(0, size / 2, size - 1).distinct()
    }

    /** 检查章节 URL 是否为完整 HTTP(S) 地址。 */
    fun isAbsoluteHttpUrl(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }

    /**
     * 对已经实际解析出的多章节结果做一致性检查。
     * 不判断正文具体内容是否合法，只拦截“全部章节指向同一 URL/同一正文”这类高概率错误。
     */
    fun validateChapterSamples(samples: List<ChapterSample>): List<AiSourceValidationIssue> {
        if (samples.isEmpty()) {
            return listOf(
                AiSourceValidationIssue(
                    code = AiSourceValidationIssue.Code.TOC_TOO_FEW,
                    stage = AiSourceValidationIssue.Stage.TOC,
                    message = "没有可用于交叉验证的章节",
                    repairHint = "检查 ruleToc.chapterList、chapterName、chapterUrl 是否匹配真实目录结构。"
                )
            )
        }

        val issues = mutableListOf<AiSourceValidationIssue>()
        val validSamples = samples.filter { it.url.isNotBlank() }

        validSamples.forEach { sample ->
            if (!isAbsoluteHttpUrl(sample.url)) {
                issues += AiSourceValidationIssue(
                    code = AiSourceValidationIssue.Code.INVALID_CHAPTER_URL,
                    stage = AiSourceValidationIssue.Stage.CONTENT,
                    chapterIndex = sample.index,
                    url = sample.url,
                    message = "第 ${sample.index + 1} 个抽样章节的正文 URL 不是完整 HTTP(S) 地址：${sample.url.take(160)}",
                    repairHint = "让 ruleToc.chapterUrl 返回完整 http:// 或 https:// URL；不要留下空 host、裸相对地址或空参数。"
                )
            }
            if (sample.contentLength <= 0) {
                issues += AiSourceValidationIssue(
                    code = AiSourceValidationIssue.Code.EMPTY_CONTENT,
                    stage = AiSourceValidationIssue.Stage.CONTENT,
                    chapterIndex = sample.index,
                    url = sample.url,
                    message = "第 ${sample.index + 1} 个抽样章节正文为空",
                    repairHint = "根据该章节 URL 的真实响应重写 ruleContent.content；JSON 用 JSONPath，HTML 用 CSS 选择器。"
                )
            }
        }

        val duplicateUrls = validSamples.groupBy { it.url.trim() }.filterValues { it.size > 1 }
        duplicateUrls.values.forEach { duplicates ->
            val indexes = duplicates.map { it.index + 1 }.joinToString(",")
            issues += AiSourceValidationIssue(
                code = AiSourceValidationIssue.Code.DUPLICATE_CHAPTER_URL,
                stage = AiSourceValidationIssue.Stage.TOC,
                chapterIndex = duplicates.first().index,
                url = duplicates.first().url,
                message = "抽样章节 [$indexes] 解析出了相同的正文 URL：${duplicates.first().url.take(160)}",
                repairHint = "检查 ruleToc.chapterUrl 是否错误地固定使用了目录 URL、第一章 ID 或空参数；必须从当前章节对象取得章节 ID。"
            )
        }

        val fingerprints = validSamples.filter { it.contentFingerprint.isNotBlank() }
            .groupBy { it.contentFingerprint }
            .filterValues { it.size > 1 }
        if (validSamples.size >= 2 && fingerprints.any { it.value.size == validSamples.size }) {
            issues += AiSourceValidationIssue(
                code = AiSourceValidationIssue.Code.SAME_CONTENT,
                stage = AiSourceValidationIssue.Stage.CONTENT,
                message = "多个抽样章节正文完全相同，疑似章节 ID 没有随 chapterUrl 改变",
                repairHint = "不要把固定章节 ID 写死；从当前章节 JSON 对象读取 C/Cid/ChapterId/ContentId 等真实字段。"
            )
        }

        return issues
    }

    /** 将结构化问题转换成适合追加到 LLM 修复提示词的短文本。 */
    fun toRepairPrompt(issues: List<AiSourceValidationIssue>, max: Int = 8): String {
        if (issues.isEmpty()) return ""
        return issues.take(max).joinToString("\n") { issue ->
            buildString {
                append("- [${issue.code}] ${issue.stage}: ${issue.message}")
                if (issue.repairHint.isNotBlank()) append("；修复：${issue.repairHint}")
                if (issue.chapterIndex != null) append("；抽样章节=${issue.chapterIndex + 1}")
            }
        }
    }
}
