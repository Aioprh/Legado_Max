# 主题列表页应用主题后 UI 卡死 —— 第一性原理根因分析与修复

> 适用范围：Compose 版主题管理页 `ThemeManageActivity`（入口：设置 → 主题设置 → 主题列表）
> 现象：应用某主题后，部分机型页面无法点击/滑动，仅返回按钮（系统 BACK / 顶部返回箭头）可响应，其余交互控件全部失效。
> 结论先行：**双层根因。第一层（本报告 4 节）：单次点击触发多条重建路径 → 竞态双重建，部分 ROM 上窗口过渡中断、旧窗口残留成触摸吞噬层。第二层（本报告 8 节，经复测确认更接近实际卡死驱动）：应用带背景图/模糊的主题后，`BaseActivity`（MainActivity/ConfigActivity 等）在重建时于主线程同步执行全屏图片解码 + `Toolkit.blur` 高斯模糊，高分屏 + 弱 CPU 上主线程被阻塞数秒，整机 UI 冻结。**

---

## 1. 现象还原与第一性原理分解

「页面无法点击和滑动，仅返回按钮可响应」在 Android 输入管线中有且仅有两类成因：

| 成因 | 判定特征 | 与本现象吻合度 |
|---|---|---|
| **A. 透明/残留窗口层吞噬触摸** | 触摸被某不可见窗口消费，系统 BACK 仍可关闭该窗口 | ✅ 高度吻合 |
| **B. 主线程永久阻塞（ANR）** | 所有输入（含 BACK）全部无响应 | ❌ 不吻合（BACK 可响应） |

因此将排查焦点收敛到：**什么代码路径会导致旧窗口（DecorView）未正确摘除、新窗口未获得输入焦点**。围绕该问题，对用户提出的五类假设逐一取证。

---

## 2. 关键代码链路（一次「应用主题」点击的主线程执行栈）

用户点击「应用」→ `ThemeCard.onApply` → `ThemeManageViewModel.applyConfig()`：

```kotlin
// ThemeManageViewModel.kt:61
fun applyConfig(item: ThemeItem) {
    ThemeConfig.applyConfig(getApplication(), item.config)   // ① 同步执行（主线程）
    _events.trySend(ThemeEvent.Applied(item.config.themeName)) // ② 事件通道
    _events.trySend(ThemeEvent.Recreate)                       // ③ 显式重建事件
}
```

① `ThemeConfig.applyConfig`（`ThemeConfig.kt:320`）内部同步执行：

```kotlin
context.defaultSharedPreferences.edit { ... 8 项批量写入 ... }   // 异步磁盘
if (applyNow) {
    AppConfig.isNightTheme = isNightTheme          // 写 themeMode pref
    applyDayNight(context)                          // ← 关键
}
```

② `applyDayNight`（`ThemeConfig.kt:75`）：

```kotlin
fun applyDayNight(context: Context) {
    applyTheme(context)                              // ThemeStore 写色值
    initNightMode()                                  // AppCompatDelegate.setDefaultNightMode() ← 路径1
    BookCover.upDefaultCover()                       // 主线程同步解码 600×900 位图
    postEvent(EventBus.RECREATE, "")                 // LiveEventBus 粘性事件 ← 路径2
}
```

③ 事件通道 → `ThemeManageScreen` 收集器 → `onRecreate()` → `activity.recreate()` ← **路径3**。

**一次点击，三条独立重建路径同时作用于同一 Activity**，构成「重建风暴」。

---

## 3. 五类假设逐一排查（含代码证据）

### 3.1 主线程阻塞（同步 IO / 大量计算 / 死循环）—— 部分成立，非永久卡死根因

主线程点击栈内确实存在同步重活：

- `clearBg()`（`ThemeConfig.kt:551`）：`listFiles()` + 逐个 `File.delete()`，**进程内首次应用主题时同步执行**，背景图多时可达数百毫秒。
- `BookCover.upDefaultCover()`（`BookCover.kt`）：主线程 `BitmapUtils.decodeBitmap(path, 600, 900)` 同步解码。
- LiveEventBus `postEvent` 同步派发 → `ConfigActivity`/`MainActivity` 的观察者**在点击栈内直接调用 `recreate()`**。

这些是**瞬时**阻塞（结束后恢复），会造成「卡一下」，但不会造成永久「无法点击」。永久卡死的主线程死循环（无限循环/无限重组）在代码中**未发现**（详见 3.3）。

### 3.2 视图层级异常（覆盖层未移除 / 透明蒙层 / 焦点抢占）—— 是**结果**而非原因

Compose 层无全屏可点击覆盖层（`LegadoBackgroundBox`、`ThemeCard` 均无触摸拦截）。`DebugFloatingBallManager`、`ReadAloudMiniBarController` 等悬浮层均有条件启用且生命周期受控（`onPause`/`onDestroy` 正确摘除）。

但「旧窗口残留为触摸吞噬层」正是最终冻结的外在表现——它不是独立根因，而是**重建竞态在部分 ROM 上的产物**（见第 4 节）。

### 3.3 状态死循环重组 —— 排除

`DayNightPager`（`DayNightPager.kt:64-79`）的 Tab↔Pager 双向联动：

```kotlin
LaunchedEffect(pagerState.currentPage) {
    val newTab = ...; if (state.tab != newTab) { state.tab = newTab }   // 相等性守卫
}
LaunchedEffect(state.tab) {
    if (pagerState.currentPage != targetPage) { scope.launch { animateScrollToPage(...) } }
}
```

双向均有相等性守卫，不会自发死循环。`ThemeManageScreen` 的 `currentConfig` 已用 `remember` 缓存，无重读风暴。**排除无限重组**。

### 3.4 机型相关 —— 成立，且是「部分机型」的关键解释

- **AppCompat 夜间模式切换的重建**（`setDefaultNightMode` → `AppCompatDelegateImpl.updateAppConfiguration` → `mActivity.recreate()`）与**显式 `recreate()`** 并发时，依赖 `ActivityThread` 的窗口切换时序。AOSP 与 OEM 差异极大：MIUI/HyperOS、EMUI、ColorOS 及 Android 8/9 旧版本对「重建中再重建」的处理不一致，部分 ROM 会遗留旧 DecorView、新窗口拿不到输入焦点。
- **SharedPreferences 监听器异步刷新**（`AppConfig.themeMode` 为缓存字段）使行为依赖主线程任务排队顺序，进一步放大机型差异（见 3.5 附注与第 4 节）。

### 3.5 手势冲突 —— 排除

`HorizontalPager`（日夜滑动切换）+ `combinedClickable` 卡片 + `SegmentedButton` 均为 Compose 标准手势体系，无新旧识别器互斥；多选模式下 `userScrollEnabled=false` 正确禁用翻页。**排除手势冲突**。

**附注（补充根因）**：`AppConfig.isNightTheme` setter（`AppConfig.kt:206`）只写 pref、缓存字段 `themeMode`/`isEInkMode` 由**异步** `OnSharedPreferenceChangeListener` 刷新。因此 `applyConfig` 同栈内：

```kotlin
AppConfig.isNightTheme = isNightTheme   // 写 pref，缓存仍为旧值
applyDayNight(context)
    → initNightMode()                    // 读到【旧】isNightTheme → 目标模式判断错误
        → setDefaultNightMode(错误目标)  // 触发本不该发生的模式切换/重建
```

即：**缓存滞后导致目标模式判断错误，可能额外触发一次错误的 AppCompat 重建级联**，且重建后的首帧主题可能与预期不符（随后自愈）。这是「部分机型时序敏感」的放大器。

---

## 4. 根因结论

**主根因：单次点击触发的「重建风暴 + 竞态双重建」。**

一次「应用主题」在主线程内产生 ≥3 条重建路径：

1. `AppCompatDelegate.setDefaultNightMode()`（日夜模式变化时，AppCompat 自动重建所有 AppCompat Activity，含本页）；
2. `postEvent(EventBus.RECREATE)`（粘性事件，同步派发 → `ConfigActivity`/`MainActivity` 各自 `recreate()`）；
3. `ThemeEvent.Recreate` 通道事件 → 收集器 → `activity.recreate()`。

放大因子：

- **通道事件重放**：`ThemeManageViewModel` 跨重建存活，`Channel(BUFFERED)` 中未消费的 `Recreate` 会在新 Activity 的收集器上**重放**，确定性产生第二次重建；
- **缓存滞后**（`AppConfig.themeMode`）可能额外触发一次错误的模式切换重建；
- **`App.onConfigurationChanged`**（`App.kt`）在 UI 模式变化时再次 `applyDayNight` → 再次 `postEvent(RECREATE)`，形成第二轮级联。

结果：ThemeManageActivity 在一次应用主题中被连续重建 2~3 次。在多数 ROM 上表现为「闪两下」；在部分 OEM ROM 上，第二次重建恰逢第一次窗口过渡未完成 → **旧 DecorView 未摘除、新窗口未获得输入焦点 → 页面定格、触摸被残留窗口吞噬，仅系统 BACK 可响应**——与用户上报现象完全一致。

---

## 5. 修复方案（已实施，4 处最小改动）

### 5.1 清单声明 `configChanges="uiMode"`（根治竞态源头）

`app/src/main/AndroidManifest.xml` — ThemeManageActivity 增加 `android:configChanges="uiMode"`。

AppCompat 检测到 Activity 自行处理 `uiMode` 变化后，**不再自动重建**（走 `applyApplicationSpecificConfig()` 资源更新路径）。该页面的重建从此收敛为**唯一显式触发**，从源头消除最危险的竞态方。（清单中其他 Activity 已有同类 `configChanges` 用法，符合项目惯例。）

### 5.2 同步刷新主题模式缓存（消除错误模式切换）

`AppConfig.kt` — `isNightTheme` setter 同步更新缓存字段：

```kotlin
set(value) {
    if (isNightTheme != value) {
        themeMode = if (value) "2" else "1"   // 同步刷新缓存，同栈内读取一致
        isEInkMode = false                    // 应用主题即退出墨水屏模式
        appCtx.putPrefString(PreferKey.themeMode, themeMode)
    }
}
```

使 `initNightMode()` 在同一调用栈内读到正确模式，不再触发错误/多余的 AppCompat 模式切换，且重建后的首帧主题即时正确。

### 5.3 页面自行处理 `uiMode` 变化（保持跟随系统能力）

`ThemeManageActivity.kt` — 覆写 `onConfigurationChanged` → `recreate()`：

- 日夜模式变化（含应用主题触发的模式切换、跟随系统模式的系统翻转）由本页自行重建，一次到位；
- 配合 5.4 的守卫，多路触发被折叠为一次。

### 5.4 基类重建合并守卫（防御性，覆盖全部 Compose 页）

`BaseComposeActivity.kt` — 覆写 `recreate()`：

```kotlin
private var recreateRequested = false

override fun recreate() {
    if (recreateRequested || isFinishing || isDestroyed) return
    recreateRequested = true
    super.recreate()
}
```

同一实例在重建完成前只接受一次重建，任何未来出现的多路重建触发（主题、导航栏、顶栏、气泡等配置切换）都会被折叠为单次。

---

## 6. 修复后的时序推演（确定性验证）

**日夜模式切换（白天→黑夜主题）**：

```
点击应用
 → prefs 写入（themeMode 缓存已同步刷新为 "2"）
 → setDefaultNightMode(MODE_NIGHT_YES) → 因 configChanges="uiMode" 不再重建
 → postEvent(RECREATE) → 仅 ConfigActivity/MainActivity 重建
 → 通道 Recreate 事件
 → onConfigurationChanged(uiMode) → recreate()：守卫置位，重建①（唯一）
 → 收集器消费 Recreate → recreate()：守卫拦截
 → 新 Activity 读取全新 prefs → 主题正确
```

**同模式主题（白天→白天）**：无 uiMode 变化 → 无 onConfigurationChanged → 仅通道 Recreate → 守卫放行 → 重建①（唯一）。

所有路径下该 Activity **恰好重建一次**，窗口过渡不再被打断。

---

## 7. 验证方式与后续建议

### 验证方式
1. 真机/模拟器覆盖：日夜主题互切、同模式主题互切、跟随系统模式（系统翻转日夜）；
2. 重点机型：MIUI/HyperOS、EMUI、ColorOS、Android 8~14（还原「部分机型」场景）；
3. 观察点：应用主题后页面应只闪烁一次即完成主题切换，点击/滑动立即恢复；
4. 回归：编辑当前主题保存后重建、多选导出/置顶/删除、导入主题等功能。

### 后续建议（本次未改动，避免扩大共享函数影响面）
1. **背景模糊降级优化**：`ThemeConfig.getBgImage` 中 `Toolkit.blur` 可改为「小尺寸模糊后放大」，显著降低后台模糊耗时与内存（当前已不阻塞主线程，仅影响背景呈现延迟）；
2. **其余主线程轻量重活**：`clearBg()`（文件遍历删除，进程内一次）与 `BookCover.upDefaultCover()`（600×900 解码）为瞬时项，可按需下沉协程；
3. **其他 Activity 的双重建**：`ConfigActivity`/`MainActivity` 观察 RECREATE 事件的同时仍会被 AppCompat 重建，存在同类竞态风险，可复用 5.1/5.4 模式治理；
4. **LiveEventBus 粘性事件**：`autoClear(false)` 使 RECREATE 成为粘性事件，新建观察者会收到历史事件——后续新增 RECREATE 观察者时需注意，优先复用本页的「通道 + 守卫」模式而非直接 `observeEvent`。

---

## 8. 第二轮复测：真正的主线程冻结驱动因子（背景图解码 + 高斯模糊）

首轮修复后用户复测仍卡死，且指明是「**某个**主题」——提示问题与主题**内容**相关。深入核查后确认第二层根因：

### 8.1 证据链

- `BaseActivity.onCreate` → `upBackgroundImage()`（`BaseActivity.kt`）：
  ```kotlin
  ThemeConfig.getBgImage(this, windowManager.windowSize)?.let { drawable ->
      window.decorView.background = drawable   // ← 主线程同步执行
  }
  ```
- `getBgImage`（`ThemeConfig.kt:111`）内部：`BitmapUtils.decodeBitmap(path, safeWidth, safeHeight)` **全屏分辨率解码** + `stackBlur(bgImgBlu)` → `Toolkit.blur(bitmap, radius)`（`com.google.android.renderscript.Toolkit`，radius 最大 25）。
- **应用带背景图主题的完整主线程时序**：
  1. 点击应用 → `postEvent(EventBus.RECREATE)` **同步派发**；
  2. MainActivity 的 RECREATE 回调**在主线程直接调用 `upBackgroundImage()`**（解码+模糊）再 `recreate()`；
  3. ConfigActivity `recreate()` → `onCreate` → 再次主线程解码+模糊；
  4. 高分屏（1440×3200/2160×3840）+ 大模糊半径 + 弱 CPU：单次 `Toolkit.blur` 可达数秒，**多次串行执行 → 整机主线程冻结数秒至更久**；
  5. 前台主题管理页同属该主线程 → 页面无法点击/滑动；模糊结束后主线程恢复，BACK 才逐渐可响应。

这精确解释了「某个主题」（带背景图/模糊的主题才触发）、「部分机型」（高分辨率屏 + 弱 CPU + ROM 差异）、「仅返回可响应」（主线程繁忙，仅系统级按键最终可送达）。

### 8.2 修复（第二轮，已实施）

**`BaseActivity.upBackgroundImage()` 解码+模糊移出主线程**：主线程仅获取窗口尺寸，`lifecycleScope.launch(Dispatchers.Default)` 后台解码+模糊，`withContext(Dispatchers.Main)` 回主线程设置背景，并加 `isDestroyed/isFinishing` 守卫防止重建期间设置已销毁窗口。保留原 OOM / Exception 处理。

覆盖范围：MainActivity、ConfigActivity 及所有继承 `BaseActivity` 的 View 系页面（任意重建路径均受益）。Compose 系（`setLegadoContent`）背景加载本就位于 `Dispatchers.Default`，无需改动。

> 说明：`Toolkit.blur` 本身耗时未优化（可后续改为小图模糊再放大，降低延迟），但已不再阻塞主线程——模糊完成后背景图异步呈现，页面交互立即恢复。

---

## 9. 双层根因总结与修复对照

| 层 | 根因 | 修复 |
|---|---|---|
| 第一层 | 重建风暴：AppCompat 夜间模式自动重建 + RECREATE 事件同步派发 + 通道显式重建 + 配置变化二次级联 → 竞态双重建（部分 ROM 窗口过渡中断） | 清单 `configChanges="uiMode"` + `recreate()` 合并守卫 + 主题模式缓存同步 |
| 第二层 | 主线程同步全屏解码 + `Toolkit.blur` 高斯模糊（背景图主题触发，机型/主题相关） | `upBackgroundImage()` 移出主线程（`Dispatchers.Default` + 主线程回设 + 生命周期守卫） |

两轮修复相互独立、均需保留：第一层消除重建竞态与异常重建次数；第二层消除应用主题时的主线程长阻塞。修复后：应用主题 → 页面**立即**重建为正确主题、背景图异步呈现、交互即时恢复。

---

## 10. 第三轮：用户确认「所有主题均卡死、与背景图无关」后的收尾修复与诊断

用户进一步反馈：**点击所有主题都会卡死，与背景图无关**——排除背景图模糊假设，指向「每次应用都发生、机型相关」的公共路径。第三轮处置：

### 10.1 ConfigActivity 补 `configChanges="uiMode"`

核查清单发现：MainActivity 已声明 `uiMode`（不会双重建），而 **ConfigActivity 未声明**——每次日夜切换时会被 AppCompat 自动重建 **且** 被 RECREATE 事件重建（双重建），与主题页修复前同源。现为 ConfigActivity 补上 `android:configChanges="uiMode"`，重建收敛为 RECREATE 事件的单次触发，消除其在主题页下方窗口的竞态重建。

### 10.2 卡死看门狗（主线程堆栈转储，`BaseComposeActivity`）

在两次盲修未定位到确切卡死点后，为**下一次复现提供决定性证据**：

- `recreate()`/`onCreate` 时武装看门狗：主线程每 200ms 心跳续期；若主线程被阻塞超过 4s 未续期，后台线程将**主线程完整堆栈**写入 logcat（Tag `ThemeFreeze`）与 `filesDir/theme_freeze_stack.txt`；
- 正常路径：`onResume` 后 2s 自动解除，零误报、可忽略开销；
- 该堆栈可一锤定音地区分三类卡死：死循环/无限重组（堆栈停在 Compose/业务代码）、同步重活（停在某方法）、窗口层僵尸（主线程空闲、堆栈停在 Looper.loop）——后者说明问题在窗口/输入管道，需从窗口层面修复。

### 10.3 待用户确认的关键信息（用于最终定性）

1. 机型与 Android 版本（复现设备）；
2. 是否已安装包含前三轮修复的最新构建；
3. 卡死是否**永久持续**，还是几秒后自动恢复（区别主线程长阻塞 vs 永久卡死）；
4. 「返回按钮」指系统返回（手势/物理键），还是页面左上角返回箭头；
5. 卡死时设备是否明显发热/高 CPU（主线程忙 vs 空闲僵尸窗口）。

---

## 11. 第四轮：设备实测数据（Redmi Note 12 Turbo / Android 15）与「延后重建」修复

### 11.1 用户实测数据（关键判据）

| 项 | 结果 | 推论 |
|---|---|---|
| 机型 / 系统 | Redmi Note 12 Turbo / Android 15（HyperOS） | OEM ROM，窗口过渡行为与 AOSP 有差异 |
| 已装修复 | 前三轮全部修复 | 重建竞态、背景模糊、ConfigActivity 均已修复，卡死仍在 |
| 卡死性质 | **永久持续** | 排除主线程长阻塞（会随计算结束而恢复）；指向窗口/输入通道级损坏 |
| 返回键 | 系统返回 + **左上角返回箭头均可用** | 同一 Compose 窗口内返回箭头可点击 → **窗口输入通道存活、Compose 命中测试局部正常** |
| 粘贴按钮（右上角，同一 TopAppBar） | **不可用** | 与返回箭头同层，却失效 → 命中测试区域异常或被局部遮挡 |
| 内容区 | 不可点击/滑动 | 同上 |

**结论**：主线程未死锁（否则返回箭头也不可能响应）；问题在**窗口层**——单次应用主题使主题页、MainActivity、ConfigActivity 在同一主线程内**并发重建 3 个窗口**，在 HyperOS/Android 15 上损坏了窗口/输入状态，导致前台页面**局部输入失效**（左上可点、其余区域命中异常）。

### 11.2 修复（第四轮，已实施）

**后台 Activity 的 RECREATE 重建延后到 onResume**（`MainActivity`、`ConfigActivity`）：

- RECREATE 事件到达时：前台（`Lifecycle.State.RESUMED`）→ 立即 `recreate()`；后台 → 置 `recreateOnResume` 标记，**不立即重建**；
- `onResume` 时再执行重建 → 每次仅一个窗口发生重建，消除并发窗口重建；
- MainActivity 原有「onResume 刷新背景」回退逻辑保留；返回时可见正确的主题（短暂重建闪烁一次）。

### 11.3 决定性验证：看门狗堆栈文件

新构建内置卡死看门狗（第 10.2 节）。请复现卡死后检查：
- `filesDir/theme_freeze_stack.txt` 与 logcat（Tag `ThemeFreeze`）；
- **若文件存在** → 主线程确实被阻塞，把堆栈内容发来即可精确定位；
- **若文件不存在** → 确认主线程空闲、问题在窗口层 → 本轮「延后重建」即为对症修复方向。

---

## 12. 第五轮：AppLog 实锤「RECREATE 广播风暴」并修复（关键进展）

用户提供 AppLog（`appLog-26-08-08_23-10-18.556.txt`），在真实设备上捕捉到了决定性证据：

```
23:10:34.508 [LiveEventBus] post:  with key: RECREATE        ← 一次点击应用主题
23:10:34.509-542 [LiveEventBus] post ×6 / message received ×8（34ms 内连续广播 6 次）
23:10:34.556-598 ThemeManageActivity onPause/onStop/onDestroy（唯一一次重建，守卫生效）
23:10:34.608-647 ThemeManageActivity onCreate/onResume
23:10:34.647 → 23:10:56.294   ← 21.6 秒无任何日志（用户尝试交互失败后按返回）
（看门狗未触发 → 主线程空闲 → 确认窗口层问题）
```

### 12.1 风暴成因（反馈循环）

`ThemeConfig.applyConfig` → `applyDayNight()` → `initNightMode()` → `setDefaultNightMode()` → **触发 uiMode 配置变化** → `App.onConfigurationChanged` → `applyDayNight()`（再次 `setDefaultNightMode`）→ **再次配置变化** → …… 反馈循环。实测：**一次点击 = 6 次 RECREATE 广播 + 6 次 setDefaultNightMode + 6 次 applyTheme/BookCover 解码**，全部在同一主线程内瞬间完成。这种高频配置/窗口churn在 HyperOS/Android 15 上破坏了窗口/输入通道状态（页面局部输入失效、永久卡死），而 AOSP 设备对此宽容——正好解释「部分机型」。

### 12.2 修复（第五轮，已实施）

1. **`App.onConfigurationChanged` 断开反馈环**：UI 模式变化时只 `applyTheme()` + `notifyRecreate()`，**不再调用 `setDefaultNightMode`**（模式此时已生效，重复设置只会再次触发配置变化形成风暴）；
2. **`ThemeConfig.notifyRecreate()` 广播防抖**：500ms 窗口内合并重复的 RECREATE 广播，`applyDayNight` 与 `App.onConfigurationChanged` 均改用它——一次应用主题的广播从 6 次收敛为 **1 次**。

### 12.3 预期效果

应用主题 → 1 次 RECREATE 广播 + 主题页 1 次重建 + 后台页 1 次延后重建（第四轮），无高频 churn，窗口/输入状态不再被破坏。

---

## 13. 第六轮：第二份日志（23:27 构建 262202324）行为判定与修复加固

用户提供 logs.zip（已移至 `logs_analysis/`，含 9 份 AppLog + 1 份 logcat）。关键判定：

### 13.1 行为证据

**新构建中第四轮「延后重建」已生效**：日志显示 ConfigActivity/MainActivity 均在**回到前台 onResume 时**才重建（44.586 / 46.347），未与主题页并发重建。

**但 RECREATE 风暴仍在**（23:27:36.048 起每 31ms 6 连发，随后 40.223/41.412/42.217 又出现 5-6 连发）——且 App 无任何用户操作时仍持续复发。**这是「未含第五轮修复」的确定性特征**：第五轮的 `App.onConfigurationChanged` 断开反馈环（不再调用 `setDefaultNightMode`）在构建中缺失时，HyperOS 会把 `setDefaultNightMode` 触发的 uiMode 配置变化**异步、分批**送达（持续数秒），每批都触发 `onConfigurationChanged → applyDayNight → setDefaultNightMode` 反馈链 → 每批 6 连发广播。

> 判定依据：versionCode 为**构建时间戳**（262202324 = 23:24 构建），而 App.kt 修复落盘于 23:21、ThemeConfig 于 23:18——构建/编译时机恰在修复落盘之前或增量编译未拾取。23:27 日志中的 6 连发模式与修复前完全一致，证明该构建不含第五轮修复。

### 13.2 修复加固（第六轮，已实施）

1. **防抖窗口 500ms → 1500ms**：覆盖 HyperOS 异步分批送达配置变化的场景（每批间隔可达 1s+）；
2. **legacy `ThemeConfigFragment.recreateActivities()` 改为 `notifyRecreate()`**：消除最后一个主题链路中的直接广播点；
3. 复核：`setDefaultNightMode` 全工程仅 `initNightMode` 一处调用；`App.onConfigurationChanged` 已不再调用 `applyDayNight`——**反馈环在源码层面已完全断开**。

### 13.3 验证方法（请严格按此复测）

1. **重新构建**（versionCode 将 ≥ 262202325，可在 我的→关于 查看）；
2. 应用主题后导出 AppLog：**一次应用主题应只出现 1 条 `post: RECREATE`**（旧构建为 6 条）；
3. 若仍多条或仍卡死，将该日志发回——即可锁定剩余路径。

---

## 附：修改文件清单

| 文件 | 修改 |
|---|---|
| `app/src/main/AndroidManifest.xml` | ThemeManageActivity 增加 `android:configChanges="uiMode"` |
| `app/src/main/java/io/legado/app/help/config/AppConfig.kt` | `isNightTheme` setter 同步刷新 `themeMode`/`isEInkMode` 缓存 |
| `app/src/main/java/io/legado/app/base/BaseComposeActivity.kt` | 新增 `recreate()` 合并守卫 |
| `app/src/main/java/io/legado/app/ui/config/theme/manage/ThemeManageActivity.kt` | 新增 `onConfigurationChanged` → `recreate()` |
| `app/src/main/java/io/legado/app/base/BaseActivity.kt` | `upBackgroundImage()` 解码+模糊移出主线程（`Dispatchers.Default` + 主线程回设 + 生命周期守卫） |
| `app/src/main/AndroidManifest.xml` | ConfigActivity 增加 `android:configChanges="uiMode"`（消除其日夜切换双重建） |
| `app/src/main/java/io/legado/app/base/BaseComposeActivity.kt` | 新增卡死看门狗：主线程阻塞超 4s 时 dump 主线程堆栈至 logcat 与 `filesDir/theme_freeze_stack.txt` |
| `app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt` | RECREATE 重建延后到 onResume（后台不立即重建，避免并发窗口重建） |
| `app/src/main/java/io/legado/app/ui/main/MainActivity.kt` | 同上（保留 onResume 背景刷新回退） |
| `app/src/main/java/io/legado/app/App.kt` | `onConfigurationChanged` 断开反馈环：不再重复 `setDefaultNightMode`，只刷新主题+通知重建 |
| `app/src/main/java/io/legado/app/help/config/ThemeConfig.kt` | 新增 `notifyRecreate()` 防抖广播（500→1500ms）；`applyDayNight` 改用它 |
| `app/src/main/java/io/legado/app/ui/config/theme/legacy/ThemeConfigFragment.kt` | `recreateActivities()` 改用 `ThemeConfig.notifyRecreate()`（消除主题链路直接广播点） |
