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
     * loader 返回 null 表示该章节无法完成正文解析，会由调用方决定是否记录额外错误。
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

        val samples = buildList {
            indexes.forEach { index ->
                loader(index)?.let(::add)
            }
        }
        val issues = AiSourceValidation.validateChapterSamples(samples)
        return VerificationResult(
            passed = issues.isEmpty() && samples.size == indexes.size,
            samples = samples,
            issues = issues,
            repairPrompt = AiSourceValidation.toRepairPrompt(issues)
        )
    }
}
