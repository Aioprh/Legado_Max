package io.legado.app.help.storage

import io.legado.app.data.entities.Book

/**
 * 书籍缓存索引的轻量数据模型。
 * 仅用于 Restore.kt 的流式解析，不进入数据库。
 */
data class BookCacheIndexData(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val folderName: String,
    val chapters: List<ChapterCacheInfoData>
)

data class ChapterCacheInfoData(
    val index: Int,
    val title: String,
    val titleMD5: String,
    val fileName: String
)

/**
 * 恢复书籍缓存时只接受具备基本定位信息的书籍，避免空对象进入书架。
 */
fun Book.sanitizeForCacheRestore(): Book? {
    if (bookUrl.isBlank() || name.isBlank()) return null
    return this
}
