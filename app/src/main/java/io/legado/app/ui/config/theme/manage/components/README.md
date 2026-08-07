# manage/components/ — 主题管理 Compose 组件

主题管理页面专用的 Jetpack Compose UI 组件。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ThemeCard.kt` | 主题卡片，展示主题预览（色块 + 背景图缩略图）、名称、日/夜标签、应用/编辑按钮、分享/删除图标、多选 Checkbox。 |
| `ThemeEditDialog.kt` | 主题编辑弹窗，包含主题名称输入、颜色属性行、背景图片选择行（带异步缩略图预览）、虚化值选择、导航栏透明开关。 |
| `ThemeTabRow.kt` | 日间/夜间主题切换 Tab 行，带图标和文字。 |
| `SegmentedTabRow.kt` | 通用胶囊分段按钮 Tab 行组件，基于 Material3 `SingleChoiceSegmentedButtonRow`，选中色通过 `lerp` 在 surfaceVariant 与 primary 之间插值。 |
| `MultiSelectBottomBar.kt` | 多选模式底部操作栏，显示已选数量并提供置顶、导出、删除按钮。 |

## 设计要点

- `ThemeCard` 的预览卡片支持异步加载背景图片缩略图（`produceState` + IO 线程解码）
- `ThemeEditDialog` 的背景图片行使用 `Modifier.weight(1f)` + `TextOverflow.MiddleEllipsis` 处理长文件名
- 所有组件均带 `@Preview` 函数，可在 IDE 中独立预览
