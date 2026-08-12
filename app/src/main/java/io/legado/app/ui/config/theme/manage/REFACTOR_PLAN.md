# 主题管理模块 Compose 重构规划

> 基于对 `manage/` 目录全量源码审查后输出的整改计划。
> 按优先级分阶段执行，每阶段独立可交付、可验证。

---

## 当前评价

| 维度 | 评分 | 说明 |
|------|------|------|
| 页面级拆分 | 7/10 | Screen / Card / Dialog / ViewModel / Repository 边界基本清楚 |
| Compose 状态提升 | 7/10 | 组件用数据和回调驱动，没有把 ViewModel 塞进叶子组件（ThemeList 除外） |
| 组件复用设计 | 5/10 | 存在死代码、胖组件、ViewModel 泄漏到列表函数 |
| 维护成本 | 中 | 已开始受重复组件和职责膨胀影响 |

---

## P0 — 死代码清理与文档同步

**目标：消除维护噪音，让目录结构和文档如实反映当前实现。**

### 1. 删除废弃组件

| 文件 | 原因 |
|------|------|
| `components/ThemeTabRow.kt` | 已标 `@deprecated`，页面实际使用 `DayNightPager` 内置 Tab 渲染，无外部调用方 |
| `components/MultiSelectBottomBar.kt` | 页面实际使用通用 `ConfigMultiSelectBar`，此组件无引用 |

- 删除前全局搜索确认无其他模块引用。
- 删除后执行编译验证。

### 2. 修正 components/README.md

- 移除已删除组件的条目。
- 移除不存在的 `SegmentedTabRow.kt` 条目（该组件在 `ui/config/widget/` 下，不属于本目录）。
- 补充实际文件清单，与源码保持一致。

### 3. 修正 manage/README.md

- 子目录说明同步更新，移除对已删除组件的引用。

**验收标准：** 目录内文件与 README 完全对齐，编译通过，无 deprecated 组件残留。

---

## P1 — ThemeCard.kt 职责拆分

**目标：消除胖组件，让每个文件只承担一个明确职责。**

### 现状问题

`ThemeCard.kt`（309 行）同时包含：
- Glide 图片加载封装（`GlideImage`）
- 主题预览渲染（`ThemePreviewCard`）
- 列表卡片布局（`ThemeCard`）
- 普通模式操作按钮
- 多选模式交互
- 颜色解析与容错

### 拆分方案

```
components/
  ThemeCard.kt          — 卡片容器 + 布局编排 + 模式切换
  ThemePreview.kt       — 主题色块预览（从 ThemePreviewCard 抽出，改可见性 public）
  ThemeBackgroundImage.kt — Glide 图片加载封装（从 GlideImage 抽出，命名语义化）
```

### 具体改动

#### ThemeBackgroundImage.kt

```kotlin
@Composable
fun ThemeBackgroundImage(
    path: String?,
    modifier: Modifier = Modifier
)
```

- 从 `ThemeCard.kt` 抽出 `GlideImage`，重命名为 `ThemeBackgroundImage`。
- `update` 回调中路径为空时，补充 `Glide.with(context).clear(imageView)`，防止列表复用残留请求。

#### ThemePreview.kt

```kotlin
@Composable
fun ThemePreview(
    primaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    backgroundImgPath: String?,
    isCurrent: Boolean,
    isMultiSelectMode: Boolean,
    isNightTheme: Boolean
)
```

- 从 `ThemePreviewCard` 抽出，改为 public 可见性。
- 预览组件可独立 Preview 和复用。

#### ThemeCard.kt（精简后）

- 只保留卡片容器、Row 布局、操作按钮编排、多选/普通模式切换。
- 颜色解析逻辑保留在 Card 内（因为解析结果仅用于传给 Preview）。
- 引用 `ThemePreview` 和 `ThemeBackgroundImage`。

### 颜色解析容错

当前三处 `runCatching { ... }.getOrDefault(...)` 重复模式，考虑抽取：

```kotlin
private fun parseColorOrDefault(hex: String, default: Int): Int =
    runCatching { hex.toColorInt() }.getOrDefault(default)
```

**验收标准：** `ThemeCard.kt` 行数降至 120 行以内，每个抽出文件职责单一，编译通过，Preview 正常。

---

## P2 — ThemeList 解耦 ViewModel

**目标：列表组合函数不持有具体 ViewModel 引用，提升可测试性和可复用性。**

### 现状问题

`ThemeManageScreen.kt` 第 203-208 行：

```kotlin
private fun ThemeList(
    items: List<ThemeItem>,
    state: ConfigManageState,
    currentConfig: ThemeConfig.Config,
    viewModel: ThemeManageViewModel  // ← 直接依赖 ViewModel
)
```

### 改动方案

```kotlin
private fun ThemeList(
    items: List<ThemeItem>,
    state: ConfigManageState,
    currentConfig: ThemeConfig.Config,
    onApply: (ThemeItem) -> Unit,
    onEdit: (ThemeItem) -> Unit,
    onShare: (ThemeItem) -> Unit,
    onDelete: (ThemeItem) -> Unit,
    onCopy: (ThemeItem) -> Unit,
    onLongClick: (ThemeItem) -> Unit,
    onToggleSelect: (ThemeItem) -> Unit
)
```

调用处改为：

```kotlin
ThemeList(
    items = dayItems,
    state = state,
    currentConfig = currentConfig,
    onApply = viewModel::applyConfig,
    onEdit = { item ->
        val draft = viewModel.startEdit(item)
        state.openEditDialog(draft.isNew, draft.editingKey)
    },
    onShare = viewModel::shareItem,
    onDelete = viewModel::deleteItem,
    onCopy = viewModel::copyItem,
    onLongClick = { item -> state.enterMultiSelect(item.key) },
    onToggleSelect = { item -> state.toggleSelection(item.key) }
)
```

**验收标准：** `ThemeList` 函数签名中不出现 `ThemeManageViewModel` 类型，编译通过。

---

## P3 — ThemeEditDialog 状态收口

**目标：消除重复空安全判断，让组件签名表达真实约束。**

### 现状问题

Screen 层已判断 `editDraft != null`，然后 `editDraft!!` 传给 Dialog。Dialog 内部又写 `val config = draft ?: return`，重复防御。

### 改动方案

`ThemeEditDialog` 的 `draft` 参数改为非空：

```kotlin
@Composable
fun ThemeEditDialog(
    draft: ThemeConfig.Config,  // 非空
    isNew: Boolean,
    ...
)
```

Screen 调用处改为智能转换：

```kotlin
val draft = editDraft
if (state.editDialog.visible && draft != null) {
    ThemeEditDialog(
        draft = draft,
        ...
    )
}
```

**验收标准：** `ThemeEditDialog` 内部不再有 `?: return` 空安全分支，编译通过。

---

## P4 — ViewModel 边界治理

**目标：消除 ViewModel 中的平台服务直接调用和线程安全隐患。**

### 4.1 剪贴板操作下沉

#### 现状

`ThemeManageViewModel.copyItem()` 直接操作 `clipboardManager`：

```kotlin
fun copyItem(item: ThemeItem) {
    val json = GSON.toJson(item.config)
    val clipData = ClipData.newPlainText(null, json)
    clipboardManager.setPrimaryClip(clipData)  // ← 平台服务直接调用
    _events.trySend(ThemeEvent.ToastMsg("${item.config.themeName}主题已拷贝"))
}
```

#### 方案

将剪贴板写入逻辑移到 Activity 层（或独立的 ClipboardHelper），ViewModel 只负责生成 JSON 和发事件：

```kotlin
// ViewModel
fun copyItem(item: ThemeItem) {
    _events.trySend(ThemeEvent.CopyJson(GSON.toJson(item.config), item.config.themeName))
}

// ThemeEvent 新增
data class CopyJson(val json: String, val themeName: String) : ThemeEvent()

// Activity 事件收集
is ThemeEvent.CopyJson -> {
    val clipData = ClipData.newPlainText(null, event.json)
    clipboardManager.setPrimaryClip(clipData)
    toastOnUi("${event.themeName}主题已拷贝")
}
```

### 4.2 applyTheme 线程安全确认

#### 现状

ViewModel 中多处：

```kotlin
withContext(Dispatchers.Main) {
    repository.applyTheme(item.config)
}
```

`ThemeRepositoryImpl.applyTheme()` 内部调用 `ThemeConfig.applyConfig(appContext, config)`。

#### 方案

- 排查 `ThemeConfig.applyConfig()` 内部是否有磁盘 IO、SharedPreferences 写入、资源加载等耗时操作。
- 如果有：在 Repository 层把耗时部分切到 IO 线程，只把必须在主线程执行的部分（如 UI 刷新、Activity recreate）留在主线程。
- 补充关键路径日志：

```kotlin
override fun applyTheme(config: ThemeConfig.Config) {
    appLog("ThemeRepository", "applyTheme start: ${config.themeName}")
    ThemeConfig.applyConfig(appContext, config)
    appLog("ThemeRepository", "applyTheme done: ${config.themeName}")
}
```

### 4.3 Hilt 接入

#### 现状

使用手动 Factory：

```kotlin
class ThemeManageViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory
```

#### 方案

- ViewModel 加 `@HiltViewModel` + `@Inject`：

```kotlin
@HiltViewModel
class ThemeManageViewModel @Inject constructor(
    private val repository: ThemeRepository,
    application: Application
) : BaseViewModel(application)
```

- Repository 实现加 `@Inject`：

```kotlin
class ThemeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemeRepository
```

- Activity 加 `@AndroidEntryPoint`，移除手动 Factory。
- 删除 `ThemeManageViewModelFactory.kt`。

**前置条件：** 确认项目已正确配置 Hilt（`HiltAndroidApp` 已在 Application 类标注，Gradle 依赖已引入）。

**验收标准：** ViewModel 不直接持有 `clipboardManager`，`applyTheme` 调用链有日志覆盖，Factory 文件删除，Hilt 注入生效。

---

## P5 — ThemeEditDialog 内部组件抽取（按需）

**目标：为后续字段扩展预留合理拆分边界，但不为了行数强行拆文件。**

### 当前判断

| 子组件 | 行数 | 复用次数 | 是否抽取 |
|--------|------|----------|----------|
| `ColorRow` | ~60 | 4 次 | 否（留在文件内） |
| `OptionRow` | ~40 | 1 次 | 否 |
| `SwitchRow` | ~35 | 1 次 | 否 |
| `BackgroundImageRow` | ~80 | 1 次 | **是** — 含异步解码逻辑，独立后可单独测试和 Preview |

### 方案

仅当以下任一条件满足时执行拆分：
- 编辑弹窗新增字段导致文件超过 500 行
- `BackgroundImageRow` 的解码逻辑需要复用到其他页面
- `ColorRow` / `OptionRow` / `SwitchRow` 出现跨文件复用需求

当前优先抽取 `BackgroundImageRow` 到独立文件 `ThemeImageRow.kt`，因为它包含 `produceState + Dispatchers.IO` 异步逻辑，独立后更易于测试和维护。

**验收标准：** 不强行拆分，以实际复用需求驱动。

---

## 执行顺序与依赖关系

```
P0（死代码清理）─ 无依赖，可立即执行
     ↓
P1（ThemeCard 拆分）─ 依赖 P0 完成后目录干净
     ↓
P2（ThemeList 解耦）─ 无强依赖，可与 P1 并行
     ↓
P3（Dialog 状态收口）─ 无强依赖，可与 P1/P2 并行
     ↓
P4（ViewModel 治理）─ 依赖 P2/P3 完成（事件模型变更需配合）
     ↓
P5（按需抽取）─ 低优先级，后续迭代中视情况执行
```

---

## 不做的事

- 不把 `ColorRow` / `OptionRow` / `SwitchRow` 强行抽成独立文件 — 当前调用次数不支持这个拆分成本。
- 不引入新的状态管理框架 — 现有 `StateFlow + Channel` 足够。
- 不重写 Repository 的 Mutex 策略 — 当前写串行化设计合理。
- 不改 `ThemeItem.key` 的生成方式（`GSON.toJson(config)` 作为 key）— 虽然不是最优，但当前能保证会话内唯一性，改动风险大于收益。
