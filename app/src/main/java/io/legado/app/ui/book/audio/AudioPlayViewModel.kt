package io.legado.app.ui.book.audio

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.getBookSource
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.MaxAudioSystem
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

class AudioPlayViewModel(application: Application) : BaseViewModel(application) {
    val titleData = MutableLiveData<String?>()
    val coverData = MutableLiveData<String?>()
    val customBtnListData = MutableLiveData<Boolean>()

    fun initData(intent: Intent, success: (() -> Unit)) = AudioPlay.apply {
        execute {
            inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            val bookUrl = intent.getStringExtra("bookUrl") ?: book?.bookUrl ?: return@execute
            val targetBook = appDb.bookDao.getBook(bookUrl) ?: run {
                inBookshelf = false
                book?.also { appDb.bookDao.insert(it) } ?: return@execute
            }
            initBook(targetBook)
        }.onSuccess { success.invoke() }.onFinally { saveRead(true) }
    }

    private suspend fun initBook(book: Book) {
        val activeBook = AudioPlay.book?.takeIf { it.bookUrl == book.bookUrl }
        val isSameBook = activeBook != null
        val keepActivePlayback = isSameBook && AudioPlayService.isRun

        // 详情页重新打开时，优先保留当前播放器已经拿到的封面，避免旧书架记录中的空 coverUrl 把它覆盖掉。
        if (book.getDisplayCover().isNullOrBlank()) {
            activeBook?.getDisplayCover()?.takeIf { it.isNotBlank() }?.let { book.coverUrl = it }
        }

        if (keepActivePlayback) {
            // 正在播放时以播放器真实章节/断点为准，避免详情页刷新把当前状态覆盖成旧书架记录。
            book.durChapterIndex = AudioPlay.durChapterIndex
            book.durChapterPos = AudioPlay.durChapterPos
            book.durChapterTitle = AudioPlay.durChapter?.title ?: book.durChapterTitle
            AudioPlay.book = book
            AudioPlay.chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
            AudioPlay.simulatedChapterSize = if (book.getReadSimulating()) book.simulatedTotalChapterNum() else AudioPlay.chapterSize
            AudioPlay.upDurChapter()
            MaxAudioSystem.syncCurrentBook(book)
            AudioPlayService.refreshMediaSession()
        } else if (isSameBook) {
            // Service 已退出时，静态播放器状态可能仍停留在旧的 0:00。
            // 先把数据库中的章节和断点灌回 AudioPlay，再走原有 upData，避免打开详情页把断点覆盖成 0。
            AudioPlay.durChapterIndex = book.durChapterIndex
            AudioPlay.durChapterPos = book.durChapterPos.coerceAtLeast(0)
            AudioPlay.upData(book)
        } else {
            AudioPlay.resetData(book)
        }

        customBtnListData.postValue(AudioPlay.bookSource?.customButton == true)
        titleData.postValue(book.name)
        publishCover(book)

        // 先使用已有封面；没有时再从当前书源补齐书籍详情。
        if (book.getDisplayCover().isNullOrBlank()) {
            loadBookInfo(book)
        } else if (book.tocUrl.isEmpty()) {
            if (!loadBookInfo(book)) return
        }

        // 当前书源仍没有封面时，使用项目原有的“启用书源搜索封面”机制补齐。
        if (book.getDisplayCover().isNullOrBlank()) {
            recoverCoverFromEnabledSources(book)
        }
        publishCover(book)

        if (AudioPlay.chapterSize == 0 && !loadChapterList(book)) return
        titleData.postValue(book.name)
        publishCover(book)
    }

    private fun publishCover(book: Book) {
        coverData.postValue(BookCover.getDisplayCover(book))
    }

    private suspend fun recoverCoverFromEnabledSources(book: Book) {
        try {
            val cover = BookCover.searchCoverByEnabledSource(book)?.takeIf { it.isNotBlank() }
                ?: return
            book.coverUrl = cover
            appDb.bookDao.update(book)
            AudioPlay.book = book
            publishCover(book)
        } catch (e: Exception) {
            AppLog.put("音频封面搜索失败: ${e.localizedMessage}", e, false)
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: book.getBookSource() ?: return true
        val existingCover = book.coverUrl
        try {
            WebBook.getBookInfoAwait(bookSource, book)
            if (book.coverUrl.isNullOrBlank() && !existingCover.isNullOrBlank()) {
                book.coverUrl = existingCover
            }
            appDb.bookDao.update(book)
            AudioPlay.book = book
            AudioPlay.bookSource = bookSource
            publishCover(book)
            return true
        } catch (e: Exception) {
            AppLog.put("详情页出错: ${e.localizedMessage}", e, true)
            return false
        }
    }

    private suspend fun loadChapterList(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        try {
            val oldIndex = AudioPlay.durChapterIndex
            val oldPosition = AudioPlay.durChapterPos
            val oldBook = book.copy()
            val cList = WebBook.getChapterListAwait(bookSource, book).getOrThrow()
            if (oldBook.bookUrl == book.bookUrl) appDb.bookDao.update(book) else appDb.bookDao.replace(oldBook, book)
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookChapterDao.insert(*cList.toTypedArray())
            AudioPlay.chapterSize = cList.size
            AudioPlay.simulatedChapterSize = book.simulatedTotalChapterNum()
            AudioPlay.durChapterIndex = oldIndex.coerceIn(0, (AudioPlay.simulatedChapterSize - 1).coerceAtLeast(0))
            AudioPlay.durChapterPos = oldPosition.coerceAtLeast(0)
            book.durChapterIndex = AudioPlay.durChapterIndex
            book.durChapterPos = AudioPlay.durChapterPos
            AudioPlay.upDurChapter()
            appDb.bookDao.update(book)
            MaxAudioSystem.syncCurrentBook(book)
            AudioPlayService.refreshMediaSession()
            return true
        } catch (_: Exception) {
            context.toastOnUi(R.string.error_load_toc)
            return false
        }
    }

    fun upSource() {
        execute {
            val book = AudioPlay.book ?: return@execute
            AudioPlay.bookSource = book.getBookSource()?.also { customBtnListData.postValue(it.customButton) }
            AudioPlay.durPlayUrl = ""
            AudioPlay.durLyric = null
            AudioPlay.upDurChapter()
            titleData.postValue(book.name)
            publishCover(book)
            AudioPlayService.refreshMediaSession()
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        execute {
            val wasPlaying = AudioPlay.status == Status.PLAY
            AudioPlay.stop()
            AudioPlay.durPlayUrl = ""
            AudioPlay.durLyric = null
            AudioPlay.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            AudioPlay.book?.delete()
            appDb.bookDao.insert(book)
            AudioPlay.book = book
            AudioPlay.bookSource = source
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            AudioPlay.chapterSize = toc.size
            AudioPlay.simulatedChapterSize = book.simulatedTotalChapterNum()
            AudioPlay.durChapterIndex = book.durChapterIndex.coerceIn(0, (AudioPlay.simulatedChapterSize - 1).coerceAtLeast(0))
            AudioPlay.durChapterPos = book.durChapterPos.coerceAtLeast(0)
            AudioPlay.upDurChapter()
            titleData.postValue(book.name)
            publishCover(book)
            AudioPlayService.refreshMediaSession()
            if (wasPlaying) AudioPlay.loadOrUpPlayUrl()
        }.onFinally { postEvent(EventBus.SOURCE_CHANGED, book.bookUrl) }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute { AudioPlay.book?.let { appDb.bookDao.delete(it) } }.onSuccess { success?.invoke() }
    }
}