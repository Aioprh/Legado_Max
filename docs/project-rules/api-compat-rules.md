# API 兼容性规范（minSdk 23 / targetSdk 37）

> 本项目的编译期是 **compileSdk 37**，但用户的设备最低是 **minSdk 23（Android 6.0）**。
> compileSdk 高 ≠ 可以用高 API。所有新 API 调用在 API 23 设备上要么 Crash（`NoSuchMethodError`），要么被 lint 拦下——线上用户不会帮你测试。

## 1. 项目现状（`app/build.gradle` + `gradle/libs.versions.toml`）

| 项 | 值 | 影响 |
|---|---|---|
| compileSdk | 37 | 可以编译到新 API |
| minSdk | **23** | 所有代码必须在 Android 6.0 能跑 |
| targetSdk | **37** | 平台行为收紧按 targetSdk 37 生效（见 §5） |
| AGP / Kotlin | 9.2.1 / 2.3.10 | 16KB 对齐检查可用 |
| Desugaring | `desugar_jdk_libs_nio` **2.1.5**，JVM 17 工具链 | `java.time` / `java.nio.file`（Path/Files/ZipFS）可用，见 §3 |
| Manifest | 存在 `tools:overrideLibrary="com.qmdeve.liquidglass"` | ⚠️ 有人绕过 manifest merger 的 minSdk 冲突，见 §4 |

## 2. 新 API 调用的强制规则

1. **`Build.VERSION.SDK_INT` 分支是默认写法，不是例外写法。**
   调用任何 `@RequiresApi` 高于 23 的框架 API，必须有运行时版本判断。参考现有写法：

   ```kotlin
   // receiver/NetworkChangedListener.kt —— 高 API 在前、低 API 兜底
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
       networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }
   } else {
       registerReceiver(...)   // API 23~24 走广播
   }
   ```

2. **同一逻辑的版本分支不许散落多层。** 三处以上出现同一 SDK 判断时，收进一个 Compat 对象（项目内已有先例：`lib/permission/Permissions.kt` 按 API 分支返回能力位）。散落五层的 if/else，半年后没人说得清哪条路径在线上生效。

3. **禁止 `@SuppressLint("NewApi")` 当万能胶。** `NewApi` lint 是最后防线，不是让你绕过去的。`NetworkChangedListener` 里的 `@SuppressLint("UnspecifiedRegisterReceiverFlag")` 属于 API 34 注册广播必须声明 flag 的逃逸，**新代码禁止效仿**——正确做法是 `if (SDK_INT >= UPSIDE_DOWN_CAKE) registerReceiver(r, f, ContextCompat.RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)`。

4. **分支条件写 `>=` 边界时，想清楚"低版本路径"。** 只写高版本分支、低版本静默 skip 是最常见的兼容事故（功能在 Android 6/7 上"没反应"但不 Crash，比 Crash 难查十倍）。低版本必须有：兜底行为 / 明确降级 / 明确报错三选一。

## 3. Desugaring 覆盖范围（`desugar_jdk_libs_nio` 2.1.5）

- **可用**：`java.time` 全系、`java.nio.file.Path/Files/ZipFileSystem`、`String.formatted()` 等 JVM 语言 API。
- **不可用（desugar 不覆盖）**：`java.sql`、`java.util.logging`、部分 `java.nio.channels`（`AsynchronousChannelGroup`、`SocketChannel` 的异步 API）、`javax.net.ssl` 的部分新类。
- **规则**：引入新的 JDK API 前先查 [Core Library Desugaring support list](https://developer.android.com/studio/write/core-library-desugaring)，不支持的直接 `NoClassDefFoundError`——**单测（JVM 上跑）发现不了，只有 API 23 真机能发现**。
- 已启用 desugar 不代表可以乱用：`Files.newInputStream` 这类在 minSdk 23 上是有性能损耗的（运行时替换实现），高频 IO 路径用 `File` / `FileInputStream` 原生 API。

## 4. 依赖的 minSdk 红线

1. **任何新依赖的 minSdk 必须 ≤ 23**，AGP manifest merger 会直接构建失败。构建失败是好事，别绕。
2. **禁止新增 `tools:overrideLibrary`。** manifest 里现有的那处（`liquidglass`）是欠债：它压制了 minSdk 冲突，意味着该库在 API 23 设备上调高版本 API 时会直接 Crash，而且 Crash 堆栈指向的是那个库。见到新依赖想加这行，先问：能不能换库？能不能 fork？
3. AndroidX 库升级时注意 minSdk 跟版：部分库（如 `androidx.compose.material3` 新版、`androidx.security` 新版）会把 minSdk 提到 23/26。升级依赖后 manifest merger 报错就是信号，**不许用 overrideLibrary 糊过去**。

## 5. targetSdk 37 的平台行为收紧（发版前必查）

targetSdk 决定平台行为，**这些在 API 23 老设备上不存在、在新设备上强制执行**：

| 项 | 要求 | 项目现状 |
|---|---|---|
| **16KB 内存页对齐**（Android 15+ 安装要求） | targetSdk 35+ 的 APK，所有 native `.so` 必须 16KB 对齐，否则在 16KB 页大小的设备上装不上/跑不了 | ⚠️ 发版前必须验证：Cronet so、Glide so、Rhino 相关 so。AGP 8.5+ 有 ABI 检查，或人工 `llvm-readelf` 验证 |
| **Foreground Service type**（targetSdk 34+） | manifest 必须声明 `foregroundServiceType`，且启动 FGS 前持有对应权限 | ✅ 已声明（`dataSync` × 5、`mediaPlayback` × 4，见 `AndroidManifest.xml`）。**注意：Android 15+ 对 `dataSync` 有 6 小时使用上限**，长任务（整本书下载/缓存）要考虑分段或切 `mediaPlayback`/`specialUse` 的合规性 |
| **Edge-to-Edge 强制**（targetSdk 35+） | 不再允许忽略系统栏 insets，所有页面（含 Dialog/BottomSheet/弹窗键盘避让）必须适配 insets | ⚠️ 新 Compose 页面必须用 `WindowInsets` 处理；View 页面用 `fitsSystemWindows`/`setDecorFitsSystemWindows`（现有 `AndroidAlertBuilder.fixDialogWindowCompat` 是 API 30+ 分支的正面例子） |
| **POST_NOTIFICATIONS 运行时权限**（API 33+） | 通知需运行时授权 | ✅ `PermissionActivity` 已处理 TIRAMISU 分支 |
| **Broadcast flag**（API 34+） | 动态注册广播必须显式 `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED` | ⚠️ 存量有 `SuppressLint` 逃逸，见 §2.3 |

## 6. CI / Review 红线

- lint `NewApi`、`MissingPermission` 规则**禁止在项目中整体 disable**（`lint.xml` 检查）。单点 `SuppressLint` 必须写理由注释。
- 发版前真机矩阵至少覆盖：**API 23 / 26 / 29 / 31 / 34** 各一台（或模拟器）。API 23 是 desugar 与旧 API 路径的唯一验证点，不能省。
- 新增 native 库（so）时，PR 必须附 16KB 对齐验证结果。
- `AppDatabase.kt` 里 `if (SDK_INT >= M) db.setLocale(...)` 的注释提到"在 21 上报错"——**API 21/22 已低于 minSdk，这类死分支和过期注释在触碰该文件时顺手清掉**，别留着误导人。