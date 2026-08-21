# Compose UI 规范 — 状态管理、事件与错误处理

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §4、§5、§6，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`theme-styles.md`（§7）、`performance.md`（§8）、`navigation-preview.md`（§9/10）、`accessibility.md`（§15）、`testing.md`（§16）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 4. 状态管理与事件

### 4.1 ViewModel 层

- **必须**暴露 `StateFlow<UiState>`，Screen 通过 `collectAsStateWithLifecycle()` 消费（见 4.2）。
- **必须**一次性事件（Toast、跳转、分享）通过 `Channel<Event>` + `receiveAsFlow()` 向上抛给 Activity，禁止 ViewModel 直接持有 `Application` 调用 `toast`、`startActivity`。
- 缓冲区**必须**显式指定，禁止用默认 `RENDEZVOUS`（零容量，`trySend` 在接收方未等待时立即失败即静默丢事件）。容量按事件重要性分档：
  - **关键事件（导航、弹窗、确认）→ `Channel<Event>(Channel.UNLIMITED)`**。若受内存约束必须用有界缓冲，`trySend` 的返回值**必须**检查，失败时打日志——`trySend` 底层是 `offerInternal(element)`，缓冲满时立即返回 `ChannelResult.failure`，不挂起、不抛异常，静默丢事件且无任何痕迹。
  - **天然允许"只留最新"的事件（Toast、非阻塞提示）→ 统一 `Channel<Event>(Channel.CONFLATED)`**（等价于容量 1 + DROP_OLDEST，禁止再写 `BUFFERED(1, BufferOverflow.DROP_OLDEST)` 的等价形式，避免两种语义并存），并在 Channel 定义处注释说明丢事件语义。
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
  **为什么 Compose 侧用裸 `viewModelScope.launch` 而不违反 `coroutine-rules.md` 规则 1**：Compose ViewModel 不继承 `BaseViewModel`，`execute` 不可用；且 `execute` 的回调链（`onSuccess`/`onError`）对快速完成的任务存在不触发的时序坑（`Coroutine.kt` 源码注释），变更操作必须同步保证"更新 UiState + 抛 Event"，用 try-catch 内联处理才是确定性写法。`coroutine-rules.md` 规则 1 的适用范围是 View 系屏幕（ViewBinding + `BaseViewModel` 宿主），两者边界已在该文档中写明。

### 4.2 Screen 层

- Screen 只做三件事：收集状态、传回调给子组件、条件渲染 Dialog/BottomSheet（策略见 §4.5）。
- **必须**使用 `collectAsStateWithLifecycle()`（`lifecycle-runtime-compose`），禁止裸 `collectAsState()`——后台/切走页面后 StateFlow 持续 emit，日活千万级别的电量与 CPU 都白烧。
- **禁止** Screen 里调 `viewModel.xxx()` 后马上改本地 mutableStateOf（状态提升不够，逻辑乱飞）。
- **禁止** 在 `@Composable` 函数体里写超过 3 个 `var xxx by remember { mutableStateOf() }`。这种场景必须抽 State Holder。
- 需要**跨进程重建存活**的用户状态（搜索框输入、分页位置、选中项）必须 `rememberSaveable` / 自定义 `Saver`；`remember` 只管会话内，Android 杀进程重建后归零。不需要存活的瞬态（动画进度、拖拽坐标）用 `remember`。

### 4.3 线程模型

- **必须** IO 操作通过 `withContext(Dispatchers.IO)`。
- **禁止** 在 Repository 或 ViewModel 里抛阻塞主线程的同步调用。
- **禁止** 使用 `GlobalScope` 启动协程。ViewModel 里用 `viewModelScope`，Screen 里用 `LaunchedEffect(key) { ... }`。

### 4.4 事件消费侧生命周期绑定

ViewModel 抛出的 `Channel<Event>`，消费侧**必须**绑定到 `Lifecycle.State.STARTED`，禁止裸 `scope.launch { events.collect { } }`——页面不可见时还在消费/堆积事件，回来后又消费一批过期事件，用户看到的就是"点了没反应，回头又弹两次"：

```kotlin
// Screen 内收集并转发给 Activity（平台操作仍在 Activity 执行，Screen 保持无平台依赖）
val lifecycleOwner = LocalLifecycleOwner.current
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.events.collect { event ->
            onEvent(event) // 回调给 Activity 执行 toast / clipboard / navigate
        }
    }
}
```

- `repeatOnLifecycle(STARTED)`：STOPPED 时收集协程取消、STARTED 时重建。配合 §4.1 的缓冲语义，行为是确定的——CONFLATED 通道里后台期间的新 Toast 自然丢弃（用户没看见，丢了无所谓）；UNLIMITED 通道里的关键事件排队，页面可见时补发（导航跳转不能丢）。
- **禁止** 消费侧在 `Application` 生命周期 scope 里常驻 collect——事件会和当前页面脱钩。

### 4.5 Dialog / BottomSheet 策略（UI State 条件渲染）

- Dialog / BottomSheet **统一由 `UiState` 条件渲染**：Dialog 状态作为 `UiState` 的字段（`sealed interface`），Screen 里 `when` 分支渲染。
- **禁止** 把 Dialog 注册成 Navigation 路由。导航栈方案会让返回键行为不一致（有时关弹窗、有时关页面），栈深度不可控，且 Dialog 的进出动画、多弹窗互斥全要自己补——条件渲染 + 返回键回调天然覆盖这些场景。
- **返回键行为**：有 Dialog 展示时，返回键关闭 Dialog（清状态字段）；无 Dialog 时，返回键才走导航。必须显式注册 `OnBackPressedCallback`：

```kotlin
// UiState 内的 Dialog 状态
sealed interface DialogState {
    data object AddItem : DialogState
    data class DeleteConfirm(val item: ThemeItem) : DialogState
}
// ThemeManageUiState: val dialog: DialogState? = null
```

```kotlin
@Composable
fun ThemeManageScreen(
    state: ThemeManageUiState,
    onDismissDialog: () -> Unit,
    onConfirmDelete: (ThemeItem) -> Unit,
    // ...
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // 有 Dialog 时拦截返回键：关闭 Dialog 而非退出页面
    DisposableEffect(state.dialog != null, backDispatcher) {
        val callback = object : OnBackPressedCallback(state.dialog != null) {
            override fun handleOnBackPressed() = onDismissDialog()
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    when (val dialog = state.dialog) {
        is DialogState.AddItem -> AddItemDialog(
            onDismiss = onDismissDialog,
            onSubmit = { ... }
        )
        is DialogState.DeleteConfirm -> ConfirmDialog(
            message = stringResource(R.string.theme_delete_confirm, dialog.item.name),
            onConfirm = { onConfirmDelete(dialog.item) },
            onDismiss = onDismissDialog
        )
        null -> Unit
    }
}
```

- **禁止** 在 Composable 体内用 `mutableStateOf` 本地持有 Dialog 显隐开关——重建即丢，返回键也拦不到，状态必须收进 `UiState`。

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
