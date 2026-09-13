package io.legado.app.ui.book.source.ai

/**
 * AI 书源验证的结构化问题。
 *
 * 目的：让自动修复知道“哪一阶段、哪一种错误”，而不是只能解析一段中文日志。
 */
data class AiSourceValidationIssue(
    val code: Code,
    val stage: Stage,
    val message: String,
    val repairHint: String,
    val severity: Severity = Severity.ERROR,
    val chapterIndex: Int? = null,
    val url: String? = null
) {
    enum class Stage { JSON, SEARCH, BOOK_INFO, TOC, CONTENT, EXPLORE, REVIEW }

    enum class Severity { WARNING, ERROR, BLOCKING }

    enum class Code {
        INVALID_JSON,
        SEARCH_FAILED,
        BOOK_INFO_FAILED,
        TOC_FAILED,
        CONTENT_FAILED,
        EXPLORE_FAILED,
        REVIEW_RULE_MISSING,
        INVALID_CHAPTER_URL,
        DUPLICATE_CHAPTER_URL,
        EMPTY_CHAPTER_URL,
        MULTI_CHAPTER_CONTENT_FAILED,
        CONTENT_NOT_DISTINCT
    }
}
