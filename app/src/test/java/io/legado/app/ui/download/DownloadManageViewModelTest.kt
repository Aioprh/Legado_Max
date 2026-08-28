package io.legado.app.ui.download

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.legado.app.MainDispatcherRule
import io.legado.app.R
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 下载管理 ViewModel 状态迁移测试（testing.md §16：runTest + Turbine + 手写 Fake）
 * 注意：轮询为 while(true) + delay(500)，测试中用 runCurrent/advanceTimeBy 驱动，
 * 禁止 advanceUntilIdle（虚拟时间下会无限推进）；
 * VM 经 [withViewModel] 创建，测试体结束时 clear() 触发 onCleared 停止轮询，
 * 否则无限循环的 delay 会让 runTest 无法收敛而超时
 */
class DownloadManageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val taskSource = FakeDownloadTaskSource()
    private val commander = FakeDownloadCommander()
    private val viewModelStore = ViewModelStore()

    /** 经 ViewModelStore 创建，便于结束时 clear() 触发 onCleared 停轮询 */
    private fun newViewModel(): DownloadManageViewModel =
        ViewModelProvider(
            viewModelStore,
            viewModelFactory {
                initializer {
                    DownloadManageViewModel(taskSource, commander) { "/downloads" }
                }
            }
        )[DownloadManageViewModel::class.java]

    private suspend fun TestScope.withViewModel(
        block: suspend (DownloadManageViewModel) -> Unit
    ) {
        val viewModel = newViewModel()
        try {
            block(viewModel)
        } finally {
            viewModelStore.clear()
        }
    }

    private fun task(
        id: Long,
        status: DownloadStatus,
        fileName: String = "file$id.epub"
    ) = DownloadTask(
        id = id,
        url = "https://example.com/$id",
        fileName = fileName,
        notificationId = id.toInt(),
        startTime = 0L,
        status = status
    )

    @Test
    fun `轮询启动后任务列表来自数据源`() = runTest(mainDispatcherRule.dispatcher) {
        taskSource.tasks = listOf(task(1L, DownloadStatus.RUNNING))
        withViewModel { viewModel ->
            runCurrent()

            viewModel.tasks.test {
                assertEquals(taskSource.tasks, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `轮询周期到达后刷新任务状态`() = runTest(mainDispatcherRule.dispatcher) {
        taskSource.tasks = listOf(task(1L, DownloadStatus.RUNNING))
        withViewModel { viewModel ->
            runCurrent()

            taskSource.tasks = listOf(task(1L, DownloadStatus.SUCCESSFUL))
            advanceTimeBy(500)
            runCurrent()

            assertEquals(DownloadStatus.SUCCESSFUL, viewModel.tasks.value.single().status)
        }
    }

    @Test
    fun `Tab 切换按状态过滤任务`() = runTest(mainDispatcherRule.dispatcher) {
        taskSource.tasks = listOf(
            task(1L, DownloadStatus.RUNNING),
            task(2L, DownloadStatus.PAUSED),
            task(3L, DownloadStatus.SUCCESSFUL),
            task(4L, DownloadStatus.FAILED)
        )
        withViewModel { viewModel ->
            runCurrent()

            viewModel.filteredTasks.test {
                // stateIn 首次订阅先发射初始值，跳过后才是 combine 真实结果
                skipItems(1)
                assertEquals(4, awaitItem().size)

                viewModel.selectTab(DownloadTab.DOWNLOADING)
                assertEquals(listOf(1L), awaitItem().map { it.id })

                viewModel.selectTab(DownloadTab.PAUSED)
                assertEquals(listOf(2L), awaitItem().map { it.id })

                viewModel.selectTab(DownloadTab.COMPLETED)
                assertEquals(listOf(3L), awaitItem().map { it.id })

                viewModel.selectTab(DownloadTab.FAILED)
                assertEquals(listOf(4L), awaitItem().map { it.id })

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `取消与重试转发给指令执行器`() = runTest(mainDispatcherRule.dispatcher) {
        withViewModel { viewModel ->
            runCurrent()

            viewModel.cancelDownload(1L)
            viewModel.retryDownload(2L)

            assertEquals(listOf(1L), commander.cancelled)
            assertEquals(listOf(2L), commander.retried)
        }
    }

    @Test
    fun `清除已完成只移除成功与失败任务`() = runTest(mainDispatcherRule.dispatcher) {
        taskSource.tasks = listOf(
            task(1L, DownloadStatus.RUNNING),
            task(2L, DownloadStatus.SUCCESSFUL),
            task(3L, DownloadStatus.FAILED),
            task(4L, DownloadStatus.PAUSED)
        )
        withViewModel { viewModel ->
            runCurrent()

            viewModel.clearCompletedTasks()

            assertEquals(listOf(2L, 3L), taskSource.removed)
        }
    }

    @Test
    fun `清除所有任务转发给指令执行器`() = runTest(mainDispatcherRule.dispatcher) {
        withViewModel { viewModel ->
            runCurrent()

            viewModel.clearAllTasks()

            assertTrue(commander.clearAllCalled)
        }
    }

    @Test
    fun `打开文件与文件夹抛出对应事件`() = runTest(mainDispatcherRule.dispatcher) {
        withViewModel { viewModel ->
            runCurrent()

            viewModel.openFile(9L)
            viewModel.openFolder()

            viewModel.events.test {
                assertEquals(DownloadEvent.OpenFile(9L), awaitItem())
                assertEquals(DownloadEvent.OpenFolder, awaitItem())
            }
        }
    }

    @Test
    fun `复制路径拼接下载目录并发提示事件`() = runTest(mainDispatcherRule.dispatcher) {
        taskSource.tasks = listOf(task(1L, DownloadStatus.SUCCESSFUL, fileName = "book.epub"))
        withViewModel { viewModel ->
            runCurrent()

            viewModel.copyPath(1L)

            viewModel.events.test {
                assertEquals(DownloadEvent.CopyPath("/downloads/book.epub"), awaitItem())
            }
            viewModel.toasts.test {
                assertEquals(R.string.download_path_copied, awaitItem().msgRes)
            }
        }
    }

    @Test
    fun `任务不存在时复制路径不发事件`() = runTest(mainDispatcherRule.dispatcher) {
        withViewModel { viewModel ->
            runCurrent()

            viewModel.copyPath(404L)

            viewModel.events.test {
                expectNoEvents()
            }
            viewModel.toasts.test {
                expectNoEvents()
            }
        }
    }
}

/**
 * 手写 Fake：内存任务表，只实现被测路径用到的方法（照 FakeReadRecordDao 模式）
 */
private class FakeDownloadTaskSource : DownloadTaskSource {

    var tasks: List<DownloadTask> = emptyList()
    val removed = mutableListOf<Long>()

    override fun queryAllTaskStatus(): List<DownloadTask> = tasks

    override fun getTask(id: Long): DownloadTask? = tasks.firstOrNull { it.id == id }

    override fun removeTask(id: Long) {
        removed += id
        tasks = tasks.filterNot { it.id == id }
    }
}

private class FakeDownloadCommander : DownloadCommander {

    val cancelled = mutableListOf<Long>()
    val retried = mutableListOf<Long>()
    var clearAllCalled = false

    override fun cancelDownload(id: Long) {
        cancelled += id
    }

    override fun retryDownload(id: Long) {
        retried += id
    }

    override fun clearAllTasks() {
        clearAllCalled = true
    }
}
