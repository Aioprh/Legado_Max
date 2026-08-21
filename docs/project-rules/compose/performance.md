# Compose UI 规范 — 性能

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §8，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`theme-styles.md`（§7）、`navigation-preview.md`（§9/10）、`accessibility.md`（§15）、`testing.md`（§16）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 8. 性能规范

### 8.1 Recomposition 防范

- **必须** `LazyColumn` / `LazyRow` / `LazyVerticalGrid` 的 `items()` 传入 `key` 参数：

```kotlin
LazyColumn {
    items(themes, key = { it.id }) { theme ->
        ThemeCard(theme)
    }
}
```

- **禁止**在 `@Composable` 函数体内做重计算（排序、过滤、格式化大列表）。这些操作**必须**在 ViewModel 或 `remember` + `derivedStateOf` 中完成。
- **必须**对**item 模型类**（列表元素，如 `ThemeItem`）加 `@Immutable` 注解。字段类型含泛型或三方库类型时，编译器 stability 推断可能降级为保守策略，导致多余 Recomposition。`UiState` 本身**不需要**标注——它的稳定性由组件属性类型决定：

```kotlin
@Immutable
data class ThemeItem(
    val id: String,
    val name: String,
    val config: ThemeConfig
)
```

- **禁止**对含可变集合字段（`MutableList` / `MutableMap` 等）的类标注 `@Immutable`。`@Immutable` 是对编译器的保证，标注后编译器跳过运行时检查，可变内容变化不触发重组，直接是线上 bug。

- **推荐**当 Composable 参数包含 `List<T>` 且 T 本身可变时，用 `@Stable` 标注或包装为 `ImmutableList`。

### 8.2 状态读取优化

- **推荐**用 `derivedStateOf` 避免中间状态触发多余 Recomposition：

```kotlin
val showEmpty by remember {
    derivedStateOf { themes.isEmpty() && !loading }
}
```

- **禁止**在 `@Composable` 体内直接读 `StateFlow.value`。必须用 `collectAsStateWithLifecycle()`，否则状态变化不会触发 Recomposition。

### 8.3 副作用

- `LaunchedEffect` 的 `key` 分场景，禁止一刀切：
  - **一次性副作用**（订阅、初始化、拉取首屏数据）：**必须**用 `LaunchedEffect(Unit)`，composition 成立时执行一次，离开 composition 自动取消。
  - **需跟随状态变化重跑的副作用**（如 `LaunchedEffect(bookId) { refresh() }`）：用稳定的业务 ID / 值做 key。
  - **禁止**把每次 recomposition 都是新实例的对象做 key（如 `viewModel.uiState`、`list` 引用）——key 永远"变化"，副作用反复 cancel + 重启，表现为加载闪断、请求风暴。
  - **禁止**用会变化的状态做 key 又期望"只执行一次"，要么改 `Unit`，要么把触发条件提到 ViewModel。
- **禁止**在 `@Composable` 体内直接启动协程（`scope.launch {}`）。用 `LaunchedEffect` 或 `rememberCoroutineScope()`。
- **必须** `DisposableEffect` 在 `onDispose` 中清理资源（注册的 listener、callback）。

### 8.4 图片与内存

图片加载规范见 `theme-styles.md` §7.3。补充：**禁止**在 `LazyColumn` / `LazyRow` 的 item 中做 bitmap 缩放、圆角等像素级处理，此类变换全部交给 Glide 的 `RequestOptions`（`override` / `transform`）在 Glide 自己的解码线程完成，UI 线程只负责 blit。

---
