# 事件总线规范（LiveEventBus + Channel 双轨）

> 项目里并存两套事件机制：**LiveEventBus**（View 侧全局事件，基于 LiveData）和 **`Channel<Event>`**（Compose 侧页面内事件，见 `compose/state-events.md` §4.1）。
> 本文管 LiveEventBus 的使用红线，以及**两套机制的选型边界**——边界不清是双轨并存的最大事故源。

## 1. 项目基础设施（不要重新封装）

| 组件 | 位置 | 说明 |
|---|---|---|
| 全局配置 | `App.kt` `onCreate` | `lifecycleObserverAlwaysActive(true)`（后台也收事件）、`autoClear(false)`（**粘性事件默认开启**，订阅时补发最近一次）、logger 桥接到项目日志 |
| tag 常量 | `constant/EventBus.kt`（object，约 40 个常量，按域分组） | **所有 tag 只能来自这里** |
| 类型安全封装 | `utils/EventBusExtensions.kt` | `postEvent` / `postEventDelay` / `postEventOrderly` / `observeEvent` / `observeEventSticky`，reified 泛型保证 tag 与 payload 类型在调用点即确定；宿主支持 `AppCompatActivity` / `Fragment` / `LifecycleService` |

- **禁止绕过 `EventBusExtensions` 直接调 `LiveEventBus.get(tag).post(...)`**——绕封装就丢了 reified 类型检查，payload 类型错了要到运行时 `ClassCastException` 才炸。
- 确实需要 `removeObserver`、`observeWithLifecycle` 之类高级操作时用 `eventObservable<EVENT>(tag)` 取 `Observable`，仍然走泛型。

## 2. 强制规则

1. **tag 只许引用 `EventBus` 常量，禁止字符串字面量。**
   新增事件：先在 `EventBus.kt` 对应域分组下加 `const val`（命名 `SCREAMING_SNAKE`，注释写清"谁发、谁收、payload 类型"），再发。散落字符串的 tag 重命名时必然漏改，线上表现为"功能悄悄失效且无任何报错"。

2. **payload 用 reified 泛型钉死类型，禁止裸 `String` / `Any` 满天飞。**
   `EventBus.kt` 头注释里"String/Int/Bundle 等"是老代码现状，不是新代码许可。结构化的事件（如校源结果、音频状态）必须定义 `data class`，tag 与类型一一对应。单值事件可用 `Unit`/`Int`，但 tag 注释里写死类型。

3. **粘性是默认行为，payload 必须轻量。**
   `autoClear(false)` 意味着**每个 tag 常驻一份最近事件**，直到进程死亡。40 个 tag × 常驻对象，塞大对象（Bitmap、整本目录列表、大 Bundle）就是常驻内存泄漏。粘性 payload 红线：< 1KB 的轻量结构；需要大对象的场景改"事件只当通知，接收方自己拉数据"（发 tag，接收方去 Repository 取）。

4. **观察者回调在主线程执行（LiveData 机制），禁止在 observer 里做任何耗时操作。**
   收到事件后需要 IO/计算：View 侧 `execute { }`（宿主是 `BaseViewModel` 体系）或 `lifecycleScope.launch`（Compose 宿主），和协程规范完全一致。

5. **高频事件必须节流后才上总线。**
   `TTS_PROGRESS`、`AUDIO_PROGRESS`、`AUDIO_BUFFER_PROGRESS`、`UP_SEEK_BAR` 这类每秒 N 次的事件是存量设计，**新代码禁止效仿**。原则：高频连续值属于"状态"，不属于"事件"——View 侧用现有单例的监听器 / `StateFlow`，Compose 侧用 `StateFlow`。事件总线只发"离散状态变化"（开始/暂停/停止）。

6. **订阅生命周期跟随宿主，不需要也不允许手动管理。**
   `observeEvent` 已绑定宿主生命周期自动解绑，Activity/Fragment 销毁即释放。**禁止**在 `onDestroy` 里手调 `removeObserver`（多余），更禁止把 observer 挂到 Application 级对象上常驻（事件与页面脱钩，内存常驻）。

7. **禁止用事件总线传递需要双向同步的状态。**
   事件是"通知"（fire-and-forget），不是"数据流"。两个组件需要共享的可读状态：View 侧走共享 ViewModel，Compose 侧走 Repository `Flow`。"发个事件让对方改状态，再发个事件告诉对方我改完了"——这是用广播模拟双向绑定，状态迟早不一致。

8. **`postEventDelay` / `postEventOrderly` 使用约束：**
   - `postEventDelay` 只用于确定性时序（重试退避、延迟刷新），禁止当"防抖"用——防抖用协程 `delay` 或 `flow.debounce`，总线延迟事件在宿主销毁后仍会派发，时序不可控。
   - `postEventOrderly` 用于"多个同类事件必须按序到达"的场景（批量刷新），默认 `post` 不保证顺序，发序列事件前先想清楚顺序是否重要。

## 3. 双轨选型边界（LiveEventBus vs `Channel<Event>`）

| 维度 | LiveEventBus | `Channel<Event>`（Compose） |
|---|---|---|
| 作用域 | **跨组件全局**：Service ↔ Activity ↔ Fragment，任意两点 | **单页面内**：ViewModel → 该页面的 Screen/Activity |
| 发起方 | 任何组件（含 Service、JS 引擎回调、Receiver） | 该页面的 ViewModel |
| 订阅方 | `AppCompatActivity` / `Fragment` / `LifecycleService` | 该页面的 Screen，`repeatOnLifecycle(STARTED)` 收集 |
| 粘性 | 有（autoClear=false） | 无（一次性，丢了就丢了，按 §4.1 缓冲分档） |
| 生命周期 | 宿主生命周期自动解绑 | Channel 随 ViewModel，收集侧绑定 STARTED |

**裁决规则（Review 标尺）：**

1. 事件**只被一个页面的 ViewModel 产生、只被该页面消费** → `Channel<Event>`，**禁止**为了发它绕道 LiveEventBus。
2. 事件**产生方和消费方不在同一页面**（Service 发、Activity 收；Fragment A 发、Activity B 收；JS/Receiver 发） → LiveEventBus。
3. **Compose ViewModel 禁止 `postEvent`**——Compose 侧 ViewModel 无平台宿主，往全局总线发事件是"页面内事件外溢成全局事件"的反模式，状态提升不够的信号。该页面的 ViewModel 用 `Channel<Event>`，由 Activity 消费。
4. **Activity 可以订阅 LiveEventBus**（它是 View 系宿主，`observeEvent` 支持），Compose Screen 内**禁止**订阅——Screen 无生命周期感知入口，订阅要么裸挂（泄漏）要么硬造 `LifecycleOwner`（绕开 §4.4 的 `repeatOnLifecycle` 约束）。
5. 同一事件不许**双发**（既 `postEvent` 又 `sendEvent`）——双轨各发一份，消费方收到两次。

## 4. 反面示例（看到就改）

```kotlin
// ❌ 字符串字面量 tag
LiveEventBus.get("refresh").post(1)

// ❌ observer 里干重活（主线程）
observeEvent<Int>(EventBus.BOOKSHELF_REFRESH) {
    val list = bookDao.queryAll()   // 主线程查库，ANR
    renderList(list)
}

// ❌ 粘性 payload 塞大对象
postEvent(EventBus.UP_BOOKSHELF, allBookItems)   // List<BookItem> 常驻内存

// ❌ 用总线模拟状态同步
postEvent(EventBus.AUDIO_STATE, 1)   // "我改成播放了，你自己查 AudioPlay 吧"
// 正确：接收方本来就是从 AudioPlay/StateFlow 读状态，事件只触发刷新

// ❌ Compose ViewModel 往全局总线发
class ThemeManageViewModel : ViewModel() {
    fun onSaved() = postEvent(EventBus.NAVIGATION_BAR_CHANGED, "")  // 用 Channel<Event>
}
```