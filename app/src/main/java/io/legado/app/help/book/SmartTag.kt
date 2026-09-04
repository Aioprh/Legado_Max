package io.legado.app.help.book

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book

/**
 * Max 智能标签第一阶段：基于书籍已有元数据生成虚拟标签。
 *
 * 标签不会写入 books 表，因此无需数据库迁移，也不会覆盖用户手动设置的 customTag。
 * 生成结果只依赖当前 Book 状态，书籍更新后重新计算即可。
 */
object SmartTag {

    data class Result(
        val name: String,
        val score: Int = 0,
        val reason: String? = null
    )

    private data class Rule(
        val name: String,
        val score: Int,
        val reason: String,
        val match: (Book) -> Boolean
    )

    private fun hasType(book: Book, type: Int): Boolean = (book.type and type) != 0

    private val rules = listOf(
        Rule("有声", 100, "书籍类型为音频") { hasType(it, BookType.audio) },
        Rule("漫画", 100, "书籍类型为图片/漫画") { hasType(it, BookType.image) },
        Rule("视频", 100, "书籍类型为视频") { hasType(it, BookType.video) },
        Rule("本地", 90, "本地书籍") { hasType(it, BookType.local) },
        Rule("网络书", 60, "来自网络书源") { !hasType(it, BookType.local) },
        Rule("更新异常", 95, "最近一次更新失败") { hasType(it, BookType.updateError) },
        Rule("已读完", 100, "已阅读到最后一章") {
            it.totalChapterNum > 0 && it.durChapterIndex >= it.totalChapterNum - 1
        },
        Rule("在读", 80, "存在阅读进度且尚未读完") {
            it.totalChapterNum > 0 && it.durChapterIndex > 0 && it.durChapterIndex < it.totalChapterNum - 1
        },
        Rule("未开始", 70, "尚未产生章节阅读进度") {
            it.totalChapterNum > 0 && it.durChapterIndex <= 0 && it.durChapterPos <= 0
        },
        Rule("超长篇", 80, "章节数达到 1000 章以上") { it.totalChapterNum >= 1000 },
        Rule("长篇", 70, "章节数达到 500 章以上") { it.totalChapterNum in 500..999 },
        Rule("中长篇", 60, "章节数达到 200 章以上") { it.totalChapterNum in 200..499 },
        Rule("短篇", 50, "章节数少于 50 章") { it.totalChapterNum in 1..49 },
        Rule("有更新", 85, "最近一次检查发现了新章节") { it.lastCheckCount > 0 },
        Rule("不可更新", 75, "已关闭自动更新") { !it.canUpdate },
        Rule("有封面", 30, "存在可显示封面") { !it.getDisplayCover().isNullOrBlank() },
        Rule("有简介", 30, "存在可显示简介") { !it.getDisplayIntro().isNullOrBlank() }
    )

    /** 计算一组稳定、可展示的智能标签。 */
    fun evaluate(book: Book, maxTags: Int = 6): List<Result> {
        if (maxTags <= 0) return emptyList()

        return rules.asSequence()
            .filter { it.match(book) }
            .map { Result(it.name, it.score, it.reason) }
            .sortedWith(compareByDescending<Result> { it.score }.thenBy { it.name })
            .distinctBy { it.name }
            .take(maxTags)
            .toList()
    }

    fun names(book: Book, maxTags: Int = 6): List<String> =
        evaluate(book, maxTags).map { it.name }
}

/** 方便书架、搜索结果、书籍详情等 UI 直接取得智能标签。 */
fun Book.getSmartTags(maxTags: Int = 6): List<String> = SmartTag.names(this, maxTags)
