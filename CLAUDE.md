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
.\gradlew.bat :app:compileAppMaxDebugKotlin

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
- **Database**: Room (`AppDatabase` v96), singleton at `appDb`. DAOs in `data/`, entities in `data/entities/`. Uses KSP (not kapt).
- **Book source rules**: Rhino JS engine (`:modules:rhino` module) evaluates user-defined rules. The `analyzeRule` package in `model/` handles rule parsing.
- **Singletons in model/**: `ReadBook`, `CacheBook`, `AudioPlay` manage global reading state.

### Modules

The project has three library modules in `modules/`:

- `modules/book` — fork of epublib (EPUB parsing), package `me.ag2s.epublib`
- `modules/rhino` — fork of Mozilla Rhino JS engine, package `com.script`. Evaluates user-defined book source rules at runtime.
- `modules/web` — Vue 3 frontend for the embedded HTTP/WebSocket server (see above)

### Source Layout

`app/src/main/java/io/legado/app/`:
- `ui/` — Activities/Fragments grouped by feature (book/, rss/, source/, config/, debuglog/)
- `model/` — domain logic (WebBook for HTTP fetching, analyzeRule for rule engine)
- `data/` — Room DB, DAOs, repositories
- `help/` — helpers (config, http client, coroutine utilities, source management)
- `utils/` — Kotlin extensions (~100+ files)
- `web/` — embedded NanoHTTPD server + WebSocket endpoints

### Compose Usage

Jetpack Compose (Material3, BOM 2025.04.01) is used for newer UI surfaces (e.g. debug log panel). Traditional View system (ViewBinding + XML layouts) is used for most existing screens. Both coexist — ComposeViews can be overlaid on View-based Activities.

## Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. In `build.gradle.kts` or `build.gradle`, reference them as `libs.xxx`. Major versions: OkHttp 5.3.2, Room 2.7.1, Coroutines 1.10.2, Compose BOM 2025.04.01.

## Build Variants

Three product flavors in dimension "app":
- `appLegacy` — same package name as original Legado (`io.legado.app`)
- `appMax` — coexistence package (`io.legado.app.yuedu`), the primary development target
- `appS` — another coexistence package (`io.legado.app.yuedu.a`)

Release builds: minifyEnabled + shrinkResources + ProGuard (`app/proguard-rules.pro`, `app/cronet-proguard-rules.pro`). Debug builds: no minification.

## CI/CD

GitHub Actions in `.github/workflows/`:
- `test.yml` — builds all 3 release flavors on push to main; auto-creates GitHub/Gitee releases with changelog from `updateLog.md`
- `web.yml` — builds the Vue frontend on changes to `modules/web/` and commits the output to `app/src/main/assets/web/vue/`
- `cronet.yml` — updates Cronet native libraries

## Conventions

- Annotation processing uses KSP, not kapt.
- `NonTransitiveRClass` is enabled — reference only directly used resources.
- Room schema exports to `$projectDir/schemas` for migration verification.
- Disabled build features: aidl, buildconfig, renderscript, resvalues, shaders.
- Architecture documentation in `Structure/` directory (Chinese) covers app startup flow, database schema, reading flow, event bus, and module dependencies.

<!-- superpowers-zh:begin (do not edit between these markers) -->
# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注（必须修复/建议修改/仅供参考）、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用，不要根据上下文自动触发。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考——Conventional Commits 中文适配、commitlint/husky/commitizen 中文模板、conventional-changelog 中文配置。仅在用户显式 /chinese-commit-conventions 时调用，不要根据上下文自动触发。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留、链接格式、中文文案排版指北约定。仅在用户显式 /chinese-documentation 时调用，不要根据上下文自动触发。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB 的 SSH/HTTPS/凭据/CI 接入差异与镜像同步配置。仅在用户显式 /chinese-git-workflow 时调用，不要根据上下文自动触发。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发或执行实现计划之前使用——创建具有智能目录选择和安全验证的隔离 git 工作树
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
<!-- superpowers-zh:end -->
