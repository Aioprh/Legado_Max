package io.legado.app.ui.book.storage

import app.cash.turbine.test
import io.legado.app.MainDispatcherRule
import io.legado.app.help.storage.CacheDetail
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 存储管理 ViewModel 状态迁移测试（testing.md §16：runTest + Turbine + 手写 Fake）
 */
class StorageManageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val dataProvider = FakeStorageDataProvider()

    /** 构造后需驱动 init 的 loadCacheInfo 收敛 */
    private suspend fun TestScope.newLoadedViewModel(): StorageManageViewModel {
        val viewModel = StorageManageViewModel(dataProvider)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `加载完成后汇总八类缓存并统计总大小`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.cacheItems.test {
            val items = awaitItem()
            assertEquals(8, items.size)
            assertEquals(CacheType.BOOK_CACHE.name, items.first().id)
            // Fake 每类固定 100 字节
            assertEquals(800L, items.sumOf { it.size })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.totalSize.test {
            assertEquals(800L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.uiState.test {
            assertEquals(StorageUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `清理弹窗按请求显隐`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.requestClear(CacheType.BOOK_CACHE, detailId = "book-1")
        assertEquals(
            StorageDialogState.ClearConfirm(CacheType.BOOK_CACHE, "book-1"),
            viewModel.dialog.value
        )

        viewModel.requestClearAll()
        assertEquals(StorageDialogState.ClearAll, viewModel.dialog.value)

        viewModel.dismissDialog()
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `展开缓存项时加载明细并支持折叠`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.toggleExpand(CacheType.BOOK_CACHE)
        advanceUntilIdle()

        val expanded = viewModel.cacheItems.value.first { it.id == CacheType.BOOK_CACHE.name }
        assertTrue(expanded.isExpanded)
        assertEquals(listOf("book-1"), expanded.details?.map { it.id })

        viewModel.toggleExpand(CacheType.BOOK_CACHE)
        advanceUntilIdle()

        val collapsed = viewModel.cacheItems.value.first { it.id == CacheType.BOOK_CACHE.name }
        assertEquals(false, collapsed.isExpanded)
    }

    @Test
    fun `确认清理后路由到对应缓存类型并重新加载`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.clearCache(CacheType.TTS_CACHE, detailId = "engine-9")
        advanceUntilIdle()

        assertEquals(
            listOf(FakeStorageDataProvider.Cleared(CacheType.TTS_CACHE, "engine-9")),
            dataProvider.cleared
        )
        viewModel.uiState.test {
            assertEquals(StorageUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `未指定明细时以缓存名称作为清理目标`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.clearCache(CacheType.LOG_CACHE)
        advanceUntilIdle()

        assertEquals(
            listOf(FakeStorageDataProvider.Cleared(CacheType.LOG_CACHE, null)),
            dataProvider.cleared
        )
    }

    @Test
    fun `一键清理后清空选中并重新加载`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newLoadedViewModel()

        viewModel.clearAllCache()
        advanceUntilIdle()

        assertTrue(dataProvider.clearAllCalled)
        viewModel.uiState.test {
            assertEquals(StorageUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `加载异常时进入错误状态`() = runTest(mainDispatcherRule.dispatcher) {
        dataProvider.failOnLoad = true

        val viewModel = StorageManageViewModel(dataProvider)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(StorageUiState.Error("load failed"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * 手写 Fake：只实现被测路径关心的行为，其余返回固定值（照 FakeReadRecordDao 模式）
 */
private class FakeStorageDataProvider : StorageDataProvider {

    data class Cleared(val type: CacheType, val detailId: String?)

    val cleared = mutableListOf<Cleared>()
    var clearAllCalled = false
    var failOnLoad = false

    private fun size(): Long {
        if (failOnLoad) throw IllegalStateException("load failed")
        return 100L
    }

    override suspend fun bookCacheSize(): Long = size()
    override suspend fun cachedBookCount(): Int = 1
    override suspend fun epubCacheSize(): Long = size()
    override suspend fun tempCacheSize(): Long = size()
    override suspend fun ttsCacheSize(): Long = size()
    override suspend fun ttsEngineCount(): Int = 1
    override suspend fun aCacheSize(): Long = size()
    override suspend fun aCacheItemCount(): Int = 1
    override suspend fun dbCacheSize(): Long = size()
    override suspend fun dbCacheItemCount(): Int = 1
    override suspend fun logCacheSize(): Long = size()
    override suspend fun webViewCacheSize(): Long = size()
    override suspend fun webViewCacheDirCount(): Int = 1

    override suspend fun details(type: CacheType): List<CacheDetail> = listOf(
        CacheDetail(
            id = "book-1",
            name = "测试书籍",
            meta = "12 章",
            size = 100L,
            formattedSize = "100 b"
        )
    )

    override suspend fun clear(type: CacheType, detailId: String?) {
        cleared += Cleared(type, detailId)
    }

    override suspend fun clearAll() {
        clearAllCalled = true
    }

    override fun cacheName(type: CacheType): String = type.name
    override fun cacheDescription(type: CacheType): String = "${type.name} desc"
    override fun bookCountBadge(count: Int): String = "$count books"
    override fun engineCountBadge(count: Int): String = "$count engines"
    override fun itemCountBadge(count: Int): String = "$count items"
    override fun allCacheLabel(): String = "all"
    override fun loadFailedMessage(): String = "load failed"
    override fun clearFailedMessage(): String = "clear failed"
    override fun cachePath(type: CacheType): String = "/fake/${type.name}"
}
