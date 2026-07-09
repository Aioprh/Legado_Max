# 高亮规则文件分布与架构

本文档梳理的是阅读内容里的“高亮规则”功能。代码编辑器的 TextMate 语法高亮是另一套体系，见文末“与代码编辑器高亮的区别”。

## 核心文件

### 规则模型与存储

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRule.kt`
  - 高亮规则数据模型。
  - 字段包括：`pattern` 正则、`enabled`、`group`、`targetScope`、文字色、下划线样式、背景色/背景图、书籍作用范围 `scope`、排除范围 `excludeScope`。
  - `matchesScope(bookName, bookOrigin)` 用于判断规则是否适用于当前书籍。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleStore.kt`
  - 规则加载、保存、规则清洗、备份恢复的兼容门面。
  - 规则不是数据库表，主要以 JSON 存在 SharedPreferences。
  - 备份文件名为 `highlightRule.json`，背景图备份目录为 `highlightRuleBg`。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleRepository.kt`
  - UI/ViewModel 面向的规则仓库入口。
  - 封装规则、分组、当前分组、导入编码等操作，减少 UI 直接依赖 Store。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleDefaultRules.kt`
  - 默认预置规则生成。
  - 当前内置默认规则已经从 `HighlightRuleStore` 拆到这里。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleBackgroundManager.kt`
  - 高亮规则背景图迁移、恢复、使用文件收集和未使用文件清理。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleGroupStore.kt`
  - 高亮规则分组加载、保存、与规则列表同步。

- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
  - 高亮规则相关偏好 key：
    - `highlightRuleDialog`
    - `highlightRuleBookTitle`
    - `highlightRuleBracketNote`
    - `highlightRuleItems`
    - `highlightRuleGroups`
    - `highlightRuleCurrentGroup`

### 配置入口与配置 UI

- `app/src/main/res/xml/pref_config_read.xml`
  - 阅读设置中的入口 preference：`highlightRuleConfig`。

- `app/src/main/java/io/legado/app/ui/book/read/config/MoreConfigDialog.kt`
  - 拦截 `highlightRuleConfig`，打开 `HighlightRuleConfigDialog`。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleConfigDialog.kt`
  - 高亮规则列表页。
  - 支持启用/禁用、编辑、删除、导入、导出、分享、重置、选择规则、分组管理。
  - 只负责 UI 渲染、弹窗和用户交互，规则状态交给 `HighlightRuleConfigViewModel`。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleConfigViewModel.kt`
  - 高亮规则列表页 ViewModel。
  - 管理规则集合、当前分组、重置、增删改、导入、启用状态和同步保存。
  - 保存后通过 `EventBus.UP_CONFIG` 通知阅读配置刷新。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleEditDialog.kt`
  - 新增/编辑单条规则。
  - 处理规则名称、正则、分组、目标范围、书籍 scope、文字色、下划线、SVG 下划线、背景色、背景图、预览文本。
  - 背景图选择后会复制到内部目录。
  - 当前编辑规则、分组列表和编辑页临时状态由 `HighlightRuleEditViewModel` 保存。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleEditViewModel.kt`
  - 单条高亮规则编辑页 ViewModel。
  - 保存正在编辑的 `HighlightRule`、可选分组列表和正则切换状态。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightPresetRuleDialog.kt`
  - 预置规则选择弹窗。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleGroupManageDialog.kt`
  - 分组管理弹窗。
  - 支持新增、重命名、删除分组，以及导出分组规则。
  - 分组列表和规则列表由 `HighlightRuleGroupManageViewModel` 管理。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleGroupManageViewModel.kt`
  - 分组管理页 ViewModel。
  - 维护分组列表和规则列表，负责新增、重命名、删除分组时的数据同步。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleBottomSheet.kt`
  - 底部弹窗拖拽关闭的公共 helper。

### 配置页预览

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRulePreview.kt`
  - 根据规则和预览文本生成配置页的预览 `CharSequence`。

- `app/src/main/java/io/legado/app/ui/book/read/config/HighlightRuleStyle.kt`
  - 统一的高亮样式模型。
  - 配置页预览和阅读页实际渲染都从 `HighlightRuleStyle.from(rule)` 获取样式字段。

- 以下 Span 主要服务配置页预览：
  - `BgColorSpan.kt`
  - `BgImageSpan.kt`
  - `SolidUnderlineSpan.kt`
  - `DashUnderlineSpan.kt`
  - `WaveUnderlineSpan.kt`
  - `DoubleUnderlineSpan.kt`
  - `SvgUnderlineSpan.kt`
  - `SvgPathParser.kt`

## 阅读页实际渲染链路

阅读页最终渲染不直接复用配置页预览 Span，而是走阅读排版层自己的 Span 标记和 Canvas 绘制。

主链路如下：

1. `TextChapterLayout` 初始化时通过 `HighlightRuleRepository.loadEnabledRules(appCtx)` 加载启用且正则不为空的规则。
2. `compiledHighlightRules` 将规则里的 `pattern` 预编译为 `Regex`。
3. `setTypeText(...)` 排版标题/正文前，调用 `applyHighlightRules(SpannableStringBuilder(text), isTitle)`。
4. `applyHighlightRules(...)` 逐条判断：
   - `targetScope` 是否适用于标题/正文。
   - `scope` / `excludeScope` 是否匹配当前书名或书源 URL。
   - 正则是否匹配当前文本。
5. 匹配成功后，先通过 `HighlightRuleStyle.from(rule)` 生成统一样式，再由 `applyRuleSpans(...)` 给匹配区间打 Span：
   - `ForegroundColorSpan`：文字色。
   - `HighlightStyleSpan`：下划线、背景色、背景图等阅读页样式标记。
6. 排版时，`TextChapterLayout` 从 `Spanned` 中提取样式，把样式字段写入 `TextColumn` / `TextHtmlColumn`。
7. `TextLine` 绘制时读取列上的样式字段：
   - `drawStyledBackgrounds(...)` 绘制背景色和背景图。
   - `drawStyledUnderlines(...)` 绘制实线、虚线、波浪线、双线、自定义 SVG 下划线。
8. 如果当前行有高亮样式，`checkFastDraw()` 会返回 false，避免走快速绘制路径。

相关文件：

- `app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt`
  - 加载、编译、匹配高亮规则。
  - 将规则转换成 `ForegroundColorSpan` 和 `HighlightStyleSpan`。

- `app/src/main/java/io/legado/app/ui/book/read/page/provider/HighlightStyleSpan.kt`
  - 阅读排版阶段传递高亮样式的轻量 Span。
  - 本身不绘制，只携带样式参数。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextBaseColumn.kt`
  - 文本列基础接口，包含高亮相关字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextColumn.kt`
  - 普通文本列，保存文字色、下划线、背景色、背景图等字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextHtmlColumn.kt`
  - HTML 文本列，同样保存高亮样式字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/TextLine.kt`
  - 最终 Canvas 绘制层。
  - 负责连续区间合并、背景色/背景图绘制、下划线绘制、背景图缓存、背景图清理。

## 默认预置规则

默认预置规则在 `HighlightRuleDefaultRules.create(context)` 中生成，不是独立 JSON 文件。

当前内置 id 包括：

- `dialog_default`
- `book_title_default`
- `bracket_note_default`
- `title_emphasis_default`
- `thought_default`
- `narrator_default`
- `emphasis_default`
- `poetry_default`
- `ellipsis_default`
- `number_default`
- `english_default`
- `date_time_default`

其中前三个还保留了旧版独立开关：

- `highlightRuleDialog`
- `highlightRuleBookTitle`
- `highlightRuleBracketNote`

## 备份与恢复

高亮规则已接入备份恢复。

相关文件：

- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
  - 写出 `highlightRule.json`。
  - `stageHighlightRuleBackgroundFiles(...)` 会把规则引用的背景图一起放入 `highlightRuleBg`。

- `app/src/main/java/io/legado/app/help/storage/Restore.kt`
  - 读取 `highlightRule.json`。
  - 调用 `HighlightRuleStore.restoreBackupData(...)` 恢复规则、分组、当前分组和背景图。

- `app/src/main/java/io/legado/app/api/controller/BackupController.kt`
  - Web 备份接口里也统计和导出高亮规则及背景图。

- `app/src/main/java/io/legado/app/help/storage/BackupInfoHelper.kt`
  - 备份信息展示中统计高亮规则数据大小。

- `app/src/main/java/io/legado/app/help/storage/BackupSelectorConfig.kt`
  - 备份选择项中注册 `highlightRule.json`。

## 需要注意的遗留点

`TextChapterLayout.kt` 中还有两个私有方法：

- `applyBuiltInHighlightRules(...)`
- `applyHighlightRulesFromStore(...)`

当前文件内没有发现调用点。实际主路径是：

```text
compiledHighlightRules
  -> applyHighlightRules(...)
  -> applyRuleSpans(...)
  -> HighlightStyleSpan
  -> TextColumn/TextHtmlColumn
  -> TextLine.drawStyledBackgrounds/drawStyledUnderlines
```

另外，当前若干高亮规则相关 Kotlin 文件中的中文注释和部分默认预置文案/正则显示为乱码，需要单独判断是文件编码问题，还是内容已经被错误转码后提交。

## 与代码编辑器高亮的区别

代码编辑器语法高亮是另一套 TextMate 架构，和阅读内容高亮规则无关。

相关文件：

- `app/src/main/java/io/legado/app/ui/code/CodeEditViewModel.kt`
  - 加载 TextMate grammar 和 theme。

- `app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt`
  - 设置编辑器语言和主题。

- `app/src/main/java/io/legado/app/ui/code/TextMateColorScheme2.kt`
  - TextMate 配色方案扩展。

- `app/src/main/assets/textmate/languages.json`
  - TextMate 语言注册表。

- `app/src/main/assets/textmate/javascript/syntaxes/javascript.tmLanguage.json`
  - JavaScript 语法规则。

- `app/src/main/assets/textmate/html/syntaxes/html.tmLanguage.json`
  - HTML 语法规则。

- `app/src/main/assets/textmate/markdown/syntaxes/markdown.tmLanguage.json`
  - Markdown 语法规则。

- `app/src/main/assets/textmate/d_*.json`、`app/src/main/assets/textmate/l_*.json`
  - 深色/浅色主题配置。
