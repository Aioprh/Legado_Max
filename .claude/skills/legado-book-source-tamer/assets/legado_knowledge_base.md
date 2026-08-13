# Legado书源知识库 - 完整版

基于阅读官方源码（约40万行）深度整合的知识库，包含核心数据结构、规则定义、API细节和实战案例。

---

## 一、核心数据结构（基于官方源码）

### 1. BookSource（书源主类）

```kotlin
data class BookSource(
    // 核心标识
    var bookSourceUrl: String = "",        // 书源地址（主键）
    var bookSourceName: String = "",       // 书源名称
    var bookSourceGroup: String? = null,   // 书源分组
    
    // 书源类型
    var bookSourceType: Int = 0,           // 0:文本, 1:音频, 2:图片, 3:文件, 4:视频
    var bookUrlPattern: String? = null,    // 详情页URL正则
    
    // 状态控制
    var customOrder: Int = 0,              // 手动排序编号
    var enabled: Boolean = true,           // 是否启用
    var enabledExplore: Boolean = true,    // 是否启用发现
    
    // 全局配置
    var jsLib: String? = null,             // JS库
    var enabledCookieJar: Boolean? = true, // 自动保存Cookie
    var concurrentRate: String? = null,    // 并发率
    var header: String? = null,            // 请求头
    var loginUrl: String? = null,          // 登录地址
    var loginUi: String? = null,           // 登录UI
    var loginCheckJs: String? = null,      // 登录检测JS
    var coverDecodeJs: String? = null,     // 封面解密JS
    var bookSourceComment: String? = null, // 注释
    var variableComment: String? = null,   // 自定义变量说明
    
    // 性能指标
    var lastUpdateTime: Long = 0,          // 最后更新时间
    var respondTime: Long = 180000L,       // 响应时间
    var weight: Int = 0,                   // 智能排序权重
    
    // 发现配置
    var exploreUrl: String? = null,        // 发现URL
    var exploreScreen: String? = null,     // 发现筛选规则
    var ruleExplore: ExploreRule? = null,  // 发现规则
    
    // 规则配置
    var searchUrl: String? = null,         // 搜索URL
    var ruleSearch: SearchRule? = null,    // 搜索规则
    var ruleBookInfo: BookInfoRule? = null,// 书籍信息规则
    var ruleToc: TocRule? = null,          // 目录规则
    var ruleContent: ContentRule? = null,  // 正文规则
    var ruleReview: ReviewRule? = null,    // 段评规则
    
    // 扩展功能
    var eventListener: Boolean = false,    // 是否监听事件
    var customButton: Boolean = false      // 自定义按钮
)
```

### 2. SearchRule（搜索规则）

```kotlin
data class SearchRule(
    // URL配置
    var url: String? = null,               // 搜索URL
    
    // 列表规则
    var bookList: String? = null,          // 书籍列表选择器
    
    // 字段提取规则
    var name: String? = null,              // 书名
    var author: String? = null,            // 作者
    var kind: String? = null,              // 分类
    var wordCount: String? = null,         // 字数
    var lastChapter: String? = null,       // 最新章节
    var intro: String? = null,             // 简介
    var coverUrl: String? = null,          // 封面URL
    var bookUrl: String? = null,           // 书籍URL
    
    // 验证规则
    var checkKeyWord: String? = null       // 校验关键词
)
```

### 3. BookInfoRule（书籍信息规则）

```kotlin
data class BookInfoRule(
    // 初始化
    var init: String? = null,              // 初始化规则
    
    // 字段提取规则
    var name: String? = null,              // 书名
    var author: String? = null,            // 作者
    var intro: String? = null,             // 简介
    var kind: String? = null,              // 分类
    var lastChapter: String? = null,       // 最新章节
    var updateTime: String? = null,        // 更新时间
    var coverUrl: String? = null,          // 封面URL
    var tocUrl: String? = null,            // 目录URL
    var wordCount: String? = null,         // 字数
    
    // 高级规则
    var canReName: String? = null,         // 可重命名规则
    var downloadUrls: String? = null       // 下载URL
)
```

### 4. TocRule（目录规则）

```kotlin
data class TocRule(
    // URL配置
    var url: String? = null,               // 目录URL
    
    // 列表规则
    var chapterList: String? = null,       // 章节列表选择器
    
    // 字段提取规则
    var chapterName: String? = null,       // 章节名称
    var chapterUrl: String? = null,        // 章节URL
    
    // 卷标识
    var volumeList: String? = null,        // 卷列表
    var volumeName: String? = null,        // 卷名称
    
    // 下一条规则
    var nextTocUrl: String? = null,        // 下一页目录URL
    
    // 逆向规则
    var reverseTocUrl: String? = null      // 逆向目录URL
)
```

### 5. ContentRule（正文规则）

```kotlin
data class ContentRule(
    // URL配置
    var url: String? = null,               // 正文URL
    
    // 内容提取
    var content: String? = null,           // 正文内容
    
    // 替换规则
    var replaceRegex: String? = null,      // 正则替换规则
    
    // 下一条规则
    var nextContentUrl: String? = null,    // 下一页正文URL
    
    // Web视图
    var webJs: String? = null,             // Web视图JS
    var sourceRegex: String? = null        // 源正则
)
```

### 6. ExploreRule（发现规则）

```kotlin
data class ExploreRule(
    // 列表规则
    var bookList: String? = null,          // 书籍列表选择器
    
    // 字段提取规则
    var name: String? = null,              // 书名
    var author: String? = null,            // 作者
    var intro: String? = null,             // 简介
    var kind: String? = null,              // 分类
    var lastChapter: String? = null,       // 最新章节
    var updateTime: String? = null,        // 更新时间
    var bookUrl: String? = null,           // 书籍URL
    var coverUrl: String? = null,          // 封面URL
    var wordCount: String? = null          // 字数
)
```

### 7. Book（书籍实体）

```kotlin
data class Book(
    // 核心标识
    var bookUrl: String = "",              // 详情页URL（主键）
    var tocUrl: String = "",               // 目录页URL
    var origin: String = "",               // 书源URL
    var originName: String = "",           // 书源名称
    
    // 书籍信息
    var name: String = "",                 // 书名
    var author: String = "",               // 作者
    var kind: String? = null,              // 分类
    var intro: String? = null,             // 简介
    var coverUrl: String? = null,          // 封面URL
    
    // 阅读进度
    var durChapterTitle: String? = null,   // 当前章节
    var durChapterIndex: Int = 0,          // 当前章节索引
    var durChapterPos: Int = 0,            // 当前阅读位置
    var durVolumeIndex: Int = 0,           // 当前卷索引
    
    // 更新信息
    var latestChapterTitle: String? = null,// 最新章节
    var latestChapterTime: Long = 0,       // 最新章节时间
    var lastCheckTime: Long = 0,           // 最后检查时间
    var totalChapterNum: Int = 0,          // 总章节数
    
    // 统计信息
    var wordCount: String? = null,         // 字数
    var canUpdate: Boolean = true,         // 是否可更新
    
    // 分组与排序
    var group: Long = 0,                   // 分组索引
    var order: Int = 0,                    // 手动排序
    
    // 扩展信息
    var customTag: String? = null,         // 自定义标签
    var customCoverUrl: String? = null,    // 自定义封面
    var customIntro: String? = null,       // 自定义简介
    var charset: String? = null,           // 字符集
    var type: Int = 0                      // 类型
)
```

### 8. BookChapter（章节实体）

```kotlin
data class BookChapter(
    var url: String = "",                  // 章节地址
    var title: String = "",                // 章节标题
    var isVolume: Boolean = false,         // 是否是卷名
    var baseUrl: String = "",              // 基础URL（用于拼接相对URL）
    var bookUrl: String = "",              // 书籍地址
    var index: Int = 0,                    // 章节序号
    var isVip: Boolean = false,            // 是否VIP
    var isPay: Boolean = false,            // 是否已购买
    var resourceUrl: String? = null,       // 音频真实URL
    var tag: String? = null,               // 附加信息（更新时间等）
    var wordCount: String? = null,         // 字数
    var imgUrl: String? = null             // 标题插图/视频封面
)
```

---

## 二、CSS选择器规则（核心）

### 1. 基本语法格式

```
CSS选择器@提取类型##正则表达式##替换内容
```

- **CSS选择器**：用于定位HTML元素
- **提取类型**：指定从元素中提取什么内容
- **正则表达式**：用于匹配和替换内容（可选）

### 2. 提取类型详解

#### @text - 提取所有文本
```css
div@text
```
- 提取元素及其所有子元素的文本内容
- 结果："Hello World"

#### @ownText - 只提取当前元素的文本
```css
div@ownText
```
- 只提取当前元素的文本，排除子元素
- 结果："Hello"

#### @html - 提取完整HTML结构
```css
div@html
```
- 提取元素及其子元素的完整HTML
- 结果："Hello <span>World</span>"

#### @textNode - 提取文本节点
```css
div@textNode
```
- 提取所有文本节点，分段返回
- 结果：["Hello", "World"]

#### @属性名 - 提取属性值
```css
a@href
img@src
input@value
```
- 提取指定属性的值
- 常用属性：href、src、class、id、value等

### 3. 基础选择器

#### 元素选择器
```css
div@text
p@text
h1@text
```
- 选择所有指定标签的元素

#### 类选择器
```css
.book-name@text
.author@text
.intro@text
```
- 选择包含指定class的元素

#### ID选择器
```css
#content@text
#main@text
```
- 选择指定id的元素

#### 属性选择器
```css
[href]@href
[class*=book]@text
[data-id="123"]@text
```
- 选择具有特定属性的元素

### 4. 组合选择器

#### 后代选择器
```css
.container .item@text
.book-list .book-name@text
```
- 选择指定元素内的所有后代元素

#### 子元素选择器
```css
ul > li@text
.book-info > h1@text
```
- 选择指定元素的直接子元素

#### 相邻兄弟选择器
```css
h1 + p@text
.title + .author@text
```
- 选择紧跟在指定元素后的兄弟元素

#### 通用兄弟选择器
```css
.chapter ~ .notes@text
.name ~ .author@text
```
- 选择指定元素后的所有兄弟元素

### 5. 伪类选择器

#### 位置伪类
```css
li:first-child@text
li:last-child@text
p:nth-child(2)@text
li:nth-of-type(3)@text
```
- 根据位置选择元素

#### 状态伪类
```css
a:hover@text
input:checked@value
```
- 根据状态选择元素

#### 内容伪类
```css
p:empty@text
div:has(p)@html
```
- 根据内容选择元素

### 6. 正则表达式应用

#### 基本替换
```css
.content@text##广告词##
```
- 删除匹配的文本

#### 替换为指定内容
```css
.content@text##旧文本##新文本##
```
- 将匹配的文本替换为新内容

#### 提取匹配内容
```css
.title@text##第(\\d+)章##
```
- 只提取匹配的内容

### 7. 实战案例

#### 书籍列表页
```json
{
  "ruleSearch": {
    "bookList": ".book-item",
    "name": "h2 a@text",
    "author": "span:last-child@text",
    "kind": "span:first-child@text",
    "intro": "p@text",
    "bookUrl": "h2 a@href",
    "coverUrl": "img@src"
  }
}
```

#### 正文内容页
```json
{
  "ruleContent": {
    "content": ".txt p@text##免费小说就在百度百科.*##"
  }
}
```

#### 书籍详情页
```json
{
  "ruleBookInfo": {
    "name": "h1@text",
    "author": ".author-info@text",
    "intro": ".intro@html",
    "coverUrl": ".cover-img@src",
    "kind": ".category@text"
  }
}
```

---

## 二、JSOUP Default语法

### 1. 基本格式

```
类型.名称.位置@提取类型
```

### 2. 类型说明

#### class - 按class选择
```css
class.odd.0@text
class.book-list.1@text
```

#### id - 按id选择
```css
id.content@text
id.main@html
```

#### tag - 按标签选择
```css
tag.div.0@text
tag.a.-1@href
```

#### text - 按文本内容选择
```css
text.搜索.0@text
```

#### children - 获取所有子标签
```css
children@html
```

### 3. 位置说明

#### 正数位置
```css
class.item.0@text     # 第一个
class.item.1@text     # 第二个
class.item.2@text     # 第三个
```
- 从0开始计数

#### 负数位置
```css
class.item.-1@text    # 倒数第一个
class.item.-2@text    # 倒数第二个
class.item.-3@text    # 倒数第三个
```
- 从-1开始计数

#### 排除位置
```css
class.item.0!1@text   # 排除第1个
class.item.0!1!2@text # 排除第1和第2个
```

#### 区间位置
```css
class.item.[0:5]@text     # 第0到第5个
class.item.[1:3]@text     # 第1到第3个
class.item.[0:-1]@text    # 第0到倒数第1个
```

#### 步长位置
```css
class.item.[0:10:2]@text  # 第0到第10个，每2个取一个
class.item.[0:10:3]@text  # 第0到第10个，每3个取一个
```

### 4. 实战案例

#### 提取第一个元素
```css
class.odd.0@text
tag.h2.0@text
```

#### 提取最后一个元素
```css
class.odd.-1@text
tag.div.-1@html
```

#### 提取指定范围
```css
class.book-list.[0:10]@text
class.chapter-list.[0:20]@html
```

#### 组合使用
```css
class.content@ownText
id.main.children@html
tag.div.0!1@text
```

---

## 三、XPath规则

### 1. 基本语法

#### 绝对路径
```xpath
/html/body/div[1]/p[2]
```

#### 相对路径
```xpath
//div[@class='content']/p
//h2[@id='title']/text()
```

### 2. 轴选择器

#### ancestor - 祖先节点
```xpath
//p/ancestor::div
```

#### descendant - 后代节点
```xpath
//div/descendant::p
```

#### following - 后面的节点
```xpath
//h1/following-sibling::p
```

#### preceding - 前面的节点
```xpath
//p/preceding-sibling::h1
```

### 3. 函数

#### text() - 提取文本
```xpath
//h1/text()
//p[@class='title']/text()
```

#### contains() - 包含匹配
```xpath
//div[contains(@class, 'content')]
//a[contains(text(), '阅读')]
```

#### starts-with() - 以...开头
```xpath
//div[starts-with(@class, 'book')]
//a[starts-with(@href, 'http')]
```

#### position() - 位置
```xpath
//li[position()=1]
//p[position()=last()]
//div[position()<3]
```

#### last() - 最后一个
```xpath
//li[last()]
//div[last()-1]
```

### 4. 属性选择

```xpath
//a[@href]
//img[@src]
//div[@class='content']
//input[@type='text']
```

### 5. 实战案例

#### 提取书名
```xpath
//h2[@class='book-name']/text()
```

#### 提取链接
```xpath
//a[@href]/@href
```

#### 提取包含特定文本的元素
```xpath
//div[contains(text(), '简介')]
```

#### 提取第3个章节
```xpath
//ul[@class='chapter-list']/li[3]/a/text()
```

---

## 四、JSONPath规则

### 1. 基本语法

```
$.字段名.子字段名
```

### 2. 常用操作

#### 根节点
```json
$
```

#### 子节点
```json
$.data.books
$.result.list
```

#### 数组索引
```json
$.books[0]
$.books[-1]
$.books[0:5]
```

#### 通配符
```json
$.data.*
$.*.name
```

#### 递归搜索
```json
$..title
$..author
```

### 3. 实战案例

#### 提取数组中的第一个元素
```json
$.data.books[0].title
```

#### 提取数组中的所有标题
```json
$.data.books[*].title
```

#### 提取符合条件的元素
```json
$.data.books[?(@.type==1)].title
```

---

## 五、正则表达式

### 1. 基本语法

#### 字符类
```
[abc]    匹配a、b、c中的任意一个
[^abc]   匹配除a、b、c之外的字符
[a-z]    匹配小写字母
[0-9]    匹配数字
```

#### 量词
```
*        匹配0次或多次
+        匹配1次或多次
?        匹配0次或1次
{n}      匹配n次
{n,}     匹配n次或更多
{n,m}    匹配n到m次
```

#### 特殊字符
```
.        匹配任意字符（除换行）
\d       匹配数字
\w       匹配字母、数字、下划线
\s       匹配空白字符
^        匹配行首
$        匹配行尾
```

### 2. 常用模式

#### 匹配章节标题
```
第[一二三四五六七八九十百千万0-9]+章
```

#### 匹配日期
```
\d{4}-\d{2}-\d{2}
```

#### 匹配URL
```
https?://[^\s]+
```

#### 匹配手机号
```
1[3-9]\d{9}
```

### 3. 在规则中的应用

```json
{
  "ruleContent": {
    "content": ".content@text##免费小说就上.*##"
  },
  "ruleSearch": {
    "name": ".title@text##《(.*)》##$1"
  }
}
```

---

## 六、JavaScript规则

### 1. 基本概念

#### JavaScript引擎
阅读使用 **Rhino v1.8.0** 作为JavaScript引擎，支持调用Java类和方法。

#### 变量声明注意事项
**重要**：在循环中使用变量时，请使用 `var` 声明，不要使用 `const`，因为 Rhino 的 `const` 不支持块级作用域，会导致值不变的问题。

#### Java类调用
想要调用 `java.*` 下的包，请使用 `Packages.java.*`。

#### 安全限制
为了安全，阅读会屏蔽部分Java类调用。

### 2. 基本语法

在规则前加上 `@js:` 前缀

```json
{
  "name": "@js:yourJavaScriptCode"
}
```

### 3. 核心变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| java | 当前类 | 主要功能入口对象 |
| baseUrl | String | 当前URL |
| result | Any | 上一步的结果 |
| book | Book类 | 书籍信息对象 |
| chapter | Chapter类 | 章节信息对象 |
| source | BaseSource类 | 书源配置对象 |
| cookie | CookieStore类 | Cookie操作对象 |
| cache | CacheManager类 | 缓存操作对象 |
| title | String | 章节当前标题 |
| src | String | 请求返回的源码 |
| nextChapterUrl | String | 下一章节url |
| isFromBookInfo | Boolean | 是否为详情页刷新 |

### 4. java对象的方法

#### 4.1 网络请求

##### ajax - 访问网络
```js
java.ajax(urlStr, callTimeout: Int? = null): String

// 示例
java.ajax('https://example.com')
java.ajax('https://example.com', 10000)
```

##### ajaxAll - 并发访问多个网络地址
```js
java.ajaxAll(urlList: Array<String>, skipRateLimit: Boolean = false): Array<StrResponse>

// 示例
java.ajaxAll(['url1', 'url2', 'url3'])
java.ajaxAll(['url1', 'url2'], true)  // 不受并发率限制
```

##### ajaxTestAll - 测试多个网络地址
```js
java.ajaxTestAll(urlList: Array<String>, timeout: Int, skipRateLimit: Boolean = false): Array<StrResponse>

// 错误码值：-1超过设定时间，-2超时，-3域名错误，-4连接被拒绝，-5连接被重置，-6SSL证书错误，-7其它错误
```

##### connect - 访问网络，返回StrResponse对象
```js
java.connect(urlStr, header = null, callTimeout: Int? = null): StrResponse

// 返回的StrResponse对象具有的方法：body() code() message() headers() raw() toString() callTime()

// 示例
var response = java.connect('https://example.com')
var body = response.body()
var code = response.code()
```

##### get、head、post
```js
java.get(url: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response
java.head(url: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response
java.post(url: String, body: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response

// 示例
java.get('https://example.com', {'User-Agent': 'Mozilla'}, 10000)
java.post('https://example.com', 'key=value', {'Content-Type': 'application/x-www-form-urlencoded'})
```

##### webView - 使用webView访问网络
```js
java.webView(html: String?, url: String?, js: String?, cacheFirst: Boolean = false): String?

// 参数说明：
// html: 直接用webView载入的html，如果为空则直接访问url
// url: html内如果有相对路径的资源不传入url访问不了
// js: 用来取返回值的js语句，没有就返回整个源代码
// cacheFirst: 优先使用缓存，为true能提高访问速度

// 示例
java.webView(null, 'https://example.com', 'document.body.innerText')
```

##### webViewGetOverrideUrl - 使用webView获取跳转url
```js
java.webViewGetOverrideUrl(html: String?, url: String?, js: String?, overrideUrlRegex: String, cacheFirst: Boolean = false, delayTime: Long = 0): String?
```

##### webViewGetSource - 使用webView获取资源url
```js
java.webViewGetSource(html: String?, url: String?, js: String?, sourceRegex: String, cacheFirst: Boolean = false, delayTime: Long = 0): String?
```

##### startBrowser - 使用内置浏览器打开链接
```js
java.startBrowser(url: String, title: String, html: String? = null)

// 示例
java.startBrowser('https://example.com', '获取验证码')
```

##### startBrowserAwait - 使用内置浏览器打开链接并等待结果
```js
java.startBrowserAwait(url: String, title: String, refetchAfterSuccess: Boolean = false, html: String? = null): StrResponse

// .body()获取网页内容
```

#### 4.2 内容解析

##### getString - 获取文本
```js
java.getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String

// 示例
java.getString('.title@text', src)
java.getString('//h2/text()', src, false)
```

##### getStringList - 获取文本列表
```js
java.getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): Array<String>
```

##### setContent - 设置解析内容
```js
java.setContent(content: Any?, baseUrl: String? = null)

// 示例
java.setContent(src, baseUrl)
```

##### getElement - 获取Element
```js
java.getElement(ruleStr: String)
```

##### getElements - 获取Element列表
```js
java.getElements(ruleStr: String)
```

#### 4.3 文件操作

##### downloadFile - 下载文件
```js
java.downloadFile(url: String): String

// 返回文件路径
```

##### readTxtFile - 读取文本文件
```js
java.readTxtFile(path: String): String

// path为相对路径，只能操作阅读缓存目录内的文件
```

##### deleteFile - 删除文件
```js
java.deleteFile(path: String)
```

##### unArchiveFile - 解压压缩文件
```js
java.unArchiveFile(zipPath: String): String

// 支持zip、rar、7z
```

##### getTxtInFolder - 读取文件夹内所有文本文件
```js
java.getTxtInFolder(unzipPath: String): String
```

##### getZipStringContent - 获取压缩文件内指定文件的内容
```js
java.getZipStringContent(url: String, path: String): String
java.getZipStringContent(url: String, path: String, charsetName: String): String

// 同样支持getRarStringContent、get7zStringContent
```

#### 4.4 导入脚本

##### importScript - 导入JavaScript脚本
```js
java.importScript(url)

// 示例
java.importScript('https://example.com/script.js')
java.importScript('file.js')  // 相对路径
java.importScript('/absolute/path/file.js')  // 绝对路径
```

##### cacheFile - 缓存网络文件
```js
java.cacheFile(url): String
java.cacheFile(url, saveTime): String  // saveTime单位：秒

// 示例
var jsCode = java.cacheFile('https://example.com/script.js', 3600)
eval(String(jsCode))
```

#### 4.5 编码解码

##### base64编码解码
```js
java.base64Encode(str: String, flags: Int)
java.base64Decode(str: String)
java.base64Decode(str: String, charset: String)
java.base64DecodeToByteArray(str: String, flags: Int)

// 示例
java.base64Encode('hello')
java.base64Decode('aGVsbG8=')
```

##### Hex编码解码
```js
java.hexDecodeToByteArray(hex: String)
java.hexDecodeToString(hex: String)
java.hexEncodeToString(utf8: String)

// 示例
java.hexDecodeToString('68656c6c6f')
java.hexEncodeToString('hello')
```

##### ByteArray转换
```js
java.strToBytes(str: String)
java.strToBytes(str: String, charset: String)
java.bytesToStr(bytes: ByteArray)
java.bytesToStr(bytes: ByteArray, charset: String)
```

##### URI编码
```js
java.encodeURI(str: String)
java.encodeURI(str: String, enc: String)

// 示例
java.encodeURI('你好')  // 默认UTF-8
java.encodeURI('你好', 'GBK')
```

##### 繁简转换
```js
java.t2s(text: String): String  // 繁体转简体
java.s2t(text: String): String  // 简体转繁体

// 示例
java.t2s('繁體中文')
java.s2t('简体中文')
```

#### 4.6 时间处理

##### timeFormat - 时间格式化
```js
java.timeFormat(time: Long): String

// 示例
java.timeFormat(1640000000000)
```

##### timeFormatUTC - UTC时间格式化
```js
java.timeFormatUTC(time: Long, format: String, sh: Int): String?

// 示例
java.timeFormatUTC(1640000000000, 'yyyy-MM-dd HH:mm:ss', 8)
```

#### 4.7 其他工具

##### htmlFormat - HTML格式化
```js
java.htmlFormat(str: String): String

// 示例
java.htmlFormat('<div>test</div>')
```

##### toast - 弹窗提示
```js
java.toast(msg: Any?)
java.longToast(msg: Any?)

// 示例
java.toast('操作成功')
```

##### log - 日志输出
```js
java.log(msg)
java.logType(var)

// 示例
java.log('调试信息')
java.logType(result)
```

##### getVerificationCode - 获取用户输入的验证码
```js
java.getVerificationCode(imageUrl)
```

##### getWebViewUA - 获取SystemWebView User-Agent
```js
java.getWebViewUA(): String
```

##### randomUUID - 生成UUID
```js
java.randomUUID(): String
```

##### androidId - 获取Android ID
```js
java.androidId(): String
```

##### openUrl - 跳转外部链接/应用
```js
java.openUrl(url: String)
java.openUrl(url: String, mimeType: String)

// 示例
java.openUrl('https://example.com')
java.openUrl('video-url', 'video/*')
```

##### openVideoPlayer - 打开视频播放器
```js
java.openVideoPlayer(url: String, title: String, float: Boolean)
```

### 5. book对象

#### 5.1 属性

```js
bookUrl          // 详情页Url
tocUrl           // 目录页Url
origin           // 书源URL
originName       // 书源名称
name             // 书籍名称
author           // 作者名称
kind             // 分类信息
customTag        // 分类信息(用户修改)
coverUrl         // 封面Url
customCoverUrl   // 封面Url(用户修改)
intro            // 简介内容
customIntro      // 简介内容(用户修改)
type             // 0:text 1:audio
group            // 自定义分组索引号
latestChapterTitle // 最新章节标题
latestChapterTime  // 最新章节标题更新时间
totalChapterNum   // 书籍目录总数
durChapterTitle   // 当前章节名称
durChapterIndex   // 当前章节索引
durChapterPos     // 当前阅读进度
durChapterTime    // 最近一次阅读时间
```

#### 5.2 方法

##### 变量存取
```js
book.putVariable(key: String, variable: String?)
book.getVariable(key: String): String?
```

### 6. chapter对象

#### 6.1 属性

```js
url          // 章节地址
title        // 章节标题
baseUrl      // 用来拼接相对url
bookUrl      // 书籍地址
index        // 章节序号
resourceUrl  // 音频真实URL
tag          // 标签
start        // 章节起始位置
end          // 章节终止位置
variable     // 变量
```

#### 6.2 方法

##### 变量存取
```js
chapter.putVariable(key: String, variable: String?)
chapter.getVariable(key: String): String?
chapter.update()  // 更新章节信息
```

##### 存储额外信息
```js
chapter.putLyric(value: String?)        // 存储音频章节歌词
chapter.putImgUrl(value: String?)       // 存储章节图标链接
```

### 7. source对象

#### 7.1 方法

##### 获取书源URL
```js
source.getKey(): String
```

##### 变量存取
```js
source.putVariable(variable: String?)
source.getVariable()
source.put(key: String, variable: String?)
source.get(key: String): String?
```

##### 登录头操作
```js
source.getLoginHeader()                   // 获取登录头
source.getLoginHeaderMap().get(key)       // 获取登录头某一键值
source.putLoginHeader(header: String)     // 保存登录头
source.removeLoginHeader()                // 清除登录头
```

##### 登录信息操作
```js
source.getLoginInfo()                     // 获取登录信息
source.getLoginInfoMap().get(key)         // 获取登录信息键值
source.removeLoginInfo()                  // 清除登录信息
source.putLoginInfo()                     // 存放登录信息
```

##### 书源缓存刷新
```js
source.refreshExplore()  // 刷新发现
source.refreshJSLib()    // 刷新jslib
```

### 8. cookie对象

```js
cookie.getCookie(url)                    // 获取全部cookie
cookie.getKey(url, key)                  // 获取cookie某一键值
cookie.setCookie(url, cookie)            // 设置cookie
cookie.replaceCookie(url, cookie)        // 替换cookie
cookie.removeCookie(url)                 // 删除cookie
cookie.setWebCookie(url, cookie)         // 设置内置浏览器cookie
```

### 9. cache对象

```js
// 保存到数据库和缓存文件
cache.put(key: String, value: String, saveTime: Int)

// 读取数据库
cache.get(key: String): String?
cache.get(key: String, onlyDisk: Boolean): String?  // onlyDisk为true时只从磁盘读取

// 删除
cache.delete(key: String)

// 缓存文件内容
cache.putFile(key: String, value: String, saveTime: Int)
cache.getFile(key: String): String?

// 内存操作
cache.putMemory(key: String, value: Any)
cache.getFromMemory(key: String): Any?
cache.deleteMemory(key: String)
```

### 10. 加密解密

#### 10.1 对称加密

```js
// 创建Cipher
java.createSymmetricCrypto(transformation, key, iv)

// 示例
var cipher = java.createSymmetricCrypto('AES/CBC/PKCS5Padding', 'myKey', 'myIv')

// 解密
cipher.decrypt(data)           // 解密为ByteArray
cipher.decryptStr(data)        // 解密为String

// 加密
cipher.encrypt(data)           // 加密为ByteArray
cipher.encryptBase64(data)     // 加密为Base64字符串
cipher.encryptHex(data)        // 加密为Hex字符串
```

#### 10.2 非对称加密

```js
// 创建Cipher
java.createAsymmetricCrypto(transformation)

// 设置密钥
.setPublicKey(key)
.setPrivateKey(key)

// 示例
var cipher = java.createAsymmetricCrypto('RSA')
  .setPublicKey(publicKey)
  .setPrivateKey(privateKey)

// 解密
cipher.decrypt(data, usePublicKey: Boolean? = true)
cipher.decryptStr(data, usePublicKey: Boolean? = true)

// 加密
cipher.encrypt(data, usePublicKey: Boolean? = true)
cipher.encryptBase64(data, usePublicKey: Boolean? = true)
cipher.encryptHex(data, usePublicKey: Boolean? = true)
```

#### 10.3 签名

```js
// 创建Sign
java.createSign(algorithm)

// 设置密钥
.setPublicKey(key)
.setPrivateKey(key)

// 签名
sign.sign(data)           // 签名输出 ByteArray
sign.signHex(data)        // 签名输出 HexString

// 示例
var sign = java.createSign('SHA256withRSA')
  .setPublicKey(publicKey)
  .setPrivateKey(privateKey)
var signature = sign.signHex(data)
```

#### 10.4 摘要

```js
java.digestHex(data: String, algorithm: String): String?
java.digestBase64Str(data: String, algorithm: String): String?

// 示例
java.digestHex('hello', 'MD5')
java.digestBase64Str('hello', 'SHA-256')
```

#### 10.5 MD5

```js
java.md5Encode(str: String): String
java.md5Encode16(str: String): String  // 取中间16位

// 示例
java.md5Encode('hello')
java.md5Encode16('hello')
```

#### 10.6 HMac

```js
java.HMacHex(data: String, algorithm: String, key: String): String
java.HMacBase64(data: String, algorithm: String, key: String): String

// 示例
java.HMacHex('hello', 'HmacSHA256', 'secret')
java.HMacBase64('hello', 'HmacSHA256', 'secret')
```

### 11. 实战案例

#### 11.1 使用ajax获取内容
```json
{
  "ruleContent": {
    "content": "@js:java.ajax(baseUrl + '/chapter/' + chapter.index)"
  }
}
```

#### 11.2 使用connect获取响应对象
```json
{
  "ruleSearch": {
    "name": "@js:var resp = java.connect(result); resp.body().match(/<h2>(.*?)<\\/h2>/)[1]"
  }
}
```

#### 11.3 使用base64解码
```json
{
  "ruleContent": {
    "content": "@js:java.base64Decode(result)"
  }
}
```

#### 11.4 使用正则处理内容
```json
{
  "ruleSearch": {
    "name": "@js:result.match(/<h2>(.*?)<\\/h2>/)[1]"
  }
}
```

#### 11.5 加密请求参数
```json
{
  "ruleSearch": {
    "name": "@js:var cipher = java.createSymmetricCrypto('AES/CBC/PKCS5Padding', 'key', 'iv'); java.ajax('https://example.com?data=' + cipher.encryptBase64(key))"
  }
}
```

#### 11.6 繁简转换
```json
{
  "ruleContent": {
    "content": "@js:java.t2s(result)"
  }
}
```

#### 11.7 使用book对象
```json
{
  "ruleContent": {
    "content": "@js:result + '\\n\\n本书共' + book.totalChapterNum + '章'"
  }
}
```

#### 11.8 使用chapter对象
```json
{
  "ruleContent": {
    "content": "@js:result + '\\n\\n章节序号：' + chapter.index"
  }
}
```

---

## 七、请求头配置

### 1. 基本格式

```json
{
  "header": "{\"User-Agent\":\"Mozilla/5.0\",\"Referer\":\"https://example.com\"}"
}
```

### 2. 常用请求头

#### User-Agent
```json
"User-Agent": "Mozilla/5.0 (Linux; Android 10; MI 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
```

#### Referer
```json
"Referer": "https://example.com"
```

#### Cookie
```json
"Cookie": "session=abc123; user=456"
```

#### Accept
```json
"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
```

### 3. 在规则中的应用

```json
{
  "header": "{\n\"User-Agent\":\"Mozilla/5.0\",\n\"Referer\":\"https://example.com\",\n\"Cookie\":\"session=abc123\"\n}",
  "ruleSearch": {
    "bookList": ".book-item",
    "name": "h2@text"
  }
}
```

---

## 八、动态加载处理

### 1. 为什么需要动态加载

当网站的内容是使用JavaScript动态生成的，直接获取HTML源码时内容为空，需要让浏览器先渲染页面。

### 2. 四种方法

#### 方法一：直接添加webView参数

**GET请求**
```json
{
  "searchUrl": "https://example.com/search?q={{key}},{\"webView\":true}"
}
```

**POST请求**
```json
{
  "searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"key={{key}}\",\"webView\":true}"
}
```

#### 方法二：正则替换

```json
{
  "ruleSearch": {
    "bookUrl": "a@href##$##,{\"webView\":true}"
  }
}
```

#### 方法三：JS拼接

```json
{
  "ruleSearch": {
    "bookUrl": "@js:result + ',{\"webView\":true}'"
  }
}
```

#### 方法四：利用{{}}拼接

```json
{
  "ruleSearch": {
    "bookUrl": "{{@@tag.a@href}},{\"webView\":true}"
  },
  "ruleToc": {
    "chapterUrl": "{{@css:a@href}},{\"webView\":true}"
  }
}
```

### 3. 注意事项

- `webView`参数会使请求速度变慢
- 只在必要时使用
- 章节链接和详情链接都可以使用

---

## 九、登录检查JS

### 1. 适用场景

只在`登录检查JS`规则中有效

### 2. 可用方法

```js
// 重新解析url，可以用于登录检测js登录后重新解析url重新访问
initUrl()

// 重新设置登录头
getHeaderMap().putAll(source.getHeaderMap(true))

// 返回访问结果，文本类型，书源内部重新登录后可调用此方法重新返回结果
getStrResponse(jsStr: String? = null, sourceRegex: String? = null)

// 返回访问结果，网络朗读引擎采用的是这个，调用登录后在调用这方法可以重新访问
getResponse(): Response
```

### 3. 实战案例

#### 检查登录状态并重新访问
```js
{
  "loginCheckJs": "@js:var response = getResponse(); if(response.body().contains('请登录')) { initUrl(); getStrResponse(); } else { return result; }"
}
```

---

## 十、订阅源规则

### 1. 订阅源类型

#### 网页类型
```json
{
  "articleStyle": 0
}
```
- 用内置浏览器加载正文内容

#### 图片类型
```json
{
  "articleStyle": 1
}
```
- 点击文章显示图片

#### 视频类型
```json
{
  "articleStyle": 2
}
```
- 点击文章使用内置视频播放器

### 2. 核心字段

#### sourceUrl - 源URL（必填）
```json
{
  "sourceUrl": "https://example.com/rss.xml"
}
```

#### sourceName - 源名称（必填）
```json
{
  "sourceName": "示例订阅源"
}
```

#### sourceIcon - 源图标（可选）
```json
{
  "sourceIcon": "https://example.com/icon.png"
}
```

#### sourceGroup - 源分组（可选）
```json
{
  "sourceGroup": "新闻"
}
```

### 3. 规则字段

#### ruleArticles - 列表规则
```json
{
  "ruleArticles": ".article-item"
}
```

#### ruleTitle - 标题规则
```json
{
  "ruleTitle": "h2@text"
}
```

#### ruleLink - 链接规则（必填）
```json
{
  "ruleLink": "a@href"
}
```

#### rulePubDate - 时间规则
```json
{
  "rulePubDate": ".time@text"
}
```

#### ruleDescription - 描述规则
```json
{
  "ruleDescription": ".desc@text"
}
```

#### ruleContent - 内容规则
```json
{
  "ruleContent": ".content@html"
}
```

#### ruleImage - 图片url规则
```json
{
  "ruleImage": "img@src"
}
```

### 4. 订阅源示例

```json
{
  "sourceUrl": "https://example.com",
  "sourceName": "示例订阅源",
  "sourceIcon": "https://example.com/icon.png",
  "sourceGroup": "新闻",
  "enabled": true,
  "articleStyle": 0,
  "ruleArticles": ".article-item",
  "ruleTitle": "h2@text",
  "ruleLink": "a@href",
  "rulePubDate": ".time@text",
  "ruleDescription": ".desc@text",
  "ruleImage": "img@src"
}
```

---

## 十一、常见问题与解决方案

### 1. CloudFlare验证问题

**问题**：网站弹出"请确认你不是机器人"验证页面

**解决方案**：
```js
@js:var cfPatterns = [/Just a moment/, /Checking your browser/, /DDoS protection/]; if(cfPatterns.some(p=>p.test(result))) { java.startBrowser(result.url(), 'CloudFlare验证'); return java.connect(result.url()).body(); } else { return result; }
```

### 2. 内容显示"加载中"

**问题**：网站内容使用JavaScript动态生成

**解决方案**：使用webView加载
```json
{
  "ruleContent": {
    "content": "#content@html"
  },
  "ruleToc": {
    "chapterUrl": "a@href##$##,{\"webView\":true}"
  }
}
```

### 3. 目录顺序是乱的

**问题**：章节顺序是倒序的

**解决方案**：在列表规则前加减号
```json
{
  "ruleToc": {
    "chapterList": "-ul.chapter-list li"
  }
}
```

### 4. 正文里有广告

**问题**：正文包含广告内容

**解决方案1**：使用@ownText
```json
{
  "ruleContent": {
    "content": ".content@ownText"
  }
}
```

**解决方案2**：使用正则净化
```json
{
  "ruleContent": {
    "content": ".content@text##广告1|广告2|广告3##"
  }
}
```

### 5. 需要分页

**问题**：正文或目录有多页

**解决方案**：配置下一页规则
```json
{
  "ruleContent": {
    "content": ".content@text",
    "nextContentUrl": "a.next@href"
  },
  "ruleToc": {
    "nextTocUrl": "a.next-page@href"
  }
}
```

### 6. 图片防盗链

**问题**：图片显示不出来

**解决方案**：配置Referer请求头
```json
{
  "header": "{\"Referer\":\"https://example.com\"}"
}
```

### 7. POST请求

**问题**：搜索需要使用POST请求

**解决方案**：配置POST搜索
```json
{
  "searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"key={{key}}&page={{page}}\"}"
}
```

---

## 十二、最佳实践

### 1. 选择器选择优先级

1. **优先使用CSS选择器**：简单易读
2. **次选JSOUP Default**：适合特定场景
3. **必要时用XPath**：复杂结构
4. **JSON数据用JSONPath**：API接口

### 2. 提取类型选择

- **@text**：需要完整文本时使用
- **@ownText**：需要去广告时使用
- **@html**：需要保留格式和图片时使用
- **@href/@src**：提取链接和图片时使用

### 3. 性能优化

- **避免过度使用webView**：只在必要时使用
- **合理使用缓存**：对不变的资源使用缓存
- **减少网络请求**：尽量一次获取所有信息

### 4. 规则调试

1. **从简单到复杂**：先测试基本选择器
2. **逐层验证**：每一层都确认能正确提取
3. **使用阅读APP调试**：导入规则后测试
4. **查看日志**：遇到错误查看详细日志

### 5. 代码规范

- **使用注释**：复杂规则添加注释
- **统一命名**：选择器命名要有意义
- **模块化**：复杂JS逻辑拆分成函数
- **错误处理**：添加异常捕获

### 6. 安全注意事项

- **不要泄露个人信息**：不要在规则中包含敏感信息
- **注意Cookie安全**：不要公开包含Cookie的规则
- **验证输入**：对用户输入进行验证
- **避免XSS攻击**：使用@ownText而不是@text处理用户内容

---

## 十三、规则标志速查

```
@@   默认规则，直接写时可以省略@@
@XPath:   xpath规则，直接写时以//开头可省略@XPath
@Json:    json规则，直接写时以$.开头可省略@Json
:         regex规则，不可省略，只可以用在书籍列表和目录列表
```

---

## 十四、完整书源JSON模板

```json
[
  {
    "bookSourceComment": "注释说明",
    "bookSourceGroup": "分组名称",
    "bookSourceName": "书源名称",
    "bookSourceType": 0,
    "bookSourceUrl": "https://example.com",
    "bookUrlPattern": "",
    "customOrder": 0,
    "enabled": true,
    "enabledCookieJar": true,
    "enabledExplore": true,
    "header": "",
    "lastUpdateTime": 1640000000000,
    "respondTime": 180000,
    "jsLib": "",
    "ruleBookInfo": {
      "author": ".author@text",
      "coverUrl": ".cover@src",
      "intro": ".intro@text",
      "kind": ".category@text",
      "lastChapter": ".last-chapter@text",
      "name": "h1@text",
      "tocUrl": ".toc-link@href"
    },
    "ruleContent": {
      "content": ".content@text##广告##",
      "nextContentUrl": "a.next@href",
      "replaceRegex": "",
      "sourceRegex": ""
    },
    "ruleExplore": {
      "author": ".author@text",
      "bookList": ".book-item",
      "bookUrl": "a@href",
      "coverUrl": "img@src",
      "intro": ".intro@text",
      "kind": ".category@text",
      "name": "h2@text",
      "wordCount": ".word-count@text"
    },
    "ruleSearch": {
      "author": ".author@text",
      "bookList": ".book-item",
      "bookUrl": "a@href",
      "checkKeyWord": "",
      "coverUrl": "img@src",
      "intro": ".intro@text",
      "kind": ".category@text",
      "name": "h2@text",
      "wordCount": ".word-count@text"
    },
    "ruleToc": {
      "chapterList": ".chapter-list li",
      "chapterName": "a@text",
      "chapterUrl": "a@href",
      "isVolume": "",
      "nextTocUrl": "a.next@href",
      "updateTime": ".time@text",
      "volumeList": ".volume-list li",
      "volumeName": "span@text"
    },
    "searchUrl": "https://example.com/search?q={{key}}&page={{page}}",
    "exploreUrl": "发现页::https://example.com/explore\n热门::https://example.com/hot",
    "loginUrl": "",
    "loginUi": "",
    "loginCheckJs": "",
    "coverDecodeJs": "",
    "weight": 0
  }
]
```

---

## 附录：快速参考

### 常用CSS选择器
```
.element          元素选择器
.class            类选择器
#id               ID选择器
[attribute]       属性选择器
parent > child    子元素选择器
ancestor descendant 后代选择器
:first-child      第一个子元素
:last-child       最后一个子元素
:nth-child(n)     第n个子元素
```

### 常用提取类型
```
@text      提取所有文本
@ownText   只提取当前元素文本
@html      提取HTML结构
@href      提取链接
@src       提取图片地址
@textNode  提取文本节点
```

### 常用正则表达式
```
第[0-9]+章              匹配章节
\d{4}-\d{2}-\d{2}       匹配日期
[0-9]+万字              匹配字数
免费小说就上.*          匹配广告
```

### 常用JavaScript函数
```
java.ajax(url)              访问网络
java.connect(url)           访问网络（返回对象）
java.webView(html,url,js)   使用webView
java.base64Decode(str)      base64解码
java.md5Encode(str)         MD5加密
java.timeFormat(timestamp)  时间格式化
```

---

## 三、JSOUP Default语法（默认规则引擎）

### 1. 基本概念

JSOUP Default是阅读使用的默认HTML解析引擎，基于Java的Jsoup库，支持强大的CSS选择器和元素操作。

### 2. 基本语法

#### 标准格式
```
选择器@属性##正则##替换
```

#### 选择器类型
- **元素选择器**：`div`、`p`、`a`、`img`等
- **类选择器**：`.class-name`
- **ID选择器**：`#id-name`
- **属性选择器**：`[attr]`、`[attr=value]`
- **组合选择器**：`parent > child`、`ancestor descendant`

### 3. 属性提取

#### @text - 文本内容
```css
div@text
```
提取元素及其子元素的所有文本

#### @ownText - 自身文本
```css
div@ownText
```
只提取元素自身的文本，不包含子元素

#### @html - HTML结构
```css
div@html
```
提取元素的完整HTML代码

#### @属性名 - 属性值
```css
a@href
img@src
input@value
```
提取指定属性的值

### 4. 正则表达式

#### 基本替换
```css
.title@text##第(\\d+)章##
```
只提取匹配正则的内容

#### 删除匹配
```css
.content@text##广告|推广##
```
删除匹配广告或推广的内容

#### 替换内容
```css
.price@text##\\$(\\d+)##￥$1##
```
将$替换为￥

### 5. 高级用法

#### 多重替换
```css
.content@text##a##b##c##d
```
连续进行多次替换

#### 正则分组
```css
.time@text##(\\d{4})-(\\d{2})-(\\d{2})##$1年$2月$3日##
```
使用分组引用

#### 贪婪匹配
```css
.content@text##<.*?>##
```
非贪婪匹配HTML标签

### 6. 实战案例

#### 提取书名
```css
h1@text
```

#### 提取作者
```css
.author@text
```

#### 提取简介
```css
.intro@text##[\\s\\n]+##
```
去除多余的换行和空格

#### 提取封面
```css
.cover@src
```

---

## 四、XPath规则（高级选择器）

### 1. 基本概念

XPath是一种在XML文档中导航的语言，也可以用于HTML文档。在阅读中，XPath选择器以`xpath:`开头。

### 2. 基本语法

#### 元素选择
```xpath
xpath://div[@class='book']
xpath://p[@id='content']
```

#### 属性选择
```xpath
xpath://a/@href
xpath://img/@src
```

#### 文本选择
```xpath
xpath://div/text()
xpath://h1/text()
```

### 3. 路径表达式

#### 绝对路径
```xpath
xpath:/html/body/div[1]/p[2]
```
从根节点开始的路径

#### 相对路径
```xpath
xpath://div[@class='content']/p
```
从当前节点开始的路径

#### 通配符
```xpath
xpath://bookstore/*
xpath://book/*/author
```
匹配任意元素

### 4. 轴选择器

#### 子节点
```xpath
xpath:child::book
```

#### 父节点
```xpath
xpath:parent::book
```

#### 祖先节点
```xpath
xpath:ancestor::book
```

#### 后代节点
```xpath
xpath:descendant::book
```

#### 兄弟节点
```xpath
xpath:following-sibling::book
```

### 5. 谓词

#### 位置选择
```xpath
xpath://book[position()=1]
xpath://book[position()=last()]
```

#### 属性选择
```xpath
xpath://book[@category='web']
xpath://book[@price>35.00]
```

#### 条件组合
```xpath
xpath://book[@category='web' and @price>35.00]
```

### 6. 函数

#### 文本函数
```xpath
xpath://book[contains(text(),'Harry')]
xpath://book[starts-with(text(),'A')]
```

#### 字符串函数
```xpath
xpath:string(//book[1]/title)
xpath:concat(//book[1]/title, ' - ', //book[1]/author)
```

#### 数值函数
```xpath
xpath:sum(//book/price)
xpath:count(//book)
```

### 7. 实战案例

#### 提取书名
```xpath
xpath://h1[@class='title']/text()
```

#### 提取作者
```xpath
xpath://span[@class='author']/text()
```

#### 提取链接
```xpath
xpath://a[@class='chapter-link']/@href
```

#### 提取所有章节
```xpath
xpath://div[@id='chapter-list']//a
```

---

## 五、JSONPath规则（JSON数据）

### 1. 基本概念

JSONPath是用于从JSON文档中提取数据的表达式语言，类似于XPath用于XML。

### 2. 基本语法

#### 根节点
```jsonpath
$.store.book[*].author
```
从根节点开始

#### 当前节点
```jsonpath
$..author
```
递归查找所有author

#### 数组索引
```jsonpath
$.store.book[0].title
```
第一个元素

#### 数组切片
```jsonpath
$.store.book[0:2].title
```
前两个元素

### 3. 操作符

#### 子节点
```jsonpath
$.store.book
```

#### 递归查找
```jsonpath
$..book
```

#### 通配符
```jsonpath
$.store.*.price
```

#### 脚本表达式
```jsonpath
$.store.book[?(@.price < 10)].title
```

### 4. 过滤器

#### 比较操作
```jsonpath
$.book[?(@.price < 10)]
$.book[?(@.category == 'reference')]
```

#### 逻辑操作
```jsonpath
$.book[?(@.price < 10 && @.category == 'fiction')]
$.book[?(@.price < 10 || @.category == 'reference')]
```

#### 存在性检查
```jsonpath
$.book[?(@.isbn)]
```

### 5. 实战案例

#### 提取书名
```jsonpath
$.data[*].name
```

#### 提取作者
```jsonpath
$.data[*].author
```

#### 提取章节列表
```jsonpath
$.data.chapters[*].title
```

#### 提取封面URL
```jsonpath
$.data.coverUrl
```

---

## 六、正则表达式（文本处理）

### 1. 基本语法

#### 字符类
```
[abc]     # 匹配a、b或c
[^abc]    # 匹配除a、b、c之外的字符
[a-z]     # 匹配小写字母
[0-9]     # 匹配数字
```

#### 量词
```
*         # 0次或多次
+         # 1次或多次
?         # 0次或1次
{n}       # 恰好n次
{n,m}     # n到m次
```

#### 边界
```
^         # 行首
$         # 行尾
\\b       # 单词边界
```

### 2. 常用模式

#### 提取章节号
```regex
第(\\d+)章
```
提取"第123章"中的数字

#### 提取日期
```regex
(\\d{4})-(\\d{2})-(\\d{2})
```
提取"2024-01-01"格式的日期

#### 提取URL
```regex
https?://[^\\s]+
```
提取http或https开头的URL

#### 去除HTML标签
```regex
<[^>]+>
```
匹配所有HTML标签

### 3. 分组与引用

#### 分组
```regex
(\\d{4})-(\\d{2})-(\\d{2})
```
分组捕获年、月、日

#### 引用
```regex
(\\d{4})-(\\d{2})-(\\d{2}) -> $1年$2月$3日
```
使用$1、$2引用分组

#### 非捕获分组
```regex
(?:\\d{4})-(\\d{2})-(\\d{2})
```
不捕获分组

### 4. 实战案例

#### 清理广告
```regex
本章完.*继续阅读
```
删除章节末尾的广告文字

#### 提取纯文本
```regex
[\\s\\n]+
```
将多个空格和换行替换为单个空格

#### 提取数字
```regex
\\d+
```
提取所有数字

#### 格式化文本
```regex
(\\d+)(\\.)(\\d+) -> $1,$2$3
```
在整数和小数之间添加逗号

---

## 七、JavaScript规则（高级脚本）

### 1. 基本概念

阅读支持在规则中使用JavaScript进行复杂的数据处理和动态生成。所有函数调用必须使用`java.`前缀。

### 2. 核心对象

#### book对象 - 书籍信息
```javascript
java.put('book.name', '书名');
java.put('book.author', '作者');
var name = java.get('book.name');
```

#### chapter对象 - 章节信息
```javascript
java.put('chapter.title', '章节标题');
java.put('chapter.url', '章节URL');
var title = java.get('chapter.title');
```

#### source对象 - 书源信息
```javascript
java.put('source.name', '书源名称');
var name = java.get('source.name');
```

#### cache对象 - 缓存数据
```javascript
java.cachePut('key', 'value');
var value = java.cacheGet('key');
```

### 3. 核心函数

#### HTTP请求
```javascript
// GET请求
var result = java.ajax(url);
var result = java.get(url);

// POST请求
var result = java.post(url, data);

// 带头请求
var result = java.ajax({
    url: url,
    method: 'GET',
    headers: {
        'User-Agent': 'Mozilla/5.0'
    }
});
```

#### 字符串处理
```javascript
// Base64编码/解码
var encoded = java.base64Encode(text);
var decoded = java.base64Decode(text);

// MD5加密
var hash = java.md5(text);

// URL编码/解码
var encoded = java.urlEncode(text);
var decoded = java.urlDecode(text);
```

#### 时间处理
```javascript
// 获取当前时间
var now = java.time();

// 格式化时间
var formatted = java.dateFormat(now, 'yyyy-MM-dd HH:mm:ss');
```

#### JSON处理
```javascript
// 解析JSON
var obj = java.jsonParse(jsonStr);

// 生成JSON
var jsonStr = java.jsonStringify(obj);
```

### 4. 正则表达式
```javascript
// 测试匹配
var matches = java.regexMatch(text, 'pattern');

// 提取匹配
var result = java.regexExtract(text, 'pattern', groupIndex);

// 替换
var replaced = java.regexReplace(text, 'pattern', 'replacement');
```

### 5. 实战案例

#### 动态生成URL
```javascript
var keyword = java.urlEncode(java.get('key'));
var url = 'https://example.com/search?q=' + keyword;
java.put('result.url', url);
```

#### 处理加密数据
```javascript
var encrypted = java.get('data');
var decrypted = java.decrypt(encrypted, 'key');
java.put('result.text', decrypted);
```

#### 提取复杂结构
```javascript
var html = java.get('result.html');
var matches = java.regexMatch(html, 'href="([^"]+)"');
var urls = [];
for (var i = 0; i < matches.length; i++) {
    urls.push(matches[i]);
}
java.put('result.urls', java.jsonStringify(urls));
```

---

## 八、请求头配置（网络请求）

### 1. 基本配置

#### 设置请求头
```json
{
  "header": "{\"User-Agent\":\"Mozilla/5.0\",\"Referer\":\"https://example.com\"}"
}
```

#### 常用请求头
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
Referer: https://example.com
Accept: text/html,application/xhtml+xml,application/xml;q=0.9
Accept-Language: zh-CN,zh;q=0.9
Connection: keep-alive
```

### 2. 高级配置

#### Cookie配置
```json
{
  "enabledCookieJar": true
}
```
自动保存和管理Cookie

#### 并发控制
```json
{
  "concurrentRate": "1"
}
```
限制并发请求数量

#### 超时配置
```
在书源设置中配置请求超时时间
```

### 3. 实战案例

#### 反爬虫应对
```json
{
  "header": "{\"User-Agent\":\"Mozilla/5.0\",\"Referer\":\"https://example.com\",\"X-Requested-With\":\"XMLHttpRequest\"}"
}
```

#### 登录状态保持
```json
{
  "enabledCookieJar": true,
  "loginUrl": "https://example.com/login",
  "loginCheckJs": "java.ajax('https://example.com/check')"
}
```

---

## 九、常见问题与解决方案

### 1. 搜索无结果

#### 问题原因
- 选择器错误
- 网站结构变化
- 反爬虫机制

#### 解决方案
- 检查选择器是否正确
- 更新选择器规则
- 添加请求头和Cookie

### 2. 正文内容缺失

#### 问题原因
- 内容动态加载
- JavaScript渲染
- 需要登录

#### 解决方案
- 使用webJs加载动态内容
- 设置正确的Cookie
- 添加登录逻辑

### 3. 目录获取失败

#### 问题原因
- 分页规则错误
- URL拼接问题
- 反爬虫限制

#### 解决方案
- 检查nextTocUrl规则
- 验证baseUrl配置
- 增加请求间隔

### 4. 封面无法显示

#### 问题原因
- URL相对路径
- 需要Referer
-防盗链

#### 解决方案
- 使用baseUrl拼接完整URL
- 添加Referer请求头
- 处理防盗链

---

## 十、最佳实践

### 1. 选择器优化

#### 优先使用稳定的选择器
```css
/* 推荐使用ID和类选择器 */
#content@text
.book-item@text

/* 避免使用位置选择器 */
div:nth-child(3)@text
```

#### 使用后代选择器而非标签选择器
```css
/* 推荐 */
.book-list .book-name@text

/* 不推荐 */
div ul li a@text
```

### 2. 正则优化

#### 使用非贪婪匹配
```regex
/* 推荐 */
<p>(.*?)</p>

/* 不推荐 */
<p>(.*)</p>
```

#### 避免复杂的回溯
```regex
/* 推荐 */
[0-9]{4}

/* 不推荐 */
[0-9][0-9][0-9][0-9]
```

### 3. 性能优化

#### 减少不必要的请求
```javascript
// 只在需要时才发起请求
if (java.get('data') == null) {
    var data = java.ajax(url);
    java.put('data', data);
}
```

#### 使用缓存
```javascript
var cached = java.cacheGet('key');
if (cached) {
    return cached;
}
var data = java.ajax(url);
java.cachePut('key', data);
```

### 4. 代码规范

#### 添加注释
```javascript
// 获取书籍信息
var book = java.get('book');

// 处理章节列表
var chapters = java.jsonParse(java.get('result.chapters'));
```

#### 统一命名
```javascript
// 使用驼峰命名
var bookName = java.get('book.name');
var chapterUrl = java.get('chapter.url');
```

---

## 十一、调试技巧

### 1. 使用调试工具

#### 启用调试模式
```json
{
  "eventListener": true
}
```

#### 查看调试日志
- 在阅读APP中打开书源编辑
- 使用调试功能测试规则
- 查看日志输出

### 2. 分步调试

#### 测试URL
```javascript
// 先测试URL是否正确
var url = 'https://example.com/search';
java.log('URL: ' + url);
```

#### 测试请求
```javascript
// 测试请求是否成功
var result = java.ajax(url);
java.log('Result: ' + result);
```

#### 测试解析
```javascript
// 测试解析是否正确
var data = java.jsonParse(result);
java.log('Data: ' + java.jsonStringify(data));
```

### 3. 常用调试函数

```javascript
// 输出日志
java.log(message);

// 检查变量
java.log('Type: ' + typeof variable);
java.log('Value: ' + variable);

// 检查对象
java.log(java.jsonStringify(object));
```

---

## 十二、完整示例

### 1. 简单文本书源

```json
[
  {
    "bookSourceUrl": "https://example.com",
    "bookSourceName": "示例书源",
    "bookSourceType": 0,
    "enabled": true,
    "searchUrl": "https://example.com/search?q={{key}}",
    "ruleSearch": {
      "bookList": ".book-item",
      "name": "h2@text",
      "author": ".author@text",
      "bookUrl": "a@href"
    },
    "ruleBookInfo": {
      "name": "h1@text",
      "author": ".author@text",
      "intro": ".intro@text",
      "coverUrl": ".cover@src"
    },
    "ruleToc": {
      "chapterList": "#chapter-list a",
      "chapterName": "text",
      "chapterUrl": "href"
    },
    "ruleContent": {
      "content": "#content@text"
    }
  }
]
```

### 2. 复杂JSON书源

```json
[
  {
    "bookSourceUrl": "https://api.example.com",
    "bookSourceName": "API书源",
    "bookSourceType": 0,
    "enabled": true,
    "searchUrl": "https://api.example.com/search?keyword={{key}}",
    "ruleSearch": {
      "bookList": "$.data[*]",
      "name": "name",
      "author": "author",
      "bookUrl": "id",
      "coverUrl": "coverUrl"
    },
    "ruleBookInfo": {
      "name": "name",
      "author": "author",
      "intro": "description",
      "coverUrl": "coverUrl"
    },
    "ruleToc": {
      "chapterList": "$.chapters[*]",
      "chapterName": "title",
      "chapterUrl": "id"
    },
    "ruleContent": {
      "content": "content"
    }
  }
]
```

### 3. 动态加载书源

```json
[
  {
    "bookSourceUrl": "https://dynamic.example.com",
    "bookSourceName": "动态书源",
    "bookSourceType": 0,
    "enabled": true,
    "searchUrl": "https://dynamic.example.com/search",
    "header": "{\"User-Agent\":\"Mozilla/5.0\",\"Referer\":\"https://dynamic.example.com\"}",
    "enabledCookieJar": true,
    "ruleSearch": {
      "bookList": ".book-item",
      "name": "h2@text",
      "author": ".author@text",
      "bookUrl": "a@href",
      "checkKeyWord": "校验关键词"
    },
    "ruleBookInfo": {
      "init": "java.ajax('https://dynamic.example.com/init')",
      "name": "h1@text",
      "author": ".author@text",
      "intro": ".intro@text",
      "coverUrl": ".cover@src"
    },
    "ruleToc": {
      "chapterList": "#chapter-list a",
      "chapterName": "text",
      "chapterUrl": "href",
      "nextTocUrl": ".next@href"
    },
    "ruleContent": {
      "url": "",
      "webJs": "window.scrollTo(0,document.body.scrollHeight);",
      "content": "#content@text"
    }
  }
]
```

---

## 附录：快速参考

### 常用选择器速查

| 选择器 | 说明 | 示例 |
|--------|------|------|
| `#id` | ID选择器 | `#content@text` |
| `.class` | 类选择器 | `.book@text` |
| `element` | 元素选择器 | `div@text` |
| `[attr]` | 属性选择器 | `[href]@href` |
| `parent child` | 后代选择器 | `.list .item@text` |
| `parent > child` | 子元素选择器 | `.list > .item@text` |
| `:first-child` | 第一个子元素 | `li:first-child@text` |
| `:last-child` | 最后一个子元素 | `li:last-child@text` |

### 常用正则表达式速查

| 模式 | 说明 | 示例 |
|------|------|------|
| `\\d` | 数字 | `\\d+` 匹配多个数字 |
| `\\w` | 字母、数字、下划线 | `\\w+` 匹配多个单词字符 |
| `\\s` | 空白字符 | `\\s+` 匹配多个空格 |
| `.` | 任意字符 | `.*` 匹配任意字符 |
| `^` | 行首 | `^\\d+` 行首的数字 |
| `$` | 行尾 | `\\d+$` 行尾的数字 |

### 常用JavaScript函数速查

| 函数 | 说明 | 示例 |
|------|------|------|
| `java.ajax(url)` | 发送AJAX请求 | `java.ajax('https://example.com')` |
| `java.get(key)` | 获取变量 | `java.get('book.name')` |
| `java.put(key, value)` | 设置变量 | `java.put('book.name', '书名')` |
| `java.log(message)` | 输出日志 | `java.log('Debug info')` |
| `java.jsonParse(str)` | 解析JSON | `java.jsonParse('{"name":"test"}')` |
| `java.jsonStringify(obj)` | 生成JSON | `java.jsonStringify(obj)` |
| `java.base64Encode(str)` | Base64编码 | `java.base64Encode('hello')` |
| `java.base64Decode(str)` | Base64解码 | `java.base64Decode('aGVsbG8=')` |

---

**文档版本**: 2.0
**最后更新**: 2024年
**基于**: 阅读官方源码（约40万行）深度整合
