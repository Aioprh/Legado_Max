# theme/ — 主题模块

本目录是应用主题功能的根目录，包含主题管理的全部代码。

## 目录结构

| 子目录 | 用途 |
|--------|------|
| `manage/` | **新版主题管理**（Jetpack Compose 实现）。主题列表的增删改查、多选、导入导出等核心交互。 |
| `manage/components/` | 新版主题管理专用的 Compose UI 组件（主题卡片、编辑弹窗、Tab 行、多选底栏）。 |
| `legacy/` | **旧版主题代码**（View 体系实现）。包括 PreferenceFragment 配置入口和旧版主题列表 Dialog。保留为兼容入口，后续将逐步移除。 |
| `legacy/appTheme/` | 旧版应用主题管理（打包日/夜主题 + 顶栏/底栏/封面图集为整体方案），基于 View + RecyclerView。 |

## 架构概览

```
theme/
├── manage/              # Compose 版（主开发目标）
│   ├── ThemeManageActivity.kt       # 入口 Activity
│   ├── ThemeManageScreen.kt         # Compose 主屏幕
│   ├── ThemeManageViewModel.kt      # 状态驱动
│   ├── ThemeManageUiState.kt        # UiState / 事件 / 数据类
│   └── components/                  # Compose 组件
│       ├── ThemeCard.kt             # 主题卡片 + 预览
│       ├── ThemeEditDialog.kt       # 编辑弹窗
│       ├── ThemeTabRow.kt          # 日/夜 Tab
│       └── MultiSelectBottomBar.kt # 多选操作栏
│
└── legacy/              # 旧版（兼容保留）
    ├── ThemeConfigFragment.kt       # PreferenceFragment 配置入口
    ├── ThemeListDialog.kt           # 旧版主题列表 Dialog
    └── appTheme/
        ├── ApplicationThemeActivity.kt       # 应用主题方案列表
        └── ApplicationThemeEditActivity.kt   # 应用主题方案编辑
```

## 外部依赖

- 主题配置数据层：`io.legado.app.help.config.ThemeConfig`
- 应用主题管理器：`io.legado.app.help.config.ApplicationThemeManager`
