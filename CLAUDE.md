# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

阅读Max (legado_Plus) — an Android e-book reader app forked from Legado. Supports custom book sources with user-defined rules (Jsoup selectors + Rhino JS), RSS subscriptions, local TXT/EPUB reading, and an embedded HTTP/WebSocket server for remote control.

## Build Commands

Uses Gradle wrapper (`gradlew.bat` on Windows). JDK 17 required.

```bash
# Debug build (default flavor: appMax)
./gradlew assembleDebug

# Release build (ProGuard + resource shrinking enabled)
./gradlew assembleRelease

# Specific flavor builds
./gradlew assembleAppMaxDebug       # appMax (io.legado.app.yuedu, coexistence)
./gradlew assembleAppLegacyRelease  # appLegacy (io.legado.app, same as original)
./gradlew assembleAppSDebug         # appS (io.legado.app.yuedu.a)

# Install to device
./gradlew installDebug
./gradlew installAppMaxDebug

# Tests
./gradlew test                      # Unit tests
./gradlew connectedAndroidTest      # Instrumented tests

# stop
./gradlew stop

# Grammar Test
./gradlew.bat :app:compileAppMaxDebugKotlin

# Lint
./gradlew lint

# Download Cronet native libs (required before first build)
./gradlew app:downloadCronet

# 查看DSL语法警告
# Windows
gradlew assembleDebug --warning-mode all
# Mac/Linux
./gradlew assembleDebug --warning-mode all
```

### Web Frontend (modules/web)

The embedded HTTP server's frontend is a Vue 3 + Vite app in `modules/web/`. It builds to `app/src/main/assets/web/vue/`.

```bash
cd modules/web
pnpm install        # requires Node >= 20, pnpm >= 9
pnpm dev            # local dev server with HMR
pnpm build          # production build + syncs to assets/web/vue/
pnpm lint:fix       # eslint auto-fix
pnpm format         # prettier
```

## Architecture

MVVM pattern with AndroidViewModel + ViewBinding + Coroutines.

### Base Classes (`io.legado.app.base`)

- `BaseActivity<VB>` — all Activities extend this. Manages theming, system bars, view binding. Override `observeLiveBus()` for event subscriptions (auto-cleaned on destroy).
- `VMBaseActivity<VB, VM>` — adds abstract `viewModel` property.
- `BaseViewModel` — extends `AndroidViewModel`. Key method: `execute { }` returns a `Coroutine<T>` with chainable `.onSuccess`, `.onError`, `.onFinally`. Default context is `Dispatchers.IO`, callbacks on `Dispatchers.Main`.

### Key Patterns

- **Coroutine helper**: `BaseViewModel.execute()` wraps `Coroutine.async()`. Use this instead of raw `viewModelScope.launch`.
- **Event bus**: `LiveEventBus` for cross-component events. Subscribe via `observeEvent<T>(key) { ... }` in `observeLiveBus()`.
- **Database**: Room (`AppDatabase` v100), singleton at `appDb`. DAOs in `data/`, entities in `data/entities/`. Uses KSP (not kapt).
- **Book source rules**: Rhino JS engine (`:modules:rhino` module) evaluates user-defined rules. The `analyzeRule` package in `model/` handles rule parsing.
- **Singletons in model/**: `ReadBook`, `CacheBook`, `AudioPlay` manage global reading state.
- **Config packages**: `TopBarConfig` and `BubblePackageManager` store configs as file system directories (JSON + assets like wallpapers/icons), not SharedPreferences. `NavigationBarConfig` uses SharedPreferences. `ApplicationThemeManager` combines all sub-configs into exportable/importable theme packages (zip).

### Modules

The project has three library modules in `modules/`:

- `modules/book` — fork of epublib (EPUB parsing), package `me.ag2s.epublib`
- `modules/rhino` — fork of Mozilla Rhino JS engine, package `com.script`. Evaluates user-defined book source rules at runtime.
- `modules/web` — Vue 3 frontend for the embedded HTTP/WebSocket server (see above)

### Source Layout

`app/src/main/java/io/legado/app/`:
- `ui/` — Activities/Fragments grouped by feature (book/, rss/, source/, config/, debuglog/, image/)
- `model/` — domain logic (WebBook for HTTP fetching, analyzeRule for rule engine, ParagraphBubbleRenderer, BookCover)
- `data/` — Room DB, DAOs, repositories
- `help/` — helpers (config managers for theme/navbar/topbar/bubble, http client, coroutine utilities, source management)
- `lib/theme/` — theme utilities (accent colors, typography, corners, page colors, TitleBar config extensions)
- `utils/` — Kotlin extensions (~100+ files)
- `web/` — embedded NanoHTTPD server + WebSocket endpoints

### Compose Usage

Jetpack Compose (Material3, BOM 2026.08.00) is used for newer UI surfaces (e.g. debug log panel). Traditional View system (ViewBinding + XML layouts) is used for most existing screens. Both coexist — ComposeViews can be overlaid on View-based Activities.

Compose 规范拆分为 8 个文件，位于 `docs/project-rules/compose/`：

- [`compose/structure.md`](docs/project-rules/compose/structure.md) — 目录结构、命名、API 契约、通用脚手架
- [`compose/state-events.md`](docs/project-rules/compose/state-events.md) — UiState / Event 流、Dialog/BottomSheet 渲染
- [`compose/theme-styles.md`](docs/project-rules/compose/theme-styles.md) — 颜色、尺寸、图片加载、字体、字符串、动画
- [`compose/performance.md`](docs/project-rules/compose/performance.md) — Recomposition 防范、副作用、图片内存
- [`compose/navigation-preview.md`](docs/project-rules/compose/navigation-preview.md) — 导航规范、Preview 规范
- [`compose/accessibility.md`](docs/project-rules/compose/accessibility.md) — 无障碍（contentDescription / semantics / 触控目标）
- [`compose/testing.md`](docs/project-rules/compose/testing.md) — 测试分层、runTest + Turbine 模板、CI 接入
- [`compose/migration-review.md`](docs/project-rules/compose/migration-review.md) — 老代码迁移三阶段、Review Checklist（CI 硬卡 + 人工项）、典型违规示例

## 项目级规范（必读）

项目级强制规范库位于 `docs/project-rules/`，索引与领域覆盖矩阵见 [`docs/project-rules/README.md`](docs/project-rules/README.md)。写代码前先按"什么时候必须读"对照索引，规范与实现冲突时以源码为准并回头修规范。

- **协程**：本项目使用自研链式协程包装（`BaseViewModel.execute` → `help/coroutine/Coroutine`）。使用协程前必读 [`docs/project-rules/coroutine-rules.md`](docs/project-rules/coroutine-rules.md)，其中包含 `execute` 链的时序坑、Scope 规则、Flow 位置与反面示例。
- **数据层（Repository）**：[`docs/project-rules/repository-rules.md`](docs/project-rules/repository-rules.md)，新增数据访问逻辑必须遵循。
- **API 兼容**：[`docs/project-rules/api-compat-rules.md`](docs/project-rules/api-compat-rules.md)。调用高于 minSdk 23 的 API、引入新依赖、发版前必读（SDK 分支写法、desugaring 边界、16KB 对齐等 targetSdk 37 红线）。
- **事件总线**：[`docs/project-rules/live-event-bus-rules.md`](docs/project-rules/live-event-bus-rules.md)。新增跨组件事件、在 LiveEventBus 与 Compose `Channel<Event>` 之间选型时必读。

## Coding Conventions

- Kotlin 代码风格遵循 Google Android Style Guide
- 命名规则：
  - Activities: `XxxActivity`
  - ViewModels: `XxxViewModel`
  - Fragments: `XxxFragment`
- 日志使用统一的 tag 格式：`AppTag.xxx`

## Comments
> 注释优先表达**为什么这么做、特殊约束、业务背景**，代码本身负责表达“是什么、怎么做”。
- **类注释**
  - 核心/复杂类（单例、引擎、解析器、管理器等）必须补充完整 KDoc。
  - 普通 `Activity` / `Adapter` / `ViewModel` 无需完整 KDoc，仅简短说明核心用途即可。
- **函数注释**
  - 对外公开 API、复杂业务逻辑、有特殊入参/返回值约束的函数，必须写 KDoc。
  - 简单 getter / setter、工具内部私有简单函数，不额外加注释。
- **变量注释**
  - 优先靠命名表达语义，命名清晰则不加行内注释。
  - 仅业务含义隐晦、存在特殊边界约定时才补充注释。
- **注释原则**
  - 写「为什么」，不重复复述代码已经能看出来的「是什么」。
  - 不要把代码逻辑翻译成自然语言。

## Dependency Management

- 所有依赖版本通过 `gradle/libs.versions.toml` 统一管理
- 禁止直接在 `build.gradle` 中硬编码版本号
- 新增依赖需同步更新版本目录文档

## Testing Strategy

这个视情况讨论，因为有时开发环境不允许。
- 单元测试：`app/src/test/`
- 集成测试：`app/src/androidTest/`
- 测试覆盖率要求：核心模块 ≥ 80%
- Mock 框架：Mockk
- 协程测试：kotlinx-coroutines-test
- LeakCanary: `debugImplementation` only — memory leak detection in debug builds.

## Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. In `build.gradle.kts` or `build.gradle`, reference them as `libs.xxx`. Major versions: Kotlin 2.3.10, Hilt 2.59, OkHttp 5.3.2, Room 2.8.4, Coroutines 1.10.2, Compose BOM 2026.08.00.

## Build Variants

Three product flavors in dimension "app":
- `appLegacy` — same package name as original Legado (`io.legado.app`)
- `appMax` — coexistence package (`io.legado.app.yuedu`), the primary development target
- `appS` — another coexistence package (`io.legado.app.yuedu.a`)

SDK levels: minSdk 23, targetSdk 37, compileSdk 37, JVM 17 toolchain. coreLibraryDesugaring is enabled — JVM 17 syntax (records, text blocks, List.of) works down to API 23.
Both build types set an applicationIdSuffix (`.debug` / `.release`), so the installed package is e.g. `io.legado.app.yuedu.debug`, not the bare flavor id.

Release builds: minifyEnabled + shrinkResources + ProGuard (`app/proguard-rules.pro`, `app/cronet-proguard-rules.pro`). Debug builds: no minification.

## CI/CD

GitHub Actions in `.github/workflows/`:
- `test.yml` — builds all 3 release flavors on push to main; auto-creates GitHub/Gitee releases with changelog from `updateLog.md`
- `web.yml` — builds the Vue frontend on changes to `modules/web/` and commits the output to `app/src/main/assets/web/vue/`
- `cronet.yml` — updates Cronet native libraries
- `lint.yaml` — runs lint in CI; treat `./gradlew lint` passing as part of "done"

## Conventions

- Annotation processing uses KSP, not kapt.
- `NonTransitiveRClass` is enabled — reference only directly used resources.
- Room schema exports to `$projectDir/schemas` for migration verification.
- Disabled build features: aidl, renderscript, resvalues, shaders. buildConfig is explicitly enabled (Cronet version fields); do not assume BuildConfig is absent.
- Architecture documentation in `Structure/` directory (Chinese) covers app startup flow, database schema, reading flow, event bus, and module dependencies.

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## Core Rules & Skills

本项目配置了自动化 Skills (位于 `.claude/skills/`) 来辅助开发。Claude 在执行任务时必须遵循以下核心原则：

1.  **Check Skills First**: 开始任务前，必须检查是否有匹配的 Skill。
2.  **Design First**: 编码前必须进行设计分析。
3.  **Test First**: 优先采用 TDD 方式开发。
4.  **Verify Before Finish**: 完成任务必须运行验证命令。

> **注意**：详细的技能列表和触发逻辑请查阅 `.claude/skills/` 目录，或者直接使用 Skill 工具调用。

## Skill 的使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

当任务明确匹配某个 skill 的应用场景时，应调用该 skill 检查。

## AI 探索项目的方式
1. 先看本文件了解模块结构
2. 定位目标模块，读项目模块的 build.gradle 确认依赖
3. 找该模块的对外接口（api/ 目录或 interface），而不是直接钻进实现
4. 找一个同类型的现有实现作为参考模板，新代码保持风格一致
