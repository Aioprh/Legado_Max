package io.legado.app.ui.book.cacheSelector

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.storage.BookCacheSelectorConfig
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class BookCacheItem(
    val book: Book,
    val cacheSize: Long,
    val formattedSize: String,
    val isSelected: Boolean
)

sealed class BookCacheSelectorUiState {
    object Loading : BookCacheSelectorUiState()
    object Idle : BookCacheSelectorUiState()
}

class BookCacheSelectorViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow<BookCacheSelectorUiState>(BookCacheSelectorUiState.Loading)
    val uiState: StateFlow<BookCacheSelectorUiState> = _uiState.asStateFlow()

    private val _bookItems = MutableStateFlow<List<BookCacheItem>>(emptyList())
    val bookItems: StateFlow<List<BookCacheItem>> = _bookItems.asStateFlow()

    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    private val _totalSelectedSize = MutableStateFlow(0L)
    val totalSelectedSize: StateFlow<Long> = _totalSelectedSize.asStateFlow()

    init {
        loadBooks()
    }

    fun loadBooks() {
        execute {
            _uiState.value = BookCacheSelectorUiState.Loading
            try {
                val books = withContext(Dispatchers.IO) {
                    BookCacheSelectorConfig.getBooksWithCache()
                }

                // 并行计算每本书的缓存大小
                val items = books.map { book ->
                    async(Dispatchers.IO) {
                        val size = calculateBookCacheSize(book)
                        BookCacheItem(
                            book = book,
                            cacheSize = size,
                            formattedSize = ConvertUtils.formatFileSize(size),
                            isSelected = BookCacheSelectorConfig.isSelected(book)
                        )
                    }
                }.awaitAll()

                _bookItems.value = items
                updateSelectionSummary()
                _uiState.value = BookCacheSelectorUiState.Idle
            } catch (e: Exception) {
                _uiState.value = BookCacheSelectorUiState.Idle
            }
        }
    }

    fun toggleSelect(book: Book) {
        val current = _bookItems.value.toMutableList()
        val index = current.indexOfFirst { it.book.bookUrl == book.bookUrl }
        if (index == -1) return

        val item = current[index]
        val newSelected = !item.isSelected
        current[index] = item.copy(isSelected = newSelected)
        BookCacheSelectorConfig.setSelected(book, newSelected)
        _bookItems.value = current
        updateSelectionSummary()
    }

    fun selectAll() {
        val current = _bookItems.value.map { item ->
            BookCacheSelectorConfig.setSelected(item.book, true)
            item.copy(isSelected = true)
        }
        _bookItems.value = current
        updateSelectionSummary()
    }

    fun deselectAll() {
        val current = _bookItems.value.map { item ->
            BookCacheSelectorConfig.setSelected(item.book, false)
            item.copy(isSelected = false)
        }
        _bookItems.value = current
        updateSelectionSummary()
    }

    fun isAllSelected(): Boolean {
        return _bookItems.value.isNotEmpty() && _bookItems.value.all { it.isSelected }
    }

    fun saveSelection() {
        BookCacheSelectorConfig.save()
    }

    private fun updateSelectionSummary() {
        val items = _bookItems.value
        val selected = items.filter { it.isSelected }
        _selectedCount.value = selected.size
        _totalSelectedSize.value = selected.sumOf { it.cacheSize }
    }

    private fun calculateBookCacheSize(book: Book): Long {
        val cacheDir = File(BookHelp.cachePath, book.getFolderNameNoCache())
        return calculateDirSize(cacheDir)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            val stack = ArrayDeque<File>()
            stack.addLast(dir)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                current.listFiles()?.forEach { file: File ->
                    if (file.isFile) {
                        size += file.length()
                    } else if (file.isDirectory) {
                        stack.addLast(file)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return size
    }
}
