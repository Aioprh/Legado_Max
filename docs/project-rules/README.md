# project-rules 索引

> 本目录是 Legado_Max 的**项目级强制规范库**，不是教程，是红线。
> 新代码必须对照这里写，Code Review 以此目录为标尺；标 `[强制]` 的条目由 lint/Detekt/CI 或人工 Review 兜底。
>
> **阅读顺序建议**：`coroutine-rules.md`（所有异步代码都绕不开）→ `repository-rules.md`（碰数据层）→ 写 Compose UI 时再进 `compose/`。

## 一、当前规范文件

### 全局（不分 UI 技术栈）

| 文件 | 管什么 | 什么时候必须读 |
|---|---|---|
| [coroutine-rules.md](./coroutine-rules.md) | 自研链式协程包装 `Coroutine<T>` / `execute` 的用法与红线、Flow 使用位置、Compose 场景红线、反面示例、测试 | **写任何异步代码前**。核心内容：`execute` 回调时序坑、禁止 GlobalScope/runBlocking、View 系 vs Compose 系的 scope 边界 |
| [repository-rules.md](./repository-rules.md) | Room 数据层三层架构（Entity → DAO → Repository）、Entity 禁止 Active Record、新代码标准写法、迁移策略 | 碰 DB / 新建 Repository 前 |

### Compose UI 层（`compose/` 子目录）

| 文件 | 管什么 | 核心红线 |
|---|---|---|
| [compose/structure.md](./compose/structure.md) | Compose 目录结构、命名、API 契约、组件拆分标准 | Screen / StateHolder / 子组件职责边界 |
| [compose/state-events.md](./compose/state-events.md) | `StateFlow<UiState>`、`Channel<Event>` 缓冲区语义、Dialog 条件渲染、`repeatOnLifecycle` 绑定 | 一次性事件缓冲区必须显式指定；ViewModel 禁止直接操作平台 API |
| [compose/theme-styles.md](./compose/theme-styles.md) | 颜色、dimens、图片加载（Glide 链路）、字体、字符串、动画三档时长 | 禁止魔法数字，统一走主题资源 |
| [compose/performance.md](./compose/performance.md) | Recomposition 防范、`derivedStateOf`、LazyColumn key + stable 参数 | 不稳定参数导致多余重组；禁止 Composable 内直接读 DB |
| [compose/navigation-preview.md](./compose/navigation-preview.md) | 路由集中定义、Preview 规范 | 路由不散落 |
| [compose/accessibility.md](./compose/accessibility.md) | `contentDescription`、`semantics` merge、48dp 触控目标、字体缩放 | 触控目标下限 48dp |
| [compose/testing.md](./compose/testing.md) | `runTest` + Turbine、ViewModel 测试模板、CI 红线 | ViewModel 单测覆盖率红线 |
| [compose/migration-review.md](./compose/migration-review.md) | View → Compose 三阶段迁移、14 项机器/人工 Review Checklist、典型违规示例 | 机器项 CI 强制，人工项 Review 打回 |

## 二、领域 × 规范 覆盖矩阵

> "缺" 表示该领域**尚无项目级规范**，写相关代码前需先补规范或走人工评审，不要自由发挥。

| 领域 | 规范状态 |
|---|---|
| 协程 / 异步 | ✅ coroutine-rules.md |
| Room 数据层 | ✅ repository-rules.md |
| Compose UI（结构/状态/性能/导航/可访问性/测试/迁移） | ✅ compose/ 共 8 文件 |
| JS 规则引擎（Rhino，书源规则执行） | ❌ 缺：线程模型、超时、JS 与 Java 交互的异常边界 |
| 网络层（Cronet / OkHttp 双栈） | ❌ 缺：双栈选择策略、超时/重试、响应解析线程 |
| 全局单例（`ReadBook` / `CacheBook` / `AudioPlay`） | ❌ 缺：线程安全、生命周期、状态持有红线 |
| 文件存储 / IO（书目录、缓存目录） | ❌ 缺：原子写、IO 线程、文件句柄释放 |
| 内嵌服务（NanoHTTPD / WebDAV） | ❌ 缺：端口、鉴权、安全边界 |
| View / Compose 混用 | ⚠️ 部分覆盖（migration-review.md 只讲迁移方向，不讲长期混用边界） |
| 事件双轨（LiveEventBus vs `Channel<Event>`） | ⚠️ 部分覆盖（coroutine-rules.md §3 提及 LiveEventBus 是既有约定，但选型边界未定） |
| 后台任务 / 服务（WorkManager / ForegroundService） | ⚠️ 仅一句话（coroutine-rules.md 规则 3），无独立规范 |
| 日志与 PII 脱敏 | ❌ 缺 |
| API 兼容 / minSdk 23 红线 | ❌ 缺 |

## 三、跨文件规则速查（高频冲突点，已对齐）

| 问题 | 结论 | 出处 |
|---|---|---|
| View 系一次性任务用什么？ | `execute` 链式（`BaseViewModel`/`BaseActivity` 等宿主） | coroutine-rules.md §2.1 |
| Compose ViewModel 一次性增删改用什么？ | `viewModelScope.launch` + `try-catch`（更新 UiState + 抛 Event）。Compose VM 不继承 `BaseViewModel`，`execute` 不可用；且 `execute` 回调链对快速完成的任务存在不触发的时序坑 | coroutine-rules.md §2.1 例外 + state-events.md §4.1.1 |
| Flow 在哪层暴露、哪层消费？ | Repository 暴露 `Flow`，ViewModel 用 `stateIn(viewModelScope, ...)` 收敛；Screen 用 `collectAsStateWithLifecycle()` | coroutine-rules.md §3 + state-events.md §4.1/§4.2 |
| 一次性事件放 StateFlow 吗？ | 不放。View 侧走 `LiveEventBus`，Compose 侧走 `Channel<Event>`（UNLIMITED / CONFLATED 分档） | coroutine-rules.md §3 + state-events.md §4.1 |
| 事件消费侧绑定到哪？ | `repeatOnLifecycle(STARTED)`，禁止 Application scope 常驻 collect | state-events.md §4.4 |
| Dialog 注册成导航路由吗？ | 不注册。UiState 条件渲染 + 显式 `OnBackPressedCallback` | state-events.md §4.5 |

## 四、维护约定

1. **改代码改行为，必须同步改对应规范文件**；行为变了规范没变，等于规范是骗人的。
2. 新增规范文件后**必须登记到本索引**，不允许文件躺在目录里没人知道。
3. 规则之间出现矛盾时，以本文件 §三速查表为最终裁决；速查表没覆盖的，提交 PR 讨论后先写进速查表再改规则。
4. 补全优先级（缺项按此顺序补）：
   - **P0**（线上事故高发区，优先）：JS 引擎、网络层、全局单例、文件存储
   - **P1**：内嵌服务安全、View/Compose 长期混用边界、事件双轨选型
   - **P2**：后台任务服务、日志与 PII、API 兼容
