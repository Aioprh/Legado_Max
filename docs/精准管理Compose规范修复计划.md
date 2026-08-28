# 精准管理 Compose 规范修复计划（下载管理 / 文件管理 / 存储管理 / 书源回收站）

> 依据：`docs/project-rules/compose/` 全套 8 个规范文件（2026-08-19 版）
> 范围：精准管理（`ConfigTag.PRECISE_MANAGE`）跳转的 4 个 Compose 子界面
> 规则引用格式：「文件名 §编号」，标 **[机器]** 的为 CI 硬卡项（违规 = 构建红），其余为 **[人工]** Review 项

---

## 涉及文件总览

| 界面 | Screen | ViewModel | Activity |
|------|--------|-----------|----------|
| 下载管理 | `ui/download/DownloadManageScreen.kt` | `ui/download/DownloadManageViewModel.kt` | `ui/download/DownloadManageActivity.kt` |
| 文件管理 | `ui/file/FileManageScreen.kt` | `ui/file/FileManageViewModel.kt` | `ui/file/FileManageActivity.kt` |
| 存储管理 | `ui/book/storage/StorageManageScreen.kt` + `ui/book/storage/components/` | `ui/book/storage/StorageManageViewModel.kt` | `ui/book/storage/StorageManageActivity.kt` |
| 书源回收站 | `ui/source/recycle/SourceRecycleBinScreen.kt` | `ui/source/recycle/SourceRecycleBinViewModel.kt` | `ui/source/recycle/SourceRecycleBinActivity.kt` |

---

## 一、四界面共性问题（统一修法，一次定模板后逐页套用）

### C1. 裸 `collectAsState()`【机器，state-events.md §4.2 / §8.2】

现状：4 个 Screen 全部使用裸 `collectAsState()`（文件管理还在 collect LiveData）。

修法：
- 统一替换为 `collectAsStateWithLifecycle()`（`androidx.lifecycle.compose`，`lifecycle-runtime-compose` 依赖项目已有，`HomepageScreen`/`CheckSourceScreen` 为合规模板）。
- 文件管理的 `filesLiveData` 顺带把 ViewModel 的 LiveData 改为 `StateFlow`，一并收敛。

### C2. Dialog 显隐开关散落本地 `mutableStateOf`【人工，state-events.md §4.5 / §4.2】

现状：4 个界面的确认弹窗显隐全部用 `var showXxxDialog by remember { mutableStateOf(...) }` 本地持有；回收站一个 Screen 里有 13 个本地 `mutableStateOf`（规范上限 3 个）。

修法（每页统一）：
1. 在各自 `UiState` 中新增 `val dialog: XxxDialogState? = null` 字段，Dialog 状态用 `sealed interface` 建模，例如：

   ```kotlin
   sealed interface RecycleBinDialogState {
       data class RestoreConfirm(val item: SourceRecycleBin) : RecycleBinDialogState
       data class ConflictConfirm(val items: List<SourceRecycleBin>) : RecycleBinDialogState
       data class DeleteConfirm(val items: List<SourceRecycleBin>) : RecycleBinDialogState
       data object ClearAll : RecycleBinDialogState
   }
   ```
2. Screen 用 `when (state.dialog)` 条件渲染，确认后调 ViewModel 并清状态。
3. 按 §4.5 显式注册 `OnBackPressedCallback`：有 Dialog 时返回键关 Dialog，无 Dialog 时走正常返回。
4. 菜单展开态（`DropdownMenu expanded`）属于纯瞬态，允许保留本地 `remember`，但数量超 3 个时随其余状态一起抽 State Holder。

### C3. 硬编码中文字符串【机器，theme-styles.md §7.5】

现状：下载管理整屏硬编码（标题、统计行、空态、菜单、`getStatusText()`）；存储管理标题/按钮/清理中文案硬编码；文件管理返回按钮描述与删除确认文案硬编码；回收站已基本资源化（仅复核）。

修法：
- 全部改 `stringResource(R.string.xxx)`，新增资源命名按 §7.5 推荐的分层前缀：`download_manage_title`、`download_empty_all_title`、`storage_clearing`、`file_delete_confirm` 等，同步补 `values/strings.xml`（英文）与 `values-zh/strings.xml`。
- 带参文案用格式化串：`"正在清理 %1$s…"`、`"确定要删除 \"%1$s\" 吗？"`。
- 下载管理 `DownloadTab` enum 的 `label: String` 改为 `labelRes: @StringRes Int`（禁止在数据层持有展示文案）。

### C4. 硬编码色值【机器，theme-styles.md §7.1】

现状：下载管理 `StatusIcon`/`getStatusColor` 两处 `Color(0xFF4CAF50)`。

修法：改为 `MaterialTheme.colorScheme` 语义色（成功态用 `tertiary`，或在 `ui/theme/` 页面色体系里新增 `pageSuccessColor()` 统一定义，禁止 Composable 体内写 `Color(0xFF...)`）。

### C5. 魔法数字与裸 `.sp`【机器，theme-styles.md §7.2 / accessibility.md §15.4】

现状：4 个界面标题统一裸写 `fontSize = 20.sp`；`16.dp`/`12.dp`/`8.dp` 散落。

修法：
- 标题直接用 `MaterialTheme.typography.titleLarge`（不再 `.copy(fontSize = 20.sp)`）。
- `Dimensions.kt` 目标态文件尚未建立：按 §7.2 落地前策略，先在 `ui/theme/` 下就近定义本批页面共用的尺寸常量（如 `PageDimens.screenPadding`、`PageDimens.cardSpacing`），禁止继续散落；`Dimensions.kt` 正式建立时再迁移。

### C6. 卡片无 `semantics` 合并【人工，accessibility.md §15.2】

现状：`DownloadTaskCard`、`CacheItemCard`、`CacheSummaryCard`、回收站条目卡片均无合并（已核实 `ui/book/storage/` 下零 `semantics` 调用）。

修法：各卡片根容器加 `Modifier.semantics(mergeDescendants = true)`，内层不加 `clearAndSetSemantics`。

### C7. Screen 文件内堆 `private fun` 组件、单文件超 400 行【人工，structure.md §1 / §11.1】

修法：各 Feature 建 `components/` 子包归集私有组件（存储管理已有该结构，作为模板）：
- `ui/download/components/DownloadTaskCard.kt`（连同 `StatusIcon`、状态文案/颜色映射）
- `ui/file/components/`（`PathBreadcrumb`、`FileListItem`、`FileSearchBar`）
- `ui/source/recycle/components/`（`SourceRecycleBinItem`、`SourceRecycleDropdownMenu(+Item)`）

### C8. ViewModel 无单测【机器，testing.md §16.4】

现状：`app/src/test` 下 4 个 ViewModel 均无测试。

修法：每个 ViewModel 补 `*ViewModelTest.kt`，模板按 `testing.md` §16：`runTest` + Turbine，禁止 `runBlocking`/手写 collect/`Thread.sleep`。DAO/Help 依赖通过构造函数注入以便 mock（现状直接 `appDb` 静态引用，测试改造时一并收敛为构造注入）。

### C9. TopAppBar 样板重复【人工，§14.2 脚手架项】

现状：`pageTopBarContainerColor()` + `onSecondary` 图标色的 TopAppBar 配色块在 4 个文件复制 4 遍。

修法：抽 `AppPageTopBar` 到 `ui/widget/components/`（跨 Feature 复用，必须提升到全局层），参数：`title`、`subtitle`、`onBackClick`、`actions`，附 `@Preview`（§10.1 强制）。本批 4 页先替换调用，后续精准管理其余页面跟进。

---

## 二、下载管理（架构违规最重，优先级最高）

### D1. ViewModel 直接持有 Context 执行平台操作【人工，§4.1 / §17 违规 C，行为正确性问题】

现状：`DownloadManageViewModel.openFile(context)`/`openFolder(context)`/`copyPath(context)` 直接调 `startActivity`、`ClipboardManager`、`toastOnUi`；Screen 把 `LocalContext` 传给 ViewModel。

修法：
1. ViewModel 定义事件流（§4.1 缓冲档位）：

   ```kotlin
   sealed interface DownloadEvent {
       data class OpenFile(val taskId: Long) : DownloadEvent
       data object OpenFolder : DownloadEvent
       data class CopyPath(val path: String) : DownloadEvent
       data class Toast(val msg: String) : DownloadEvent   // 允许丢失
   }
   // 导航/打开类关键事件：Channel(UNLIMITED)；Toast：Channel(CONFLATED) 并注释丢事件语义
   ```
2. `openFile`/`openFolder`/`copyPath` 改为只做数据准备 + `trySend` 事件；`retryDownload(context, id)` 里的 context 依赖评估能否下沉到 `DownloadService`（若 `DownloadService.retryDownload` 必须要 context，则同样改为事件由 Activity 执行）。
3. `DownloadManageActivity` 用 `repeatOnLifecycle(STARTED)` 收集事件并执行平台操作（§4.4）；Screen 不再接触 `LocalContext`。
4. 事件发送失败（有界缓冲场景）按 §4.1 检查 `trySend` 返回值并打日志——本方案用 UNLIMITED/CONFLATED 天然规避。

### D2. `items()` key ✓ 已有（`key = { it.id }`），保持。

### D3. 500ms 轮询

现状：`startPolling()` 里 `while (true) { ...; delay(500) }`。数据轮询不属于 §7.6 动画条款，不算违规，但：
- 页面不可见时轮询不停（费电）。修法：轮询协程挂在 `repeatOnLifecycle(STARTED)` 语义下——由 Screen 侧 `LaunchedEffect(Unit)` 触发 `viewModel.startPolling()`/离开自动取消，或 ViewModel 内监听 `ProcessLifecycleOwner`。选前者，实现更简单。
- 长远可将 `DownloadState` 改为 `Flow` 推送（超出本次范围，记 TODO）。

---

## 三、文件管理

### F1. 手写 `BitmapFactory.decodeByteArray` 解码图标【机器，theme-styles.md §7.3 明确禁止】

现状：`bitmapFromBytes()` 把 `FilePickerIcon` 的 PNG 字节数组解码成 `ImageBitmap`，`remember` 4 份图标。

修法：
- 图标是静态资源，直接替换为 Material Icons：文件夹 `Icons.Default.Folder`、文件 `Icons.Default.InsertDriveFile`、上级 `Icons.Default.SubdirectoryArrowLeft`（或 `ArrowUpward`）、面包屑箭头 `Icons.AutoMirrored.Filled.ChevronRight`， tint 走 `MaterialTheme.colorScheme`。
- 删除 `bitmapFromBytes()` 与 `FilePickerIcon` 依赖；若视觉上必须保留原 PNG 风格，退路是放 `res/drawable` 用 `painterResource`，同样不允许手写 decode。

### F2. `items(files)` 缺 `key`【机器，§8.1】

修法：`items(files, key = { it.absolutePath })`。

### F3. 触控目标不足 48dp【人工，accessibility.md §15.3】

现状：`FileItem` 行 `padding 5.dp`、`PathItem` 无命中区扩展。

修法：行 `padding` 提升至 `vertical = 8.dp` 起步，图标区用 `Modifier.minimumInteractiveComponentSize()`（Compose 版本已 ≥1.7）或 padding 撑满 48dp；面包屑项同理。

### F4. LiveData → StateFlow（并入 C1）

`filesLiveData` 改 `StateFlow`，Screen 侧 `collectAsStateWithLifecycle()`。

### F5. 其余套共性修法：C2（删除确认 Dialog 进 UiState）、C3、C5（`18.sp`、`36.dp`、`24.dp` 等）、C7（457 行 > 400，拆 `ui/file/components/`）。

---

## 四、存储管理

### S1. `items(cacheItems)` 缺 `key`【机器，§8.1】

修法：`items(cacheItems, key = { it.id })`。

### S2. Screen 内直接 `context.startActivity<FileManageActivity>`【人工，§4.1 平台操作下沉】

现状：`onOpenPathClick` 里 `LocalContext` 直接跳转。

修法：`StorageManageScreen` 增加 `onOpenPath: (String) -> Unit` 参数，由 `StorageManageActivity`（`BaseComposeActivity`）执行 `startActivity` + `putExtra(EXTRA_INITIAL_PATH)`；Screen 移除 `LocalContext`。

### S3. 其余套共性修法：C1（3 处）、C2（`showClearDialog`/`showClearAllDialog` 收进 `StorageUiState.dialog`）、C3、C5、C6（`CacheItemCard`/`CacheSummaryCard` 加 merge）。

保留亮点：`components/` 子包结构已合规，不动。

---

## 五、书源回收站

### R1. 13 个本地 `mutableStateOf` → State Holder【人工，§4.2 硬限 3 个】

修法：
- 7 个弹窗开关按 C2 收进 `UiState.dialog`（sealed）。
- 选择态 `selectedIds`、搜索态 `showSearch`/`searchQuery`：`selectedIds` 提升到 ViewModel（批量操作本就需要 ViewModel 感知）；`searchQuery` 是用户输入，按 §4.2 用 `rememberSaveable` 保进程重建存活。
- 菜单展开态 3 个保留本地 `remember`，合计不超 3 个。

### R2. ViewModel 直接 `toastOnUi`【人工，§4.1】

现状：`restore`/`delete`/`clearAll` 成功后 `context.toastOnUi(getString(...))`。

修法：ViewModel 抛 `Toast(@StringRes Int)` 事件（CONFLATED 通道），Activity 收集后执行；ViewModel 不再拼文案（§7.5）。

### R3. `checkConflict(item) { hasConflict -> }` 回调风格

修法：改为 `suspend fun hasConflict(...): Boolean`，Screen 侧 `rememberCoroutineScope().launch { ... }` 拿结果后设置 `UiState.dialog`——与 C2 的 Dialog 状态化天然衔接，消除"回调里改本地状态"的链路。

### R4. item 模型 `@Immutable`【人工，§8.1】

修法：`SourceRecycleBin`（Room entity）标注 `androidx.compose.runtime.Immutable`——纯编译期注解，不影响 Room；确认无可变集合字段后标注。

### R5. 其余套共性修法：C1（3 处裸 `collectAsState`，`items` 的 Flow 用 `stateIn(WhileSubscribed(5000))` 收敛）、C5、C6、C7（739 行，拆 `ui/source/recycle/components/`）。

保留亮点：字符串资源化、`items` key、`AppConfirmDialog` 复用均合规，不动。

---

## 六、实施顺序与里程碑

| 阶段 | 内容 | 涉及界面 |
|------|------|----------|
| P1 行为正确性 | D1（ViewModel 去 Context）、S2（跳转下沉）、F1（去手写 decode）、S1/F2（补 key） | 下载、存储、文件 |
| P2 机器项批量替换 | C1（collectAsStateWithLifecycle）、C3（stringResource）、C4（色值）、C5（.sp/dimens） | 全部 4 页 |
| P3 状态结构 | C2（Dialog 进 UiState + 返回键拦截）、R1（State Holder）、R2/R3（事件化） | 全部 4 页 |
| P4 结构拆分与无障碍 | C7（components/ 拆分）、C6（semantics）、F3（触控目标）、C9（AppPageTopBar） | 全部 4 页 |
| P5 测试 | C8（4 个 ViewModelTest） | 全部 4 页 |

每个阶段独立可提交，P1 完成后界面行为不变、只是架构归位；P2~P4 为纯重构。

## 七、验证方式

```bash
./gradlew.bat :app:compileAppMaxDebugKotlin   # 语法/编译
./gradlew lint                                # CI 把 lint 通过视为完成
./gradlew test                                # ViewModel 单测
```

人工回归：精准管理入口 → 4 个页面各自走一遍核心路径（下载取消/重试/打开文件、文件浏览/删除、缓存清理、回收站恢复/冲突覆盖/批量删除），重点验证返回键先关弹窗再退页面。

## 八、本次明确不做（另立计划）

- URL 访问记录、模块状态、权限管理三个页面的同类问题（已单独盘点，修法可直接套用本计划的 C 系列模板）
- `DownloadState` 轮询改 Flow 推送
- `ui/theme/Dimensions.kt` / `AppImage.kt` 目标态文件的正式建立（本批按"落地前过渡策略"就近定义）
