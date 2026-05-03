package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.model.BookCover

class BookRepository {

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return null
        return chapter.title
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        book.getDisplayCover()?.let { return it }
        val coverUrl = BookCover.searchCover(book) ?: return null
        book.customCoverUrl = coverUrl
        book.save()
        return book.getDisplayCover()
    }

    suspend fun getBookDurChapterTitle(bookName: String, bookAuthor: String): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        return book.durChapterTitle
    }

    suspend fun getAuthorByBookName(bookName: String): String? {
        val book = appDb.bookDao.getBookByName(bookName) ?: return null
        return book.author.ifBlank { null }
    }
}
