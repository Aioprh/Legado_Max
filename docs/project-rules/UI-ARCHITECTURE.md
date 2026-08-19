# Legado Max — Compose UI 架构规范

> **生效范围**：`io.legado.app.ui` 包及以下所有代码  
> **执行方式**：软约束 — Code Review 时人工对照本文档 Checklist，不达标 PR 打回  
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡  
> **最后更新**：2026-08-19

---

## 1. 目录结构规范

```
ui/
├── components/                     # 全局纯展示积木（跨 Feature 复用，无状态/无手势/无三方依赖，对外 API 稳定）〔目标态，首次落地时建〕
│   ├── AppTopBar.kt
│   ├── AppScaffold.kt
│   ├── AppListItem.kt
│   ├── AppButton.kt
│   ├── AppCard.kt
│   ├── AppEmptyState.kt
│   └── AppLoadingState.kt
├── widget/                         # 全局交互组件 & 第三方封装（带状态/手势/三方库依赖，区别于 components 的纯展示积木）
│   ├── components/                 # 新 Compose 通用组件一律进这里；可按业务域建子包归集（现有 card/ list/ dialog/ settings/ swipe/）
│   │   ├── AppImage.kt             # 图片加载统一入口（内部 Glide bitmap 链路，见 §7.3）〔目标态，尚未建立〕
│   │   ├── BookBottomSheet.kt
│   │   └── ...
│   ├── TitleBar.kt                 # ┐
│   ├── SearchView.kt               # │ XML 时代自定义 View 存量混存区：迁移期保留原位不动，
│   ├── ...                         # ┘ 禁止继续往这里新增（见下方硬规则）
│   ├── image/                      # 同上，存量子包（CoverImageView、PhotoView 等）
│   ├── dialog/
│   └── recycler/
├── theme/                          # 主题层：颜色、尺寸、圆角、动效参数的唯一定义处
│   ├── LegadoTheme.kt
│   ├── CommonPageColors.kt
│   ├── ComposeActivitySupport.kt
│   ├── Dimensions.kt               # 〔目标态，尚未建立，见 §7.2〕
│   ├── Shapes.kt                   # 〔目标态，尚未建立，见 §7.2〕
│   └── ...
├── config/                         # Feature 示例：配置域（book/、about/ 等其余 Feature 均按此模式组织）
│   ├── theme/
│   │   ├── manage/
│   │   │   ├── ThemeManageScreen.kt
│   │   │   ├── ThemeManageViewModel.kt
│   │   │   ├── ThemeManageUiState.kt
│   │   │   ├── ThemeRepository.kt
│   │   │   └── components/         # Feature 私有 UI 组件，多文件组件归集于此
│   │   │       ├── ThemeCard.kt
│   │   │       └── ThemeEditDialog.kt
│   │   └── ...                     # 其他子域（legacy 等）
│   ├── widget/                     # Config 模块级通用组件（跨子域复用，不出 config 包）
│   │   ├── ConfigManageScaffold.kt
│   │   └── ConfigMultiSelectBar.kt
│   └── ...
└── README.md
```

> 注：每个 Feature 内部还允许 `[Feature]/widget/`（模块级通用组件，如 `config/widget/`），以及更深层子域的 `components/`（Feature 私有），规则见下方硬规则。

### 硬规则

- **禁止**在 Screen 文件内定义 `private fun` 形式的可复用 UI 组件。Screen 只管编排，不造积木。
- Feature 内部**允许**存在 `components/` 子目录（如 `config/theme/manage/components/`），用于归集该 Feature 专属的多文件 UI 组件。
- **禁止**跨 Feature 引用 `ui/[feature]/*/components/` 下的组件，这些组件对外不保证 API 稳定。跨 Feature 复用的组件必须提升到 `ui/widget/components/` 或 `ui/[模块]/widget/`（如 `ui/config/widget/`）。
- **禁止**跨层引用：`ui/widget/`（含 `ui/widget/components/`）和 `ui/theme/` 不准引用任何 Feature 包（`ui/[feature]/*`）的类；Feature 包可以单向引用 `ui/widget/` 和 `ui/theme/`。
- `ui/widget/` 根目录是**存量混存区**（XML 时代自定义 View）。新 Compose 通用组件一律进 `ui/widget/components/`，禁止继续往根目录堆放；旧 View 迁移完成前保留原位置不动。

---

## 2. 文件命名规范

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| Screen（页面级 Composable） | `*Screen.kt` | `ReadRecordScreen.kt`、`ThemeManageScreen.kt` |
| ViewModel | `*ViewModel.kt` | `ThemeManageViewModel.kt` |
| Repository / DataSource | `*Repository.kt` / `*DataSource.kt` | `BookRepository.kt`、`ThemeRepository.kt` |
| Feature 私有组件 | `*Card.kt` / `*Dialog.kt` / `*Menu.kt` 语义命名 | `ThemeCard.kt` 而非 `ThemeComponents.kt` |
| 通用组件 | `*Sheet.kt` / `*Scaffold.kt` / 语义命名 | `BookBottomSheet.kt`、`ConfigManageScaffold.kt` |
| State 定义 | `*State.kt` / `*UiState.kt` | `ThemeManageUiState.kt`、`ConfigManageState.kt` |
| 工具函数扩展 | `*Utils.kt` / `*Extensions.kt` | `CodeViewExtensions.kt` |

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

- **必须**暴露 `StateFlow<UiState>`，Screen 通过 `collectAsStateWithLifecycle()` 消费（见 4.2）。
- **必须**一次性事件（Toast、跳转、分享）通过 `Channel<Event>` + `receiveAsFlow()` 向上抛给 Activity，禁止 ViewModel 直接持有 `Application` 调用 `toast`、`startActivity`。
- 缓冲区**必须**显式指定，禁止用默认 `RENDEZVOUS`（零容量，`trySend` 在接收方未等待时立即失败即静默丢事件）：
  - 默认用 `Channel<Event>(Channel.BUFFERED)`；
  - 天然允许"只留最新"的事件（Toast、导航跳转）可用 `Channel.BUFFERED(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` 或 `CONFLATED`，并在 Channel 定义处注释说明丢事件语义。
- **禁止** 在 ViewModel 里直接操作 `clipboardManager`、`startActivity`、`showDialog` 等平台 API。这些下沉到 Activity 或 UseCase。

### 4.1.1 Repository 数据流

- Repository 对外**必须**暴露 `Flow<T>`（如 `asFlow()` / `flow { emit(...) }`），禁止只提供一次性 `suspend fun fetchXxx(): List<T>` 的散装 API——数据流断在 Repository 门口，ViewModel 就只能手写刷新逻辑。
- ViewModel 用 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InitialState)` 将 `Flow` 收敛为 `StateFlow<UiState>`：

```kotlin
val uiState: StateFlow<ThemeManageUiState> = themeRepository.observeThemes()
    .catch { e ->
        // 日志必带异常对象，否则堆栈全丢
        AppLog.e("ThemeRepo", "observeThemes failed", e)
        emit(themeRepository.lastKnownThemes().copy(error = UiError.Unknown(e.message.orEmpty())))
    }
    .map { ThemeManageUiState(themes = it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeManageUiState())
```

- 流内异常在 Repository 或 ViewModel 的 `catch` 中转为 `UiError` 状态，**禁止** `throw` 穿透到 `viewModelScope` 导致进程崩溃。
- 一次性变更操作（增删改）仍用 `suspend fun`，ViewModel 内 `viewModelScope.launch` 调用，异常在 `try-catch` 中处理并更新 `UiState` + 抛 `Event`。

### 4.2 Screen 层

- Screen 只做三件事：收集状态、传回调给子组件、条件渲染 Dialog/BottomSheet。
- **必须**使用 `collectAsStateWithLifecycle()`（`lifecycle-runtime-compose`），禁止裸 `collectAsState()`——后台/切走页面后 StateFlow 持续 emit，日活千万级别的电量与 CPU 都白烧。
- **禁止** Screen 里调 `viewModel.xxx()` 后马上改本地 mutableStateOf（状态提升不够，逻辑乱飞）。
- **禁止** 在 `@Composable` 函数体里写超过 3 个 `var xxx by remember { mutableStateOf() }`。这种场景必须抽 State Holder。
- 需要**跨进程重建存活**的用户状态（搜索框输入、分页位置、选中项）必须 `rememberSaveable` / 自定义 `Saver`；`remember` 只管会话内，Android 杀进程重建后归零。不需要存活的瞬态（动画进度、拖拽坐标）用 `remember`。

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
- 文件头加注释：`// DI 降级原因：xxx，迁回 Hilt deadline：YYYY-MM-DD（#issue号）`——必须带**具体回填期限**，"后续"、"下个迭代"这类无期限表述一律视为未说明原因，PR 打回
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
- **必须** Repository / ViewModel 内每个 `catch` 分支打日志，且**必须传入异常对象**（`Log.e(TAG, msg, e)`）保留完整堆栈，并带可定位上下文：实体 ID、来源 URL、操作类型。`catch (e: Exception) { Log.e(TAG, "error") }` 这种无堆栈无上下文的写法按违规打回——线上出问题时别让我盲猜。

---

## 7. 主题与样式

### 7.1 颜色使用

- **必须**优先使用 `MaterialTheme.colorScheme.xxx` 获取色值。
- **禁止**直接调用 `colorResource(R.color.xxx)` 绕过主题系统。（例外：`Color.Transparent`、`Color.Black`、`Color.White` 等标准色允许直用）
- **禁止** 在 Composable 函数体内用 `Color(0xFFxxxxxx)` 硬编码，色值必须来自 `ThemeEntity` 或 `MaterialTheme`。

### 7.2 魔法数字

- 所有 dimens 必须集中定义在 `ui/theme/Dimensions.kt`（**目标态文件，当前尚未建立**，首次落地时创建并同步 §1 目录树；落地前新代码先把 dimens 就近定义在 `ui/theme/` 下，禁止散落各 Feature）。
- 所有 shapes 必须集中定义在 `ui/theme/Shapes.kt`（同上）。
- **禁止**在 Composable 体内裸写 `16.dp`、`12.dp`、`0.8f`。
- 动画参数（时长、easing）允许调用点就近写 `tween(200, FastOutSlowInEasing)` 这类标准写法，不强制集中；**禁止**自定义 `CubicBezier` / `keyframes` 曲线散落多处，新增自定义曲线必须集中定义在 `ui/theme/` 下的统一文件（如 `AnimationSpecs.kt`，**目标态文件，首次有自定义曲线时创建**）并注释用途。


```kotlin
// ui/theme/Dimensions.kt（目标态示例，落地时创建）
object AppDimens {
    val cardCornerRadius = 12.dp
    val listItemVerticalPadding = 12.dp
    val listItemHorizontalPadding = 16.dp
    val listItemIconSpacing = 16.dp
}

// ui/theme/Shapes.kt（同上）
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)
```

### 7.3 图片加载

> **技术选型：本项目图片框架统一为 Glide**（存量技术栈，全项目已依赖）。不引入 Coil / Fresco 等第二套框架；如未来要换，属于架构级决策，必须全局迁移，禁止单个 PR 局部混用。

- **必须**走 `ui/widget/components/` 下的统一封装组件（暂名 `AppImage.kt`，**目标态文件，当前尚未建立**，落地时创建并同步 §1 目录树）加载图片，内部用 Glide 的 bitmap 链路。封装组件落地前，新 Compose 代码按下述链路模式实现，禁止用 View 版 API 过渡：

```kotlin
Glide.with(context)
    .asBitmap()
    .load(source)
    .apply(RequestOptions().override(widthPx, heightPx)) // 显式尺寸，禁止全尺寸解码
    .into(pendingTarget)
```

- **必须**用自持的 `PendingTarget<Bitmap>` 承接结果并交给 `Image(bitmap)` 渲染，`DisposableEffect` 的 `onDispose` 里 `clear()` target 取消 in-flight 请求——页面滑走后 Glide 继续解码就是白烧内存和 CPU。
- **禁止**在 Composable / ViewModel 里手写 `withContext(Dispatchers.IO) { BitmapFactory.decode... }` 自己解码 bitmap 塞 `Image()`——缓存、采样率、请求去重、取消逻辑全要自己维护，纯造轮子。
- **禁止**在 Compose 层使用 View 版 API（`Glide.with(...).into(imageView)`）；老 XML 代码里的存量调用不动，新代码一律走上面的 bitmap 链路。

### 7.4 字体与排版

- **必须**通过 `MaterialTheme.typography.xxx` 拿字体。
- **禁止**在 Composable 内直接调 `FontFamily` 构建。

### 7.5 字符串资源规范

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

图片加载规范见 7.3。补充：**禁止**在 `LazyColumn` / `LazyRow` 的 item 中做 bitmap 缩放、圆角等像素级处理，此类变换全部交给 Glide 的 `RequestOptions`（`override` / `transform`）在 Glide 自己的解码线程完成，UI 线程只负责 blit。

---

## 9. 导航规范

- 路由路径**必须**集中定义（如 `object NavRoute`），**禁止**在调用点散落路由字符串字面量。
- 路由参数**必须**通过 `navArgument` + `NavType` 定义，Screen 统一解包成 `NavArgs` 数据类（见 §15 违规 D）后使用，**禁止**在 Screen 里直接 `savedStateHandle["xxx"]` 再手动转类型。
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

## 11. 组件拆分标准

### 11.1 何时拆分文件

满足下列任一条件即拆分：

1. 单个文件超过 **400 行**。
2. 逻辑过于复杂
3. 单个 `@Composable fun` 超过 **130 行**。
4. 文件里超过 **3 个** `@Composable fun` 且语义不相关（同为 `*Screen` 内部私有组件不算）。

### 11.2 何时抽取通用组件

- 同一种 UI 模式在 **2 个及以上** Feature 出现 → 抽到 `ui/widget/components/` 或 `ui/[模块]/widget/`。
- 同 Feature 内部的 `*Card`/`*Dialog`/`*Row` 只要**语义清晰**，即使只在本 Feature 内复用，也抽成独立文件（如 `ThemeCard.kt`），禁止堆在 `ThemeManageScreen.kt` 里用 `private fun` 实现。

---

## 12. 注释规范

### 12.1 原则
- **只写 "Why"，不写 "What"**。代码逻辑应自解释。

### 12.2 必须注释场景
- 业务决策与直觉相反时。
- 边界条件 / 防御性代码。
- 魔法数字来源。
- 暂时性 Hack。

### 12.3 KDoc 规范（公开 API 强制）
- `ui/widget/components/` 及 `ui/[模块]/widget/` 下跨 Feature 暴露的 `public` Composable **必须**编写 KDoc。
- 必须对所有非默认值参数进行说明。
- 示例：

```kotlin
/**
 * 列表页通用条目：图标 + 标题 + 可选副标题 + 尾部插槽。
 *
 * @param icon 条目主图标，未走 Material Icons 体系的资源请自行转 `ImageVector`。
 * @param title 主文案，超长由内部 ellipsis 处理，调用方无需截断。
 * @param subtitle 副标题，传 `null` 时条目自动收缩为单行高度。
 * @param onClick 整行点击回调，默认空实现表示纯展示条目。
 */
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null
)
```
  
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

## 14. Code Review Checklist（人工对照）

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
- [ ] `ui/widget/components/` 下新增组件是否附带 `@Preview`？
- [ ] `LazyColumn` / `LazyRow` / `LazyVerticalGrid` 的 `items()` 是否传入了 `key`？
- [ ] 列表类 `UiState` 数据类是否加了 `@Immutable` 或 `@Stable` 注解？
- [ ] 有无在 Composable 内硬编码中文字符串？（必须走 `stringResource`）
- [ ] Screen 状态收集是否用 `collectAsStateWithLifecycle()` 而非裸 `collectAsState()`？
- [ ] `Channel<Event>` 是否显式指定缓冲区（BUFFERED / CONFLATED）？用了默认 RENDEZVOUS = 违规。
- [ ] 需要跨进程重建存活的用户输入状态是否用了 `rememberSaveable`？
- [ ] 图片加载是否走统一的 Glide 封装组件（`AppImage` 等）+ 显式 `override` 尺寸？有无手写 decode 或第二套图片框架混入？
- [ ] `LazyColumn` / `LazyRow` item 里有无 bitmap 像素级处理（缩放/圆角）？
- [ ] 每个 `catch` 分支是否带异常对象 + 上下文的日志？
- [ ] 路由字符串是否集中定义？调用点有无散落的路由字面量 / `savedStateHandle` 裸读？

---

## 15. 附录：典型违规示例

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
// ❌ ThemeManageScreen 参数 11 个，超过 §14 Checklist 的 5 个上限
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