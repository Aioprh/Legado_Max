package io.legado.app.ui.book.source.ai

import io.legado.app.data.entities.BookSource
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils

/**
 * AI 书源生成后的真实运行时多章节验证。
 *
 * 与静态规则检查不同，这里直接复用 App 的真实搜索、详情、目录、正文链路，
 * 从非卷章节中抽取首/中/末三个位置，确认 chapterUrl 会随章节变化且正文不是同一份。
 */
object AiSourceRuntimeMultiChapter {

    data class Result(
        val passed: Boolean,
        val summary: String,
        val samples: List<AiSourceValidation.ChapterSample> = emptyList(),
        val repairPrompt: String = ""
    )

    suspend fun verify(sourceJson: String, keyword: String): Result {
        if (keyword.isBlank()) {
            return Result(false, "没有搜索关键词，无法执行多章节运行时验证")
        }
        val fixed = AiSourceValidate.parseSource(sourceJson)
            ?: return Result(false, "书源 JSON 无法解析，无法执行多章节运行时验证")
        val bookSource = runCatching {
            GSON.fromJson(fixed, BookSource::class.java)
        }.getOrElse {
            return Result(false, "书源对象转换失败：${it.message ?: it.javaClass.simpleName}")
        }
        bookSource.loginCheckJs = null
        bookSource.loginUi = null

        val books = runCatching {
            WebBook.searchBookAwait(bookSource, keyword, 1)
        }.getOrElse {
            return Result(false, "多章节验证搜索失败：${it.message ?: it.javaClass.simpleName}")
        }
        val first = books.firstOrNull()
            ?: return Result(false, "多章节验证搜索没有返回书籍")
        val book = first.toBook()
        val infoBook = runCatching {
            WebBook.getBookInfoAwait(bookSource, book, canReName = false)
        }.getOrElse {
            return Result(false, "多章节验证详情失败：${it.message ?: it.javaClass.simpleName}")
        }
        val chapters = runCatching {
            WebBook.getChapterListAwait(bookSource, infoBook, runPerJs = true).getOrThrow()
        }.getOrElse {
            return Result(false, "多章节验证目录失败：${it.message ?: it.javaClass.simpleName}")
        }
        val contentChapters = chapters.filterNot { it.isVolume }
        if (contentChapters.isEmpty()) {
            return Result(false, "多章节验证失败：目录没有可读取的普通章节")
        }

        val indexes = AiSourceMultiChapterVerifier.verify(contentChapters.size) { index ->
            val chapter = contentChapters.getOrNull(index) ?: return@verify null
            runCatching {
                val content = WebBook.getContentAwait(
                    bookSource,
                    infoBook,
                    chapter,
                    needSave = false
                )
                AiSourceValidation.ChapterSample(
                    index = index,
                    title = chapter.title,
                    url = chapter.url,
                    contentLength = content.length,
                    contentFingerprint = MD5Utils.md5Encode(
                        content.replace(Regex("\\s+"), " ").trim()
                    )
                )
            }.getOrNull()
        }

        if (indexes.passed) {
            return Result(
                true,
                "✓ 多章节运行时验证通过：首/中/末 ${indexes.samples.size} 章均能正常读取，章节 URL 与正文均有效且内容不重复。",
                indexes.samples
            )
        }
        val summary = buildString {
            appendLine("✗ 多章节运行时验证失败")
            if (indexes.samples.isNotEmpty()) {
                indexes.samples.forEach { sample ->
                    appendLine("- 第 ${sample.index + 1} 章「${sample.title}」：URL=${sample.url}，正文=${sample.contentLength} 字")
                }
            }
            if (indexes.issues.isNotEmpty()) {
                indexes.issues.forEach { appendLine("- ${it.message}") }
            }
        }.trim()
        return Result(false, summary, indexes.samples, indexes.repairPrompt)
    }
}
