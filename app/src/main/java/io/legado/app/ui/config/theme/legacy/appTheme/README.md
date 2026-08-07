# legacy/appTheme/ — 旧版应用主题管理

基于 View + RecyclerView 实现的应用主题方案管理，将日间/夜间主题、顶栏、底栏、封面图集打包为整体方案。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ApplicationThemeActivity.kt` | 应用主题方案列表页，展示所有方案，支持创建、导入（支持按组件选择）、导出、应用、编辑、删除。列表项展示当前模式的预览效果（背景色 + 主色条 + 背景图）。 |
| `ApplicationThemeEditActivity.kt` | 应用主题方案编辑页，编辑单个方案的各组件配置：日间/夜间主题、顶栏、底栏、封面图集。每个组件通过选择器对话框从已有配置中挑选。 |

## 依赖

- `io.legado.app.help.config.ApplicationThemeManager` — 应用主题方案的增删改查、导入导出
- `io.legado.app.help.config.ThemeConfig` — 底层主题配置
- `io.legado.app.help.config.TopBarConfig` / `NavigationBarConfig` — 顶栏/底栏配置
- `io.legado.app.data.repository.CoverGalleryRepository` — 封面图集
