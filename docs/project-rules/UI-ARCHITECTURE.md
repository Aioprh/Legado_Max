# Legado Max — Compose UI 架构规范

> **生效范围**：`io.legado.app.ui` 包及以下所有代码  
> **执行方式**：软约束 — Code Review 时人工对照本文档 Checklist，不达标 PR 打回  
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡  
> **最后更新**：2026-08-12

---

## 1. 目录结构规范

```
ui/
├── components/                 # 全局通用 Compose 组件（跨 Feature 复用）
│   ├── AppTopBar.kt
│   ├── AppScaffold.kt
│   ├── AppListItem.kt
│   ├── AppButton.kt
│   ├── AppCard.kt
│   ├── AppEmptyState.kt
│   └── AppLoadingState.kt
├── widget/                     # 通用交互组件 & 第三方封装
│   ├── shimmer/
│   ├── swipe/
│   └── ConfigList.kt          # 配置页通用列表容器
├── theme/                      # 主题层
│   ├── MoRealmTheme.kt
│   ├── ColorParsers.kt
│   ├── ThemeColorMix.kt
│   ├── Dimensions.kt
│   └── Shapes.kt
├── [feature]/                  # 业务 Feature，每个 Feature 包含自己的 Screen/ViewModel/Repository
│   ├── config/
│   │   ├── theme/
│   │   │   ├── manage/
│   │   │   │   ├── ThemeManageScreen.kt
│   │   │   │   ├── ThemeManageViewModel.kt
│   │   │   │   ├── ThemeRepository.kt
│   │   │   │   ├── ThemeCard.kt         # Feature 私有 UI 组件，直接放 feature 包下
│   │   │   │   └── ThemeEditDialog.kt
│   │   │   └── widget/         # Config 模块通用组件（跨 Theme/Rule/BookSource）
│   │   │       ├── ConfigManageScaffold.kt
│   │   │       └── ConfigList.kt
│   │   └── ...
│   └── shelf/
│       ├── ShelfScreen.kt
│       └── ...
└── README.md
```

### 硬规则

- **禁止**在 Screen 文件内定义 `private fun` 形式的可复用 UI 组件。Screen 只管编排，不造积木。
- Feature 内部**允许**存在 `components/` 子目录（如 `theme/manage/components/`），用于归集该 Feature 专属的多文件 UI 组件。
- **禁止**跨 Feature 引用 `ui/[feature]/*/components/` 下的组件，这些组件对外不保证 API 稳定。跨 Feature 复用的组件必须提升到 `ui/components/` 或 `ui/[模块]/widget/`。
- **禁止**跨层引用：`ui/components/` 不准引用 `ui/feature/*` 任何类；`ui/feature/*` 可以单向引用 `ui/components/` 和 `ui/widget/`。

---

## 2. 文件命名规范

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| Screen（页面级 Composable） | `*Screen.kt` | `BookDetailScreen.kt` |
| ViewModel | `*ViewModel.kt` | `BookDetailViewModel.kt` |
| Repository / DataSource | `*Repository.kt` / `*DataSource.kt` | `BookRepository.kt` |
| Feature 私有组件 | `*Card.kt` / `*Dialog.kt` / `*Menu.kt` 语义命名 | `ThemeCard.kt` 而非 `ThemeComponents.kt` |
| 通用组件 | `App*.kt` / `*List.kt` / `*Scaffold.kt` | `AppListItem.kt` |
| State 定义 | `*State.kt` / `*UiState.kt` | `ThemeManageUiState.kt` |
| 工具函数扩展 | `*Utils.kt` / `*Extensions.kt` | `ColorParsers.kt` |

### 硬规则

- **禁止** `*Components.kt` 大杂烩文件。如果文件里超过 3 个 `@Composable fun` 且职责不同，必须拆分。
- **禁止** `*View.kt` 命名用于 Compose 时代码（那是 XML 时代的遗毒，看到直接改掉）。

---

## 3. Composable API 契约

### 3.1 参数顺序（强制）

```kotlin
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,          // 1. Modifier 永远是第一个参数
    icon: ImageVector,                      // 2. 样式属性
    title: String,                          // 3. 内容属性
    subtitle: String? = null,               // 4. 可选内容
    onClick: () -> Unit = {},               // 5. 回调
    trailing: @Composable (() -> Unit)? = null // 6. 尾部插槽
)
```

### 3.2 命名规则

- **必须**用 DSL 风格命名回调：`onXxx`、`onXxxChange`、`onXxxClick`。
- **禁止** `mOnClick`、`clickListener`、`listener`。
- **禁止** 单个参数命名 `data`、`item`、`config`——必须带领域语义：`theme` → `themeItem` 或 `themeConfig`；`book` → `bookItem` 或 `bookEntity`。

### 3.3 可见性

- **必须**加 `@Composable` 注解。
- **推荐** `internal` 可见性（禁止无意义 `public`）。
- **禁止** 在 `*Screen.kt` 里暴露 `private` 可复用组件给其他文件引用（物理上不可能，但别搞 `internal` 套 Screen 文件）。

---

## 4. 状态管理与事件

### 4.1 ViewModel 层

- **必须**暴露 `StateFlow<UiState>`，Screen 通过 `collectAsState()` 消费。
- **必须**一次性事件（Toast、跳转、分享）通过 `Channel<Event>()` + `receiveAsFlow()` 向上抛给 Activity，禁止 ViewModel 直接持有 `Application` 调用 `toast`、`startActivity`。
- **禁止** 在 ViewModel 里直接操作 `clipboardManager`、`startActivity`、`showDialog` 等平台 API。这些下沉到 Activity 或 UseCase。

### 4.2 Screen 层

- Screen 只做三件事：`collectAsState` 收状态、传回调给子组件、条件渲染 Dialog/BottomSheet。
- **禁止** Screen 里调 `viewModel.xxx()` 后马上改本地 mutableStateOf（状态提升不够，逻辑乱飞）。
- **禁止** 在 `@Composable` 函数体里写超过 3 个 `var xxx by remember { mutableStateOf() }`。这种场景必须抽 State Holder。

### 4.3 线程模型

- **必须** IO 操作通过 `withContext(Dispatchers.IO)`。
- **禁止** 在 Repository 或 ViewModel 里抛阻塞主线程的同步调用。
- **禁止** 使用 `GlobalScope` 启动协程。ViewModel 里用 `viewModelScope`，Screen 里用 `LaunchedEffect(key) { ... }`。

---

## 5. 依赖注入规范

> **约束级别：推荐（非强制）** — 理想情况下必须使用 Hilt，但某些构建环境、模块拆分阶段或历史遗留可能导致 Hilt 无法引入。此时允许降级为手动构造 + Factory 模式，但**必须**在文件头注释说明原因。

### 5.1 Hilt 用法

- **推荐** ViewModel 通过 `@HiltViewModel` 注入，Screen 中用 `hiltViewModel()` 获取。
- **推荐** Repository 通过 `@Inject` 构造注入，由 ViewModel 持有，禁止 ViewModel 内部 `new`。
- **推荐** 跨模块共享的 DataSource 通过 Hilt Module 的 `@Binds` 或 `@Provides` 绑定接口。

### 5.2 降级条件

当以下任一情况成立时，允许不使用 Hilt：
- 模块尚未接入 Hilt 插件（如独立编译的子模块）
- 构建环境 KSP/KAPT 冲突无法解决
- 老代码迁移过渡期，尚未完成 DI 改造

降级时**必须**：
- 文件头加注释：`// DI 降级原因：xxx，待 yyy 后迁回 Hilt`
- 手动构造的对象通过 Factory 模式管理，禁止散落 `object` 单例

### 5.3 禁止项

- **禁止** ViewModel 内部直接 `new Repository()`，即使不用 Hilt 也必须通过构造函数注入或 Factory。
- **禁止** 用 `object` 伪装单例替代 DI，这是全局可变状态，测试时无法替换。

---

## 6. 错误处理规范

### 6.1 UI 状态模型

- **必须** 在 `UiState` 中定义明确的错误状态字段：

```kotlin
data class ThemeManageUiState(
    val loading: Boolean = false,
    val themes: List<ThemeItem> = emptyList(),
    val error: UiError? = null  // 统一错误模型
)

sealed interface UiError {
    data class NetworkError(val message: String) : UiError
    data class DataError(val message: String) : UiError
    data class Unknown(val message: String) : UiError
}
```

### 6.2 错误展示策略

| 错误类型 | 展示方式 | 说明 |
|---------|---------|------|
| 网络异常 / 列表加载失败 | 全屏 `AppErrorState` | 占据内容区域，带重试按钮 |
| 单项操作失败（复制、导入） | `Snackbar` | 不打断用户当前操作 |
| 需要用户确认的错误 | `AlertDialog` | 如：导入冲突、数据覆盖 |
| 非阻塞性提示 | `Snackbar` | 如：已复制、已删除 |

### 6.3 硬规则

- **禁止** 在 Composable 里直接 `try-catch` 网络请求。异常在 Repository 层捕获，转成 `Result` 或 sealed class 传上来。
- **禁止** 用 `Log.e` 代替用户可见的错误反馈。日志是给开发看的，UI 必须给用户反馈。
- **必须** 错误状态可恢复：`AppErrorState` 必须提供重试回调，不能只展示错误不给出路。

---

## 7. 主题与样式

### 7.1 颜色使用

- **必须**优先使用 `MaterialTheme.colorScheme.xxx` 获取色值。
- **禁止**直接调用 `colorResource(R.color.xxx)` 绕过主题系统。（例外：`Color.Transparent`、`Color.Black`、`Color.White` 等标准色允许直用）
- **禁止** 在 Composable 函数体内用 `Color(0xFFxxxxxx)` 硬编码，色值必须来自 `ThemeEntity` 或 `MaterialTheme`。

### 7.2 魔法数字

- 所有 dimens 必须集中定义在 `ui/theme/Dimensions.kt`。
- 所有 shapes 必须集中定义在 `ui/theme/Shapes.kt`。
- 动画曲线必须定义在 `ui/theme/AnimationSpecs.kt`。
- **禁止**在 Composable 体内裸写 `16.dp`、`12.dp`、`0.8f`、`cubic-bezier(0.16, 1, 0.3, 1)`。

```kotlin
// ui/theme/Dimensions.kt
object AppDimens {
    val cardCornerRadius = 12.dp
    val listItemVerticalPadding = 12.dp
    val listItemHorizontalPadding = 16.dp
    val listItemIconSpacing = 16.dp
}

// ui/theme/Shapes.kt
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)
```

### 7.3 字体与排版

- **必须**通过 `MaterialTheme.typography.xxx` 拿字体。
- **禁止**在 Composable 内直接调 `FontFamily` 构建。

### 7.4 字符串资源规范

- **必须**所有用户可见文案通过 `stringResource(R.string.xxx)` 获取，禁止在 Composable 内硬编码中文字符串。
- **禁止**在 ViewModel 里拼接展示文案（如 `"已复制" + item.name`）。ViewModel 只发数据，文案拼接在 UI 层完成。
- **推荐** string resource 命名保持简洁可读，允许使用缩写或分层前缀（用斜杠表示层级分组），示例：`theme_title`、`theme_card_delete_confirm`、`config/theme/list_empty`。
- **例外**：纯调试用的 `Log` 消息、`TODO` 注释中的文案不需要走 string resource。

```kotlin
// ❌ 违规
Text("已复制 ${theme.name}")

// ✅ 正确
Text(stringResource(R.string.theme_copied, theme.name))
```

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
- **必须**对列表类 `UiState` 数据类加 `@Immutable` 注解，避免 Compose 编译器跳过 stability 推断走保守策略导致多余 Recomposition：

```kotlin
@Immutable
data class ThemeItem(
    val id: String,
    val name: String,
    val config: ThemeConfig
)
```

- **推荐**当 Composable 参数包含 `List<T>` 且 T 本身可变时，用 `@Stable` 标注或包装为 `ImmutableList`。

### 8.2 状态读取优化

- **推荐**用 `derivedStateOf` 避免中间状态触发多余 Recomposition：

```kotlin
val showEmpty by remember {
    derivedStateOf { themes.isEmpty() && !loading }
}
```

- **禁止**在 `@Composable` 体内直接读 `StateFlow.value`。必须用 `collectAsState()`，否则状态变化不会触发 Recomposition。

### 8.3 副作用

- **必须** `LaunchedEffect` 的 `key` 必须精准：用业务 ID 做 key，不要用 `Unit` 或常量，否则要么不执行要么死循环。
- **禁止**在 `@Composable` 体内直接启动协程（`scope.launch {}`）。用 `LaunchedEffect` 或 `rememberCoroutineScope()`。
- **必须** `DisposableEffect` 在 `onDispose` 中清理资源（注册的 listener、callback）。

---

## 9. Preview 规范

### 9.1 通用组件（强制）

- `ui/components/` 和 `ui/widget/` 下的**每个**公共 Composable **必须**附带至少一个 `@Preview`。
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

### 9.2 Screen 级（推荐）

- Screen 级 Composable **推荐**写 Preview，至少覆盖默认状态。
- 如果 Screen 依赖 ViewModel，用 fake data 手动构造 `UiState` 传入，禁止在 Preview 里调真实 Repository。

### 9.3 禁止项

- **禁止** Preview 函数设为 `public`。必须 `private`，它们不参与生产编译。
- **禁止** 在 Preview 里写业务逻辑。Preview 只负责渲染验证。

---

## 10. 组件拆分标准

### 10.1 何时拆分文件

满足下列任一条件即拆分：

1. 单个文件超过 **400 行**。
2. 逻辑过于复杂
3. 单个 `@Composable fun` 超过 **130 行**。
4. 文件里超过 **3 个** `@Composable fun` 且语义不相关（同为 `*Screen` 内部私有组件不算）。

### 10.2 何时抽取通用组件

- 同一种 UI 模式在 **2 个及以上** Feature 出现 → 抽到 `ui/components/` 或 `ui/widget/`。
- 同 Feature 内部的 `*Card`/`*Dialog`/`*Row` 只要**语义清晰**，即使只在本 Feature 内复用，也抽成独立文件（如 `ThemeCard.kt`），禁止堆在 `ThemeManageScreen.kt` 里用 `private fun` 实现。

---

## 11. 注释规范

### 11.1 原则
- **只写 "Why"，不写 "What"**。代码逻辑应自解释。

### 11.2 必须注释场景
- 业务决策与直觉相反时。
- 边界条件 / 防御性代码。
- 魔法数字来源。
- 暂时性 Hack。

### 11.3 KDoc 规范（公开 API 强制）
- `ui/components/` 下的 `public` Composable **必须**编写 KDoc。
- 必须对所有非默认值参数进行说明。
- 示例：
  
---

## 12. 老代码迁移规范（分阶段）

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
- 通用组件层（`ui/components/`、`ui/widget/`）优先清理。

### 阶段三：固化（第三 Sprint）

- 新增代码零容忍，禁止新增 `LegadoUiViolation` Suppress。
- 存量未清理的老代码持续挂 TODO 追踪。

---

## 13. Code Review Checklist（人工对照）

Reviewer 逐条打勾，任一 ❌ 打回：

- [ ] 新 Screen 是否套了 `ConfigManageScaffold` / `AppScaffold` 等通用脚手架？还是裸 `Scaffold`？
- [ ] 有无裸写 `colorResource(R.color.xxx)`？（允许场景：完全不在主题系统内的透明色、纯黑纯白）
- [ ] 有无裸写 `16.dp`、`12.dp`、`0.8f` 等魔法数字？
- [ ] 新增 Composable 函数是否 `Modifier` 为第一个参数？
- [ ] 回调命名是否 `onXxx` 风格？
- [ ] 有无新增 `private fun` 形式的可复用组件？（Screen 内私有且仅在当前文件使用两次以上 = 违规）
- [ ] ViewModel 是否直接调用 `clipboardManager` / `startActivity` / `toast`？
- [ ] 事件是否走 `Channel<Event>` 而非直接平台调用？
- [ ] 新文件是否按目录结构规范落到了正确的包？
- [ ] 注释是否只解释了 "Why" 没有解释 "What"？
- [ ] Screen 函数参数是否超过 5 个？超过则必须抽 Args 数据类。
- [ ] `ui/components/` 和 `ui/widget/` 下新增组件是否附带 `@Preview`？
- [ ] `LazyColumn` / `LazyRow` / `LazyVerticalGrid` 的 `items()` 是否传入了 `key`？
- [ ] 列表类 `UiState` 数据类是否加了 `@Immutable` 或 `@Stable` 注解？
- [ ] 有无在 Composable 内硬编码中文字符串？（必须走 `stringResource`）

---

## 14. 附录：典型违规示例

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

// ThemeCard 是 theme/manage 的私有组件，shelf 不能引用它
// 如果确实需要跨 Feature 复用，必须提升到 ui/components/ 或 ui/config/widget/

✅ ui/config/shelf/ShelfScreen.kt
   import io.legado.app.ui.components.ThemeCard   // 已提升到全局
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

### 违规 D：Screen 函数参数超过 8 个

```kotlin
// ❌ ThemeManageScreen 参数 8 个，严重超标
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