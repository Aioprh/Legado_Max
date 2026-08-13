# Legado书源 - 目录页分页规则（nextTocUrl）详解

> **更新时间**: 2025-01-20
> **来源**: 知识库学习总结

---

## 📋 目录

1. [什么是nextTocUrl](#什么是nexttocurl)
2. [使用场景](#使用场景)
3. [支持格式](#支持格式)
4. [实际案例](#实际案例)
5. [判断规则](#判断规则)
6. [与nextContentUrl的区别](#与nextcontenturl的区别)
7. [常见错误](#常见错误)

---

## 什么是nextTocUrl

**nextTocUrl** 是 Legado 书源规则中用于处理**目录页分页**的字段。

**作用**:
- 当一个书籍的目录被分成多个页面显示时
- 自动获取后续目录页的章节列表
- 合并所有页面的章节为一个完整的目录

**位置**:
```json
{
  "ruleToc": {
    "chapterList": "...",
    "chapterName": "...",
    "chapterUrl": "...",
    "nextTocUrl": "..."  // ← 这里
  }
}
```

---

## 使用场景

### ✅ 需要使用 nextTocUrl

1. **目录有分页**
   - 底部显示 "第1页"、"第2页"、"第3页"
   - 有 "下一页"、"末页" 按钮

2. **URL有页码**
   - `https://example.com/book/123/catalog/1.html`
   - `https://example.com/book/123/catalog/2.html`

3. **章节列表被截断**
   - 每页只显示50个章节
   - 总共有200个章节，需要4页显示

### ❌ 不需要使用 nextTocUrl

1. **目录只有一页**
   - 所有章节都在一个页面
   - 没有分页按钮

2. **自动加载**
   - 网站使用无限滚动自动加载
   - 使用AJAX动态加载章节

---

## 支持格式

### 格式1: 单个URL（CSS选择器）

**适用场景**: 简单的"下一页"按钮

```json
"ruleToc": {
  "chapterList": "//div[@class='list']//li",
  "chapterName": "//a/text()",
  "chapterUrl": "//a/@href",
  "nextTocUrl": "//div[@class='pagination']//li[last()-1]/a/@href"
}
```

**说明**:
- `//div[@class='pagination']` - 定位分页区域
- `//li[last()-1]` - 选择倒数第二个链接（"下一页"）
- `/a/@href` - 提取链接地址

### 格式2: URL数组（JS脚本）

**适用场景**: 需要一次生成所有后续页面的URL

```json
"ruleToc": {
  "chapterList": "//div[@class='list']//li",
  "chapterName": "//a/text()",
  "chapterUrl": "//a/@href",
  "nextTocUrl": "@js:\nconst pages = [];\nlet end = java.getString('#end@href');\nlet num = +end.match(/(\\d+)\\.html/)[1] || 0;\nfor (let i = 2; i <= num; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"
}
```

**说明**:
- 获取"末页"链接中的页码
- 循环生成第2页到末页的所有URL
- 返回URL数组，Legado会依次加载

### 格式3: 不需要分页（留空）

**适用场景**: 目录只有一页

```json
"ruleToc": {
  "chapterList": "//div[@class='list']//li",
  "chapterName": "//a/text()",
  "chapterUrl": "//a/@href",
  "nextTocUrl": ""
}
```

**说明**:
- 留空字符串表示不需要分页
- 也可以省略不写

---

## 实际案例

### 案例1: 八叉书库（CSS选择器）

**书源**: 八叉书库
**地址**: https://bcshuku.com/

**规则配置**:
```json
{
  "ruleToc": {
    "chapterList": "//div[@id='list-chapter']//ul[@class='list-chapter']//li/a",
    "chapterName": "//span[@class=\"chapter-text\"]/text()",
    "chapterUrl": "@js:\nvar doc = org.jsoup.Jsoup.parse(result);\nvar link = doc.select(\"a\").first();\n'https://bcshuku.com' + link.attr(\"href\");",
    "nextTocUrl": "//div[@id=\"pagination\"]//li[last()-1]/a/@href"
  }
}
```

**解析**:
- `//div[@id="pagination"]` - 定位分页区域
- `//li[last()-1]` - 选择倒数第二个链接（"下一页"）
- `/a/@href` - 提取链接地址

---

### 案例2: 同人小说网（JS脚本）

**书源**: 同人小说网
**地址**: https://www.rrssk.com/

**规则配置**:
```json
{
  "ruleToc": {
    "chapterList": "$.data.list[*]",
    "chapterName": "$.chaptername",
    "chapterUrl": "$.chapterurl",
    "nextTocUrl": "@js:\nresult = [];\nid = java.get(\"id\");\npages = java.get(\"page\");\nfor (let i = 2 ; i <= pages ; i++) {\n  option = baseUrl.match(/^(.*?\\/\\/.*?)\\//)[1] + \"/index.php?action=loadChapterPage\" + \",\" + JSON.stringify({\n    \"body\": `id=${id}&page=${i}`,\n    \"method\": \"POST\"\n  });\n  result.push(option);\n}\nresult"
  }
}
```

**解析**:
- 使用POST请求加载后续页面
- 动态生成第2页到最后一页的请求配置
- 返回包含请求配置的数组

---

### 案例3: 完本小说网（URL数组）

**书源**: 🔞完本小说网
**地址**: https://example.com/

**规则配置**:
```json
{
  "ruleToc": {
    "chapterList": "//ul[@id='chapter-list']//li",
    "chapterName": "title",
    "chapterUrl": "url",
    "nextTocUrl": "@js:\nconst pages = [];\nlet end = java.getString('#end@href');\nlet num = +end.match(/(\\d+)\\.html/)[1] || 0;\nfor (let i = 2; i <= num; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"
  }
}
```

**解析**:
- 从"末页"链接中提取总页码
- 生成第2页到最后一页的所有URL
- 返回URL数组，Legado会依次加载

---

### 案例4: 笔趣阁（select下拉菜单分页） ⭐ 重要

**书源**: 笔趣阁
**地址**: https://example.com/

**HTML结构**:
```html
<select name="pageselect" onchange="self.location.href=options[selectedIndex].value">
  <option value="/biquge_317279/1/#all" selected="selected">1 - 30章</option>
  <option value="/biquge_317279/2/#all">31 - 60章</option>
  <option value="/biquge_317279/3/#all">61 - 90章</option>
</select>
```

**规则配置**:
```json
{
  "ruleToc": {
    "chapterList": "//div[@id='list']//li",
    "chapterName": "//a/text()",
    "chapterUrl": "//a/@href",
    "nextTocUrl": "select[name='pageselect'] option:not([selected])@value"
  }
}
```

**解析**:
- `select[name='pageselect']` - 定位下拉菜单
- `option:not([selected])` - 选择未选中的选项
- `@value` - 提取option的value属性（分页URL）
- 排除已选中的选项，避免重复加载当前页

**⚠️ 常见错误**:
```json
"nextTocUrl": "select@value"  // ❌ 错误！select没有value属性
"nextTocUrl": "select option@value"  // ⚠️ 可能提取当前页
```

**✅ 正确写法**:
```json
"nextTocUrl": "select[name='pageselect'] option:not([selected])@value"  // ✅ 精确定位且排除已选中
```

**💡 技巧**: 如果需要一次生成所有分页URL，可以使用JS脚本:
```json
"nextTocUrl": "@js:\nvar select = document.querySelector('select[name=\"pageselect\"]');\nvar options = select.options;\nvar urls = [];\nfor (var i = 0; i < options.length; i++) {\n  if (!options[i].selected) {\n    urls.push(options[i].value);\n  }\n}\nurls"
```

---

## 判断规则

### 如何判断需要设置 nextTocUrl

**步骤1**: 检查目录页HTML

```html
<!-- 传统链接分页，需要设置 nextTocUrl -->
<div class="pagination">
  <a href="/catalog/1.html">第1页</a>
  <a href="/catalog/2.html">第2页</a>
  <a href="/catalog/3.html">第3页</a>
  <a href="/catalog/next.html">下一页</a>
  <a href="/catalog/last.html">末页</a>
</div>
```

```html
<!-- select下拉菜单分页，需要设置 nextTocUrl ⭐ 重要 -->
<select name="pageselect" onchange="self.location.href=options[selectedIndex].value">
  <option value="/biquge_317279/1/#all" selected="selected">1 - 30章</option>
  <option value="/biquge_317279/2/#all">31 - 60章</option>
  <option value="/biquge_317279/3/#all">61 - 90章</option>
</select>
```

```html
<!-- 无分页，不需要设置 nextTocUrl -->
<div class="chapter-list">
  <!-- 所有章节都在这里 -->
</div>
```

**步骤2**: 检查章节列表

如果章节列表被截断（如"显示1-50章节，共200个"），则需要分页。

**步骤3**: 检查URL模式

- `.../catalog/` - 无页码，可能不需要分页
- `.../catalog/1.html` - 有页码，可能需要分页
- `.../catalog?page=1` - 有页码参数，可能需要分页

---

### 分页类型识别与规则选择

#### 类型1: 传统链接分页

**HTML特征**:
```html
<a href="/catalog/1.html">第1页</a>
<a href="/catalog/2.html">第2页</a>
<a href="/catalog/next.html">下一页</a>
```

**规则写法**:
```json
"nextTocUrl": "//div[@class='pagination']//a[text()='下一页']/@href"
```

#### 类型2: select下拉菜单分页 ⭐ 重要

**HTML特征**:
```html
<select name="pageselect" onchange="self.location.href=options[selectedIndex].value">
  <option value="/biquge_317279/1/#all" selected="selected">1 - 30章</option>
  <option value="/biquge_317279/2/#all">31 - 60章</option>
  <option value="/biquge_317279/3/#all">61 - 90章</option>
</select>
```

**规则写法**:
```json
"nextTocUrl": "select[name='pageselect'] option:not([selected])@value"
```

**⚠️ 注意**:
- ❌ 错误: `select@value`（select没有value属性）
- ✅ 正确: `select option@value`（提取option的value）
- ✅ 最佳: `select option:not([selected])@value`（排除已选中）

#### 类型3: URL参数分页

**HTML特征**:
```html
<a href="/catalog?page=1">第1页</a>
<a href="/catalog?page=2">第2页</a>
```

**规则写法**:
```json
"nextTocUrl": "@js:\nlet page = baseUrl.match(/page=(\\d+)/);\nlet num = page ? parseInt(page[1]) + 1 : 2;\nbaseUrl.replace(/page=\\d+/, 'page=' + num)"
```

#### 类型4: JS脚本生成所有分页

**适用场景**: 需要一次生成所有后续页面

**规则写法**:
```json
"nextTocUrl": "@js:\nconst pages = [];\nlet end = java.getString('#end@href');\nlet num = +end.match(/(\\d+)\\.html/)[1] || 0;\nfor (let i = 2; i <= num; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"
```

---

## 与nextContentUrl的区别

| 特性 | nextTocUrl | nextContentUrl |
|------|-----------|----------------|
| **位置** | `ruleToc` | `ruleContent` |
| **用途** | 目录页分页 | 正文页分页 |
| **获取内容** | 更多章节列表 | 同一章节的更多内容 |
| **示例** | 第1页目录 → 第2页目录 | 第1页正文 → 第2页正文 |
| **按钮文本** | "下一页"、"末页" | "下一页阅读"、"继续阅读" |
| **URL模式** | `/catalog/1.html`, `/catalog/2.html` | `/chapter/1.html`, `/chapter/2.html` |

**关键区别**:

**nextTocUrl**（目录分页）:
```
第1页目录: 第1章, 第2章, ..., 第50章
              ↓ (nextTocUrl)
第2页目录: 第51章, 第52章, ..., 第100章
              ↓ (nextTocUrl)
第3页目录: 第101章, 第102章, ..., 第150章
```

**nextContentUrl**（正文分页）:
```
第1页正文: 这是一段很长的文字...
              ↓ (nextContentUrl)
第2页正文: 这是同一章节的后续文字...
              ↓ (nextContentUrl)
第3页正文: 这是同一章节的最后部分...
```

---

## 常见错误

### ❌ 错误1: 混淆 nextTocUrl 和 nextContentUrl

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "text.下一页阅读@href"  // ❌ 错误！这是nextContentUrl
  }
}
```

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "//div[@class='pagination']//li[last()-1]/a/@href"  // ✅ 正确
  },
  "ruleContent": {
    "nextContentUrl": "text.下一页阅读@href"  // ✅ 正确位置
  }
}
```

### ❌ 错误2: 不需要分页却设置了 nextTocUrl

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "//div[@class='pagination']//li[last()-1]/a/@href"  // ❌ 不需要！
  }
}
```

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": ""  // ✅ 留空或省略
  }
}
```

### ❌ 错误3: 返回值格式错误

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "@js: return 'https://example.com/next.html'"  // ❌ 不需要return
  }
}
```

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "@js: 'https://example.com/next.html'"  // ✅ 直接返回字符串
  }
}
```

### ❌ 错误4: JS脚本语法错误

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "@js: for (let i = 2; i <= 10; i++) {\n  return book.tocUrl + i + '.html';\n}"  // ❌ 循环中不能return
  }
}
```

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "@js: const pages = [];\nfor (let i = 2; i <= 10; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"  // ✅ 返回数组
  }
}
```

### ❌ 错误5: select下拉菜单分页 - 提取select的value ⭐ 重要

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select@value"  // ❌ 错误！select没有value属性
  }
}
```

**原因**: `<select>` 元素本身没有 `value` 属性，value是在 `<option>` 元素上的。

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select option@value"  // ✅ 正确！提取option的value
  }
}
```

### ❌ 错误6: select下拉菜单分页 - 未排除已选中选项

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select option@value"  // ❌ 可能提取当前页，导致循环
  }
}
```

**原因**: 包含已选中的选项，会重复加载当前页，导致无限循环。

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select option:not([selected])@value"  // ✅ 排除已选中
  }
}
```

### ❌ 错误7: select下拉菜单分页 - 定位不准确

**错误写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select@value"  // ❌ 不够精确，可能选错select
  }
}
```

**原因**: 页面可能有多个select元素（如搜索、排序等），定位不明确。

**正确写法**:
```json
{
  "ruleToc": {
    "nextTocUrl": "select[name='pageselect'] option:not([selected])@value"  // ✅ 精确定位
  }
}
```

---

## 最佳实践

### 1. 优先使用CSS选择器

如果目录页有明确的"下一页"按钮，优先使用CSS选择器：

```json
"nextTocUrl": "//div[@class='pagination']//li[last()-1]/a/@href"
```

**优点**:
- 简单直接
- 易于调试
- 性能好

### 2. 使用JS脚本处理复杂情况

如果需要一次生成所有后续页面，或需要特殊处理，使用JS脚本：

```json
"nextTocUrl": "@js:\nconst pages = [];\nfor (let i = 2; i <= num; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"
```

**优点**:
- 灵活性高
- 可以处理复杂逻辑
- 可以使用POST请求

### 3. 提供停止条件

在JS脚本中，确保有正确的停止条件：

```json
"nextTocUrl": "@js:\nif (!hasNextPage) return '';  // ✅ 提供停止条件\nconst pages = [];\nfor (let i = 2; i <= num; i++) {\n  pages.push(book.tocUrl + i + '.html');\n}\npages"
```

### 4. 测试分页功能

使用调试工具测试分页是否正常工作：

```python
debug_book_source(book_source_json, test_type="toc")
```

---

## 总结

### 核心要点

1. **nextTocUrl** 用于目录页分页
2. 支持单个URL和URL数组两种格式
3. 返回空数组、null或空字符串时停止加载
4. 与 nextContentUrl 区分开来使用

### 判断流程

```
检查目录页
    ↓
有分页按钮？
    ├─ 是 → 检查分页按钮位置
    │        ↓
    │    设置 nextTocUrl
    │
    └─ 否 → nextTocUrl 留空
```

### 常用选择器

```javascript
// 最后一个链接（通常是"下一页"）
//div[@class='pagination']//li[last()-1]/a/@href

// 文本包含"下一页"的链接
//a[contains(text(), '下一页')]/@href

// 文本包含"下页"的链接
//a[contains(text(), '下页')]/@href

// class包含"next"的链接
//a[contains(@class, 'next')]/@href
```

---

## 参考资料

- **知识库文档**:
  - `assets/书源规则：从入门到入土.md`
  - `assets/legado知识库.md`
  - `assets/真实书源模板库.txt`

- **真实书源示例**:
  - 八叉书库（CSS选择器）
  - 同人小说网（JS脚本）
  - 完本小说网（URL数组）

- **调试工具**:
  - `tools/legado_debugger.py`
  - `tools/book_source_debugger.py`

---

> **更新**: 本文档已同步到 `assets/智能体自我认知.md`，智能体将在生成书源时自动应用这些规则。
