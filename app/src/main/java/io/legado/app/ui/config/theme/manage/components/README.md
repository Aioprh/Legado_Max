# manage/components/ — 主题管理 Compose 组件

主题管理页面专用的 Jetpack Compose UI 组件。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ThemeCard.kt` | 主题卡片容器，编排预览、名称、操作按钮和多选交互。 |
| `ThemePreview.kt` | 主题色块预览，用主色/强调色/背景色/背景图组合渲染迷你预览。 |
| `ThemeBackgroundImage.kt` | 基于 Glide 的 Compose 背景图片加载，AndroidView 包裹 ImageView，利用 Glide 缓存池避免列表滑动卡顿。 |
| `ThemeEditDialog.kt` | 主题编辑弹窗，包含主题名称输入、颜色属性行、背景图片选择行（带异步缩略图预览）、虚化值选择、导航栏透明开关。 |

## 设计要点

- `ThemeBackgroundImage` 在路径为空时调用 `Glide.clear()` 清理旧请求，防止列表复用残留
- `ThemeEditDialog` 的背景图片行使用 `Modifier.weight(1f)` + `TextOverflow.MiddleEllipsis` 处理长文件名
- `ThemeEditDialog` 带有 `@Preview` 函数，可在 IDE 中独立预览
