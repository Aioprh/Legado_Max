package io.legado.app.ui.book.read

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.widget.dialog.BaseContentEditDialog
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书籍正文内容编辑
 */
class ContentEditDialog : BaseContentEditDialog() {

    val viewModel by viewModels<ContentEditViewModel>()

    override fun getTitle(): CharSequence {
        return ReadBook.curTextChapter?.title ?: getString(R.string.edit_content)
    }

    override fun getSourceType(): String = "chapterContent"

    override fun getSourceKey(): String = ReadBook.book?.bookUrl ?: ""

    override fun onContentReady() {
        // 点击标题编辑章节名
        binding.toolBar.setOnClickListener {
            lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter = withContext(IO) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                } ?: return@launch
                editTitle(chapter)
            }
        }
        // 加载状态指示器
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.rlLoading.visible() else binding.rlLoading.gone()
        }
        // 异步加载章节内容
        viewModel.initContent { content ->
            binding.contentView.setText(content)
            binding.contentView.post {
                binding.contentView.apply {
                    val lineIndex = layout.getLineForOffset(ReadBook.durChapterPos)
                    val lineHeight = layout.getLineTop(lineIndex)
                    scrollTo(0, lineHeight)
                }
            }
        }
    }

    override fun onSave(content: String): Boolean {
        // 内容未变化时不保存，避免覆盖缓存
        if (content == viewModel.content) {
            return true
        }
        Coroutine.async {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
        return true
    }

    override fun onReset() {
        viewModel.initContent(true) { content ->
            binding.contentView.setText(content)
            originalContent = null
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        // 取消时自动保存
        val content = binding.contentView.text?.toString() ?: return
        if (content != viewModel.content) {
            Coroutine.async {
                val book = ReadBook.book ?: return@async
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@async
                BookHelp.saveText(book, chapter, content)
                ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
            }
        }
    }

    private fun editTitle(chapter: BookChapter) {
        alert {
            setTitle(R.string.edit)
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            alertBinding.editView.setText(chapter.title)
            setCustomView(alertBinding.root)
            okButton {
                chapter.title = alertBinding.editView.text.toString()
                lifecycleScope.launch {
                    withContext(IO) {
                        chapter.update()
                    }
                    binding.toolBar.title = chapter.getDisplayTitle()
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
            }
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null

        fun initContent(reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@execute null

                if (reset) {
                    content = null
                    BookHelp.delContent(book, chapter)
                    if (!book.isLocal) ReadBook.bookSource?.let { bookSource ->
                        WebBook.getContentAwait(bookSource, book, chapter)
                    }
                }

                // 懒加载书源且当前章节未完全加载，提示用户稍后编辑
                val bookSource = ReadBook.bookSource
                if (bookSource != null && bookSource.nextPageLazyLoad) {
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && textChapter.chapter.index == chapter.index
                        && !textChapter.isFullyLoaded()
                    ) {
                        return@execute "[已开启下一页懒加载，加载完成后可编辑]"
                    }
                }

                // 从缓存文件读取（懒加载完成后已保存完整内容）
                return@execute content ?: let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    val cachedContent = BookHelp.getContent(book, chapter) ?: return@let null
                    contentProcessor.getContent(book, chapter, cachedContent, includeTitle = false)
                        .toString()
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                content = it
                success.invoke(it ?: "")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }
    }
}
