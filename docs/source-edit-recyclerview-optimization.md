# 源编辑界面 RecyclerView 性能优化方案

## 问题概述

源编辑界面（`BookSourceEditActivity` / `RssSourceEditActivity`）的 RecyclerView 存在严重的帧卡顿。主线程绝大部分时间消耗在 **measure + layout** 阶段，来不及执行 draw 就已超 16ms 预算。

### 根因分析

每个 RecyclerView item 的 View 结构：

```
TextInputLayout (wrap_content)
  └── CodeView (wrap_content)  ← 继承链: CodeView → ScrollMultiAutoCompleteTextView
                                    → AppCompatMultiAutoCompleteTextView
                                    → AutoCompleteTextView → EditText → TextView → View
```

三重因素叠加导致 measure 极重：

1. **CodeView 的 onMeasure 包含完整文本排版**：TextView 的 measure 需要创建 `StaticLayout` 对全文做换行计算，复杂度 O(n)，n 为字符数。当字段含几万字符时，单次 measure 可耗 5~10ms。
2. **ScrollMultiAutoCompleteTextView.onMeasure 额外调用 `initOffsetHeight()`**：每次 measure 后都访问 `layout` 对象做滚动边界计算。
3. **TextInputLayout.onMeasure 重量级**：测量子 View + hint 浮动动画空间 + error 预留，多次 `measureChildWithMargins`。

加上 Tab 切换时调用 `notifyDataSetChanged()` 全量刷新 13 个 item，每次刷新触发 13 次重量级 measure，总耗时可达 65~130ms，远超 16ms 帧预算。

### 约束

- **不能改用 ScrollView**：单个规则字段可能含几万甚至几十万字符，ScrollView 预创建全部 View 会内存溢出。RecyclerView 的 ViewHolder 复用是刚需。
- 已有"全屏编辑"入口（`CodeEditActivity`），用户可在全屏中查看/编辑完整内容。

---

## 优化方案（按优先级排序）

### P0：bind 时关闭 CodeView 内部高亮级联

**问题**

`CodeView` 在 `init` 块中注册了内部 `mEditorTextWatcher`（`CodeView.kt` line 102）。`onBindViewHolder` 调用 `editText.setText(editEntity.value)` 时，虽然外层业务 TextWatcher 是 `setText` 之后才注册的不会被触发，但 CodeView 自身的 `mEditorTextWatcher` 是构造时就注册好的，`setText` 一定会触发它。

触发链：

```
setText() → mEditorTextWatcher.onTextChanged()
  → postDelayed(mUpdateRunnable, 500ms)
    → 500ms 后: highlightWithoutChange(source)
      → clearSpans() + highlightSyntax()  (全文正则匹配 + span 增删)
        → span 变更 → 再一次 requestLayout
```

13 个 item 同时 bind，500ms 后有 13 次高亮 + 13 次额外 `requestLayout` 堆叠在同一帧。

**方案**

给 `CodeView` 增加一个 flag（如 `var skipNextHighlight = false`），在 `mEditorTextWatcher.onTextChanged` 中检查此 flag，为 true 时直接 return，不 postDelayed。

在 Adapter 的 `bind()` 中：

1. 设置 `editText.skipNextHighlight = true`（或提供 `setHighlightEnabled(false)` 方法）
2. `editText.setText(editEntity.value)`
3. bind 完成后恢复 `editText.skipNextHighlight = false`（确保用户手动编辑时高亮正常）

同时调用 `editText.cancelHighlighterRender()` 清除可能已排队的旧高亮回调。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/widget/code/CodeView.kt` — 增加 flag + 修改 `mEditorTextWatcher`
- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — `bind()` 中设置 flag
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：消除 bind 后 500ms 的 13 次全文正则匹配 + span 操作 + 13 次 requestLayout。每次高亮约 2~5ms（取决于文本长度），总计省 26~65ms。

---

### P1：bind 时按需 setText（内容未变化则跳过）

**问题**

每次 `notifyDataSetChanged` 或 `notifyItemRangeChanged` 触发 `onBindViewHolder`，无论 item 内容是否变化都会调 `editText.setText(editEntity.value)`。当文本有几万字符时，`setText` 触发的 `StaticLayout` 重新排版成本极高。

**方案**

给 ViewHolder 增加字段记录上次绑定的 key 和 value 摘要。`bind()` 中检查：

- 如果 `editEntity.key == lastBoundKey` 且 `editEntity.value == editText.text.toString()`，说明 ViewHolder 已展示正确内容，直接 return，不调 `setText`
- 只有内容真正不同时才调 `setText`

这在 Tab 切换回切（数据未变）场景下特别有效——大部分 ViewHolder 命中"未变化"路径，直接跳过。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — ViewHolder 增加缓存字段 + bind 逻辑
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：对内容未变化的 item，从 5~10ms 降到接近 0。全量刷新 13 个 item 时大部分可能命中"未变化"路径。

---

### P2：CodeView 的 maxLines 设默认上限

**问题**

`AppConfig.sourceEditMaxLine` 默认为 `Int.MAX_VALUE`（`AppConfig.kt` line 938），此时 `editText.maxLines = Int.MAX_VALUE` 无效，TextView 的 `onMeasure` 必须对**完整文本**做 StaticLayout 排版来计算自然高度。几万字符的排版耗时可达 5~10ms/item。

**方案**

无论用户设置如何，bind 时给 `editText.maxLines` 设一个合理上限（如 30 行）。用户需要查看/编辑完整内容时，通过已有的"全屏编辑"按钮（`menu_fullscreen_edit` → `CodeEditActivity`）进入全屏编辑。

这样 TextView 的 measure 只需排版 30 行（无论全文多长），measure 成本从 O(全文字符数) 降到 O(30 行字符数)。

**实现要点**

- Adapter 的 `editEntityMaxLine` 取值逻辑调整：当 `AppConfig.sourceEditMaxLine >= 999` 时，用一个固定值（如 30）而非 `Int.MAX_VALUE`
- 不改变 `AppConfig` 本身的值，只在 Adapter 层做 clamp
- `BookSourceEditActivity.sendText()` 中基于 `editEntityMaxLine >= 999` 判断的逻辑需同步适配

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — `editEntityMaxLine` 计算
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上
- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt` — `sendText()` 中的 `>= 999` 判断适配

**预期收益**：对"几万字符"场景最直接的优化。从可能的 5~10ms/item 降到 < 0.5ms/item。这是对大文本场景最对症的措施。

**风险**：UX 变化——列表中只显示前 30 行。但已有全屏编辑入口，合理。

---

### P3：增大 RecyclerView 缓存 + setHasFixedSize

**问题**

- 默认 `itemViewCacheSize = 2`，Tab 切换时 13 个 item 大部分 ViewHolder 进 Pool 超限销毁（默认上限 5 个/type），切回来重新 inflate XML + 3 次 `addSyntaxPattern`。
- 未调用 `setHasFixedSize(true)`，每次 item 变化触发 RecyclerView 自身 measure。

**方案**

在 Activity 的 `initView()` 中：

```kotlin
binding.recyclerView.setHasFixedSize(true)
binding.recyclerView.setItemViewCacheSize(15)

// BookSource: 只有一种 type
binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)

// RssSource: 两种 type（checkBox=1, text=0）
binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
binding.recyclerView.recycledViewPool.setMaxRecycledViews(1, 10)
```

`setHasFixedSize(true)` 是安全的：RecyclerView 高度为 `match_parent`（布局确认），不受内容数量影响。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt` — `initView()`（line 431 附近）
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt` — `initView()`（line 345 附近）

**预期收益**：省掉 RecyclerView 自身 measure；Tab 来回切换时避免反复 inflate XML + addSyntaxPattern（每次约 1~2ms × 13 = 13~26ms）。

---

### P4：Tab 切换用 notifyItemRangeChanged 替代 notifyDataSetChanged

**问题**

`editEntities` 的 setter 中 `notifyDataSetChanged()` 会让所有 ViewHolder 全部失效，强制 detach + re-bind。

**方案**

去掉 setter 里的 `notifyDataSetChanged()`，在 Activity 的 `setEditEntities()` 中手动处理：

```kotlin
// 伪代码
val oldSize = adapter.editEntities.size
adapter.editEntities = newEntities  // 只赋值，不 notify
val newSize = newEntities.size
if (newSize > oldSize) {
    adapter.notifyItemRangeChanged(0, oldSize)
    adapter.notifyItemRangeInserted(oldSize, newSize - oldSize)
} else if (newSize < oldSize) {
    adapter.notifyItemRangeChanged(0, newSize)
    adapter.notifyItemRangeRemoved(newSize, oldSize - newSize)
} else {
    adapter.notifyItemRangeChanged(0, newSize)
}
```

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — setter 去掉 `notifyDataSetChanged()`
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上
- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt` — `setEditEntities()`（line 529）
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt` — `setEditEntities()`（line 399）

**预期收益**：RecyclerView 从"全量布局"变为"增量更新"，配合 P3 的缓存增大，ViewHolder 留在屏幕上直接 re-bind，不需要 detach/reattach。`notifyItemRangeChanged` 还能触发 RecyclerView 的 prefetch 机制，在多帧内分摊。

**注意**：`scrollToField` / `scrollToEntity` 等场景中也有 `adapter.notifyDataSetChanged()` 调用（`BookSourceEditActivity.kt` line 265、`RssSourceEditActivity.kt` line 221/286），需一并审查是否可改为增量更新。

---

### P5：TextWatcher 移到 ViewHolder init，bind 时不再反复注册/反注册

**问题**

`bind()` 每次创建新 `TextWatcher` 对象（line 75-92），然后 `removeTextChangedListener` 旧的 + `addTextChangedListener` 新的。每次 bind 有 2 次 ArrayList 操作 + 1 次对象分配。

**方案**

在 ViewHolder 的 `init` 块中一次性创建 `TextWatcher`，通过 `editText.getTag(R.id.tag)` 获取当前绑定的 entity key，写入对应 entity。`bind()` 中只更新 tag 指向的 entity，不反复 add/remove listener。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — ViewHolder `init` 块 + `bind()` 精简
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：每个 bind 省 2 次 ArrayList 操作 + 1 次对象分配。单次约 0.5ms，13 个省 ~6ms。收益不大但在全量刷新时有助于降低峰值。

---

### P6：去掉 bind 中的 clearFocus()

**问题**

`bind()` 末尾的 `editText.clearFocus()`（line 95）对每个 item 执行。`notifyDataSetChanged` 全量刷新时 13 个 item 依次 clearFocus，触发 `ViewTreeObserver.OnGlobalFocusChangeListener` 回调（Activity line 436），每个回调又 `postDelayed { sendText("") }`。`clearFocus` 本身也可能触发 `requestLayout`。

**方案**

Activity 的 `setEditEntities()` 已经调用 `window.decorView.rootView.clearFocus()`（line 540）做了一次全局清焦，Adapter 不需要逐个清焦。如果有 ViewHolder 复用时焦点残留的问题，在 `onViewRecycled` 中调一次 `clearFocus` 即可。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — 删除 `bind()` 中的 `editText.clearFocus()`
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：减少 13 次 clearFocus → 13 次 requestLayout + 13 次焦点回调。

---

### P7：onViewAttachedToWindow 中的 cursor 刷新优化

**问题**

`bind()` 中注册的 `OnAttachStateChangeListener.onViewAttachedToWindow`（line 54-59）做了：

```kotlin
editText.isCursorVisible = false
editText.isCursorVisible = true
```

连续赋值会触发 `invalidate` + 可能的 `requestLayout`（cursor 绘制区域变化）。每次 attach 都执行。

**方案**

只在 item 真正获得焦点时才设 cursor visible，或移到 `setOnFocusChangeListener` 中。不需要每次 attach 都强制刷新。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — `bind()` 中的 listener 逻辑
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：减少每次 attach 的 cursor 刷新开销（约 0.3ms × 13）。

---

### P8：ScrollMultiAutoCompleteTextView.onTextChanged 中的 initOffsetHeight 延后

**问题**

```kotlin
override fun onTextChanged(...) {
    super.onTextChanged(...)
    initOffsetHeight()  // ← setText 时触发，同步执行
}
```

`setText` → `onTextChanged` → `initOffsetHeight` → 访问 `layout` 对象做计算。然后 measure 阶段 `onMeasure` 中的 `initOffsetHeight()` 又会再算一次。

**方案**

- `onTextChanged` 中的 `initOffsetHeight()` 改为 `post { initOffsetHeight() }`，推迟到下一帧
- 或加 dirty flag：`onTextChanged` 只设 `mOffsetHeightDirty = true`，`onMeasure` 中检查 flag 才重新计算

**涉及文件**

- `app/src/main/java/io/legado/app/ui/widget/text/ScrollMultiAutoCompleteTextView.kt` — `onTextChanged`（line 94-102）

**预期收益**：每次 `setText` 省掉一次同步 `layout.height` 计算。13 个 item 全量 bind 时省 13 次。收益不大但配合其他项效果叠加。

---

### P9：scrollToPosition 与 notify 合并执行

**问题**

当前 `setEditEntities` 中：

```kotlin
adapter.editEntities = entities  // 触发 notifyDataSetChanged（一次 layout pass）
binding.recyclerView.scrollToPosition(0)  // 又触发一次 layout pass
```

两次操作各自触发一次布局流程。

**方案**

先 `scrollToPosition(0)` 再 `notifyItemRangeChanged`，或合并到同一个 `post { }` 块中执行，确保只走一次 layout pass。

**涉及文件**

- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt` — `setEditEntities()`（line 529-541）
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt` — `setEditEntities()`（line 399-408）

**预期收益**：Tab 切换时从两次 layout pass 降为一次。

---

### P10（可选）：TextInputLayout 替换为轻量布局

**问题**

`TextInputLayout` 的 `onMeasure` 成本高：hint 浮动动画、error 空间预留、多次 `measureChildWithMargins`。

**方案**

如果不需要 Material Design 的浮动 hint 动画和 error 显示，把 `item_source_edit.xml` 的根布局从 `TextInputLayout` 改为简单 `LinearLayout` + `TextView`（hint 标签）+ `CodeView`。hint 用独立 `TextView` 显示，不做浮动动画。

**涉及文件**

- `app/src/main/res/layout/item_source_edit.xml` — 根布局替换
- `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditAdapter.kt` — ViewBinding 类型变更
- `app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt` — 同上

**预期收益**：每个 item 省掉 `TextInputLayout.onMeasure` 的多次子 View 测量和动画计算（约 1~2ms/item）。

**风险**：UI 变化，需设计确认。浮动 hint 是 Material Design 规范的一部分。

---

## 实施建议

### 第一批（快速见效，1~2 小时）

- **P0**：bind 时关闭 CodeView 高亮级联
- **P3**：setHasFixedSize + 增大缓存
- **P6**：去掉 bind 中 clearFocus

这三项改动小、风险低，能消除大部分级联开销和重复 inflate 开销。

### 第二批（核心优化，2~3 小时）

- **P2**：maxLines 设默认上限 ← **对大文本场景最关键**
- **P1**：bind 时按需 setText
- **P4**：notifyItemRangeChanged 替代 notifyDataSetChanged

这三项直接削减 measure 成本和 setText 开销。

### 第三批（收尾微调，1~2 小时）

- **P5**：TextWatcher 移到 init
- **P7**：onViewAttachedToWindow cursor 优化
- **P8**：initOffsetHeight 延后
- **P9**：scroll + notify 合并

### 可选

- **P10**：TextInputLayout 替换（需设计评审）

---

## 验证方式

1. **Systrace / Perfetto**：对比优化前后的 measure + layout 耗时，重点关注 Tab 切换帧
2. **Profiler CPU trace**：在 Tab 快速来回切换场景下录制 CPU trace，确认 `setText` / `StaticLayout` 调用次数和耗时下降
3. **帧率监控**：使用 `adb shell dumpsys gfxinfo` 对比优化前后的 janky frames 比例
4. **功能回归**：确保 Tab 切换数据正确、TextWatcher 正常写入 entity、全屏编辑回传值正常、语法高亮在用户编辑时正常工作
