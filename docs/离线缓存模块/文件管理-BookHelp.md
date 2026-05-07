# 文件管理（BookHelp）详解

主要内容：

缓存文件夹结构与布局
章节内容读写（saveText/getContent）
图片下载与管理（saveImage/getImage）
ConcurrentHashMap + Mutex 防并发机制
漫画缓存清理策略
Epub 文件处理
缓存扫描（getChapterFiles）
去重标题管理
@Synchronized 文件写入保护

## 概述

`BookHelp` 是一个 **object（单例）**，负责管理书籍相关的本地文件操作，包括：
- 缓存文件夹管理
- 章节内容读写
- 图片下载和管理
- Epub 文件处理
- 漫画缓存清理
- 章节文件扫描

---

## 目录结构

### 缓存文件夹布局

```
appCtx.externalFiles/
└── book_cache/
    └── {book.getFolderName()}/
        ├── chapter_0.txt           # 章节内容文件
        ├── chapter_1.txt
        ├── ...
        └── images/
            ├── xxx.jpg            # 正文图片
            └── yyy.png
```

**文件夹名称计算**：

```kotlin
book.getFolderName() = MD5(bookUrl) + "_" + 书籍类型
book.getFolderNameNoCache() = MD5(bookUrl)
```

### Epub 缓存文件夹

```
appCtx.externalFiles/
└── epub/
    └── {book.originName}          # Epub 文件名
```

---

## 核心数据结构

```kotlin
object BookHelp {
    // 应用外部存储目录
    private val downloadDir: File = appCtx.externalFiles

    // 文件夹名称常量
    private const val cacheFolderName = "book_cache"
    private const val cacheImageFolderName = "images"
    private const val cacheEpubFolderName = "epub"

    // 图片下载锁（防止同一图片并发下载）
    private val downloadImages = ConcurrentHashMap<String, Mutex>()

    // 缓存根路径
    val cachePath = FileUtils.getPath(downloadDir, cacheFolderName)
}
```

---

## 缓存管理

### 1. 清理所有缓存

```kotlin
fun clearCache() {
    FileUtils.delete(FileUtils.getPath(downloadDir, cacheFolderName))
}
```

### 2. 清理指定书籍缓存

```kotlin
fun clearCache(book: Book) {
    val filePath = FileUtils.getPath(downloadDir, cacheFolderName, book.getFolderName())
    FileUtils.delete(filePath)
}
```

### 3. 更新缓存文件夹名称

当书籍信息变化时（如书名改变），需要迁移缓存文件夹：

```kotlin
fun updateCacheFolder(oldBook: Book, newBook: Book) {
    val oldFolderName = oldBook.getFolderNameNoCache()
    val newFolderName = newBook.getFolderNameNoCache()

    if (oldFolderName == newFolderName) return  // 如果哈希值相同，无需移动

    val oldFolderPath = FileUtils.getPath(downloadDir, cacheFolderName, oldFolderName)
    val newFolderPath = FileUtils.getPath(downloadDir, cacheFolderName, newFolderName)

    FileUtils.move(oldFolderPath, newFolderPath)
}
```

### 4. 清理无效缓存

```kotlin
suspend fun clearInvalidCache() {
    withContext(IO) {
        val bookFolderNames = hashSetOf<String>()
        val originNames = hashSetOf<String>()

        // 收集所有有效书籍的文件夹名
        appDb.bookDao.all.forEach {
            clearComicCache(it)  // 清理漫画过期缓存
            bookFolderNames.add(it.getFolderName())
            if (it.isEpub) originNames.add(it.originName)
        }

        // 删除无人认领的缓存文件夹
        downloadDir.getFile(cacheFolderName).listFiles()?.forEach { bookFile ->
            if (!bookFolderNames.contains(bookFile.name)) {
                FileUtils.delete(bookFile.absolutePath)
            }
        }

        // 删除无人认领的 Epub 缓存
        downloadDir.getFile(cacheEpubFolderName).listFiles()?.forEach { epubFile ->
            if (!originNames.contains(epubFile.name)) {
                FileUtils.delete(epubFile.absolutePath)
            }
        }

        // 清理临时文件
        FileUtils.delete(ArchiveUtils.TEMP_PATH)
    }
}
```

---

## 漫画缓存清理

漫画阅读器需要定期清理已读章节的图片缓存，保留"向前 N 章 + 向后预下载章节"的图片：

```kotlin
private fun clearComicCache(book: Book) {
    // 只处理漫画类型
    if (!book.isImage || AppConfig.imageRetainNum == 0) {
        return
    }

    // 确定保留范围
    // startIndex = 当前章节 - 保留章节数
    // endIndex = 当前章节 + 预下载章节数
    val startIndex = book.durChapterIndex - AppConfig.imageRetainNum
    val endIndex = book.durChapterIndex + AppConfig.preDownloadNum

    val chapterList = appDb.bookChapterDao.getChapterList(
        book.bookUrl, startIndex, endIndex
    )

    // 收集需要保留的图片名称
    val imgNames = hashSetOf<String>()
    chapterList.forEach { chapter ->
        val content = getContent(book, chapter)
        if (content != null) {
            val matcher = AppPattern.imgPattern.matcher(content)
            while (matcher.find()) {
                val src = matcher.group(1) ?: continue
                val mSrc = NetworkUtils.getAbsoluteURL(it.url, src)
                imgNames.add("${MD5Utils.md5Encode16(mSrc)}.${getImageSuffix(mSrc)}")
            }
        }
    }

    // 删除不在保留列表中的图片
    downloadDir.getFile(
        cacheFolderName,
        book.getFolderName(),
        cacheImageFolderName
    ).listFiles()?.forEach { imgFile ->
        if (!imgNames.contains(imgFile.name)) {
            imgFile.delete()
        }
    }
}
```

**清理策略图解**：

```
章节索引:  0    1    2    3    4    5    6    7    8    9
          ├───┘    │              │
          │        │   ┌──────────┘
          │        │   │
          ▼        ▼   ▼
        已删除   保留  当前  预下载
        范围1    范围   章    范围

假设:
  - imageRetainNum = 2  (向前保留2章)
  - preDownloadNum = 3  (向后预下载3章)
  - 当前章节 = 5

保留范围: 3, 4, 5, 6, 7, 8
删除范围: 0, 1, 2, 9 及以后
```

---

## 章节内容管理

### 1. 保存章节内容

```kotlin
suspend fun saveContent(
    bookSource: BookSource,
    book: Book,
    bookChapter: BookChapter,
    content: String
) {
    try {
        // 保存文本内容
        saveText(book, bookChapter, content)
        // 保存图片（可选，在 downloadImages 时处理）
        postEvent(EventBus.SAVE_CONTENT, Pair(book, bookChapter))
    } catch (e: Exception) {
        AppLog.put("保存正文失败 ${book.name} ${bookChapter.title}", e)
    }
}
```

### 2. 保存文本到文件

```kotlin
fun saveText(
    book: Book,
    bookChapter: BookChapter,
    content: String
) {
    if (content.isEmpty()) return

    // 写入文件: {缓存目录}/{书籍文件夹}/{章节文件}.txt
    FileUtils.createFileIfNotExist(
        downloadDir,
        cacheFolderName,
        book.getFolderName(),
        bookChapter.getFileName(),
    ).writeText(content)

    // 如果是在线 TXT 且启用字数统计，更新数据库
    if (book.isOnLineTxt && AppConfig.tocCountWords) {
        val wordCount = StringUtils.wordCountFormat(content.length)
        bookChapter.wordCount = wordCount
        appDb.bookChapterDao.upWordCount(
            bookChapter.bookUrl,
            bookChapter.url,
            wordCount
        )
    }
}
```

### 3. 读取章节内容

```kotlin
fun getContent(book: Book, bookChapter: BookChapter): String? {
    val file = downloadDir.getFile(
        cacheFolderName,
        book.getFolderName(),
        bookChapter.getFileName()
    )

    // 优先读取本地缓存
    if (file.exists()) {
        val string = file.readText()
        if (string.isEmpty()) {
            return null
        }
        return string
    }

    // 如果是本地书籍，从原始文件读取
    if (book.isLocal) {
        val string = LocalBook.getContent(book, bookChapter)
        // Epub 缓存到本地
        if (string != null && book.isEpub) {
            saveText(book, bookChapter, string)
        }
        return string
    }

    return null
}
```

### 4. 删除章节内容

```kotlin
fun delContent(book: Book, bookChapter: BookChapter) {
    FileUtils.createFileIfNotExist(
        downloadDir,
        cacheFolderName,
        book.getFolderName(),
        bookChapter.getFileName()
    ).delete()
}
```

### 5. 检测章节是否已缓存

```kotlin
fun hasContent(book: Book, bookChapter: BookChapter): Boolean {
    return if (book.isLocalTxt ||
        // 一级目录且 URL 以标题开头（特殊处理）
        (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title))
    ) {
        true  // 本地书籍始终认为有内容
    } else {
        // 检查文件是否存在
        downloadDir.exists(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
    }
}
```

---

## 图片管理

### 1. 获取图片文件路径

```kotlin
fun getImage(book: Book, src: String): File {
    // 图片以 MD5(URL) 为文件名，避免特殊字符问题
    return downloadDir.getFile(
        cacheFolderName,
        book.getFolderName(),
        cacheImageFolderName,
        "${MD5Utils.md5Encode16(src)}.${getImageSuffix(src)}"
    )
}
```

### 2. 下载并保存图片

```kotlin
suspend fun saveImage(
    bookSource: BookSource?,
    book: Book,
    src: String,
    chapter: BookChapter? = null
) {
    // 1. 检查是否已存在
    if (isImageExist(book, src)) {
        return
    }

    // 2. 获取或创建下载锁（防止同一图片并发下载）
    val mutex = synchronized(this) {
        downloadImages.getOrPut(src) { Mutex() }
    }
    mutex.lock()

    try {
        // 3. 双重检查（获取锁后可能已被其他线程下载）
        if (isImageExist(book, src)) {
            return
        }

        // 4. 下载图片
        val analyzeUrl = AnalyzeUrl(
            src,
            source = bookSource,
            coroutineContext = currentCoroutineContext()
        )
        val bytes = analyzeUrl.getByteArrayAwait()

        // 5. 执行 JS 解密（如有）
        runScriptWithContext {
            ImageUtils.decode(src, bytes, isCover = false, bookSource, book)
        }?.let {
            // 6. 校验图片有效性
            if (!checkImage(it)) {
                AppLog.put("${book.name} ${chapter?.title} 图片 $src 下载错误 数据异常")
            }
            // 7. 写入文件
            writeImage(book, src, it)
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        val msg = "${book.name} ${chapter?.title} 图片 $src 下载失败\n${e.localizedMessage}"
        AppLog.put(msg, e)
    } finally {
        downloadImages.remove(src)
        mutex.unlock()
    }
}
```

**图片下载锁机制**：

```
线程1: saveImage("http://xxx/1.jpg", ...)
       mutex.lock()
       下载中...
       mutex.unlock()

线程2: saveImage("http://xxx/1.jpg", ...)
       mutex.lock()  // 等待...
                      线程1下载完成
                      mutex.unlock()
       if (isImageExist) return  // 直接返回，不重复下载
```

### 3. 批量保存图片

```kotlin
suspend fun saveImages(
    bookSource: BookSource,
    book: Book,
    bookChapter: BookChapter,
    content: String,
    concurrency: Int = AppConfig.threadCount
) = coroutineScope {
    // 从正文中提取图片 URL，Flow 发射
    flowImages(bookChapter, content)
        .onEachParallel(concurrency) { mSrc ->
            saveImage(bookSource, book, mSrc, bookChapter)
        }
        .collect()
}
```

### 4. 提取正文中的图片 URL

```kotlin
fun flowImages(bookChapter: BookChapter, content: String): Flow<String> {
    return flow {
        val matcher = AppPattern.imgPattern.matcher(content)
        while (matcher.find()) {
            val src = matcher.group(1) ?: continue
            // 转换为绝对 URL
            val mSrc = NetworkUtils.getAbsoluteURL(bookChapter.url, src)
            emit(mSrc)
        }
    }
}
```

### 5. 写入图片文件

```kotlin
@Synchronized
fun writeImage(book: Book, src: String, bytes: ByteArray) {
    getImage(book, src).createFileIfNotExist().writeBytes(bytes)
}

@Synchronized
fun isImageExist(book: Book, src: String): Boolean {
    return getImage(book, src).exists()
}
```

### 6. 检测图片有效性

```kotlin
private fun checkImage(bytes: ByteArray): Boolean {
    val op = BitmapFactory.Options()
    op.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, op)

    // 检查尺寸
    if (op.outWidth < 1 && op.outHeight < 1) {
        // 可能是 SVG 格式
        return SvgUtils.getSize(ByteArrayInputStream(bytes)) != null
    }
    return true
}
```

### 7. 检测图片内容是否完整

用于判断漫画章节是否需要重新下载：

```kotlin
fun hasImageContent(book: Book, bookChapter: BookChapter): Boolean {
    if (!hasContent(book, bookChapter)) {
        return false
    }

    var ret = true
    val op = BitmapFactory.Options()
    op.inJustDecodeBounds = true

    getContent(book, bookChapter)?.let { content ->
        val matcher = AppPattern.imgPattern.matcher(content)
        while (matcher.find()) {
            val src = matcher.group(1)!!
            val image = getImage(book, src)

            if (!image.exists()) {
                ret = false
                continue
            }

            // 检查图片尺寸是否有效
            BitmapFactory.decodeFile(image.absolutePath, op)
            if (op.outWidth < 1 && op.outHeight < 1) {
                if (SvgUtils.getSize(image.absolutePath) != null) {
                    continue  // SVG 格式，有效
                }
                ret = false
                image.delete()  // 删除无效图片
            }
        }
    }

    return ret
}
```

---

## Epub 文件管理

### 1. 获取 Epub ZipFile

```kotlin
@Throws(IOException::class, FileNotFoundException::class)
fun getEpubFile(book: Book): ZipFile {
    val uri = book.getLocalUri()

    if (uri.isContentScheme()) {
        // Content URI（如 MediaStore）需要复制到缓存目录
        FileUtils.createFolderIfNotExist(downloadDir, cacheEpubFolderName)
        val path = FileUtils.getPath(downloadDir, cacheEpubFolderName, book.originName)
        val file = File(path)

        val doc = DocumentFile.fromSingleUri(appCtx, uri)
            ?: throw IOException("文件不存在")

        // 如果源文件更新，重新复制
        if (!file.exists() || doc.lastModified() > book.latestChapterTime) {
            LocalBook.getBookInputStream(book).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return ZipFile(file)
    }

    // 普通文件路径
    return ZipFile(uri.path)
}
```

### 2. 获取 ParcelFileDescriptor

用于书籍阅读时的随机访问：

```kotlin
@Throws(IOException::class, FileNotFoundException::class)
fun getBookPFD(book: Book): ParcelFileDescriptor? {
    val uri = book.getLocalUri()

    return if (uri.isContentScheme()) {
        // Content URI 使用 ContentResolver
        appCtx.contentResolver.openFileDescriptor(uri, "r")
    } else {
        // 普通文件路径
        ParcelFileDescriptor.open(
            File(uri.path!!),
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }
}
```

---

## 扫描缓存文件

### 获取已缓存的文件名列表

```kotlin
fun getChapterFiles(book: Book): HashSet<String> {
    val fileNames = hashSetOf<String>()

    // 本地 TXT 不扫描
    if (book.isLocalTxt) {
        return fileNames
    }

    // 列出缓存目录下的所有文件
    FileUtils.createFolderIfNotExist(
        downloadDir,
        subDirs = arrayOf(cacheFolderName, book.getFolderName())
    ).list()?.let {
        fileNames.addAll(it)
    }

    return fileNames
}
```

这个方法被 `CacheViewModel` 调用，用于统计已缓存的章节数量。

---

## 去重标题管理

### 1. 设置是否禁用去重标题

```kotlin
fun setRemoveSameTitle(
    book: Book,
    bookChapter: BookChapter,
    removeSameTitle: Boolean
) {
    val fileName = bookChapter.getFileName("nr")  // "nr" 后缀表示不去除重复
    val contentProcessor = ContentProcessor.get(book)

    if (removeSameTitle) {
        // 启用去重：删除缓存文件，重新处理
        val path = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            fileName
        )
        contentProcessor.removeSameTitleCache.remove(fileName)
        File(path).delete()
    } else {
        // 禁用去重：创建标记文件
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            fileName
        )
        contentProcessor.removeSameTitleCache.add(fileName)
    }
}
```

### 2. 获取是否去除重复标题

```kotlin
fun removeSameTitle(book: Book, bookChapter: BookChapter): Boolean {
    val path = FileUtils.getPath(
        downloadDir,
        cacheFolderName,
        book.getFolderName(),
        bookChapter.getFileName("nr")
    )
    return !File(path).exists()
}
// 如果标记文件不存在，说明启用了去重
// 如果标记文件存在，说明禁用了去重
```

---

## 章节名称解析

### Jaccard 相似度匹配

当目录更新时，需要将旧的阅读位置映射到新目录：

```kotlin
private val jaccardSimilarity by lazy {
    JaccardSimilarity()
}

fun getDurChapter(
    oldDurChapterIndex: Int,
    oldDurChapterName: String?,
    newChapterList: List<BookChapter>,
    oldChapterListSize: Int = 0
): Int {
    // ...
    // 使用 Jaccard 相似度算法匹配章节名称
    // ...
}
```

---

## 格式化工具

### 格式化书名

```kotlin
fun formatBookName(name: String): String {
    return name
        .replace(AppPattern.nameRegex, "")  // 去除特殊字符
        .trim { it <= ' ' }                  // 去除首尾空白
}
```

### 格式化作者

```kotlin
fun formatBookAuthor(author: String): String {
    return author
        .replace(AppPattern.authorRegex, "")
        .trim { it <= ' ' }
}
```

---

## 关键设计模式

### 1. ConcurrentHashMap + Mutex 防并发

```kotlin
private val downloadImages = ConcurrentHashMap<String, Mutex>()

suspend fun saveImage(...) {
    val mutex = synchronized(this) {
        downloadImages.getOrPut(src) { Mutex() }
    }
    mutex.lock()
    try {
        // 下载逻辑
    } finally {
        mutex.unlock()
    }
}
```

**好处**：
- 不同图片并行下载
- 同一图片串行下载（防止重复）
- 线程安全

### 2. @Synchronized 写入文件

```kotlin
@Synchronized
fun writeImage(book: Book, src: String, bytes: ByteArray) {
    getImage(book, src).createFileIfNotExist().writeBytes(bytes)
}
```

保证同一书籍的图片不会并发写入。

### 3. Lazy 初始化正则表达式

```kotlin
private val chapterNamePattern1 by lazy {
    Pattern.compile(".*?第([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话]")
}
```

避免频繁创建 Pattern 对象。

---

## 缓存目录计算

```
书籍缓存目录 = externalFiles/book_cache/{MD5(bookUrl)}_{书籍类型}/
章节文件 = {MD5(章节URL)}.txt
图片目录 = images/
图片文件 = {MD5(图片URL)}.{后缀}
```

使用 MD5 哈希值作为文件名，避免特殊字符导致的文件系统问题。

---

## 与 CacheViewModel 的协作

```kotlin
// CacheViewModel.loadCacheFiles()
val cacheNames = BookHelp.getChapterFiles(book)  // 扫描已缓存文件
if (cacheNames.isNotEmpty()) {
    appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
        // 匹配已缓存的章节
        if (cacheNames.contains(chapter.getFileName()) || chapter.isVolume) {
            chapterCaches.add(chapter.url)
        }
    }
}
cacheChapters[book.bookUrl] = chapterCaches
```

---

## 常见问题

**Q1: 缓存占用空间大？**
A: 使用 `clearInvalidCache()` 清理无人认领的缓存，使用漫画的 `clearComicCache()` 清理已读图片。

**Q2: 图片下载失败？**
A: 检查网络是否可达，图片 URL 是否有效，JS 解密是否正确。

**Q3: 章节内容读取失败？**
A: 可能是文件被删除或损坏，检查 `hasContent()` 返回值。

**Q4: Epub 文件无法打开？**
A: 确保 `getEpubFile()` 返回的 ZipFile 完整，检查文件是否被占用。
