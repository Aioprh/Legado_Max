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

# Lint
./gradlew lint

# Download Cronet native libs (required before first build)
./gradlew app:downloadCronet
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
