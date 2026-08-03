# ThemeManageActivity → Compose 重写计划

> 目标：在保留原有功能与界面的前提下，将 `ThemeManageActivity.kt` 从传统 RecyclerView 体系迁移至 Jetpack Compose + ViewModel 架构。

## 一、功能清单（原封不动迁过来）

| 功能 | 描述 |
|------|------|
| **日间/夜间 Tab** | 顶部两个 Tab，切换 `isDark` 状态，主题列表随之过滤 |
| **主题列表** | 显示当前 Tab 下所有主题，每项含：预览色块、名称、背景图缩略图、当前选中标记 |
| **新建主题** | 点击 `+` FAB，弹出主题编辑弹窗 |
| **主题编辑弹窗** | 名称输入、颜色选择器（多色）、背景图选择、模糊度调节 |
| **应用主题** | 点击列表项或弹窗中的应用按钮 |
| **编辑主题** | 点编辑按钮 → 弹出编辑弹窗，回填已有数据 |
| **导出主题** | 序列化为 JSON，通过系统分享 |
| **删除主题** | 确认弹窗后移除 |
| **更多菜单** | 每项的溢出按钮 → 四个选项（应用/编辑/导出/删除） |
| **多选模式** | 长按进入，支持批量：删除、置顶、导出。点击取消退出 |
| **剪贴板导入** | 从剪贴板解析 JSON 导入 |
| **下拉刷新** | SwipeRefreshLayout → Compose `pullRefresh` |
| **置顶逻辑** | 当前正在使用的主题自动排到列表最前面 |

## 二、架构设计

```
ThemeManageScreen (Composable)
├── ThemeManageViewModel
│   ├── StateFlow<UiState>
│   └── 操作方法（add/delete/apply/edit/import/multiDelete...）
├── 组件拆分：
│   ├── ThemeTabRow          ← 日间/夜间 Tab
│   ├── ThemeList            ← LazyColumn
│   ├── ThemeCard            ← 单个主题项
│   ├── ThemeEditDialog      ← 新建/编辑弹窗（AlertDialog + 自定义内容）
│   ├── MoreOptionsDropdown  ← 溢出菜单（DropdownMenu）
│   └── MultiSelectBar       ← 多选模式底部操作栏
└── 数据层（不动）：
    └── ThemeConfig（单例，configList，save/delConfig/applyConfig...）
```

## 三、状态管理

```kotlin
data class ThemeManageUiState(
    val isDark: Boolean = false,
    val configs: List<ThemeConfig.Config> = emptyList(),
    val selectedIndex: Int = -1,
    val multiSelectMode: Boolean = false,
    val selectedIndices: Set<Int> = emptySet(),
    val showEditDialog: Boolean = false,
    val editingConfig: ThemeConfig.Config? = null,
    val isRefreshing: Boolean = false,
)
```

```kotlin
class ThemeManageViewModel : ViewModel() {
    private val _state = MutableStateFlow(ThemeManageUiState())
    val state: StateFlow<ThemeManageUiState> = _state.asStateFlow()

    fun switchTab(isDark: Boolean)
    fun applyTheme(config: ThemeConfig.Config)
    fun editTheme(config: ThemeConfig.Config?)
    fun saveTheme(config: ThemeConfig.Config)
    fun deleteTheme(config: ThemeConfig.Config)
    fun deleteSelected()
    fun exportTheme(config: ThemeConfig.Config)
    fun exportSelected()
    fun topSelected()
    fun importFromClipboard()
    fun toggleMultiSelect(index: Int)
    fun exitMultiSelect()
}
```

`configs` 来源：直接读 `ThemeConfig.configList`，每次操作后 `state` 里刷新不可变副本，避免 View 层碰可变集合。

## 四、交互映射

| 原实现 | Compose 方案 |
|--------|-------------|
| `RecyclerView + Adapter` | `LazyColumn` + `items(configs, key = { it.hashCode() })` |
| `TabView` 手写 | `TabRow` + `Tab`（Material3） |
| `SwipeRefreshLayout` | `pullRefresh` modifier |
| `selector` 弹窗更多菜单 | `DropdownMenu`，锚定到更多按钮 |
| `AlertDialog.Builder` 确认删除 | `AlertDialog` composable |
| 编辑弹窗（手撸 LinearLayout） | `AlertDialog` + `Column` 内部色卡 `LazyRow` |
| 多选底部操作栏 | `AnimatedVisibility` + `BottomAppBar` 或固定 `Row` |
| `recreate()` | 调用 ThemeConfig.applyConfig → ViewModel 发 SideEffect → Activity 重启 |

## 五、风险点

1. **`ThemeConfig.applyConfig` 内部调用 `activity.recreate()`**
   必须在 Activity 层处理，不能在 Composable 闭包里直接调。方案：ViewModel 通过 `Channel` 发一次性事件，Activity `collect` 后执行。

2. **多选 + 列表滚动性能**
   `selectedIndices` 用 `Set`，Card 用 `remember(selected)` 避免全量重组。列表超 50 项时，确保 `key` 稳定。

3. **背景图加载**
   原来用的是 `Glide.into(ImageView)`。Compose 里用 `AsyncImage`（Coil），或继续用 Glide Compose 集成。原代码有同步 `File.exists()` 检查，Compose 里必须走异步。

4. **颜色选择器**
   原是自定义 View（手画色块 + 勾选）。Compose 方案：`LazyRow` + `Box(Modifier.background)` + 边框标识选中。颜色值用 `ThemeConfig.availableColorList`。

5. **`ThemeConfig` 是单例 `object`**
   ViewModel 直接调 `ThemeConfig` 方法，不包一层 Repository，保持最小侵入。

## 六、文件结构

```
app/src/main/java/io/legado/app/ui/config/
├── theme/
│   ├── ThemeManageActivity.kt       ← 重写，仅做 setContent{ ThemeManageScreen() } + 处理 SideEffect
│   ├── ThemeManageScreen.kt         ← 顶层 Composable
│   ├── ThemeManageViewModel.kt      ← 状态管理
│   ├── ThemeManageUiState.kt        ← 状态 data class
│   ├── components/
│   │   ├── ThemeTabRow.kt
│   │   ├── ThemeCard.kt
│   │   ├── ThemeEditDialog.kt
│   │   ├── MoreOptionsDropdown.kt
│   │   └── MultiSelectBottomBar.kt
```

- 不删原 `ThemeManageActivity.kt`，重命名备份为 `ThemeManageActivity.kt.old`。

## 七、执行顺序

1. 建模：`UiState` + `ViewModel` + `SideEffect`
2. 骨架：`ThemeManageScreen` + `TabRow` + 空 `LazyColumn`
3. 单项：`ThemeCard` + `MoreOptionsDropdown`
4. 编辑：`ThemeEditDialog`（颜色/背景图/模糊度）
5. 多选：长按触发 + `MultiSelectBottomBar`
6. 导入导出：Clipboard / Share Intent
7. 打磨：动画 + 深色模式适配 + 测试
