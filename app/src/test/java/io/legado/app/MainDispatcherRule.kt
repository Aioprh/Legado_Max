package io.legado.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 将 Dispatchers.Main 替换为测试调度器（testing.md §16.3）
 *
 * 用法：规则与 runTest 共用同一个调度器，保证 viewModelScope 与测试体共享虚拟时间：
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * @Test
 * fun xxx() = runTest(mainDispatcherRule.dispatcher) { ... }
 * ```
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
