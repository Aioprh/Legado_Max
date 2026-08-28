# Compose UI 规范 — 迁移、Review Checklist 与典型违规示例

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §13、§14、§17，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`theme-styles.md`（§7）、`performance.md`（§8）、`navigation-preview.md`（§9/10）、`accessibility.md`（§15）、`testing.md`（§16）。
> **最后更新**：2026-08-19

---

## 13. 老代码迁移规范（分阶段）

### 阶段一：标记（当前 Sprint）

对违反规范的存量代码做标记，不打回，但必须挂 annotation + TODO：

```kotlin
@Suppress("LegadoUiViolation")
@Composable
private fun ThemeAddBottomBar(...) { ... }
// TODO(#issue号): 迁移到 ui/config/widget/ConfigAddBottomBar.kt，统一 Dim. 和 Color
```

**要求**：`LegadoUiViolation` Suppress 必须伴随具体 TODO，否则 PR 打回。

### 阶段二：清理（次 Sprint）

- 每个 Feature 迭代时，顺手重构本 Feature 内标记过的老代码。
- 通用组件层（`ui/widget/components/`、各模块 `widget/`）优先清理。

### 阶段三：固化（第三 Sprint）

- 新增代码零容忍，禁止新增 `LegadoUiViolation` Suppress。
- 存量未清理的老代码持续挂 TODO 追踪。

---

## 14. Code Review Checklist

分两层：**机器项先行（CI 硬卡），人工项次之（Review）**。机器规则已把违规拦在构建阶段的，Reviewer 不重复检查。

### 14.1 [机器] CI 硬卡（违规 = 构建红，不依赖 Reviewer 心情）

| 规则 | 实现方式 |
|------|--------|
| 裸 `colorResource(R.color.xxx)`、硬编码 `Color(0xFF...)` | 自定义 lint 规则（白名单：`Color.Transparent` / 纯黑 / 纯白，见 `theme-styles.md` §7.1） |
| Composable 体内裸魔法数字 `16.dp`、`12.dp`、`0.8f` | 自定义 lint 规则（标准动画参数豁免，见 `theme-styles.md` §7.2） |
| Composable 的 `Modifier` 非第一参数 | Compose 官方 Lint Checks（`androidx.compose:compose-lint-checks` 依赖） |
| `GlobalScope`、构造器/Factory 之外 `new Repository()` | Detekt `ForbiddenMethodCall` + 自定义规则（`new Repository` 需 AST 级检测） |
| `collectAsState()` 替代 `collectAsStateWithLifecycle()` | 自定义 lint 规则 |
| `Channel<Event>` 用 RENDEZVOUS / BUFFERED | 自定义 lint 规则（仅允许 UNLIMITED / CONFLATED，见 `state-events.md` §4.1） |
| `Log.e` / `Log.w` 缺异常对象第三参数 | 自定义 lint 规则 |
| Composable 内硬编码中文字符串（含 `contentDescription` 参数，见 `theme-styles.md` §7.5 / `accessibility.md` §15.1） | lint 硬编码字符串检测（白名单：Log、TODO、Preview） |
| `tween` / `delay` 魔法数字时长不在三档（150/300/450）且未引用 `AnimationSpecs` 常量（`theme-styles.md` §7.6.1） | 自定义 lint 规则（常量引用豁免） |
| `rememberInfiniteTransition` 缺 `label` 参数（`theme-styles.md` §7.6.3） | 自定义 lint 规则 |
| `spring(...)` 调用点散落自定义 stiffness / dampingRatio（`theme-styles.md` §7.6.1） | 自定义 lint 规则（仅允许引用统一 SpringSpec 常量） |
| Screen 函数参数超过 5 个 | Detekt `LongParameterList`（按文件类型设阈值） |
| 阶段三起新增 `@Suppress("LegadoUiViolation")` | CI grep，直接挂 |
| 新增 `*ViewModel.kt` 但无对应 `*ViewModelTest.kt`（`testing.md` §16.4） | CI 脚本对比 src/test 路径 |

> 机器规则统一维护在 `tools/lint-rules/` 独立模块并纳入 CI。**修改本表的 PR 必须同步更新规则代码**，只改文档不改规则的一律视为"未落地"，打回。

### 14.2 [人工] Reviewer 对照（任一 ❌ 打回）

- [ ] 新 Screen 是否套了 `ConfigManageScaffold` / `AppScaffold` 等通用脚手架？还是裸 `Scaffold`？
- [ ] 回调命名是否 `onXxx` 风格？
- [ ] 有无新增 `private fun` 形式的可复用组件？（Screen 内私有且仅在当前文件使用两次以上 = 违规）
- [ ] ViewModel 是否直接调用 `clipboardManager` / `startActivity` / `toast`？
- [ ] 事件是否走 `Channel<Event>` 而非直接平台调用？消费侧是否用 `repeatOnLifecycle(STARTED)` 绑定，而非裸 launch？（`state-events.md` §4.4）
- [ ] Dialog / BottomSheet 是否由 `UiState` 条件渲染？有无 Dialog 被注册成导航路由、显隐开关散落 `mutableStateOf`？返回键拦截是否覆盖？（`state-events.md` §4.5）
- [ ] 新文件是否按目录结构规范落到了正确的包？
- [ ] 注释是否只解释了 "Why" 没有解释 "What"？
- [ ] `ui/widget/components/` 下新增组件是否附带 `@Preview`？
- [ ] `LazyColumn` / `LazyRow` / `LazyVerticalGrid` 的 `items()` 是否传入了 `key`？
- [ ] item 模型类是否加了 `@Immutable`，且标注的类无 `MutableList` / `MutableMap` 等可变集合字段？
- [ ] 需要跨进程重建存活的用户输入状态是否用了 `rememberSaveable`？
- [ ] 图片加载是否走统一的 Glide 封装组件（`AppImage` 等）+ 显式 `override` 尺寸？有无手写 decode 或第二套图片框架混入？
- [ ] `LazyColumn` / `LazyRow` item 里有无 bitmap 像素级处理（缩放/圆角）？
- [ ] 路由字符串是否集中定义？调用点有无散落的路由字面量 / `savedStateHandle` 裸读？
- [ ] 动画属性是否走 `graphicsLayer` / `Modifier.alpha` / `Modifier.scale`？有无逐帧动 layout 属性（`animateDpAsState` 改 height 之类）？（`theme-styles.md` §7.6.2）
- [ ] 无限动画离开 composition 是否即停？有无 `LaunchedEffect` 里手写帧循环？（`theme-styles.md` §7.6.3）
- [ ] 可交互 `Icon` 的 `contentDescription` 是否非空且走 `stringResource`？装饰图标是否用 `Image` 而非 `Icon`？（`accessibility.md` §15.1）
- [ ] 卡片 / 列表项组件根容器是否 `semantics(mergeDescendants = true)`？（`accessibility.md` §15.2）
- [ ] 可点击区域是否 ≥ 48×48 dp（小图标是否用 padding / `minimumInteractiveComponentSize` 撑命中区）？（`accessibility.md` §15.3）
- [ ] 有无硬编码 `.sp`？有无固定高度容器硬裁文字？（`accessibility.md` §15.4）
- [ ] 新增 ViewModel / 修改状态机的 PR 是否带对应单测？ViewModel 测试是否 `runTest` + Turbine，有无 `runBlocking` / 手写 collect / `Thread.sleep` / `@Ignore`？（`testing.md` §16）

---

## 17. 附录：典型违规示例

### 违规 A：Screen 内私有组件直接写死 dimens 和 R.color

```kotlin
// ❌ 违规
@Composable
private fun ThemeAddBottomBar(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp), // 魔法数字
        color = colorResource(R.color.background_add_button), // 绕过主题
        border = BorderStroke(1.dp, colorResource(R.color.border_add_button))
    ) { ... }
}
```

**正确做法**：

```kotlin
// ✅ 放到 ui/config/widget/ConfigAddBottomBar.kt，或至少用 AppDimens/MaterialTheme
Surface(
    shape = AppShapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
)
```

### 违规 B：跨 Feature 引用私有组件

```
❌ ui/config/shelf/ShelfScreen.kt
   import io.legado.app.ui.config.theme.manage.components.ThemeCard

// ThemeCard 是 config/theme/manage 的私有组件，book 包不能引用它
// 如果确实需要跨 Feature 复用，必须提升到 ui/widget/components/ 或 ui/config/widget/

✅ ui/config/shelf/ShelfScreen.kt
   import io.legado.app.ui.widget.components.ThemeCard   // 已提升到全局
   // 或
   import io.legado.app.ui.config.widget.ThemeCard   // 已提升到模块 widget
```

### 违规 C：ViewModel 直接持有平台服务

```kotlin
// ❌ 违规：ViewModel 直接写剪贴板
fun copyItem(item: ThemeItem) {
    val json = GSON.toJson(item.config)
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, json))
}
```

**正确做法**：

```kotlin
// ✅ ViewModel 只发事件
fun copyItem(item: ThemeItem) {
    _events.trySend(ThemeEvent.CopyJson(GSON.toJson(item.config)))
}

// ✅ Activity 收集事件，执行平台操作
when (event) {
    is ThemeEvent.CopyJson -> {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, event.json))
        toast("已复制")
    }
}
```

### 违规 D：Screen 函数参数超过 5 个

```kotlin
// ❌ ThemeManageScreen 参数 11 个，超过本文件 §14 Checklist 的 5 个上限
fun ThemeManageScreen(
    onBackClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportEmpty: () -> Unit,
    onImportFailed: () -> Unit,
    onSelectImage: () -> Unit,
    onShareJson: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onToast: (Int) -> Unit = {},
    onToastMsg: (String) -> Unit = {},
    onColorClick: (String, String) -> Unit = { _, _ -> },
    onBlurClick: (Int) -> Unit = {}
)
```

**正确做法**：抽 Navigation Contract 或 Args 数据类。

```kotlin
data class ThemeManageNavArgs(
    val onToast: (Int) -> Unit = {},
    val onToastMsg: (String) -> Unit = {},
    // ...
)

@Composable
fun ThemeManageScreen(
    viewModel: ThemeManageViewModel,
    onBackClick: () -> Unit,
    args: ThemeManageNavArgs
)
```

---