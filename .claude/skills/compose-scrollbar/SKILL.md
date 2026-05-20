---
name: compose-scrollbar
description: Jetpack Compose 可拖拽垂直滚动条的正确实现方式，包含所有已知坑点和修复方案
---

# Compose 可拖拽垂直滚动条 Skill

## 背景

Compose 没有内置的可拖拽滚动条。自定义实现踩过大量坑，此 Skill 记录唯一验证通过的模式。

## 核心文件

已有的可复用组件：`app/src/main/java/io/legado/app/ui/widget/components/VerticalScrollbar.kt`

提供两个重载：
- `VerticalScrollbar(state: LazyListState, ...)` — LazyColumn 用
- `VerticalScrollbar(state: ScrollState, ...)` — Column + verticalScroll 用

## 使用方式

### LazyColumn 场景

```kotlin
val listState = rememberLazyListState()
Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) { ... }
    VerticalScrollbar(
        state = listState,
        modifier = Modifier.align(Alignment.CenterEnd)
    )
}
```

### Column + verticalScroll 场景（如弹窗详情）

```kotlin
Box(modifier = Modifier.weight(1f)) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) { ... }
    VerticalScrollbar(
        state = scrollState,
        modifier = Modifier.align(Alignment.CenterEnd)
    )
}
```

## 已知坑点（全部踩过）

### 1. `weight(0f)` 导致闪退

**症状**：`java.lang.IllegalArgumentException: invalid weight; must be greater than zero`
**原因**：用 Column + Spacer 来定位滚动条 thumb，当滚动到边界时 weight 可能为 0。
**修复**：绝对不要用 weight 定位 thumb。用 `Box + offset { IntOffset(0, px) }` 代替。

### 2. 滚动条永远不出现（onSizeChanged 鸡生蛋问题）

**症状**：滚动条从不显示，trackHeightPx 始终为 0。
**原因**：`if (trackHeightPx <= 0f) return` 跳过了包含 `onSizeChanged` 的 Box，导致尺寸永远无法被测量。
**修复**：外层 Box 必须始终渲染（承载 onSizeChanged），只在内部条件绘制 thumb。

### 3. 拖拽时和手指"对抗"

**症状**：拖动滚动条时它来回跳动，无法正常使用。
**原因**：用 `scrollFraction + dragAmount/trackHeightPx` 增量计算，scroll 改变 scrollFraction，scrollFraction 改变下一次目标，形成反馈循环。
**修复**：用 `change.position.y / trackHeightPx` 做绝对位置映射，手指在顶部=滚到开头，底部=滚到结尾。

### 4. `mutableFloatStateOf` 委托不支持

**症状**：编译错误。
**修复**：用 `mutableStateOf(0f)` 代替。

### 5. raw pointer API 找不到

**症状**：`awaitPointerEventScope`、`awaitFirstDown` 等 unresolved。
**修复**：使用 `detectVerticalDragGestures` 高级 API，不要用底层 pointer API。

### 6. `onSizeChanged` 必须放在始终渲染的元素上

**规则**：承载 `onSizeChanged` 的元素不能有任何条件返回包裹它。

## 实现原则

1. **外层 Box 始终渲染**，承担 `onSizeChanged` 和 `pointerInput`
2. **thumb 用 `offset { IntOffset(...) }` 定位**，不用 weight
3. **拖拽用绝对位置** (`change.position.y / trackHeightPx`)，不用增量
4. **thumb 最小高度** 32px，防止缩到看不见
5. **scrollFraction < 0.99 才绘制 thumb**，内容太短不需要滚动条

## 改造现有界面的步骤

1. 在内容区域外层包一个 `Box(Modifier.fillMaxSize())`
2. 内容区域加 `modifier` 参数（如果还没有）
3. 创建 `val listState = rememberLazyListState()` 或 `val scrollState = rememberScrollState()`
4. 将 state 传给内容组件
5. 在 Box 内添加 `VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))`

## 调试滚动条问题

如果滚动条不出现或行为异常，检查清单：
- [ ] 外层 Box 是否始终渲染（没有被 if/return 包裹）
- [ ] onSizeChanged 是否在外层 Box 上
- [ ] thumb 定位是否用了 offset 而非 weight
- [ ] 拖拽是否用了 change.position.y 绝对映射
- [ ] 内容是否确实超出可视区域（`visible.size < totalItems` 或 `maxValue > 0`）
