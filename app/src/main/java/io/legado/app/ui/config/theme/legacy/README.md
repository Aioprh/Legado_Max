# legacy/ — 旧版主题代码

基于 Android View 体系实现的旧版主题功能代码，保留为兼容入口。

> **注意**：这些文件将在 Compose 版功能完善后逐步移除。新增功能请在 `manage/` 目录中实现。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ThemeConfigFragment.kt` | 旧版主题配置 PreferenceFragment，通过 XML PreferenceScreen 展示底层偏好配置项（主题色、背景图、虚化、导航栏透明等），同时作为枢纽跳转至应用主题管理、顶栏/底栏/气泡管理、封面配置等子页面。入口：我的 → 主题设置。 |
| `ThemeListDialog.kt` | 旧版主题列表 Dialog（BaseDialogFragment + RecyclerView），全屏弹窗展示日/夜间主题列表，支持应用、编辑、删除、置顶、多选导出。已被 Compose 版 `ThemeManageActivity` 替代。 |

## 子目录

- `appTheme/` — 旧版应用主题管理（打包整体主题方案）
