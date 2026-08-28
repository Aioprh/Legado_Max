# Compose UI 规范 — 测试

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §16，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`theme-styles.md`（§7）、`performance.md`（§8）、`navigation-preview.md`（§9/10）、`accessibility.md`（§15）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 16. 测试规范

> 现状（2026-08-27 更新）：`ReadRecordRepositoryTest` 的模式已固化；`kotlinx-coroutines-test`/`turbine`/`mockk` 依赖已补，
> `MainDispatcherRule` 测试基建已就位，四个精准管理 ViewModel（回收站/文件/存储/下载）已按 §16.3 模板完成构造注入改造与单测。
> 存量 `runBlocking` 测试（如 `ReadRecordRepositoryTest`）迁移期保留，按 `migration-review.md` §13 三阶段策略随迭代清理。
>
> ~~首条 ViewModel 测试落地前，`gradle/libs.versions.toml` 需补三个依赖~~（已补）：`org.jetbrains.kotlinx:kotlinx-coroutines-test`、`app.cash.turbine:turbine`、`io.mockk:mockk`（与 coroutines 同版本线）。

### 16.1 测什么（分档，强制）

| 层 | 是否必须测 | 说明 |
|------|-----------|------|
| ViewModel（状态迁移 + 事件） | **必须** | UI 契约核心，测试成本低、回归价值最高。改了状态机没测试 = 拿线上用户当回归 |
| Repository（聚合/转换/导入导出） | **必须** | 存量 `ReadRecordRepositoryTest` 模式照抄 |
| 纯函数（解析/格式化/规则匹配） | **必须** | 无依赖，断言直给 |
| Composable UI 层 | **不强制** | Preview ≠ 测试（Preview 只在 IDE 跑，CI 里从不执行，编译通过只证明语法对）；`createComposeRule` 的维护成本高于收益，UI 回归靠集成验证 + 人工，不强推 |
| androidTest（JS 引擎 / HTTP 等真实环境） | **保留存量，不新增为主** | 慢 + 易 flaky，禁止进 PR CI，放 release 验证或手动触发 |

### 16.2 技术选型与禁令（强制）

- 测试框架统一 JUnit4（存量），**禁止**顺手迁 JUnit5——纯成本无收益，别在测试基建上刷存在感。
- 新单元测试**必须** `runTest`（虚拟时间 + 泄漏协程自动报错）；**禁止**在 ViewModel 测试里 `runBlocking`——不支持虚拟时间，泄漏的协程没人管，测试过了不代表没挂后台协程。存量 `runBlocking` 测试迁移期保留，随迭代清理。
- Flow / Channel 断言**必须** Turbine（`expectItem` / `expectComplete`）；**禁止**测试里手写 `for (x in flow)` 或 `launch { collect }` 收事件——异步竞态，测试本身变 flaky，然后有人 `@Ignore`，恶性循环起点。
- 协作者对象**优先手写 Fake**（照 `FakeReadRecordDao`：只实现被测路径用到的方法，返回真实构造的数据）；MockK 只用于无行为桩对象或验证调用次数；**禁止** `every { a.b.c().d() }` 深链桩——那是把生产代码的耦合原样搬进测试，重构时测试先碎。
- **禁止** JVM 单元测试依赖 Android 运行时（`android.util.Log`、`Context`、资源 ID）。现在**不引入** Robolectric。单测里摸到 Context = 分层设计有问题，正确做法是把依赖 Context 的逻辑收进接口或下沉，别用 Robolectric 把设计错误盖过去。
- **禁止** `@Ignore` 或注释掉失败测试绕过 CI。flaky 测试修；修不动的保留测试 + `TODO(#issue号): deadline YYYY-MM-DD` 并在 PR 描述里报出来。

### 16.3 ViewModel 测试模板（强制）

```kotlin
class ThemeManageViewModelTest {
    // Fake 只实现被测路径用到的接口方法
    private val repository = FakeThemeRepository(themes = listOf(theme))
    private val viewModel = ThemeManageViewModel(repository)

    @Test
    fun `复制主题发 CopyJson 事件且不改本地状态`() = runTest {
        // act
        viewModel.copyItem(theme)

        // 断言只认终态和已发生的事件
        viewModel.uiState.test {
            // stateIn(WhileSubscribed) 需要先有收集者才启动，这里即订阅即驱动
            assertEquals(false, awaitItem().loading)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertEquals(ThemeEvent.CopyJson(GSON.toJson(theme.config)), expectItem())
        }
    }
}
```

- 测试方法名用反引号 `` `条件_期望` `` 风格，写行为和期望，**不写实现细节**（`shouldCallTrySend` 这种名字三个月后没人读得懂，也换个实现就全红）。
- 断言**只认终态和事件**，禁止 `advanceTimeBy(100)` + 断言中间帧——那是测实现细节，别人改个 `debounce` 参数你的测试全红，然后你被骂。
- `stateIn(WhileSubscribed)` 的 StateFlow 启动依赖有收集者：测试里通过 Turbine 订阅（如上）或 `advanceUntilIdle()` 驱动收敛，别假设"构造完 ViewModel 状态就到位了"。
- 事件断言走 `viewModel.events` 的 Flow 暴露（`state-events.md` §4.1 的 `receiveAsFlow()` 同一条流）；断言"没发事件"用 `expectMostRecent` 超时语义，**禁止** `Thread.sleep` 后猜。

### 16.4 CI 接入

- JVM 单测（`:app:testAppMaxDebugUnitTest`）进 PR CI，红 = 不合，由 `.github/workflows/unit-test.yaml` 执行（test 分支 push/PR 触发），**禁止**以“CI 慢”为由降级为 warning。
- 覆盖率数字**禁止**当验收指标（数字是虚荣指标）；真实约束落在 Checklist：新增 ViewModel / 修改状态机的 PR 没有对应测试，直接打回。
- androidTest 留在 release 验证阶段，不进 PR CI。

---