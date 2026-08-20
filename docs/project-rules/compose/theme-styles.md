# Compose UI 规范 — 主题与样式

> **生效范围**：`io.legado.app.ui` 包及以下所有代码
> **本文件为原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物**：含原章节 §7，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。
> 同目录全套：`structure.md`（§1/2/3/11/12）、`state-events.md`（§4/5/6）、`performance.md`（§8）、`navigation-preview.md`（§9/10）、`accessibility.md`（§15）、`testing.md`（§16）、`migration-review.md`（§13/14/17）。
> **执行方式**：§14（见 `migration-review.md`）中标 [机器] 的项由 lint/Detekt/CI 规则强制，违规直接构建失败；[人工] 项 Code Review 时人工对照，不达标 PR 打回
> **老代码策略**：分阶段迁移，允许 `@Suppress("LegadoUiViolation")` + TODO 临时过渡（见 `migration-review.md` §13）
> **最后更新**：2026-08-19

---

## 7. 主题与样式

### 7.1 颜色使用

- **必须**优先使用 `MaterialTheme.colorScheme.xxx` 获取色值。
- **禁止**直接调用 `colorResource(R.color.xxx)` 绕过主题系统。（例外：`Color.Transparent`、`Color.Black`、`Color.White` 等标准色允许直用）
- **禁止** 在 Composable 函数体内用 `Color(0xFFxxxxxx)` 硬编码，色值必须来自 `ThemeEntity` 或 `MaterialTheme`。

### 7.2 魔法数字

- 所有 dimens 必须集中定义在 `ui/theme/Dimensions.kt`（**目标态文件，当前尚未建立**，首次落地时创建并同步 `structure.md` §1 目录树；落地前新代码先把 dimens 就近定义在 `ui/theme/` 下，禁止散落各 Feature）。
- 所有 shapes 必须集中定义在 `ui/theme/Shapes.kt`（同上）。
- **禁止**在 Composable 体内裸写 `16.dp`、`12.dp`、`0.8f`。
- 动画参数（时长档位、easing、spring、无限动画）**必须**遵循 §7.6，禁止调用点随手写 `tween(200, ...)`、`tween(600, ...)` 这类无档位时长。
- **禁止**自定义 `CubicBezier` / `keyframes` 曲线散落多处，新增自定义曲线必须集中定义在 `ui/theme/AnimationSpecs.kt`（**目标态文件，首次落地时创建**）并注释用途。


```kotlin
// ui/theme/Dimensions.kt（目标态示例，落地时创建）
object AppDimens {
    val cardCornerRadius = 12.dp
    val listItemVerticalPadding = 12.dp
    val listItemHorizontalPadding = 16.dp
    val listItemIconSpacing = 16.dp
}

// ui/theme/Shapes.kt（同上）
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)
```

### 7.3 图片加载

> **技术选型：本项目图片框架统一为 Glide**（存量技术栈，全项目已依赖）。不引入 Coil / Fresco 等第二套框架；如未来要换，属于架构级决策，必须全局迁移，禁止单个 PR 局部混用。

- **必须**走 `ui/widget/components/` 下的统一封装组件（暂名 `AppImage.kt`，**目标态文件，当前尚未建立**，落地时创建并同步 `structure.md` §1 目录树）加载图片，内部用 Glide 的 bitmap 链路。封装组件落地前，新 Compose 代码按下述链路模式实现，禁止用 View 版 API 过渡：

```kotlin
Glide.with(context)
    .asBitmap()
    .load(source)
    .apply(RequestOptions().override(widthPx, heightPx)) // 显式尺寸，禁止全尺寸解码
    .into(pendingTarget)
```

- **必须**用自持的 `PendingTarget<Bitmap>` 承接结果并交给 `Image(bitmap)` 渲染，`DisposableEffect` 的 `onDispose` 里 `clear()` target 取消 in-flight 请求——页面滑走后 Glide 继续解码就是白烧内存和 CPU。
- **禁止**在 Composable / ViewModel 里手写 `withContext(Dispatchers.IO) { BitmapFactory.decode... }` 自己解码 bitmap 塞 `Image()`——缓存、采样率、请求去重、取消逻辑全要自己维护，纯造轮子。
- **禁止**在 Compose 层使用 View 版 API（`Glide.with(...).into(imageView)`）；老 XML 代码里的存量调用不动，新代码一律走上面的 bitmap 链路。

### 7.4 字体与排版

- **必须**通过 `MaterialTheme.typography.xxx` 拿字体。
- **禁止**在 Composable 内直接调 `FontFamily` 构建。

### 7.5 字符串资源规范

- **必须**所有用户可见文案通过 `stringResource(R.string.xxx)` 获取，禁止在 Composable 内硬编码中文字符串。"用户可见"包括 `contentDescription`——图标无障碍描述同样禁止硬编码中文，机器检测覆盖该参数（见 `migration-review.md` §14.1）。
- **禁止**在 ViewModel 里拼接展示文案（如 `"已复制" + item.name`）。ViewModel 只发数据，文案拼接在 UI 层完成。
- **推荐** string resource 命名保持简洁可读，允许使用缩写或分层前缀（用斜杠表示层级分组），示例：`theme_title`、`theme_card_delete_confirm`、`config/theme/list_empty`。
- **例外**：纯调试用的 `Log` 消息、`TODO` 注释中的文案不需要走 string resource。

```kotlin
// ❌ 违规
Text("已复制 ${theme.name}")

// ✅ 正确
Text(stringResource(R.string.theme_copied, theme.name))
```

### 7.6 动画规范

> 存量动画用法总体合规（`AnimatedVisibility`、`animateColorAsState`、`graphicsLayer` 旋转、`rememberInfiniteTransition` shimmer），本节是**固化现状 + 收口时长档位 + 约束未来代码**，不是补课。

#### 7.6.1 时长与曲线档位（强制）

时长**只允许三档**，禁止 `tween(200, ...)`、`tween(600, ...)` 这类档位外取值：

| 档位 | 时长 | 用途 | 曲线 |
|------|------|------|------|
| 微交互 | 150ms | 按压缩放、颜色态切换、`animateColorAsState` | `FastOutSlowInEasing` |
| 标准 | 300ms | 展开/收起、`AnimatedVisibility` 进出场 | `FastOutSlowInEasing` |
| 进场过渡 | 450ms | 页面级 `Crossfade`、大图位替换 | `FastOutSlowInEasing` |

- **弹性动画**统一 `SpringSpec(stiffness = Spring.StiffnessMediumLow)`（存量 `CheckSourceScreen` 已在用，固化）；禁止调用点散落 `spring(...)` 随手传 stiffness/dampingRatio。
- **禁止**手写帧循环（`withFrameNanos` / `while` 循环）实现动画——无限动画一律 `rememberInfiniteTransition`，一次性动画一律 `animate*AsState` 或 `Animatable`。
- `Animatable` 手动驱动的场景（手势联动等）必须在 `DisposableEffect` 的 `onDispose` 里 `cancel()`。

#### 7.6.2 动画属性选择（性能相关）

- 位移、缩放、旋转、透明度**必须**走 `graphicsLayer` / `Modifier.alpha` / `Modifier.scale`，禁止对参与 layout 的属性（`padding`、`size`、`offset` modifier 链上的 dp 值）做逐帧动画——layout 脏化 vs 只脏 GPU 图层，同屏 20 个 item 同时动时帧率差一倍以上。
- 高度展开/收起类（必须动 layout 的场景）优先 `AnimatedVisibility` + `expandVertically/fadeIn` 组合，其次 `animateContentSize`；禁止 `animateDpAsState` 逐帧改 `height = x.dp` 的裸写法。

#### 7.6.3 无限动画生命周期（强制）

- `rememberInfiniteTransition` **必须**传 `label` 参数（无障碍：TalkBack 可感知，且 composition 离开时自动取消）。
- 无限动画组件**必须**保证离开 composition 即停（`AnimatedVisibility` 包裹的退出分支要覆盖到）。禁止在 `LaunchedEffect` 里用 `while(true) { delay(16); ... }` 自转——白烧 CPU + 电池，Systrace 上一眼假。

#### 7.6.4 系统"减少动态效果"（强制）

- 读取 `Settings.Global.ANIMATION_SCALE`，`CompositionLocal`（建议 `LocalAnimationScale`，Application 初始化读一次 + `ContentObserver` 监听变化）下发。
- `scale < 0.5f` 时：装饰性动画（shimmer、转圈、背景流动）**时长归零直接 snap 到终态或静态样式**；功能性动画（页面进出场）保留。
- **禁止** Composable 内直接读 `Settings.Global`（读 ContentResolver 有 IPC 成本，不能进 composition 热路径）。

---
