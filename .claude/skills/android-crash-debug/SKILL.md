---
name: android-crash-debug
description: Android/Kotlin 应用崩溃调试方法论。诊断 UI 冻结 + 崩溃、协程架构中的线程安全问题、Compose 悬浮层崩溃以及 ANR 问题。适用于用户报告：应用冻结后崩溃、UI 卡住、ANR、打开对话框/面板时崩溃，或协程密集型代码中原因不明的崩溃。
user-invocable: true
---

# /android-crash-debug — Android 崩溃诊断与修复

系统化方法论，用于诊断和修复 Android 应用崩溃，特别是涉及 UI 冻结、协程线程安全和 Compose 悬浮层问题的崩溃。

传入参数：`$ARGUMENTS` — 描述崩溃症状。

---

## 步骤 1：分类崩溃症状

询问用户（或从 `$ARGUMENTS` 推断）匹配哪种模式：

| 模式 | 主要症状 | 可能根本原因 |
|---------|------------|-------------------|
| **A：冻结后崩溃** | UI 冻结数秒，然后应用退出并生成崩溃日志 | 主线程上的未捕获异常 → CrashHandler `Thread.sleep()` → 系统 ANR 杀死进程 |
| **B：立即崩溃** | 操作时应用立即崩溃 | 同步代码中的 NPE、ClassCastException 或资源错误 |
| **C：冻结但不崩溃** | UI 卡住但应用不崩溃 | 主线程被同步 I/O、`runBlocking` 或死锁阻塞 |
| **D：打开悬浮层/对话框时崩溃** | 仅在打开对话框、面板或悬浮层时崩溃 | Compose 生命周期问题、ViewModel 作用域不匹配或窗口状态冲突 |

---

## 步骤 2：追踪崩溃处理器

Android 应用通常有自定义的 `Thread.UncaughtExceptionHandler`。首先找到它 — 它揭示了崩溃时的实际行为。

```
搜索关键词: "UncaughtExceptionHandler", "CrashHandler", "handleException"
```

**需要检查的关键点**：处理器是否在崩溃线程上调用 `Thread.sleep()`？如果崩溃发生在主线程上，这会导致应用退出前出现可见的冻结（模式 A）。

**在处理器中需要查找的内容**：
- `Thread.sleep()` — 导致退出前的冻结
- `saveCrashInfo2File()` — 检查崩溃日志目录获取实际的堆栈跟踪
- `Looper.loop()` — 某些处理器通过重新循环来吸收某些异常

---

## 步骤 3：找出从触发到崩溃的调用链

映射用户执行崩溃触发操作时发生的确切顺序：

1. **识别入口点**：什么 UI 元素触发了崩溃？（按钮点击、菜单项、悬浮球等）
2. **追踪处理器**：什么方法处理点击/轻触？
3. **跟随链路**：接下来调用什么？（Activity 跳转、对话框显示、ViewModel 创建等）
4. **找到崩溃发生的位置**：异常在哪一步暴露出来？

**对于悬浮层/对话框崩溃**，特别追踪以下内容：
- 悬浮层/对话框如何添加到窗口？（在 `decorView` 上调用 `addView()`、`WindowManager.addView()`、Dialog `show()`）
- ViewModel 是如何创建的？（`viewModel()`、`ViewModelProvider()`）
- 向 Compose 提供了什么 `LocalViewModelStoreOwner`？
- 关闭时会发生什么？（视图移除、状态重置、生命周期回调）

---

## 步骤 4：检查线程安全性（模式 A 最常见的原因）

这是协程密集型应用"冻结后崩溃"的首要原因。检查所有从多个线程访问的可变状态。

### 4a. 查找共享可变状态

搜索从以下两者读写的变量：
- **主线程**（UI 回调、`Dispatchers.Main`、`viewModelScope`）
- **后台线程**（`Dispatchers.IO`、`Dispatchers.Default`、flow 收集上下文）

```
搜索关键词: MutableSharedFlow, MutableStateFlow, ViewModel 中的 "var _"
```

### 4b. 检查同步

对于每个共享变量，验证：
- 是否是 `@Volatile`？（如果从多个线程读取则必需）
- 读写是否受到相同同步原语保护？（不要在相同数据上混用 `Mutex` 和 `synchronized()`）
- 在 Flow `onEach`/`map` 操作符中：操作符在哪个线程运行？（取决于上游 flow 的发送上下文）

### 4c. 常见线程安全问题

| 问题 | 如何检测 | 修复方法 |
|-----|--------------|-----|
| `var` 字段从多个线程读写但没有 `@Volatile` | 字段同时在 `Dispatchers.Main` 协程和 `Dispatchers.Default` flow 收集器中赋值 | 添加 `@Volatile` + 在所有访问处加上 `synchronized(this) { }` |
| 在相同数据上混用同步原语 | `emit()` 使用 `Mutex`，`getXxx()` 在同一集合上使用 `synchronized()` | 统一为一种原语（非挂起函数优先使用 `synchronized()`）|
| Flow 操作符在发送线程而非主线程运行 | `SharedFlow` 从 `Dispatchers.Default` 发送，订阅者更新 UI 状态 | 在 `launchIn(viewModelScope)` 前使用 `flowOn(Dispatchers.Main)` |
| 列表被复制后在读取时被修改 | `listOf(...)` + `addAll(_sharedList)` 但没有锁 | 始终在 `synchronized` 块内复制 |
| 在同一数据上混用 Mutex 和 synchronized | emit() 使用 Mutex，getXxx() 使用 synchronized() | 统一为一种原语（非挂起函数优先使用 synchronized()）|

### 4d. 如何验证线程上下文

对于每个 `SharedFlow`/`StateFlow`：
1. `emit()` 在哪里调用？（检查调用者的协程上下文）
2. `collect`/`onEach` 在哪里运行？（检查 `launchIn(scope)` — 如果是 `viewModelScope`，则是 `Dispatchers.Main`）
3. 它们之间是否有 `flowOn()`？

如果发送线程 ≠ 收集线程，且收集修改了共享状态，则存在数据竞争。

---

## 步骤 5：检查 Compose 悬浮层生命周期（模式 D）

当 `ComposeView` 覆盖在基于 View 的 Activity 上时：

### 5a. ViewModel 作用域

```kotlin
// 在 createComposeView() 中：
CompositionLocalProvider(
    LocalViewModelStoreOwner provides activity as ViewModelStoreOwner
) { ... }
```

验证：首次调用 `viewModel()` 时 Activity 是否仍然存活？ViewModel 的作用域是 Activity — 如果 Activity 被销毁，`viewModelStore` 也会消失。

### 5b. 状态管理单例

如果对话框/面板使用带有 `var isShowing`、`var currentActivity` 的单例：
- `onActivityDestroyed()` 是否重置了所有状态标志？（不只是 `isShowing` — 还要包括 `dialogView`、`currentActivity`）
- 视图仍然附加时 `isShowing` 是否可能为 `false`？（例如，设置标志为 true 后显示失败）

### 5c. 视图移除时机

```
hide() → postDelayed(50ms) → removeView()
show() → postDelayed(200ms) → addView()
```

检查：旧视图是否保证在新视图添加之前被移除？状态标志（`isShowing`）是否同步更新（在延迟移除之前）？

---

## 步骤 6：应用修复

### 线程安全修复模板

```kotlin
// 修复前：数据竞争
private var _allLogs = listOf<Item>()

// 在后台线程中：
_allLogs = newList

// 在主线程中：
val copy = _allLogs  // 过期读取！

// 修复后：同步 + volatile
@Volatile
private var _allLogs = listOf<Item>()

fun updateFromBackground(newList: List<Item>) {
    synchronized(this) {
        _allLogs = newList
    }
    // 使用本地 newList 更新 UI，而不是 _allLogs
    _uiState.value = _uiState.value.copy(logs = newList)
}
```

### 悬浮层崩溃修复模板

```kotlin
// 在打开悬浮层的点击处理器中：
try {
    if (!activity.isFinishing && !activity.isDestroyed) {
        PanelDialog.show(activity)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to show panel", e)
    // 恢复之前的 UI 状态（例如重新显示悬浮球）
    if (!activity.isFinishing && !activity.isDestroyed) {
        restorePreviousState(activity)
    }
}

// 在 onActivityDestroyed 中：
fun onActivityDestroyed(activity: Activity) {
    if (currentActivity == activity) {
        // 强制清理 — 不要依赖 isShowing 标志
        dialogView?.let { view ->
            try { (view.parent as? ViewGroup)?.removeView(view) } catch (_: Exception) {}
        }
        dialogView = null
        isShowing = false
        currentActivity = null
    }
}
```

### 同步锁作用域缩减

```kotlin
// 修复前：在持有锁期间启动协程
@Synchronized
fun log(msg: String) {
    callback?.invoke(msg)
    GlobalScope.launch { heavyWork(msg) }  // 在锁下启动
}

// 修复后：捕获值，释放锁，然后启动
@Synchronized
fun log(msg: String) {
    callback?.invoke(msg)
    val captured = msg  // 捕获用于锁外使用
}
GlobalScope.launch { heavyWork(captured) }  // 在 @Synchronized 外
```

---

## 步骤 7：验证

1. **构建**：`./gradlew assembleDebug` — 无编译错误
2. **静态检查**：搜索仍存在的 bug 模式（`grep -n "var _allLogs\|mutex\|synchronized"` 在相关文件中）
3. **复现**：让用户复现确切的崩溃场景
4. **监控**：如果崩溃仍然存在，检查 `<externalCacheDir>/crash/` 中的崩溃日志

---

## 快速参考：按崩溃类型需要检查的文件

| 崩溃类型 | 首先检查的文件 |
|-----------|---------------------|
| UI 冻结 + 崩溃 | ViewModel `init` 块、Flow `onEach`、共享 `var` 字段 |
| 打开对话框时崩溃 | 对话框的 `show()`、`createComposeView()`、`LocalViewModelStoreOwner` |
| 按返回键时崩溃 | `BackHandler` + `OnBackPressedDispatcher`、`dismiss()` 状态重置 |
| 操作期间随机崩溃 | 启动协程的 `@Synchronized` 方法、`GlobalScope` 发送 |
| ANR | `CrashHandler` 中的 `Thread.sleep()`、主线程上的任何 `runBlocking` |
