package io.legado.app.ui.book.toc


import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocalTxt
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeText

class TocViewModel(application: Application) : BaseViewModel(application) {
    var bookUrl: String = ""
    var bookData = MutableLiveData<Book>()
    var chapterListCallBack: ChapterListCallBack? = null
    var bookMarkCallBack: BookmarkCallBack? = null
    var searchKey: String? = null

    var searchChapterName: Boolean = true
    var searchBookText: Boolean = true
    var searchContent: Boolean = true

    fun initBook(bookUrl: String) {
        this.bookUrl = bookUrl
        execute {
            appDb.bookDao.getBook(bookUrl)?.let {
                bookData.postValue(it)
            }
        }
    }

    fun upBookTocRule(book: Book, complete: (Throwable?) -> Unit) {
        execute {
            appDb.bookDao.update(book)
            LocalBook.getChapterList(book).let {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*it.toTypedArray())
                appDb.bookDao.update(book)
                ReadBook.onChapterListUpdated(book)
                bookData.postValue(book)
            }
        }.onSuccess {
            complete.invoke(null)
        }.onError {
            complete.invoke(it)
        }
    }

    /**
     * 手动刷新在线书籍目录。
     *
     * 与普通的目录显示不同，这里会强制重新请求书源目录，并执行
     * ruleToc.preUpdateJs，从而支持书源通过 java.refreshTocUrl()/
     * java.reGetBook() 重新获取最新目录地址。只有新目录完整解析成功后
     * 才替换数据库中的旧目录，避免网络失败导致原目录丢失。
     *
     * 本地 TXT 等本地书籍仍走 LocalBook 的目录解析逻辑。
     */
    fun refreshChapterList(complete: (Throwable?) -> Unit) {
        execute {
            val book = bookData.value ?: throw NoStackTraceException(
                context.getString(R.string.no_book)
            )

            if (book.isLocalTxt) {
                LocalBook.getChapterList(book).let { chapters ->
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.onChapterListUpdated(book)
                    bookData.postValue(book)
                }
            } else {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: throw NoStackTraceException("找不到书源：${book.origin}")

                // runPerJs=true：刷新时执行书源的 preUpdateJs，兼容需要动态
                // 重新获取 tocUrl / 重新获取书籍详情的书源。
                val chapters = WebBook.getChapterListAwait(
                    bookSource = source,
                    book = book,
                    runPerJs = true
                ).getOrThrow()

                // WebBook/BookChapterList 已完成章节缓存迁移；新目录完整成功后
                // 再替换旧目录，避免刷新失败把现有目录清空。
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*chapters.toTypedArray())
                appDb.bookDao.update(book)
                ReadBook.onChapterListUpdated(book)
                bookData.postValue(book)
            }
        }.onSuccess {
            complete.invoke(null)
        }.onError {
            complete.invoke(it)
        }
    }

    fun reverseToc(success: (book: Book) -> Unit) {
        execute {
            bookData.value?.apply {
                setReverseToc(!getReverseToc())
                val toc = appDb.bookChapterDao.getChapterList(bookUrl)
                // 先用旧 index 创建迁移器, 记住旧缓存文件名
                val migrator = BookHelp.createChapterCacheMigrator(this, toc)
                // 反转并重新编号 (toc.reversed() 共享对象引用, 必须在创建 migrator 之后修改 index)
                val newToc = toc.reversed()
                newToc.forEachIndexed { index, bookChapter ->
                    bookChapter.index = index
                }
                //倒序后章节序号变化会导致缓存文件名变化, 迁移缓存文件
                migrator.migrate(newToc)
                appDb.bookChapterDao.insert(*newToc.toTypedArray())
            }
        }.onSuccess {
            it?.let(success)
        }
    }

    fun startChapterListSearch(newText: String?) {
        chapterListCallBack?.upChapterList(newText)
    }

    fun startBookmarkSearch(newText: String?) {
        bookMarkCallBack?.upBookmark(newText)
    }

    fun upChapterListAdapter() {
        chapterListCallBack?.upAdapter()
    }

    fun saveBookmark(treeUri: Uri) {
        execute {
            val book = bookData.value
                ?: throw NoStackTraceException(context.getString(R.string.no_book))
            val fileName = "bookmark-${book.name} ${book.author}.json"
            val doc = FileDoc.fromUri(treeUri, true)
            doc.createFileIfNotExist(fileName).writeText(
                GSON.toJson(
                    appDb.bookmarkDao.getByBook(book.name, book.author)
                )
            )
        }.onError {
            AppLog.put("📤导出失败\n${it.localizedMessage}", it, true)
        }.onSuccess {
            context.toastOnUi("导出成功")
        }
    }

    fun saveBookmarkMd(treeUri: Uri) {
        execute {
            val book = bookData.value
                ?: throw NoStackTraceException(context.getString(R.string.no_book))
            val fileName = "bookmark-${book.name} ${book.author}.md"
            val treeDoc = FileDoc.fromUri(treeUri, true)
            val fileDoc = treeDoc.createFileIfNotExist(fileName)
                .openOutputStream()
                .getOrThrow()
            fileDoc.use { outputStream ->
                outputStream.write("## ${book.name} ${book.author}\n\n".toByteArray())
                appDb.bookmarkDao.getByBook(book.name, book.author).forEach {
                    outputStream.write("#### ${it.chapterName}\n\n".toByteArray())
                    outputStream.write("###### 原文\n ${it.bookText}\n\n".toByteArray())
                    outputStream.write("###### 摘要\n ${it.content}\n\n".toByteArray())
                }
            }
        }.onError {
            AppLog.put("📤导出失败\n${it.localizedMessage}", it, true)
        }.onSuccess {
            context.toastOnUi("导出成功")
        }
    }

    interface ChapterListCallBack {
        fun upChapterList(searchKey: String?)

        fun clearDisplayTitle()

        fun upAdapter()
    }

    interface BookmarkCallBack {
        fun upBookmark(searchKey: String?)
    }
}