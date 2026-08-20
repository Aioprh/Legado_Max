# 书架多本同书名书籍支持 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让书架支持多本同书名同作者但不同书源的书籍各自独立存在；在首页/发现/搜索点击已有同名不同源的书籍时弹窗让用户选择进入哪个源的详情页；加入书架时若检测到同名冲突则弹窗让用户选择"替换"或"新增一本"。

**架构：** 三层改动——DAO 层新增 `getShelfBookConflict` 查询（仅加入书架时调用，不用于列表点击）；`BookshelfMatcher` 新增 `getShelfBookUrl()` 方法（O(1) 内存查询，用于列表点击跳转）；ViewModel/UI 层在 3 个入口的 `showBookInfo` 前做 O(1) 冲突检测，详情页 `addToBookshelf` 前做数据库冲突检测。

**性能策略：**
- 列表点击跳转：**零数据库查询**，利用 `BookshelfMatcher` 已有的 `ConcurrentHashMap` 内存缓存做 O(1) 判断
- 加入书架冲突检测：仅在用户主动点击"加入书架"时查一次数据库（`getShelfBookConflict`），不在列表滚动或绑定中触发
- `initData` 查找顺序调整：不加新查询，只调换已有查询的先后顺序
- `loadBookInfo` 合并条件收紧：不加新查询，只修改已有查询的判断条件

**技术栈：** Kotlin + Room + MVVM (LiveData + ViewModel) + View Binding + AlertDialog

---

## 文件结构

| 文件 | 职责 | 性能影响 |
|---|---|---|
| `app/src/main/java/io/legado/app/data/dao/BookDao.kt` | 新增 `getShelfBookConflict` 查询 | 仅加入书架时调用一次 |
| `app/src/main/java/io/legado/app/help/book/BookshelfMatcher.kt` | 新增 `getShelfBookUrl()` 方法 | O(1) 内存查询，零 IO |
| `app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt` | 调整 `initData` 顺序、收紧 `loadBookInfo` 条件、`addToBookshelf` 冲突检测 | 不加新查询 |
| `app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt` | 详情页加入书架冲突弹窗 | 仅点击时触发 |
| `app/src/main/java/io/legado/app/ui/book/search/SearchActivity.kt` | 搜索列表点击冲突检测 | O(1) 内存查询 |
| `app/src/main/java/io/legado/app/ui/book/explore/ExploreShowActivity.kt` | 发现列表点击冲突检测 | O(1) 内存查询 |
| `app/src/main/java/io/legado/app/ui/main/homepage/HomepageFragment.kt` | 首页列表点击冲突检测 | O(1) 内存查询 |
| `app/src/main/res/values/strings.xml` | 新增字符串资源 | 无 |
| `app/src/main/res/values-zh-rCN/strings.xml` | 新增中文字符串 | 无 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 新增繁体中文字符串 | 无 |
| `app/src/main/res/values-zh-rHK/strings.xml` | 新增繁体中文字符串 | 无 |

---

## 任务 1：DAO 层 — 新增 `getShelfBookConflict` 查询

**文件：**
- 修改：`app/src/main/java/io/legado/app/data/dao/BookDao.kt`

- [ ] **步骤 1：在 `BookDao` 中添加 `getShelfBookConflict` 方法**

在 `getBookByOrigin` 方法之后插入：

```kotlin
    /**
     * 查询书架上是否存在同名同作者的书籍（排除 notShelf）。
     *
     * 仅在用户主动点击"加入书架"时调用一次，不在列表滚动中使用。
     * 返回最近阅读的那条记录，供弹窗显示已有书的书源信息。
     */
    @Query(
        """
        SELECT * FROM books
        WHERE name = :name AND author = :author
            AND type & ${BookType.notShelf} = 0
        ORDER BY durChapterTime DESC
        LIMIT 1
        """
    )
    fun getShelfBookConflict(name: String, author: String): Book?
```

- [ ] **步骤 2：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/data/dao/BookDao.kt
git commit -m "feat(book): 新增 getShelfBookConflict 查询用于加入书架时冲突检测"
```

---

## 任务 2：BookshelfMatcher — 新增 `getShelfBookUrl()` 方法

**文件：**
- 修改：`app/src/main/java/io/legado/app/help/book/BookshelfMatcher.kt`

在已有的 `exactKeys`（`ConcurrentHashSet<Triple<name, author, bookUrl>>`）上新增一个方法，用于在列表点击时 O(1) 获取已有书架记录的 `bookUrl`。

- [ ] **步骤 1：新增 `bookUrlByKey` 映射集合和 `getShelfBookUrl` 方法**

在 `BookshelfMatcher` 中，`nameAuthorKeys` 声明之后添加：

```kotlin
    /** name+author → bookUrl 列表映射，用于 O(1) 获取书架上所有同名同作者书的 bookUrl */
    private val nameAuthorToBookUrls: MutableMap<Pair<String, String>, MutableList<String>> =
        java.util.concurrent.ConcurrentHashMap()
```

在 `start()` 方法的 `keys.forEach` 块中，在 `nameAuthorKeys.add(key.name to key.author)` 之后添加：

```kotlin
                    nameAuthorToBookUrls
                        .getOrPut(key.name to key.author) { mutableListOf() }
                        .add(key.bookUrl)
```

在 `start()` 方法的 `exactKeys.clear()` 和 `nameAuthorKeys.clear()` 之后添加：

```kotlin
                nameAuthorToBookUrls.clear()
```

在 `getState` 方法之后添加：

```kotlin
    /**
     * 获取同名同作者在书架上的 bookUrl 列表（O(1) 内存查询）。
     *
     * 供列表点击跳转时判断是否需要弹窗选择。
     * - 返回 null → 书架上没有同名书，直接跳转
     * - 返回列表中包含 bookUrl → 就是书架上某本，直接跳转
     * - 返回列表非空但不包含 bookUrl → 书架上有同名不同源的书，弹窗选择
     *
     * 注意：列表可能包含多个 bookUrl（多本同名书在架），
     * 当前 MVP 版弹窗仅展示第一个不等于当前 bookUrl 的条目。
     * 未来可扩展为多选弹窗。
     */
    fun getShelfBookUrl(name: String, author: String): String? {
        val urls = nameAuthorToBookUrls[name to author] ?: return null
        // 返回第一个 bookUrl（用于弹窗"书架书源"选项）
        return urls.firstOrNull()
    }

    /**
     * 获取同名同作者在书架上的所有 bookUrl 列表（O(1) 内存查询）。
     *
     * 当书架上有 2 本以上同名书时，返回完整列表供未来扩展多选弹窗。
     * 当前 MVP 版 ShelfConflictHelper 仅使用 [getShelfBookUrl] 的单值返回。
     */
    fun getShelfBookUrls(name: String, author: String): List<String>? {
        return nameAuthorToBookUrls[name to author]?.takeIf { it.isNotEmpty() }
    }
```

- [ ] **步骤 2：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/help/book/BookshelfMatcher.kt
git commit -m "feat(bookshelf): BookshelfMatcher 新增 getShelfBookUrl 方法用于 O(1) 获取书架书 URL"
```

---

## 任务 3：字符串资源 — 新增冲突弹窗文案

**文件：**
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-zh-rCN/strings.xml`
- 修改：`app/src/main/res/values-zh-rTW/strings.xml`
- 修改：`app/src/main/res/values-zh-rHK/strings.xml`

- [ ] **步骤 1：在 `values/strings.xml` 中添加英文字符串**

在 `add_to_bookshelf` 字符串附近添加：

```xml
    <string name="bookshelf_book_conflict_title">Book Already on Bookshelf</string>
    <string name="bookshelf_book_conflict_message">A book with the same title and author already exists. Replace it while keeping its reading data, or add another copy.</string>
    <string name="replace_current_book">Replace This Book</string>
    <string name="add_another_copy">Add Another Copy</string>
    <string name="shelf_conflict_choose_title">Book Already on Bookshelf</string>
    <string name="shelf_conflict_choose_message">This book is already on your bookshelf from a different source. Which version would you like to open?</string>
    <string name="shelf_conflict_current_source">Current Source</string>
    <string name="shelf_conflict_shelf_source">Bookshelf Source</string>
```

- [ ] **步骤 2：在 `values-zh-rCN/strings.xml` 中添加简体中文**

```xml
    <string name="bookshelf_book_conflict_title">书架中已有同名书籍</string>
    <string name="bookshelf_book_conflict_message">检测到书名和作者相同的书籍。可替换现有书并保留阅读数据，或仍然新增一本。</string>
    <string name="replace_current_book">替换本书</string>
    <string name="add_another_copy">新增一本</string>
    <string name="shelf_conflict_choose_title">书架中已有同名书籍</string>
    <string name="shelf_conflict_choose_message">此书已在书架中但来自不同书源，要打开哪个版本？</string>
    <string name="shelf_conflict_current_source">当前书源</string>
    <string name="shelf_conflict_shelf_source">书架书源</string>
```

- [ ] **步骤 3：在 `values-zh-rTW/strings.xml` 中添加繁体中文（台湾）**

```xml
    <string name="bookshelf_book_conflict_title">書架中已有同名書籍</string>
    <string name="bookshelf_book_conflict_message">偵測到書名和作者相同的書籍。可替換現有書並保留閱讀資料，或仍然新增一本。</string>
    <string name="replace_current_book">替換本書</string>
    <string name="add_another_copy">新增一本</string>
    <string name="shelf_conflict_choose_title">書架中已有同名書籍</string>
    <string name="shelf_conflict_choose_message">此書已在書架中但來自不同書源，要開啟哪個版本？</string>
    <string name="shelf_conflict_current_source">當前書源</string>
    <string name="shelf_conflict_shelf_source">書架書源</string>
```

- [ ] **步骤 4：在 `values-zh-rHK/strings.xml` 中添加繁体中文（香港）**

```xml
    <string name="bookshelf_book_conflict_title">書架中已有同名書籍</string>
    <string name="bookshelf_book_conflict_message">偵測到書名和作者相同的書籍。可替換現有書並保留閱讀資料，或仍然新增一本。</string>
    <string name="replace_current_book">替換本書</string>
    <string name="add_another_copy">新增一本</string>
    <string name="shelf_conflict_choose_title">書架中已有同名書籍</string>
    <string name="shelf_conflict_choose_message">此書已在書架中但來自不同書源，要開啟哪個版本？</string>
    <string name="shelf_conflict_current_source">當前書源</string>
    <string name="shelf_conflict_shelf_source">書架書源</string>
```

- [ ] **步骤 5：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/res/values-zh-rHK/strings.xml
git commit -m "feat(i18n): 新增书架同名冲突弹窗的四语言字符串资源"
```

---

## 任务 4：通用工具 — 列表点击冲突检测弹窗

**文件：**
- 创建：`app/src/main/java/io/legado/app/ui/book/info/ShelfConflictHelper.kt`

一个轻量的工具对象，封装列表点击时的 O(1) 冲突检测和弹窗逻辑，供首页、发现、搜索三个入口复用。

- [ ] **步骤 1：创建 `ShelfConflictHelper`**

```kotlin
package io.legado.app.ui.book.info

import android.app.Activity
import io.legado.app.R
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.startActivity

/**
 * 列表点击跳转详情页时的同名冲突检测工具。
 *
 * 利用 [BookshelfMatcher] 的内存缓存做 O(1) 判断，不查数据库。
 * 当书架上已有同名同作者但不同 bookUrl 的书时，弹窗让用户选择
 * 进入当前源的详情页还是书架已有书的详情页。
 */
object ShelfConflictHelper {

    /**
     * 检查并跳转。
     *
     * @param activity 当前 Activity（用于 startActivity 和 alert）
     * @param name 书名
     * @param author 作者
     * @param bookUrl 当前点击书籍的 bookUrl
     * @param origin 当前点击书籍的书源 URL
     * @param originName 当前点击书籍的书源名称（用于弹窗显示）
     * @param coverPath 封面路径（可选，传给详情页）
     *
     * 性能：O(1) 内存查询，零数据库 IO。
     * 如果无冲突直接跳转；有冲突才弹窗。
     */
    fun showBookInfoWithConflictCheck(
        activity: Activity,
        name: String,
        author: String,
        bookUrl: String,
        origin: String = "",
        originName: String? = null,
        coverPath: String? = null
    ) {
        val shelfBookUrls = BookshelfMatcher.getShelfBookUrls(name, author)
        if (shelfBookUrls == null) {
            // 书架上没有同名书，直接跳转
            launchBookInfo(activity, name, author, bookUrl, origin, originName, coverPath)
            return
        }
        if (bookUrl in shelfBookUrls) {
            // 当前点击的书就在书架上，直接跳转
            launchBookInfo(activity, name, author, bookUrl, origin, originName, coverPath)
            return
        }
        // 书架上有同名书但不是当前这本，弹窗选择
        // MVP：取第一个作为"书架书源"选项
        val shelfBookUrl = shelfBookUrls.first()
        showChooseSourceDialog(
            activity,
            name,
            author,
            bookUrl,
            origin,
            originName,
            shelfBookUrl,
            coverPath
        )
    }

    private fun showChooseSourceDialog(
        activity: Activity,
        name: String,
        author: String,
        currentBookUrl: String,
        currentOrigin: String,
        currentOriginName: String?,
        shelfBookUrl: String,
        coverPath: String?
    ) {
        activity.alert(
            titleResource = R.string.shelf_conflict_choose_title,
            messageResource = R.string.shelf_conflict_choose_message
        ) {
            // 当前书源
            yesButton(R.string.shelf_conflict_current_source) {
                launchBookInfo(activity, name, author, currentBookUrl, currentOrigin, currentOriginName, coverPath)
            }
            // 书架已有书源
            noButton(R.string.shelf_conflict_shelf_source) {
                launchBookInfo(activity, name, author, shelfBookUrl, "", null, null)
            }
            neutralButton(R.string.cancel) {}
        }
    }

    private fun launchBookInfo(
        activity: Activity,
        name: String,
        author: String,
        bookUrl: String,
        origin: String,
        originName: String?,
        coverPath: String?
    ) {
        activity.startActivity<BookInfoActivity> {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
            if (origin.isNotEmpty()) putExtra("origin", origin)
            originName?.let { putExtra("originName", it) }
            coverPath?.let { putExtra("coverPath", it) }
        }
    }
}
```

- [ ] **步骤 2：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/info/ShelfConflictHelper.kt
git commit -m "feat(book-info): 新增 ShelfConflictHelper 用于列表点击时 O(1) 同名冲突检测"
```

---

## 任务 5：搜索列表 — 点击跳转接入冲突检测

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/book/search/SearchActivity.kt:613-620`

- [ ] **步骤 1：修改 `showBookInfo` 方法**

将现有的 `showBookInfo` 方法替换为调用 `ShelfConflictHelper`：

```kotlin
    override fun showBookInfo(name: String, author: String, bookUrl: String, origin: String) {
        ShelfConflictHelper.showBookInfoWithConflictCheck(
            activity = this,
            name = name,
            author = author,
            bookUrl = bookUrl,
            origin = origin
        )
    }
```

- [ ] **步骤 2：添加 import**

在文件顶部 import 区域添加：

```kotlin
import io.legado.app.ui.book.info.ShelfConflictHelper
```

- [ ] **步骤 3：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/search/SearchActivity.kt
git commit -m "feat(search): 搜索列表点击接入同名冲突检测弹窗"
```

---

## 任务 6：发现列表 — 点击跳转接入冲突检测

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/book/explore/ExploreShowActivity.kt:855-862`

- [ ] **步骤 1：修改 `showBookInfo` 方法**

将现有的 `showBookInfo(book: SearchBook)` 方法替换为：

```kotlin
    override fun showBookInfo(book: SearchBook) {
        ShelfConflictHelper.showBookInfoWithConflictCheck(
            activity = this,
            name = book.name,
            author = book.author,
            bookUrl = book.bookUrl,
            origin = book.origin,
            originName = book.originName
        )
    }
```

- [ ] **步骤 2：添加 import**

在文件顶部 import 区域添加：

```kotlin
import io.legado.app.ui.book.info.ShelfConflictHelper
```

- [ ] **步骤 3：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/explore/ExploreShowActivity.kt
git commit -m "feat(explore): 发现列表点击接入同名冲突检测弹窗"
```

---

## 任务 7：首页列表 — 点击跳转接入冲突检测

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/main/homepage/HomepageFragment.kt:83-90`

- [ ] **步骤 1：修改书源书籍跳转逻辑**

将现有的书源书籍跳转块替换为：

```kotlin
                            // 书源书籍 → 跳转详情页（带同名冲突检测）
                            ShelfConflictHelper.showBookInfoWithConflictCheck(
                                activity = requireActivity(),
                                name = name,
                                author = author,
                                bookUrl = bookUrl,
                                origin = origin,
                                coverPath = coverPath
                            )
```

- [ ] **步骤 2：添加 import**

在文件顶部 import 区域添加：

```kotlin
import io.legado.app.ui.book.info.ShelfConflictHelper
```

- [ ] **步骤 3：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/main/homepage/HomepageFragment.kt
git commit -m "feat(homepage): 首页列表点击接入同名冲突检测弹窗"
```

---

## 任务 8：ViewModel 层 — 调整 `initData` 查找顺序

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt:77-147`

将 `initData` 的查找顺序从"name+author 优先"改为"bookUrl 优先"，避免不同 `bookUrl` 的书被合并到已有同名书上。

- [ ] **步骤 1：重写 `initData` 方法**

将现有 `initData` 方法（第 77-147 行）替换为：

```kotlin
    fun initData(intent: Intent) {
        execute {
            inBookshelf = false
            hasCustomBtn = false
            bookSource = null
            fromAuthorOtherWorks = intent.getBooleanExtra("fromAuthorOtherWorks", false)
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            val bookUrl = intent.getStringExtra("bookUrl") ?: ""

            // 优先用 bookUrl 精确查找
            // 关键：仅用 bookUrl 精确匹配判断是否在书架，不做 name+author 回退
            // 这确保源 B 的书不会误命中源 A 的书架记录
            if (bookUrl.isNotBlank()) {
                appDb.bookDao.getBook(bookUrl)?.let { book ->
                    inBookshelf = !book.isNotShelf
                    upBook(book)
                    return@execute
                }
                appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let { book ->
                    inBookshelf = false
                    upBook(book)
                    return@execute
                }
                // bookUrl 有值但 books 表和 searchBook 表都未命中
                // 直接创建临时 Book 对象（含 origin），不做 name+author 回退
                // 这避免发现页/首页无搜索记录时误命中书架同名书
                if (name.isNotBlank()) {
                    val tempBook = Book(
                        bookUrl = bookUrl,
                        name = name,
                        author = author,
                        origin = intent.getStringExtra("origin") ?: ""
                    )
                    upBook(tempBook)
                    return@execute
                }
            }

            // 以下回退仅在 bookUrl 为空时执行
            appDb.bookDao.getBook(name, author)?.let { book ->
                inBookshelf = !book.isNotShelf
                upBook(book)
                return@execute
            }

            appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let { book ->
                inBookshelf = false
                upBook(book)
                return@execute
            }

            throw NoStackTraceException("未找到书籍")
        }.onError {
            AppLog.put(it.localizedMessage, it)
            context.toastOnUi(it.localizedMessage)
        }
    }
```

**设计决策说明：**

- **移除了 `upResolvedBook` / `resolveShelfBook` 及其 name+author 回退逻辑。** 原计划中 `resolveShelfBook` 在 bookUrl 精确匹配失败后会回退到 name+author 查找，导致源 B 的候选书误命中源 A 的书架记录（`inBookshelf=true`），使任务 10/11 的冲突弹窗永远无法触发（`BookInfoActivity` 第 888 行 `if (viewModel.inBookshelf)` 直接走"移出书架"分支，不会进入 `else` 中的 `addToBookshelf`）。
- 现在的逻辑非常简洁：`bookUrl` 精确查找 → 找到则 `inBookshelf = !book.isNotShelf`，找不到则从搜索记录获取或创建临时对象，`inBookshelf = false`。
- 这确保用户从 `ShelfConflictHelper` 弹窗选择"当前书源（源 B）"后，进入详情页看到的是源 B 的书，`inBookshelf=false` → 点击"加入书架" → 触发任务 10 的冲突检测 → 弹出"替换/新增"弹窗。

- [ ] **步骤 2：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt
git commit -m "refactor(book-info): initData 查找顺序改为 bookUrl 优先，移除 name+author 回退"
```

---

## 任务 9：~~ViewModel 层 — 收紧 `loadBookInfo` 合并条件~~（已完成，现状已满足）

**状态：无需修改。** 当前分支 `BookInfoViewModel.kt` 第 257-261 行已实现目标逻辑：

```kotlin
val dbBook = appDb.bookDao.getBook(book.name, book.author)
if (!inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == book.origin) {
    dbBook.updateTo(it)
    inBookshelf = true
}
```

条件 `dbBook.origin == book.origin` 已确保仅在书架上同名同源书时才合并，不同源的书不会被误合并。执行此任务会产生零 diff。

**保留说明：** 任务 8 的 `initData` 改动已确保源 B 的书以 `inBookshelf=false` 进入 `loadBookInfo`，配合已有的 `origin` 检查条件，不会误合并。

---

## 任务 10：ViewModel 层 — `addToBookshelf` 增加冲突检测

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt:617-655`

- [ ] **步骤 1：修改 `addToBookshelf` 方法**

将现有 `addToBookshelf` 方法替换为：

```kotlin
    /** 标记 addToBookshelf 因冲突退出，阻止 onSuccess 调用 success */
    private var addToBookshelfConflict: Boolean = false

    fun addToBookshelf(
        success: (() -> Unit)? = null,
        groupId: Long? = null,
        onConflict: ((existingBook: Book) -> Unit)? = null,
        force: Boolean = false
    ) {
        addToBookshelfConflict = false
        execute {
            inBookshelf = true
            bookData.value?.let { book ->
                book.removeType(BookType.notShelf)
                if (groupId != null) {
                    AppLog.put("addToBookshelf: 设置groupId=$groupId, bookUrl=${book.bookUrl}")
                    book.group = groupId
                }
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }

                // 冲突检测：仅在此处查一次数据库
                // force=true 时跳过（用户已在弹窗中选"新增一本"）
                if (!force) {
                    val conflictBook = appDb.bookDao.getShelfBookConflict(book.name, book.author)
                    if (conflictBook != null && conflictBook.bookUrl != book.bookUrl) {
                        inBookshelf = false
                        book.addType(BookType.notShelf)
                        addToBookshelfConflict = true
                        onConflict?.invoke(conflictBook)
                        return@execute
                    }
                }

                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterIndex = it.durChapterIndex
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                if (ReadBook.book?.isSameNameAuthor(book) == true) {
                    ReadBook.book = book
                    ReadBook.inBookshelf = true
                } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                    AudioPlay.book = book
                    AudioPlay.inBookshelf = true
                } else if (ReadManga.book?.isSameNameAuthor(book) == true) {
                    ReadManga.inBookshelf = true
                }
                book.save()
                SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, bookSource, book)
            }
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onError {
            inBookshelf = false
            AppLog.put("加入书架失败: ${it.localizedMessage}", it)
        }.onSuccess {
            // 注意：execute 的 onSuccess 在 block 正常结束（含 return@execute）时总会触发
            // 冲突分支 return@execute 后 addToBookshelfConflict=true，阻止 success 误执行
            if (!addToBookshelfConflict) {
                success?.invoke()
            }
        }
    }

    /**
     * 替换书架上已有的同名书：保留阅读进度，用新书替换旧书记录。
     */
    fun replaceShelfBook(
        oldBook: Book,
        newBook: Book,
        success: (() -> Unit)? = null
    ) {
        execute {
            newBook.durChapterIndex = oldBook.durChapterIndex
            newBook.durChapterPos = oldBook.durChapterPos
            newBook.durChapterTitle = oldBook.durChapterTitle
            newBook.group = oldBook.group
            newBook.order = oldBook.order
            newBook.customCoverUrl = oldBook.customCoverUrl
            newBook.customIntro = oldBook.customIntro
            newBook.customTag = oldBook.customTag
            newBook.canUpdate = oldBook.canUpdate
            newBook.readConfig = oldBook.readConfig
            newBook.removeType(BookType.notShelf)
            appDb.bookDao.replace(oldBook, newBook)
            if (oldBook.bookUrl != newBook.bookUrl) {
                BookHelp.updateCacheFolder(oldBook, newBook)
            }
            bookData.postValue(newBook)
            inBookshelf = true
            if (ReadBook.book?.isSameNameAuthor(newBook) == true) {
                ReadBook.book = newBook
                ReadBook.inBookshelf = true
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            inBookshelf = false
            AppLog.put("替换书架书籍失败: ${it.localizedMessage}", it)
        }
    }
```

- [ ] **步骤 2：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt
git commit -m "feat(book-info): addToBookshelf 增加同名冲突检测和 replaceShelfBook 方法"
```

---

## 任务 11：UI 层 — 详情页加入书架冲突弹窗

**文件：**
- 修改：`app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt:886-899`

- [ ] **步骤 1：修改 `tvShelf.setOnClickListener`**

将现有的 `tvShelf.setOnClickListener` 块替换为：

```kotlin
        tvShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf(
                            success = {
                                upTvBookshelf()
                            },
                            onConflict = { existingBook ->
                                showShelfConflictDialog(existingBook, book)
                            }
                        )
                    }
                }
            }
        }
```

- [ ] **步骤 2：添加 `showShelfConflictDialog` 方法**

在 `deleteBook()` 方法之前插入：

```kotlin
    /**
     * 加入书架时的同名冲突对话框。
     * "替换" → 保留阅读进度并替换；"新增" → 作为独立新书添加。
     */
    private fun showShelfConflictDialog(existingBook: Book, newBook: Book) {
        alert(
            titleResource = R.string.bookshelf_book_conflict_title,
            messageResource = R.string.bookshelf_book_conflict_message
        ) {
            yesButton(R.string.replace_current_book) {
                viewModel.replaceShelfBook(existingBook, newBook) {
                    upTvBookshelf()
                    toastOnUi(R.string.success)
                }
            }
            noButton(R.string.add_another_copy) {
                viewModel.addToBookshelf(
                    success = {
                        upTvBookshelf()
                        toastOnUi(R.string.success)
                    },
                    force = true  // 用户已确认新增，跳过冲突检测
                )
            }
            neutralButton(R.string.cancel) {}
        }
    }
```

- [ ] **步骤 3：验证编译通过**

运行：`.\gradlew.bat :app:compileAppMaxDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt
git commit -m "feat(book-info): 加入书架时检测同名冲突并弹窗让用户选择替换或新增"
```

---

## 任务 12：端到端验证

- [ ] **步骤 1：构建 debug APK**

运行：`.\gradlew.bat :app:assembleAppMaxDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：安装到设备**

运行：`.\gradlew.bat :app:installAppMaxDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：手动测试场景**

1. **搜索列表点击同名不同源书 → 弹窗选择** — 先从源A搜索加入书架 → 再搜索同名书（源B）→ 点击 → 验证弹出"当前书源 / 书架书源"选择弹窗
2. **弹窗选择"当前书源"** → 验证进入源B的详情页
3. **弹窗选择"书架书源"** → 验证进入源A（已有书架书）的详情页
4. **详情页加入书架冲突弹窗** → 搜索源B书进入详情页 → 点"加入书架" → 验证弹出"替换/新增"弹窗
5. **选择"新增一本"** → 验证书架上出现两本同名书
6. **选择"替换本书"** → 验证书架上只有一本但换了源，阅读进度保留
7. **发现页点击同名不同源书** → 验证同样弹出选择弹窗
8. **首页点击同名不同源书** → 验证同样弹出选择弹窗
9. **无冲突时直接跳转** → 搜索一本全新书 → 点击 → 验证不弹窗直接进入详情页

- [ ] **步骤 4：Commit 最终状态**

```bash
git add -A
git commit -m "test: 完成多本同书名书籍支持的端到端验证"
```

---

## 自检

### 1. 规格覆盖度

| 需求 | 对应任务 |
|---|---|
| DAO 层 `getShelfBookConflict` 查询 | 任务 1 |
| `BookshelfMatcher` 新增 `getShelfBookUrl` O(1) 方法 | 任务 2 |
| 四语言字符串资源（含列表跳转弹窗 + 加入书架弹窗） | 任务 3 |
| 通用 `ShelfConflictHelper` 工具（列表点击冲突检测） | 任务 4 |
| 搜索列表点击接入冲突检测 | 任务 5 |
| 发现列表点击接入冲突检测 | 任务 6 |
| 首页列表点击接入冲突检测 | 任务 7 |
| `initData` 查找顺序改为 bookUrl 优先 | 任务 8 |
| `loadBookInfo` 仅同书源才合并 | 任务 9 |
| `addToBookshelf` 冲突检测 + `replaceShelfBook` | 任务 10 |
| 详情页加入书架冲突弹窗 | 任务 11 |
| 端到端验证 | 任务 12 |

### 2. 性能审查

| 操作 | 数据库查询次数 | 内存查询次数 | 说明 |
|---|---|---|---|
| 列表滚动绑定 | 0 | O(1) per item | `BookshelfMatcher.getState` 已有 |
| 列表点击跳转 | 0 | O(1) | `BookshelfMatcher.getShelfBookUrl` 内存查询 |
| 详情页 `initData` 查找 | 1-2（已有查询，无新增） | 0 | bookUrl 优先：有 bookUrl 时不回退 name+author |
| 详情页 `loadBookInfo` 合并 | 1（已有查询，无新增） | 0 | 仅修改判断条件加 `origin` 检查 |
| 加入书架冲突检测 | 1（新增 `getShelfBookConflict`） | 0 | 仅用户主动点击时触发一次 |
| 替换书架书 | 2（replace + updateCacheFolder） | 0 | 仅用户确认替换时触发 |

**关键结论：列表点击跳转零数据库查询，利用 `BookshelfMatcher` 的 `ConcurrentHashMap` 内存缓存做 O(1) 判断。**

### 3. 占位符扫描

- 无 `TODO`、`FIXME`、`XXX`、`???` 占位符
- 所有代码块均为完整可编译的实现，不是伪代码
- 行号引用基于当前分支 `0814-18` 的最新代码状态

### 4. 类型一致性

| 方法签名 | 返回类型 | 调用方 |
|---|---|---|
| `BookDao.getShelfBookConflict(name, author)` | `Book?` | `BookInfoViewModel.addToBookshelf` |
| `BookshelfMatcher.getShelfBookUrl(name, author)` | `String?` | `ShelfConflictHelper.showBookInfoWithConflictCheck` |
| `BookInfoViewModel.addToBookshelf(success, groupId, onConflict, force)` | `Unit` | `BookInfoActivity.tvShelf.setOnClickListener` |
| `BookInfoViewModel.replaceShelfBook(oldBook, newBook, success)` | `Unit` | `BookInfoActivity.showShelfConflictDialog` |
| `ShelfConflictHelper.showBookInfoWithConflictCheck(...)` | `Unit` | SearchActivity / ExploreShowActivity / HomepageFragment |

### 5. 风险评估

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| `nameAuthorToBookUrls` 映射可能过时 | 用户在详情页加入新书后，缓存未立即更新 | `BookshelfMatcher` 已有 Room Flow 订阅，数据库变化会自动刷新缓存 |
| `addToBookshelfConflict` 实例变量线程安全 | `execute` block 在 IO 线程，`onSuccess` 在 Main 线程，可能可见性问题 | `Boolean` 写入是原子操作，且 `addToBookshelfConflict = false` 在 `execute` 调用前（Main 线程），block 内的 `= true` 在 IO 线程，`onSuccess` 回到 Main 线程读取。由于 `execute` 的 `start` 默认 `DEFAULT`，block 先于 `onSuccess` 完成，happens-before 关系保证可见性 |
| 弹窗打扰用户 | 频繁弹窗影响体验 | 仅在 `SAME_NAME_AUTHOR` 状态（同名不同 bookUrl）才弹窗，`IN_SHELF` 和 `NOT_IN_SHELF` 不弹窗 |
| `getShelfBookUrl` 仅返回首个 bookUrl | 书架上有 3 本以上同名书时，弹窗“书架书源”只能进入最后一本 | MVP 已接受此限制；`getShelfBookUrls` 方法已返回完整列表，未来可扩展为多选弹窗 |
| `replaceShelfBook` 的 `appDb.bookDao.replace` 方法 | 需确认 DAO 中有此方法 | **已确认存在**（`BookDao.kt:200`） |
| `BookHelp.updateCacheFolder` 方法 | 需确认存在 | **已确认存在**（`BookHelp.kt:78`） |
| `alert` DSL 的 `yesButton/noButton/neutralButton` | 需确认 API 存在 | 当前分支已在其他地方使用 `alert` DSL，API 已确认 |

---

## 执行方式

### 方式 A：子代理驱动（推荐）

使用 `superpowers:subagent-driven-development` 逐任务实现，每个任务完成后审查。

### 方式 B：内联执行

使用 `superpowers:executing-plans` 批量执行，设置检查点。