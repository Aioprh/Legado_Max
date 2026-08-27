# widget/ — 配置管理通用零件（Compose）

所有配置管理页（主题、顶栏、底栏等）共享的通用 UI 状态和 Composable 组件，
采用组合模式而非继承模式实现复用。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ConfigManageState.kt` | 通用状态 Holder，封装日/夜 Tab、多选模式、编辑弹窗可见性等 UI 交互状态，与具体数据类型解耦。 |
| `ConfigManageScaffold.kt` | Scaffold + TopAppBar 骨架，含返回行为、多选/普通模式 actions 插槽、多选底栏插槽。 |
| `DayNightPager.kt` | 日/夜分页管理器，封装 Tab + Pager 双向联动、摘要文本、空状态，以及通用 `ConfigList`。 |
| `ConfigMultiSelectBar.kt` | 可配置操作项的多选底栏，各管理页按需传入不同的批量操作列表。 |
| `SegmentedTabRow.kt` | 泛型胶囊分段 Tab 行（滑动指示器版），视觉态由连续 `progress: Float` 驱动，滑块随 Slider 逐帧跟随滑动，文字/图标激活色过半切换，选中色跟随应用主色调。 |

## 架构模式

- **组合模式**：每个管理页通过 `rememberConfigManageState()` 持有通用状态，
  用自己的 Screen 函数组装通用零件 + 专属卡片/弹窗，无需继承任何基类。
- **状态与数据分离**：通用状态（Tab/多选/编辑弹窗）在 Composable 层由 `ConfigManageState` 管理，
  数据加载/增删改查由各页面 ViewModel 自行负责。
- **事件转发**：一次性事件（Toast、分享、Recreate）由各 ViewModel 通过 `Channel` 向上抛给 Activity。

## 与具体管理页的关系

```
widget/（通用零件）          theme/manage/（主题专用）
────────────────────         ──────────────────────────
ConfigManageState      ←──    rememberConfigManageState()
ConfigManageScaffold   ←──    组装 Screen
DayNightPager          ←──    传入 dayContent / nightContent
ConfigMultiSelectBar   ←──    传入 MultiSelectAction 列表
ConfigList             ←──    渲染 ThemeCard

                             ThemeManageViewModel（仅数据操作）
                             ThemeCard（主题专用卡片）
                             ThemeEditDialog（主题专用弹窗）
```

新增配置管理页时：

1. 创建专属 ViewModel（仅数据操作，不含 UI 状态）
2. 创建专属 Card 和 EditDialog
3. 在 Screen 中组装上述通用零件 + 专属组件
