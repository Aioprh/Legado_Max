# Compose UI 规范 — 无障碍

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §15，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`theme-styles.md`（§7）、`performance.md`（§8）、`navigation-preview.md`（§9/10）、`testing.md`（§16）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 15. 无障碍规范

> 现状：`contentDescription` 覆盖面尚可，但**硬编码中文描述散落**（`"返回"`、`"刷新"`、`"更多"` 等，违反 `theme-styles.md` §7.5 口径）、**可点击图标 `contentDescription = null` 未区分装饰/遗漏**、**`semantics` 全库零使用**（卡片对 TalkBack 是碎裂的多节点）。本节收口，新增代码零容忍，存量按 `migration-review.md` §13 三阶段策略随迭代清理。

### 15.1 图标描述（强制）

- 可交互 `Icon`（挂 `clickable` / `toggleable` 等）：`contentDescription` **必须**非空且走 `stringResource`，禁止硬编码中文（并入 `theme-styles.md` §7.5，机器检测覆盖，见 `migration-review.md` §14.1）。
- 状态切换图标**必须**在描述里带状态：`if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)`（存量 `BlockRuleConfigDialog` 已合规，固化）。
- 纯装饰图标（无语义、无操作）：用 `Image` 替代 `Icon`，`contentDescription` 保持 `null`。`Icon` 本身是控件语义，装饰场景用它是错的。

### 15.2 语义合并 `semantics`（强制）

- 卡片 / 列表项组件（`ThemeCard`、`BookCacheItemCard`、`CacheItemCard` 等）**必须** `Modifier.semantics(mergeDescendants = true)`，把封面 + 标题 + 副标题 + 操作合并为单一焦点节点——用户焦点落在卡片上一次性读完整信息，不用在四五个节点间逐个滑。

```kotlin
Row(
    // 合并为单一 TalkBack 节点：焦点落在卡片上，一次性读出书名+状态，
    // 避免用户焦点在封面、书名、按钮之间逐个滑动
    modifier = Modifier.semantics(mergeDescendants = true)
) { ... }
```

- 合并层级定在**组件根容器**，子 `Text` / `Icon` 不单独加 `clearAndSetSemantics`——内层清语义会让外层 merge 拿不到内容。
- 需要整行读但**不合并**的场景（如带独立操作按钮的行）用 `isTraversalGroup = true`，保持子节点独立焦点但限制滑动越界。

### 15.3 触控目标（强制）

- 可点击区域**最小 48×48 dp**（Material 标准）。
- 小图标按钮（`size(18.dp)` 的编辑/删除等，存量 `BlockRuleConfigDialog` 就有）：图标保持视觉尺寸，外层 `Modifier.padding` 撑出命中区，或 Compose 1.7+ 用 `Modifier.minimumInteractiveComponentSize()`（1.6 及以下用 padding 写法，按项目当前依赖版本定）。
- **禁止**靠缩小 `clip` 区域来"精确命中"——缩小触控区是反向优化，误触率直接上去。

### 15.4 字体缩放（强制）

- 禁止 Composable 内硬编码 `.sp`，全部走 `MaterialTheme.typography`（`theme-styles.md` §7.4 口径，本节重申：系统"显示大小"拉到 200% 时裸 `.sp` 不跟随缩放，布局直接碎）。
- 允许 `maxLines` + `overflow` 截断，但**禁止**配合固定高度容器（`Modifier.height(20.dp)` 之类）硬裁文字——放大场景下文字溢出容器且用户看不到截断提示。

### 15.5 自绘 View 无障碍（强制）

- 所有 `AndroidView` 包装的自绘控件**必须**显式设置 `contentDescription`，或自绘 View 内实现 `onInitializeAccessibilityNodeInfo` 提供角色 + 文本。默认空 = TalkBack 完全静音 = 不可用。
- 存量参照：`PageView.kt` 的 `contentTextView.contentDescription = content` 已做对，新自绘控件不允许倒退。

### 15.6 系统"减少动态效果"

- 见 `theme-styles.md` §7.6.4：`ANIMATION_SCALE < 0.5` 时装饰性动画归零。此处不重复，`LocalAnimationScale` 的 `ContentObserver` 清理也一并要求：`DisposableEffect` 反注册，禁止进程级裸注册不反注册。

---