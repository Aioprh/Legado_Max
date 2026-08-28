package io.legado.app.ui.source.recycle

import app.cash.turbine.test
import io.legado.app.MainDispatcherRule
import io.legado.app.R
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.source.SourceRecycleBinHelp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 回收站 ViewModel 状态迁移测试（testing.md §16：runTest + Turbine + 手写 Fake）
 */
class SourceRecycleBinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val allItems = MutableStateFlow(
        listOf(
            item(id = 1L, name = "书源A"),
            item(id = 2L, name = "书源B")
        )
    )

    private val repository = FakeRecycleBinRepository(allItems)

    private fun item(id: Long, name: String) = SourceRecycleBin(
        id = id,
        type = SourceRecycleBinHelp.TYPE_BOOK_SOURCE,
        key = "key$id",
        name = name
    )

    @Test
    fun `初始状态加载全部条目`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)

        viewModel.items.test {
            // WhileSubscribed 首次订阅先发射初始值，跳过后才是数据源真实值
            skipItems(1)
            assertEquals(allItems.value, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `切换筛选后条目跟随类型过滤`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)

        viewModel.setFilter(SourceRecycleBinFilter.BOOK_SOURCE)

        viewModel.items.test {
            skipItems(1)
            assertEquals(allItems.value, awaitItem())
            // 订阅生效后 flatMapLatest 才按类型切换数据源（WhileSubscribed 无订阅者不收集）
            assertEquals(SourceRecycleBinHelp.TYPE_BOOK_SOURCE, repository.lastFlowType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `开关状态同步写入数据源`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)

        viewModel.setEnabled(true)

        assertTrue(viewModel.enabled.value)
        assertTrue(repository.enabled)
    }

    @Test
    fun `弹窗状态按请求显隐`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        val target = listOf(item(1L, "书源A"))

        viewModel.showDialog(RecycleBinDialogState.DeleteConfirm(target))
        assertEquals(RecycleBinDialogState.DeleteConfirm(target), viewModel.dialog.value)

        viewModel.dismissDialog()
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `选中切换全选与清理`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)

        viewModel.toggleSelected(1L)
        assertEquals(setOf(1L), viewModel.selectedIds.value)

        viewModel.toggleSelected(2L)
        assertEquals(setOf(1L, 2L), viewModel.selectedIds.value)

        viewModel.toggleSelected(1L)
        assertEquals(setOf(2L), viewModel.selectedIds.value)

        viewModel.setSelected(setOf(1L, 2L))
        assertEquals(setOf(1L, 2L), viewModel.selectedIds.value)

        viewModel.clearSelection()
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `列表刷新后剔除已失效的选中项`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        viewModel.setSelected(setOf(1L, 2L, 3L))

        viewModel.pruneInvalidSelection(listOf(item(1L, "书源A")))

        assertEquals(setOf(1L), viewModel.selectedIds.value)
    }

    @Test
    fun `冲突检测只要列表中存在同名源即为真`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        repository.conflictKeys += "key1"

        assertTrue(viewModel.hasConflict(listOf(item(1L, "书源A"), item(2L, "书源B"))))
        assertFalse(viewModel.hasConflict(listOf(item(2L, "书源B"))))
    }

    @Test
    fun `确认后恢复发还原事件并移除对应选中项`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        val target = listOf(item(1L, "书源A"))
        viewModel.setSelected(setOf(1L, 2L))

        viewModel.restore(target, overwrite = true)
        advanceUntilIdle()

        assertEquals(listOf(FakeRecycleBinRepository.Restored("key1", overwrite = true)), repository.restored)
        assertEquals(setOf(2L), viewModel.selectedIds.value)
        viewModel.toasts.test {
            assertEquals(R.string.source_recycle_bin_restored, awaitItem().msgRes)
        }
    }

    @Test
    fun `确认后删除发删除事件并移除对应选中项`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        val target = listOf(item(2L, "书源B"))
        viewModel.setSelected(setOf(1L, 2L))

        viewModel.delete(target)
        advanceUntilIdle()

        assertEquals(listOf("key2"), repository.deletedKeys)
        assertEquals(setOf(1L), viewModel.selectedIds.value)
        viewModel.toasts.test {
            assertEquals(R.string.source_recycle_bin_deleted, awaitItem().msgRes)
        }
    }

    @Test
    fun `清空回收站后选中集合并发清空事件`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SourceRecycleBinViewModel(repository)
        viewModel.setSelected(setOf(1L, 2L))

        viewModel.clearAll()
        advanceUntilIdle()

        assertTrue(repository.deleteAllCalled)
        assertTrue(viewModel.selectedIds.value.isEmpty())
        viewModel.toasts.test {
            assertEquals(R.string.source_recycle_bin_cleared, awaitItem().msgRes)
        }
    }
}

/**
 * 手写 Fake：只实现被测路径用到的方法，行为为真实数据迁移（照 FakeReadRecordDao 模式）
 */
private class FakeRecycleBinRepository(
    private val allItemsFlow: MutableStateFlow<List<SourceRecycleBin>>
) : RecycleBinRepository {

    data class Restored(val key: String, val overwrite: Boolean)

    override var enabled: Boolean = false
    var lastFlowType: String? = null
    val conflictKeys = mutableSetOf<String>()
    val restored = mutableListOf<Restored>()
    val deletedKeys = mutableListOf<String>()
    var deleteAllCalled = false
    var cleanupExpiredCalled = false

    override fun flowAll(): Flow<List<SourceRecycleBin>> {
        lastFlowType = null
        return allItemsFlow
    }

    override fun flowByType(type: String): Flow<List<SourceRecycleBin>> {
        lastFlowType = type
        return allItemsFlow
    }

    override suspend fun cleanupExpired() {
        cleanupExpiredCalled = true
    }

    override suspend fun hasConflict(item: SourceRecycleBin): Boolean = item.key in conflictKeys

    override suspend fun restore(item: SourceRecycleBin, overwrite: Boolean) {
        restored += Restored(item.key, overwrite)
    }

    override suspend fun delete(items: List<SourceRecycleBin>) {
        deletedKeys += items.map { it.key }
    }

    override suspend fun deleteAll() {
        deleteAllCalled = true
    }
}
