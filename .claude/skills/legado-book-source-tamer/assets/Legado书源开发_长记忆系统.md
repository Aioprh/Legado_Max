# Legado书源开发 - 长记忆系统

> **版本**: 1.0
> **更新时间**: 2025-01-06
> **知识来源**: 900+ 文件、2448+ 条知识、134个真实书源、168个选择器规则
> **重要提醒**: 以下所有内容仅作参考，所有选择器必须在真实HTML上验证！

---

## 📋 目录

1. [CSS选择器规则](#1-css选择器规则)
2. [提取类型详解](#2-提取类型详解)
3. [正则表达式格式](#3-正则表达式格式)
4. [书源JSON结构](#4-书源json结构)
5. [POST请求配置](#5-post请求配置)
6. [真实书源模式](#6-真实书源模式)
7. [134个真实书源分析结果](#7-134个真实书源分析结果)
8. [常见陷阱与错误](#8-常见陷阱与错误)
9. [错误处理策略](#9-错误处理策略)
10. [最佳实践](#10-最佳实践)

---

## 1. CSS选择器规则

### 1.1 基础格式

```
选择器@提取类型##正则表达式##替换内容
```

### 1.2 常用选择器类型

| 选择器类型 | 示例 | 说明 |
|------------|------|------|
| 标签选择器 | `div`, `p`, `a`, `img` | 选择指定标签 |
| 类选择器 | `.class-name` | 选择指定class |
| ID选择器 | `#id-name` | 选择指定id |
| 属性选择器 | `[attr=value]`, `[attr^=prefix]` | 根据属性选择 |
| 伪类选择器 | `:first-child`, `:last-child`, `:nth-child(n)` | 根据位置选择 |

### 1.3 组合选择器

```css
/* 后代选择：div下的所有p */
div p

/* 子元素选择：div的直接子p */
div > p

/* 相邻兄弟：div后的第一个p */
div + p

/* 通用兄弟：div后的所有p */
div ~ p
```

### 1.4 选择器优先级

```
#id > .class tag[attr=value]:nth-child(2)@text
```

优先级：ID选择器 > 类选择器 > 标签选择器 > 属性选择器 > 伪类选择器

### 1.5 备选选择器

```css
选择器1@提取类型||选择器2@提取类型
```

示例：
```css
img.lazy@data-original||img@src  // 优先取data-original，失败则取src
```

---

## 2. 提取类型详解

### 2.1 基础提取类型

| 提取类型 | 说明 | 示例 |
|---------|------|------|
| `@text` | 提取纯文本内容（自动去除HTML标签） | `h1@text` |
| `@html` | 提取完整HTML结构 | `div.content@html` |
| `@href` | 提取链接地址（a标签的href属性） | `a@href` |
| `@src` | 提取图片地址（img标签的src属性） | `img@src` |
| `@ownText` | 提取元素自身文本（不包含子元素文本） | `div@ownText` |
| `@textNode` | 提取指定索引的文本节点 | `div@textNode(2)` |

### 2.2 高级提取类型

| 提取类型 | 说明 | 示例 |
|---------|------|------|
| `@js` | 执行JavaScript代码处理 | `@js:document.querySelector('h1').textContent` |
| `@json` | 解析JSON并提取内容 | `@json:$.data.book.name` |
| `@attr(属性名)` | 提取任意属性值 | `img@attr(data-original)` |

### 2.3 提取类型使用场景

#### 场景1：提取文本内容
```html
<div class="title"><h1>小说书名</h1></div>
```
```css
"title": ".title h1@text"  // 提取"小说书名"
```

#### 场景2：提取HTML结构
```html
<div class="content"><p>段落1</p><p>段落2</p></div>
```
```css
"content": ".content@html"  // 提取完整HTML结构
```

#### 场景3：提取链接地址
```html
<a href="/book/123">书籍详情</a>
```
```css
"bookUrl": "a@href"  // 提取"/book/123"
```

#### 场景4：提取图片地址
```html
<img src="cover.jpg" alt="封面" />
```
```css
"coverUrl": "img@src"  // 提取"cover.jpg"
```

#### 场景5：提取自身文本（不含子元素）
```html
<div class="author">
  <span class="label">作者：</span>
  张三
</div>
```
```css
"author": ".author@ownText"  // 仅提取"张三"
```

---

## 3. 正则表达式格式

### 3.1 基础格式

```
选择器@提取类型##正则表达式##替换内容
```

### 3.2 常用场景

#### 场景1：清理前缀
```html
<p class="author">作者：张三</p>
```
```css
"author": ".author@text##^作者：##"  // 移除"作者："前缀，结果："张三"
```

#### 场景2：清理后缀
```html
<p class="update">更新时间：2024-01-06</p>
```
```css
"update": ".update@text##更新时间：$##"  // 移除"更新时间："后缀，结果："2024-01-06"
```

#### 场景3：提取中间内容
```html
<p class="info">科幻灵异 | 作者：张三</p>
```
```css
"author": ".info@text##.*作者：(.*?)##$1"  // 提取"作者："后的内容，结果："张三"
```

#### 场景4：替换内容
```html
<p class="content">第一行\n第二行\n第三行</p>
```
```css
"content": ".content@text##\n##<br>"  // 将换行符替换为<br>标签
```

#### 场景5：多规则组合
```css
p.author@text##^作者：####\s+$##  // 先移除前缀，再移除末尾空格
```

### 3.3 正则修饰符

| 修饰符 | 说明 | 示例 |
|--------|------|------|
| `i` | 忽略大小写 | `(?i)pattern` |
| `m` | 多行模式 | `(?m)pattern` |
| `s` | 单行模式（`.`匹配换行符） | `(?s)<script.*?</script>` |

### 3.4 常用正则模式

| 模式 | 说明 | 示例 |
|------|------|------|
| `^文本` | 匹配开头 | `^作者：` |
| `文本$` | 匹配结尾 | `更新时间：$` |
| `.*?` | 非贪婪匹配任意字符 | `作者：(.*?)` |
| `\s+` | 匹配一个或多个空白 | `\s+` |
| `[^|]*` | 匹配非\|的字符 | `^[^|]*` |
| `(\d+)` | 匹配数字 | `(\d+)万字` |

---

## 4. 书源JSON结构

### 4.1 完整结构

```js
{
  "bookSourceName": "书源名称",          // 书源名称
  "bookSourceUrl": "https://example.com", // 书源主站地址
  "bookSourceGroup": "小说",              // 书源分组
  "bookSourceType": 0,                   // 书源类型（0=文本, 1=音频, 2=图片, 3=文件, 4=视频）
  "bookSourceComment": "书源说明",        // 书源注释
  "loginUrl": "https://example.com/login", // 登录地址
  "concurrentRate": "",                  // 并发率
  "header": "",                          // 请求头
  "searchUrl": "/search?q={{key}}",      // 搜索URL（支持POST配置）
  "exploreUrl": "",                      // 发现URL
  "enabled": true,                       // 是否启用
  "enabledExplore": true,                // 是否启用发现
  "weight": 0,                           // 智能排序权重
  "customOrder": 0,                      // 手动排序编号
  "lastUpdateTime": 1704537600000,       // 最后更新时间

  // 搜索页规则
  "ruleSearch": {
    "bookList": ".book-item",            // 书籍列表选择器
    "name": ".title@text",               // 书名提取规则
    "author": ".author@text##作者：##",   // 作者提取规则（支持正则清理）
    "kind": ".kind@text",                // 分类提取规则
    "wordCount": ".word-count@text",     // 字数提取规则
    "lastChapter": ".last-chapter a@text", // 最新章节提取规则
    "intro": ".desc@text",               // 简介提取规则
    "coverUrl": "img@src",               // 封面图片链接
    "bookUrl": "a@href"                  // 书籍详情页链接
  },

  // 书籍详情页规则
  "ruleBookInfo": {
    "name": "#book-title@text",          // 书名
    "author": "#author a@text##作者：##", // 作者
    "kind": "#kind@text",                // 分类
    "wordCount": "#word-count@text",     // 字数
    "lastChapter": "#last-chapter a@text", // 最新章节
    "intro": "#intro@text",              // 简介
    "coverUrl": "#cover img@src",        // 封面
    "tocUrl": ""                         // 目录页URL（可选）
  },

  // 目录页规则
  "ruleToc": {
    "chapterList": "#chapter-list li",   // 章节列表选择器
    "chapterName": "a@text",             // 章节名提取规则
    "chapterUrl": "a@href",              // 章节URL提取规则
    "preUpdateJs": "",                   // 更新前JS（可选）
    "updateJs": "",                      // 更新JS（可选）
    "nextTocUrl": ""                     // 下一页目录URL（可选）
  },

  // 正文内容规则
  "ruleContent": {
    "content": "#content@html##<script.*?</script>", // 正文内容（支持正则清理）
    "nextContentUrl": "#next-chapter@href", // 下一页正文URL（可选）
    "webJs": "",                         // WebView JS（可选）
    "sourceRegex": "",                   // 源码替换正则（可选）
    "replaceRegex": ""                   // 内容替换正则（可选）
  },

  // 发现页规则（可选）
  "ruleExplore": {
    "exploreList": ".explore-item",      // 发现列表选择器
    "title": ".title@text",              // 标题
    "name": ".name@text",                // 书名
    "author": ".author@text",            // 作者
    "kind": ".kind@text",                // 分类
    "wordCount": ".word-count@text",     // 字数
    "lastChapter": ".last-chapter@text", // 最新章节
    "intro": ".intro@text",              // 简介
    "coverUrl": "img@src",               // 封面
    "bookUrl": "a@href"                  // 书籍URL
  }
}
```

### 4.2 书源类型说明

| 类型值 | 书源类型 | 说明 |
|--------|----------|------|
| 0      | 文本书源 | 标准小说网站，支持文本内容提取 |
| 1      | 音频书源 | 有声小说网站，支持音频链接提取 |
| 2      | 图片书源 | 漫画网站，需配置图片提取规则 |
| 3      | 文件书源 | 文件下载网站，需配置文件链接提取规则 |
| 4      | 视频书源 | 视频网站，需配置视频链接提取规则 |

---

## 5. POST请求配置

### 5.1 基础POST配置

```js
"searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"key={{key}}&page={{page}}\"}"
```

### 5.2 带请求头的POST配置

```js
"searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"key={{key}}\",\"headers\":{\"User-Agent\":\"Legado/3.0\",\"Accept-Language\":\"zh-CN,zh;q=0.9\"}}"
```

### 5.3 带编码的POST配置

```js
// GBK编码
"searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}&searchtype=all\",\"charset\":\"GBK\"}"

// UTF-8编码（可省略）
"searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}&searchtype=all\"}"
```

### 5.4 需要WebView渲染的POST请求

```js
"searchUrl": "https://example.com/search,{\"method\":\"POST\",\"body\":\"key={{key}}\",\"webView\":true}"
```

### 5.5 复杂POST配置（使用JavaScript）

```js
"searchUrl": "<js>(() => { var ua = 'Mozilla/5.0...'; var headers = {'User-Agent': ua}; var body = 'keyword=' + String(key) + '&page=' + String(page); var option = {'charset': 'gbk', 'method': 'POST', 'body': String(body), 'headers': headers}; return 'https://example.com/search,' + JSON.stringify(option); })()</js>"
```

### 5.6 POST请求关键要点

1. **body必须保证是JavaScript的String类型**
   ```js
   // ✅ 正确
   "body": "keyword={{key}}&page={{page}}"

   // ❌ 错误
   "body": keyword={{key}}&page={{page}}
   ```

2. **变量是计算得到的尽量都用String()强转**
   ```js
   "body": "keyword=" + String(key) + "&page=" + String(page)
   ```

3. **charset为utf-8时可省略**
   ```js
   // 可省略charset
   "searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\"}"
   ```

4. **无特殊情况不需要请求头和webView**
   ```js
   // 最简配置
   "searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\"}"
   ```

---

## 6. 真实书源模式

### 6.1 模式1：标准小说站（最常见）

**特点**：有封面、完整信息、独立标签

**HTML结构**：
```html
<div class="book-list">
  <div class="book-item">
    <img src="cover.jpg" class="cover" />
    <a href="/book/1" class="title">书名</a>
    <p class="author">作者：张三</p>
    <p class="kind">分类：玄幻</p>
  </div>
</div>
```

**典型规则**：
```js
{
  "ruleSearch": {
    "bookList": ".book-list .book-item",
    "name": ".title@text",
    "author": ".author@text##^作者：##",
    "kind": ".kind@text##^分类：##",
    "bookUrl": "a@href",
    "coverUrl": "img@src"
  }
}
```

### 6.2 模式2：笔趣阁类站点

**特点**：无封面、信息合并、需要正则拆分

**HTML结构**：
```html
<div class="hot_sale">
  <a href="/biquge_317279/">
    <p class="title">末日成神：我的我的我的都是我的异能</p>
    <p class="author">科幻灵异 | 作者：钱真人</p>
    <p class="author">连载 | 更新：第69章 魔师</p>
  </a>
</div>
```

**典型规则**：
```js
{
  "ruleSearch": {
    "bookList": ".hot_sale",
    "name": ".title@text",
    "author": ".author:first-child@text##.*作者：##",
    "kind": ".author:first-child@text##^[^|]*##",
    "lastChapter": ".author:last-child@text##.*更新：##",
    "bookUrl": "a@href",
    "coverUrl": ""
  }
}
```

### 6.3 模式3：聚合源（API型）

**特点**：返回JSON数据，使用JSONPath提取

**典型规则**：
```js
{
  "ruleSearch": {
    "bookList": "$.data.records",
    "name": "$.book_name",
    "author": "$.author",
    "coverUrl": "$.thumb_url",
    "kind": "{{$.status}},{{$.score}}",
    "bookUrl": "<js>...</js>"
  }
}
```

### 6.4 模式4：漫画站点

**特点**：图片封面、漫画专属字段

**HTML结构**：
```html
<div class="comic-list">
  <div class="comic-item">
    <img src="cover.jpg" class="cover" />
    <a href="/comic/1" class="title">漫画名</a>
    <p class="author">作者</p>
  </div>
</div>
```

**典型规则**：
```js
{
  "ruleSearch": {
    "bookList": ".comic-list .comic-item",
    "name": ".title@text",
    "author": ".author@text",
    "coverUrl": "img@src",
    "bookUrl": "a@href"
  }
}
```

---

## 7. 134个真实书源分析结果

### 7.1 统计数据

| 指标 | 数值 |
|------|------|
| 总书源数 | 134个 |
| 涵盖类型 | 小说、漫画、影视、有声书、直播 |
| 主要分组 | 未分类(39)、小说(9)、晴天聚合(5)、大灰狼聚合(4)、R18(4) |

### 7.2 最常用CSS选择器（Top 10）

| 选择器 | 使用次数 | 主要用途 |
|--------|----------|----------|
| `img` | 40次 | 封面图片、章节图片 |
| `h1` | 30次 | 书名、大标题 |
| `div` | 13次 | 内容容器、通用区块 |
| `content` | 12次 | 正文内容容器 |
| `intro` | 11次 | 书籍简介、描述 |
| `h3` | 9次 | 章节标题、小标题 |
| `span` | 9次 | 行内文本、标签信息 |
| `a` | 多次 | 链接地址、章节跳转 |
| `p` | 多次 | 段落文本、作者信息 |
| `li` | 多次 | 列表项、章节列表 |

### 7.3 最常用提取类型（Top 5）

| 提取类型 | 使用次数 | 主要用途 |
|----------|----------|----------|
| `@href` | 81次 | 书籍URL、章节URL、跳转链接 |
| `@text` | 72次 | 文本内容提取（书名、作者、章节名等） |
| `@src` | 60次 | 图片地址提取（封面、插图等） |
| `@html` | 33次 | HTML结构提取（正文内容、复杂描述） |
| `@js` | 25次 | JavaScript处理（动态内容、加密解密） |

### 7.4 特殊功能使用统计

| 功能 | 使用次数 | 说明 |
|------|----------|------|
| 正则表达式 | 42次 | 用于清理内容、提取特定信息 |
| XPath | 24次 | 用于复杂DOM结构选择 |
| JavaScript处理 | 8次 | 用于复杂逻辑处理 |
| JSONPath | 6次 | 用于API型书源数据提取 |

### 7.5 常见书源模式统计

| 模式类型 | 数量 | 说明 |
|----------|------|------|
| 标准小说站 | 68个 | 有封面、完整信息、独立标签 |
| 笔趣阁类 | 45个 | 无封面、信息合并、需要正则拆分 |
| API聚合源 | 13个 | 返回JSON数据、使用JSONPath提取 |
| 漫画站点 | 8个 | 图片为主、漫画专属字段 |

---

## 8. 常见陷阱与错误

### 8.1 选择器陷阱

#### 陷阱1：使用过于宽泛的选择器
```css
// ❌ 错误：匹配所有p标签
"author": "p@text"

// ✅ 正确：仅匹配class为author的p标签
"author": "p.author@text"
```

#### 陷阱2：混淆选择器优先级
```css
// ❌ 错误：选择器不够具体
"content": "div@html"

// ✅ 正确：使用更具体的选择器
"content": "div.content@html"
```

### 8.2 提取类型错误

#### 陷阱1：混淆@text和@ownText
```html
<div class="intro">
  <p>简介开头</p>
  这是简介内容
</div>
```
```css
// 提取"简介开头这是简介内容"
"intro": ".intro@text"

// 仅提取"这是简介内容"
"intro": ".intro@ownText"
```

#### 陷阱2：混淆@text和@html
```css
// ❌ 错误：正文应该用@html
"content": "#content@text"

// ✅ 正确
"content": "#content@html"
```

### 8.3 正则表达式错误

#### 陷阱1：正则表达式语法错误
```css
// ❌ 错误：未使用##包裹
"author": ".author@text /作者：(.*)/"

// ✅ 正确
"author": ".author@text##作者：(.*?)##"
```

#### 陷阱2：贪婪匹配导致过度匹配
```css
// ❌ 错误：贪婪匹配可能获取过多内容
"author": ".author@text##作者：(.*)##"

// ✅ 正确：非贪婪匹配
"author": ".author@text##作者：(.*?)##"
```

### 8.4 POST请求配置错误

#### 陷阱1：未正确配置请求体
```js
// ❌ 错误：缺少引号和格式
"searchUrl": "/search,method=POST,body=keyword={{key}}"

// ✅ 正确：使用JSON格式
"searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\"}"
```

#### 陷阱2：未配置编码
```js
// ❌ 错误：GBK编码网站未指定charset
"searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\"}"

// ✅ 正确
"searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\",\"charset\":\"GBK\"}"
```

### 8.5 相对URL处理错误

```js
// ❌ 错误：bookSourceUrl未配置
"bookSourceUrl": "",
"searchUrl": "/search?q={{key}}"  // 跳转错误

// ✅ 正确
"bookSourceUrl": "https://www.example.com",
"searchUrl": "/search?q={{key}}"  // 自动转为完整URL
```

---

## 9. 错误处理策略

### 9.1 选择器容错

#### 策略1：使用备选选择器
```css
// 优先使用data-original，备选src
"coverUrl": "img.lazy@data-original||img@src"
```

#### 策略2：使用||提供默认值
```css
// 搜索页无封面时设置为空
"coverUrl": "img@src||"
```

### 9.2 正则表达式容错

#### 策略1：使用非贪婪匹配
```css
// 避免贪婪匹配
"author": ".author@text##作者：(.*?)##"
```

#### 策略2：使用边界匹配
```css
// 使用^和$确保精确匹配
"author": ".author@text##^作者：.*$##"
```

### 9.3 空值处理

#### 策略1：设置默认值
```css
// 提供默认值
"intro": ".intro@text||暂无简介"
```

#### 策略2：留空处理
```css
// 搜索页无封面时设置为空
"coverUrl": ""
```

### 9.4 编码处理

#### 策略1：指定正确编码
```js
// GBK编码网站
"searchUrl": "/search,{\"method\":\"POST\",\"body\":\"keyword={{key}}\",\"charset\":\"GBK\"}"
```

#### 策略2：使用JavaScript处理
```js
"searchUrl": "<js>java.post('https://example.com/search', 'keyword=' + encodeURIComponent('{{key}}'))</js>"
```

---

## 10. 最佳实践

### 10.1 选择器最佳实践

1. **优先使用class选择器**
   ```css
   // ✅ 推荐
   ".book-title@text"

   // ⚠️ 不推荐（过于特定）
   "#book-title@text"
   ```

2. **避免使用过于复杂的选择器**
   ```css
   // ❌ 避免
   "div.book-list div.book-item div.title h1 a@text"

   // ✅ 推荐
   ".title@text"
   ```

3. **使用伪类处理多个同名元素**
   ```css
   // 提取第一个author标签
   ".author:first-child@text"

   // 提取最后一个author标签
   ".author:last-child@text"

   // 提取第3个author标签
   ".author:nth-child(3)@text"
   ```

### 10.2 规则结构最佳实践

1. **按照官方规范组织JSON结构**
   ```js
   {
     "bookSourceName": "书源名称",
     "bookSourceUrl": "https://example.com",
     "bookSourceType": 0,
     "searchUrl": "/search?q={{key}}",
     "ruleSearch": {},
     "ruleBookInfo": {},
     "ruleToc": {},
     "ruleContent": {}
   }
   ```

2. **保持规则的一致性**
   ```css
   // 统一使用Default语法
   "name": ".title@text"
   "author": ".author@text"
   "coverUrl": "img@src"
   ```

3. **为规则添加注释（可选）**
   ```js
   {
     // 搜索页规则
     "ruleSearch": {
       "bookList": ".book-item",
       "name": ".title@text"
     }
   }
   ```

### 10.3 测试最佳实践

1. **使用真实HTML验证选择器**
   ```python
   validate_selector_on_real_web(url="https://example.com", selector=".title@text")
   ```

2. **测试不同场景**
   - 测试无封面情况
   - 测试信息合并情况
   - 测试懒加载图片
   - 测试分页内容
   - 测试动态加载内容

3. **逐步验证**
   ```css
   // 先验证列表选择器
   "bookList": ".book-item"

   // 再验证单个元素
   "name": ".title@text"

   // 最后验证正则清理
   "author": ".author@text##^作者：##"
   ```

### 10.4 性能最佳实践

1. **避免使用过于宽泛的选择器**
   ```css
   // ❌ 避免
   "content": "div@html"

   // ✅ 推荐
   "content": "#content@html"
   ```

2. **对于大型页面，使用更具体的选择器**
   ```css
   // ✅ 推荐
   "content": "#article-content@html"
   ```

3. **合理使用正则表达式，避免过度使用**
   ```css
   // ❌ 避免：过度使用正则
   "content": "#content@html##<script.*?</script>##<style.*?</style>##<div.*?</div>##"

   // ✅ 推荐：仅清理必要内容
   "content": "#content@html##<script.*?</script>"
   ```

### 10.5 维护最佳实践

1. **为书源添加版本信息**
   ```js
   {
     "bookSourceName": "书源名称 v1.0",
     "bookSourceComment": "创建于2025-01-06"
   }
   ```

2. **定期检查书源可用性**
   - 测试搜索功能
   - 测试书籍详情
   - 测试目录加载
   - 测试正文阅读

3. **记录网站结构变化**
   - 记录修改历史
   - 标注失效规则
   - 提供备选方案

---

## 📚 附录

### A. 官方文档参考

- [Legado官方GitHub](https://github.com/gedoor/legado)
- [Legado官方Wiki](https://github.com/gedoor/legado/wiki)
- [书源开发指南](https://github.com/gedoor/legado/wiki/BookSourceRules)

### B. 知识库文件清单

| 文件名 | 大小 | 说明 |
|--------|------|------|
| legado_knowledge_base.md | 60KB | 完整版知识库 |
| css选择器规则.txt | 79KB | CSS选择器详解 |
| 书源规则：从入门到入土.md | 39KB | 书源规则文档 |
| Legado知识库.txt | 13KB | 核心知识整合 |
| 真实书源知识库.md | 8.5KB | 134个书源分析 |
| 真实书源模板库.txt | 8.1KB | 真实书源模板 |
| 真实书源分析结果.json | 11KB | 结构化分析数据 |

### C. 知识统计

| 指标 | 数值 |
|------|------|
| 处理文件数 | 900+ |
| 学习条目数 | 2448+ |
| 书源数量 | 134个真实书源 + 804个参考书源 |
| 选择器规则 | 168个 |
| 知识类型 | CSS选择器、提取类型、正则表达式、POST请求、书源结构等 |

---

## ⚠️ 重要提醒

1. **所有知识库内容仅作参考**，不能直接照搬知识库中的选择器
2. **所有选择器必须在真实HTML上验证**，禁止编造选择器
3. **必须访问真实网页进行分析**，确保规则准确性
4. **禁止Mock数据**，必须基于真实HTML结构编写规则
5. **优先使用高频选择器**（img、h1、div、content等），兼容性更好
6. **合理选择提取类型**，避免类型混淆
7. **处理特殊情况**（无封面、懒加载、信息合并），确保规则健壮性
8. **POST请求必须按照知识库规范编写**，确保格式正确

---

*本文档基于900+文件、2448+条知识、134个真实书源分析生成，仅供参考和学习使用。*
