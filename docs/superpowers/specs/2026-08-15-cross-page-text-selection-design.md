# 跨页文本选择设计规格

## 概述

在阅读页面中，用户长按选中文本后，将手指拖至屏幕右下角（下一页方向）或左上角（上一页方向）的热区并停留 500ms，自动翻页并将选区延续到新页面，实现跨页/跨章文本选择。

## 功能需求

### 触发条件

- 用户已通过长按进入文本选择模式（`isTextSelected = true`）
- 手指拖动到右下角或左上角热区
- 手指在热区内停留 500ms（不可配置，固定值）

### 热区定义

- **物理正方形**，边长 = 屏幕长边 × 1/8
- 竖屏时长边为高度 H，热区边长 = H/8
- 横屏时自动反转，长边为宽度 W，热区边长 = W/8
- **右下角热区**（触发下一页）：以屏幕右下角为原点
  - 左边界 = viewWidth - edgeLen
  - 上边界 = viewHeight - edgeLen
- **左上角热区**（触发上一页）：以屏幕左上角为原点
  - 右边界 = edgeLen
  - 下边界 = edgeLen
- edgeLen = max(viewWidth, viewHeight) / 8

### 翻页行为

1. 手指进入右下角热区并停留 500ms → 翻到下一页
2. 手指进入左上角热区并停留 500ms → 翻到上一页
3. 翻页后，选区自动延伸：
   - **下一页方向**：选区终点延伸到新页第一行第一列（页首），手指继续拖动再扩展
   - **上一页方向**：选区起点延伸到新页最后一行最后一列（页尾），手指继续拖动再扩展
4. 翻页后如果手指仍在热区内 → 再次延迟 500ms 后继续翻页（持续跨页）
5. 可跨章

### 选区锁定

- 手指离开热区（但未抬起）时，立即停止自动翻页
- 选区锁定不再扩展，直到手指抬起
- 手指离开热区后可以在当前页内继续拖动选择

### 选区高亮保留

- 翻页后旧页面选区高亮需要保留
- 用户翻回旧页面时应能看到高亮
- 通过逻辑锚点（章索引+页索引+行+列）记录选区起止位置，翻页后重新映射到当前 `ContentTextView` 的 `selectStart`/`selectEnd`

### 锚点不变

- 选区的起点锚点在逻辑上保持不变（记录的是原始的章索引+页索引+行+列）
- 翻页后通过重新映射机制将逻辑锚点映射回当前可见页的相对位置

### 配置项

- 新增 `PreferKey.crossPageSelect`（Boolean，默认 false）
- 在 `AppConfig` 中添加 `val crossPageSelect: Boolean`
- 需要单独开关，默认关闭，只有开启时才启用跨页选择功能
- 前提条件：`textSelectAble` 必须为 true

## 架构设计

### 新增组件

#### `CrossPageSelectionManager`

挂在 `ReadView` 上的跨页选区管理器，职责：

1. 管理逻辑锚点（章索引+页索引+行+列四元组）
2. 检测手指是否在热区内
3. 管理自动翻页定时器
4. 翻页后重新映射锚点到当前可见页

```kotlin
class CrossPageSelectionManager(private val readView: ReadView) {
    // 逻辑锚点：记录选区起止的绝对位置
    data class SelectionAnchor(
        val chapterIndex: Int,
        val pageIndex: Int,
        val lineIndex: Int,
        val columnIndex: Int
    )

    var selectionStart: SelectionAnchor? = null
    var selectionEnd: SelectionAnchor? = null
    var isCrossPageSelecting: Boolean = false
    var crossPageDirection: PageDirection = PageDirection.NONE

    private var autoTurnRunnable: Runnable? = null
    private val autoTurnDelay = 500L

    // 热区矩形
    private val nextHotZone = RectF()   // 右下角
    private val prevHotZone = RectF()   // 左上角

    fun updateHotZones() { ... }
    fun isInNextHotZone(x: Float, y: Float): Boolean { ... }
    fun isInPrevHotZone(x: Float, y: Float): Boolean { ... }
    fun startAutoTurn(direction: PageDirection) { ... }
    fun stopAutoTurn() { ... }
    fun saveAnchorFromStart(textPos: TextPos) { ... }
    fun saveAnchorFromEnd(textPos: TextPos) { ... }
    fun remapAnchorToCurrent(): Pair<TextPos, TextPos>? { ... }
    fun restoreSelectionHighlight() { ... }
    fun reset() { ... }
}
```

#### `SelectionAnchor` 数据结构

逻辑选区锚点，独立于页面视图：

- `chapterIndex: Int` — 章节索引
- `pageIndex: Int` — 章节内页索引
- `lineIndex: Int` — 页内行索引
- `columnIndex: Int` — 行内列索引

### 修改现有组件

#### `ReadView.kt`

1. 新增 `CrossPageSelectionManager` 成员
2. 在 `onTouchEvent` 的 `ACTION_MOVE` 分支中，当 `isTextSelected = true` 时：
   - 检测手指是否在热区
   - 在热区内 → 启动 500ms 定时器
   - 不在热区 → 停止定时器，选区锁定
3. 在 `ACTION_UP` / `ACTION_CANCEL` 中清理跨页选择状态
4. 在 `onLongPress` 中保存逻辑锚点
5. 在 `upContent` 后调用 `restoreSelectionHighlight()` 恢复高亮

#### `ContentTextView.kt`

1. 新增方法 `remapSelection(start: TextPos, end: TextPos)`，用于翻页后重新设置选区
2. `setContent` 时检查是否需要恢复跨页选区高亮
3. `getSelectedText` 支持跨页拼接（通过逻辑锚点重建选中文本）

#### `PageDelegate.kt`

- 新增方法 `turnPageForSelection(direction: PageDirection)`，用于跨页选择时的无动画翻页（或短动画翻页），避免干扰选择手势

#### `constant/PreferKey.kt`

- 新增 `const val crossPageSelect = "crossPageSelect"`

#### `help/config/AppConfig.kt`

- 新增 `val crossPageSelect: Boolean get() = appCtx.getPrefBoolean(PreferKey.crossPageSelect, false)`

#### `ui/book/read/config/MoreConfigDialog.kt`

- 添加跨页选择开关项

### 数据流

```
用户长按选择文本
  → onLongPress() 保存逻辑锚点到 CrossPageSelectionManager
  → 用户拖动手指
  → ACTION_MOVE: 检测热区
    ├── 在热区 → postDelayed(500ms)
    │   └── 触发翻页 (pageFactory.moveToNext/Prev)
    │       └── upContent() 回调
    │           └── CrossPageSelectionManager.remapAnchorToCurrent()
    │               ├── 重新映射 selectStart 到当前页
    │               ├── 设置 selectEnd 到新页页首/页尾
    │               └── upSelectChars() 重建高亮
    │               └── 如果手指仍在热区 → 再次 postDelayed(500ms)
    │
    └── 不在热区 → stopAutoTurn(), 选区锁定
  → ACTION_UP: 清理跨页状态, 显示操作菜单
```

### 翻页方式

跨页选择时的翻页使用 `pageFactory.moveToNext(upContent=true)` / `pageFactory.moveToPrev(upContent=true)`，不经过 `PageDelegate` 的动画流程，避免动画干扰手势。翻页后 `upContent` 回调中由 `CrossPageSelectionManager` 重建选区。

### 跨章支持

`TextPageFactory.moveToNext` / `moveToPrev` 已内置跨章逻辑。`CrossPageSelectionManager` 的逻辑锚点记录 `chapterIndex`，跨章后通过 `ReadBook.durChapterIndex` 和 `pageFactory.pageIndex` 重新映射。

### getSelectedText 跨页拼接

当用户完成跨页选择后抬起手指，需要获取完整的跨页选中文本。由于翻页过程中旧页面内容已不在内存中，采取以下策略：
1. 如果 `crossPageCount == 0`（未跨页），走原有 `getSelectedText()` 逻辑
2. 如果跨页了，从逻辑锚点出发，遍历从 `selectionStart` 到 `selectionEnd` 之间的所有页面文本进行拼接。利用 `ReadBook.textChapter(0)` 和 `ReadBook.textChapter(-1)` 等 API 获取章节文本

## 错误处理

- 翻页失败（无下一页/上一页）→ 停止自动翻页，SnackBar 提示
- 章节未加载 → 等待加载完成后继续
- 逻辑锚点映射失败（页面内容变化）→ 取消选择，提示用户

## 测试要点

1. 长按选择 → 拖到右下角 → 500ms 后翻页，选区延续到新页页首
2. 持续停在右下角 → 连续翻页
3. 拖到左上角 → 500ms 后翻上一页，选区延续到新页页尾
4. 跨章翻页正常
5. 翻回旧页面时高亮保留
6. 手指离开热区后选区锁定
7. 手指抬起后显示操作菜单
8. 开关关闭时不影响原有选择行为
9. 横屏热区自动反转

## 范围限制

- 仅支持非滚动翻页模式（覆盖、滑动、仿真、无动画）
- 滚动翻页模式不适用（已有滚动内连续选择能力）
- 双页模式（doublePage）暂不支持跨页选择热区
