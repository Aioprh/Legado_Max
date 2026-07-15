# WebView 清除功能分析

## 完整流程

### 路径一：设置页面手动清除

```
1. XML 入口
   pref_config_other.xml:309  key="clearWebViewData"

2. UI 触发
   OtherConfigFragment.onPreferenceTreeClick()  (:208)
   → 匹配 PreferKey.clearWebViewData

3. 弹出确认对话框
   OtherConfigFragment.clearWebViewData()  (:418)
   → alert(R.string.clear_webview_data, R.string.sure_del)

4. 用户点击"确定"
   → ConfigViewModel.clearWebViewData()  (:33)

5. 协程执行（IO 线程）
   FileUtils.delete(context.getDir("webview", MODE_PRIVATE))     // 删 webview 目录
   FileUtils.delete(context.getDir("hws_webview", MODE_PRIVATE), true)  // 删 hws_webview 目录（含根）
   toastOnUi(R.string.clear_webview_data_success)  // 提示"清除成功，3秒后自动重启应用"

6. 等待 3 秒
   delay(3000)

7. 重启应用
   appCtx.restart()  → ContextExtensions.kt:212
   → getLaunchIntentForPackage → CLEAR_TASK | CLEAR_TOP | NEW_TASK
   → startActivity → killProcess → exitProcess(0)
```

### 路径二：存储管理页面清除

```
1. StorageManageScreen 点击清理  (:88)
   → StorageManageViewModel.clearCache(CacheType.WEBVIEW_CACHE)  (:180)

2. StorageCalculator.clearWebViewCache()  (:643)
   → invalidateCache()  // 清缓存计数
   → 遍历删除两个目录：
     - appCtx.getDir("webview", MODE_PRIVATE)
     - appCtx.getDir("hws_webview", MODE_PRIVATE)
   → 每个目录调 FileUtils.delete(path)

3. reloadCacheInfo()  // 刷新存储信息
```

### 关键区别

| | 路径一（设置页） | 路径二（存储管理） |
|---|---|---|
| 删除 webview 根目录 | 是（deleteRootDir 默认 true） | 是（delete(path) 默认 true） |
| 删除 hws_webview 根目录 | 是（显式传 true） | 是 |
| 弹确认对话框 | 是 | 否（由 Compose UI 管理） |
| 重启应用 | 是（3秒后） | 否 |
| 刷新缓存统计 | 否 | 是 |

`FileUtils.delete()` 内部采用 **先 rename 再 delete** 的策略解决 EBUSY 问题（`FileUtils.kt:332`）。

---

## 清除完整性分析

### 没清到的东西

#### 1. Android 系统级 WebView 目录

当前只删了 `getDir("webview")` 和 `getDir("hws_webview")`，但 WebView 还会在以下位置写数据：

- `app_chromium_webview/` — Chromium WebView 的默认数据目录
- `app_webview/` — 旧版 WebView 目录
- `shared_prefs/` 下的 WebView 相关 SP 文件（如 `WebViewChromiumPrefs.xml`）

#### 2. CookieManager 没清

`android.webkit.CookieManager.getInstance()` 在 WebView 数据清除时 **不会** 自动清理。`OtherConfigFragment` 的 `clearWebViewData` 没有调用 `CookieManager.getInstance().removeAllCookies(null)`。

数据库层面的 `CookieStore`（Room `cookie` 表）也没清。

#### 3. WebStorage 没清

`android.webkit.WebStorage.getInstance().deleteAllData()` 没有调用，IndexedDB / localStorage 残留。

#### 4. WebViewPool 内存中的实例没销毁

`WebViewPool` 持有 `idlePool` 和 `inUsePool`，清除数据后这些 WebView 实例仍然存活，旧的 JS 环境、Cookie、localStorage 都还在。虽然有 3 秒后重启，但重启前这些实例都还在用。

#### 5. CacheManager 内存缓存没清

`BackstageWebView.setCookie()` 会往 `CacheManager` 内存里写 `xxx_cookie` / `xxx_session_cookie`，这些是进程级内存，删文件目录不影响。

#### 6. `FileUtils.delete` 的 deleteRootDir 默认值问题

`ConfigViewModel:35` 对 `webview` 目录调用 `delete(context.getDir("webview", MODE_PRIVATE))` 没传第二个参数，默认 `deleteRootDir = true`（`FileUtils.kt:344`），所以根目录会删。但 `hws_webview` 那行显式传了 `true`，两个都删了，这部分没问题。

### 清除完整性对照表

| 要清的 | 是否清到 |
|--------|---------|
| `app_webview/` 目录 | ✅ |
| `app_hws_webview/` 目录 | ✅ |
| `app_chromium_webview/` 目录 | ❌ |
| `shared_prefs/WebViewChromiumPrefs.xml` | ❌ |
| `android.webkit.CookieManager` | ❌ |
| `android.webkit.WebStorage` | ❌ |
| Room `cookie` 表 | ❌ |
| `CacheManager` 内存中的 cookie | ❌ |
| `WebViewPool` 中存活的实例 | ❌ |

对于"杀进程重启"的场景，内存相关的问题（CacheManager、WebViewPool）会被重启清掉，但 **chromium 数据目录、SharedPreferences、系统 CookieManager、WebStorage** 这些是磁盘持久化的，重启也清不掉。
