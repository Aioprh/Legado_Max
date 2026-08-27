package io.legado.app.ui.file

import app.cash.turbine.test
import io.legado.app.MainDispatcherRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 文件管理 ViewModel 状态迁移测试（testing.md §16：runTest + Turbine，真实临时目录驱动）
 */
class FileManageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var rootDir: File
    private lateinit var subDir: File

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("file_manage_test").toFile()
        subDir = File(rootDir, "sub").apply { mkdir() }
        File(rootDir, "b.txt").createNewFile()
        File(rootDir, "a.txt").createNewFile()
        File(subDir, "inner.txt").createNewFile()
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    private fun newViewModel() = FileManageViewModel(rootDoc = rootDir)

    @Test
    fun `根目录加载时文件夹优先且同名按名称排序`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("sub", "a.txt", "b.txt"),
            viewModel.files.value.map { it.name }
        )
        assertTrue(viewModel.subDocsFlow.value.isEmpty())
        assertEquals(rootDir, viewModel.lastDir)
    }

    @Test
    fun `进入子目录后首项为上级目录`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.enterDir(subDir)
        advanceUntilIdle()

        assertEquals(listOf(subDir), viewModel.subDocsFlow.value)
        assertEquals(subDir, viewModel.lastDir)
        assertEquals(
            listOf(subDir, File(subDir, "inner.txt")),
            viewModel.files.value
        )
    }

    @Test
    fun `返回上级目录后回到根目录列表`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.enterDir(subDir)
        advanceUntilIdle()

        viewModel.gotoLastDir()
        advanceUntilIdle()

        assertTrue(viewModel.subDocsFlow.value.isEmpty())
        assertEquals(
            listOf("sub", "a.txt", "b.txt"),
            viewModel.files.value.map { it.name }
        )
    }

    @Test
    fun `路径跳转按索引截断面包屑`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.enterDir(subDir)
        advanceUntilIdle()
        // 再深一层：sub/sub2
        val deepDir = File(subDir, "sub2").apply { mkdir() }
        viewModel.enterDir(deepDir)
        advanceUntilIdle()
        assertEquals(listOf(subDir, deepDir), viewModel.subDocsFlow.value)

        viewModel.goToPath(0)
        advanceUntilIdle()

        assertEquals(listOf(subDir), viewModel.subDocsFlow.value)
        assertEquals(subDir, viewModel.files.value.first())
    }

    @Test
    fun `返回根目录清空面包屑`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.enterDir(subDir)
        advanceUntilIdle()

        viewModel.goToRoot()
        advanceUntilIdle()

        assertTrue(viewModel.subDocsFlow.value.isEmpty())
        assertEquals(
            listOf("sub", "a.txt", "b.txt"),
            viewModel.files.value.map { it.name }
        )
    }

    @Test
    fun `搜索只保留名称包含关键词的条目`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("a")
        assertEquals(listOf("a.txt"), viewModel.files.value.map { it.name })

        // 清空关键词恢复全量
        viewModel.updateSearchQuery("")
        assertEquals(
            listOf("sub", "a.txt", "b.txt"),
            viewModel.files.value.map { it.name }
        )
    }

    @Test
    fun `切换目录会重置搜索关键词`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.updateSearchQuery("a")

        viewModel.enterDir(subDir)
        advanceUntilIdle()

        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `删除弹窗按请求显隐`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = File(rootDir, "a.txt")

        viewModel.requestDelete(target)
        assertEquals(FileDialogState.DeleteConfirm(target), viewModel.uiState.value.dialog)

        viewModel.dismissDialog()
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `确认删除后文件被移除并刷新列表`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = File(rootDir, "a.txt")
        viewModel.requestDelete(target)

        viewModel.confirmDelete(target)
        advanceUntilIdle()

        assertFalse(target.exists())
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf("sub", "b.txt"), viewModel.files.value.map { it.name })
    }

    @Test
    fun `打开文件抛出 OpenFile 事件`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = File(rootDir, "a.txt")

        viewModel.openFile(target)

        viewModel.events.test {
            assertEquals(FileManageEvent.OpenFile(target), awaitItem())
        }
    }

    @Test
    fun `外部路径跳转定位到文件所在目录`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.openPath(File(subDir, "inner.txt").absolutePath)
        advanceUntilIdle()

        assertEquals(listOf(subDir), viewModel.subDocsFlow.value)
        assertEquals(subDir, viewModel.files.value.first())
    }
}
