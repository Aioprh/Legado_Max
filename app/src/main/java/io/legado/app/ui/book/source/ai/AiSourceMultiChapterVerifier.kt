package io.legado.app.ui.book.source.ai

/**
 * AI 书源生成器的多章节运行时验证桥接层。
 *
 * WebBook 的具体 Chapter 类型留在调用方处理，避免验证器与 WebBook 模型强耦合。
 * 调用方只需要为指定章节返回 ChapterSample，即可统一执行首/中/末交叉验证，
 * 并把结构化问题转换成自动修复提示词。
 */
object AiSourceMultiChapterVerifier {

    data class VerificationResult(
        val passed: Boolean,
        val samples: List<AiSourceValidation.ChapterSample>,
        val issues: List<AiSourceValidationIssue>,
        val repairPrompt: String
    )

    /**
     * 对目录章节执行首/中/末真实解析。
     * loader 返回 null 时必须形成结构化错误，避免出现 passed=false 但 repairPrompt 为空的情况。
     */
    suspend fun verify(
        chapterCount: Int,
        loader: suspend (index: Int) -> AiSourceValidation.ChapterSample?
    ): VerificationResult {
        val indexes = AiSourceValidation.selectChapterIndexes(chapterCount)
        if (indexes.isEmpty()) {
            val issues = listOf(
                AiSourceValidationIssue(
                    code = AiSourceValidationIssue.Code.TOC_TOO_FEW,
                    stage = AiSourceValidationIssue.Stage.TOC,
                    message = "目录没有可用于多章节验证的章节",
                    repairHint = "检查 ruleToc.chapterList、chapterName、chapterUrl。"
                )
            )
            return VerificationResult(false, emptyList(), issues, AiSourceValidation.toRepairPrompt(issues))
        }

        val samples = mutableListOf<AiSourceValidation.ChapterSample>()
        val issues = mutableListOf<AiSourceValidationIssue>()
        indexes.forEach { index ->
            val sample = runCatching { loader(index) }.getOrNull()
            if (sample != null) {
                samples += sample
            } else {
                issues += AiSourceValidationIssue(
                    code = AiSourceValidationIssue.Code.CONTENT_FAILED,
                    stage = AiSourceValidationIssue.Stage.CONTENT,
                    message = "第 ${index + 1} 个抽样章节无法完成正文解析",
                    repairHint = "检查该章节的 chapterUrl 是否随章节 ID 正确变化，并检查 ruleContent.content 是否适配真实正文响应。",
                    chapterIndex = index
                )
            }
        }
        issues += AiSourceValidation.validateChapterSamples(samples)

        return VerificationResult(
            passed = issues.isEmpty() && samples.size == indexes.size,
            samples = samples,
            issues = issues,
            repairPrompt = AiSourceValidation.toRepairPrompt(issues)
        )
    }
}
