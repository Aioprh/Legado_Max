# Compose UI 规范 — 导航与 Preview

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §9、§10，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`theme-styles.md`（§7）、`performance.md`（§8）、`accessibility.md`（§15）、`testing.md`（§16）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 9. 导航规范

- 路由路径**必须**集中定义（如 `object NavRoute`），**禁止**在调用点散落路由字符串字面量。
- 路由参数**必须**通过 `navArgument` + `NavType` 定义，Screen 统一解包成 `NavArgs` 数据类（见 `migration-review.md` §17 违规 D）后使用，**禁止**在 Screen 里直接 `savedStateHandle["xxx"]` 再手动转类型。
- 回栈操作（跳指定页、关指定页）**必须**走统一的 `NavController` 扩展或路由管理器，**禁止**调用点散落 `popBackStack("xxx", false)` 字面量。
- 路由定义与 `NavArgs` **禁止**依赖 ViewModel / 数据层类，导航层保持可独立拆分。

---

## 10. Preview 规范

### 10.1 通用组件（强制）

- `ui/widget/components/` 下（跨 Feature 复用层）的**每个**公共 Composable **必须**附带至少一个 `@Preview`。
- Preview 命名格式：`{Composable名}Preview`，如 `AppListItemPreview`。
- **推荐**提供多状态 Preview（正常 / 禁用 / 空数据 / 长文本截断）。

```kotlin
@Preview(name = "Normal")
@Preview(name = "Long text", locale = "zh")
@Composable
private fun AppListItemPreview() {
    LegadoTheme {
        AppListItem(
            icon = Icons.Default.Book,
            title = "书源管理",
            subtitle = "已导入 23 个书源"
        )
    }
}
```

### 10.2 Screen 级（推荐）

- Screen 级 Composable **推荐**写 Preview，至少覆盖默认状态。
- 如果 Screen 依赖 ViewModel，用 fake data 手动构造 `UiState` 传入，禁止在 Preview 里调真实 Repository。

### 10.3 禁止项

- **禁止** Preview 函数设为 `public`。必须 `private`，它们不参与生产编译。
- **禁止** 在 Preview 里写业务逻辑。Preview 只负责渲染验证。

---