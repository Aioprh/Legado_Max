# manage/ — 新版主题管理（Compose）

Jetpack Compose 实现的主题管理模块，是当前主开发目标。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ThemeManageActivity.kt` | 入口 Activity，处理平台侧回调（ColorPickerDialog、NumberPickerDialog、文件选择、分享等）。 |
| `ThemeManageScreen.kt` | Compose 主屏幕，渲染主题列表、Tab 切换、多选操作栏、编辑弹窗，收集一次性事件转发给 Activity。 |
| `ThemeManageViewModel.kt` | ViewModel，持有 UiState 并通过 StateFlow 驱动渲染，所有 ThemeConfig 写操作在协程中执行。 |
| `ThemeManageUiState.kt` | UiState 数据类定义：`ThemeItem`、`MultiSelectState`、`EditDialogState`、`ThemeTab`、`ThemeEvent`、`ThemeAction`、`ThemeManageUiState`。 |

## 架构模式

- **MVVM + StateFlow**：`ThemeManageViewModel.uiState` 驱动全部 UI 状态
- **事件转发**：一次性事件（Toast、分享、Recreate）通过 `Channel` → `receiveAsFlow()` 向上抛给 Activity
- **双模式**：普通模式（应用/编辑/分享/删除）+ 多选模式（置顶/导出/批量删除）

## 子目录

- `components/` — 主题管理专用的 Compose UI 组件
