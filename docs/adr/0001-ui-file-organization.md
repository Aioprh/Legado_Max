# UI 文件组织边界（0001）

**Status**: accepted

UI 文件组织遵循"功能聚合优先、纯分层稍让步"的原则：页面级 UI 一律归 `ui/` 包，但跨页复用的 UI 基建（`base/`、`utils/` 的 View/Dialog/Fragment 扩展、`lib/theme`、`help/webView` 等）允许留在原包，不强制搬入 `ui/`。

## 背景

审计发现 `ui/` 外散布少量页面级 UI 文件，主要两处：`help/gsyVideo/` 的 10 个视频播放器文件，以及 `receiver/SharedReceiverActivity`。其余"观感乱"来自命名/包名语义不一致（如 `utils/` 里的 UI 扩展），而非摆放错误。

## 决策

1. `help/gsyVideo/` 全部 10 个文件整包迁入 `ui/video/player/`。它是自洽的播放器子系统，拆开 UI 件与 Exo 引擎会切断 `ExoXxxManager`↔`VideoPlayer` 的紧耦合。
2. `receiver/SharedReceiverActivity` 迁入新建的 `ui/shared/`。
3. 明确**不搬**：`help/TextViewTagHandler`、`help/webView/*`、`help/http/BackstageWebView`、`utils/*Extensions`、`viewbindingdelegate/`、`base/`、`lib/` —— 均为跨页基建，按 C 方案留在原地。
4. `lib/theme/view/ThemeBottomNavigationView` 是死代码（0 处外部引用），另案处理，不随本次迁移。

## 接受取舍

`service/VideoPlayService`、`model/VideoPlay` 依赖 `help/gsyVideo.*`；整包迁入 `ui/video/player/` 后这些非 UI 层将 import `ui.video.player.*`，形成"非 UI 层依赖 UI 包"的倒挂。因项目本就按功能而非分层组织，且包名不变不破坏编译，接受此取舍。

## 验收标准

迁移后 `grep "help.gsyVideo"` 0 命中；`ui/` 外不再存在页面级 Dialog/Adapter/Activity（基建命名例外如前所列）。
