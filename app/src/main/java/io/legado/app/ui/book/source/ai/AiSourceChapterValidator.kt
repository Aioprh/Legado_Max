package io.legado.app.ui.book.source.ai

/**
 * 多章节验证的纯规则层。
 * 不直接依赖 WebBook，方便生成器把真实章节解析结果喂进来，也方便单元测试。
 */
object AiSourceChapterValidator {
    data class ChapterSample(
        val index: Int,
        val title: String,
        val url: String,
        val contentLength: Int,
        /** 正文归一化后的指纹，可选；用于识别“不同章节实际返回同一正文”。 */
        val contentFingerprint: String = ""
    )

    data class Result(
        val passed: Boolean,
        val message: String,
        val issues: List<AiSourceValidationIssue>
    )

    /** 首/中/末交叉验证；少于三个章节时全部验证。 */
    fun validate(samples: List<ChapterSample>): Result {
        if (samples.isEmpty()) {
            return Result(
                false,
                "没有可验证的章节样本",
                listOf(
                    AiSourceValidationIssue(
                        AiSourceValidationIssue.Code.TOC_TOO_FEW,
                        AiSourceValidationIssue.Stage.TOC,
                        "目录没有产生可验证章节",
                        "检查 chapterList/chapterName/chapterUrl。"
                    )
                )
            )
        }
        val issues = mutableListOf<AiSourceValidationIssue>()
        val seen = HashMap<String, Int>()
        samples.forEach { sample ->
            val url = sample.url.trim()
            if (url.isBlank()) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.EMPTY_CHAPTER_URL,
                    AiSourceValidationIssue.Stage.TOC,
                    "第 ${sample.index + 1} 章 URL 为空：${sample.title}",
                    "chapterUrl 必须从真实章节对象生成有效 URL。",
                    chapterIndex = sample.index
                )
            } else if (!isHttpUrl(url)) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.INVALID_CHAPTER_URL,
                    AiSourceValidationIssue.Stage.TOC,
                    "第 ${sample.index + 1} 章 URL 不是完整 HTTP(S) 地址：$url",
                    "不要返回裸相对路径；依据真实响应补全合法 http/https URL。",
                    chapterIndex = sample.index,
                    url = url
                )
            }
            val old = seen.put(url, sample.index)
            if (old != null && url.isNotBlank()) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.DUPLICATE_CHAPTER_URL,
                    AiSourceValidationIssue.Stage.TOC,
                    "第 ${old + 1} 章与第 ${sample.index + 1} 章 URL 相同：$url",
                    "chapterUrl 取到了目录 URL、固定 URL 或错误的 ID；必须使用当前章节真实 ID/URL。",
                    chapterIndex = sample.index,
                    url = url
                )
            }
            if (sample.contentLength <= 0) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.CONTENT_FAILED,
                    AiSourceValidationIssue.Stage.CONTENT,
                    "第 ${sample.index + 1} 章正文为空：${sample.title}",
                    "对该章节真实 URL 重新抓取响应，并依据响应重写 ruleContent.content。",
                    chapterIndex = sample.index,
                    url = url
                )
            }
        }

        val nonEmpty = samples.filter { it.contentLength > 0 }
        if (nonEmpty.size >= 3) {
            val distinctTitles = nonEmpty.map { it.title.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .size
            if (distinctTitles <= 1) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.CONTENT_NOT_DISTINCT,
                    AiSourceValidationIssue.Stage.CONTENT,
                    "首/中/末章节标题没有体现差异，可能始终解析到同一章节。",
                    "检查 chapterUrl 是否真正随章节 ID 变化，并避免把固定正文 URL 当成所有章节地址。"
                )
            }
            val fingerprints = nonEmpty.map { it.contentFingerprint.trim() }
                .filter { it.isNotBlank() }
            if (fingerprints.size >= 3 && fingerprints.distinct().size == 1) {
                issues += AiSourceValidationIssue(
                    AiSourceValidationIssue.Code.CONTENT_NOT_DISTINCT,
                    AiSourceValidationIssue.Stage.CONTENT,
                    "首/中/末章节正文指纹完全相同，疑似所有章节实际读取了同一正文。",
                    "检查 chapterUrl 中的章节 ID 是否来自当前章节对象；不要固定使用第一章 ID、目录 URL 或缓存结果。"
                )
            }
        }

        return Result(
            passed = issues.isEmpty(),
            message = if (issues.isEmpty()) {
                "多章节验证通过：${samples.size} 个章节样本的 URL、正文均有效且无重复。"
            } else {
                issues.joinToString("\n") { "- ${it.message}" }
            },
            issues = issues
        )
    }

    /** 供 ViewModel 选取真实章节时使用：首章 + 中间章 + 末章。 */
    fun sampleIndexes(size: Int): List<Int> = when {
        size <= 0 -> emptyList()
        size == 1 -> listOf(0)
        size == 2 -> listOf(0, 1)
        else -> listOf(0, size / 2, size - 1).distinct()
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
}
