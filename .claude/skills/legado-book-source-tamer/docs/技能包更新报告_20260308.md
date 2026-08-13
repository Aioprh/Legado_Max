# Legado书源驯兽师 - 技能包更新报告

**版本**: V0.5 
**更新日期**: 2026-03-08  
**编写人**: AI Assistant  
**适用范围**: Legado书源开发、调试与优化

---

## 📋 目录

1. [更新概述](#更新概述)
2. [功能模块更新](#功能模块更新)
3. [问题修复与性能优化](#问题修复与性能优化)
4. [实战问题与解决方案](#实战问题与解决方案)
5. [关键功能使用示例](#关键功能使用示例)
6. [后续版本迭代建议](#后续版本迭代建议)

---

## 更新概述

本次技能包更新主要围绕 **Legado书源开发流程规范化** 和 **常见问题解决方案** 两大核心方向展开。通过实际开发过程中的用户反馈和问题分析，技能包新增了多项功能特性，修正了多个关键错误，并形成了一套完整的知识吸收机制。

### 更新统计

| 类别 | 数量 | 说明 |
|------|------|------|
| 新增功能 | 4项 | 文件整理、property提取、API分页目录、搜索URL发现 |
| 错误修正 | 5项 | 正则表达式规则、nextContentUrl判断、JS返回值类型等 |
| 流程优化 | 3项 | 三阶段工作流、工具调用优先级、文件管理流程 |
| 口诀总结 | 12条 | 便于记忆的规则口诀 |

---

## 功能模块更新

### 1. 文件自动整理模块 🆕

#### 功能说明

新增 `file_organizer.py` 文件整理模块，实现书源创建完成后自动整理相关文件到统一目录的功能。

#### 核心特性

- **自动创建书源专属文件夹**：在 `temp/` 目录下以书源名称创建子文件夹
- **支持多种文件类型**：JSON配置、HTML缓存、Python脚本等
- **会话模式支持**：可在对话过程中注册文件，最后统一整理
- **文件名冲突处理**：自动添加时间戳后缀避免覆盖

#### 使用方法

```python
# 方法1：直接整理指定文件
from debugger.engine.file_organizer import organize_book_source_files

result = organize_book_source_files(
    book_source_name="无限小说网",
    files_to_move=["无限小说网.json", "search.html"],
    copy_mode=False  # False=移动，True=复制
)
print(result.message)

# 方法2：会话模式（推荐用于多文件场景）
from debugger.engine.file_organizer import start_file_session, register_generated_file

session_id = start_file_session()
register_generated_file("无限小说网.json")
register_generated_file("search.html")
register_generated_file("book.html")

result = organize_book_source_files(
    book_source_name="无限小说网",
    session_id=session_id
)
```

#### 整理后的目录结构

```
legadoSkill/
├── temp/
│   ├── 无限小说网/
│   │   ├── 无限小说网.json    # 书源JSON配置
│   │   ├── search.html        # 搜索页HTML
│   │   ├── book.html          # 详情页HTML
│   │   └── content.html       # 正文页HTML
│   └── 其他书源/
│       └── ...
```

---

### 2. Property属性提取法 🆕

#### 功能说明

使用 `[property$=xxx]@content` 提取HTML meta标签的content属性，比传统CSS选择器更稳定。

#### 核心优势

- **稳定性高**：不受页面结构变化影响
- **语义明确**：直接通过meta标签的property属性定位
- **支持多种匹配模式**：`$=`（后缀匹配）、`~=`（正则匹配）

#### 使用示例

```json
{
  "ruleBookInfo": {
    "name": "[property$=book_name]@content",
    "author": "[property$=author]@content",
    "coverUrl": "[property$=image]@content",
    "intro": "[property$=description]@content",
    "lastChapter": "[property$=latest_chapter_name]@content",
    "kind": "[property~=category|status|update_time]@content"
  }
}
```

#### 对应的HTML结构

```html
<meta property="og:book_name" content="斗破苍穹">
<meta property="og:author" content="天蚕土豆">
<meta property="og:image" content="https://example.com/cover.jpg">
<meta property="og:description" content="这里是小说简介...">
```

---

### 3. API分页目录处理 🆕

#### 功能说明

针对使用API接口返回章节列表的网站，提供完整的分页目录处理方案。

#### 核心流程

```
详情页 → init提取bookId → tocUrl指向API → nextTocUrl分页获取
```

#### 完整示例（无限小说网）

```json
{
  "ruleBookInfo": {
    "init": "<js>\nvar src=java.getString(\"tag.section.0@html\");\nvar id=src.match(/data\\-bookid\\=\\\"(\\d+)\\\"/);\nif(id){\njava.put(\"id\",id[1]);\n}\n</js>",
    "tocUrl": "<js>\nvar id=java.get(\"id\");\nif(id){\n\"https://wuxianbook.com/e/extend/bookpage/pages.php?id=\"+id+\"&pageNum=0&dz=asc\";\n}else{\nbaseUrl;\n}\n</js>"
  },
  "ruleToc": {
    "chapterList": ".list[*]",
    "chapterName": ".title",
    "chapterUrl": "{{book.bookUrl}}{{$.pic}}",
    "nextTocUrl": ".totalPage\n<js>\nvar id=java.get(\"id\");\nvar match=String(result).match(/(\\d+)/);\nif(match){\nvar n=parseInt(match[1]);\nvar list=[];\nfor(var i=1;i<=n;i++){\nlist.push(\"https://wuxianbook.com/e/extend/bookpage/pages.php?id=\"+id+\"&pageNum=\"+i+\"&dz=asc\");\n}\nlist;\n}\n</js>"
  }
}
```

#### 关键技术点

| 字段 | 作用 | 说明 |
|------|------|------|
| `init` | 预处理 | 提取bookId并存储到变量 |
| `tocUrl` | 目录入口 | 指向API接口URL |
| `nextTocUrl` | 分页控制 | 返回URL列表，Legado自动遍历 |
| `{{book.bookUrl}}` | URL拼接 | 动态拼接章节URL |

---

### 4. 搜索URL发现优化 🆕

#### 功能说明

优化搜索URL的发现流程，从"猜测+尝试"改为"分析+定向测试"。

#### 优化前后对比

| 优化前 | 优化后 |
|--------|--------|
| 猜测URL格式 | 先分析HTML/JS代码 |
| 盲目尝试多种格式 | 找到线索后定向测试 |
| 忽略JavaScript代码 | 仔细查看JS中的API调用 |
| 效率低、耗时长 | 效率高、快速定位 |

#### 正确工作流程

```
步骤1: 获取首页HTML
    ↓
步骤2: 查找搜索表单/搜索相关代码
    ↓
步骤3: 如果是静态表单 → 分析action属性
      如果是JS加载 → 查看JS代码中的API调用
    ↓
步骤4: 直接测试发现的API
    ↓
步骤5: 成功！
```

#### 实战案例

```html
<!-- 从首页HTML中发现搜索表单 -->
<form action="/e/search/index.php" method="post">
    <input type="text" name="keyboard">
    <input type="hidden" name="tbname" value="bookname">
    <input type="hidden" name="show" value="title,writer">
    <input type="hidden" name="tempid" value="1">
</form>

<!-- 直接得到搜索URL -->
"searchUrl": "https://wuxianbook.com/e/search/index.php,{\"method\":\"POST\",\"body\":\"keyboard={{key}}&tbname=bookname&show=title,writer&tempid=1\"}"
```

---

## 问题修复与性能优化

### 1. 正则表达式替换规则修正 🔧

#### 问题描述

之前对正则表达式末尾 `##` 的理解有误，导致规则编写错误。

#### 修正内容

| 格式 | 含义 | 示例 |
|------|------|------|
| `##正则表达式` | 替换为空白（删除） | `##^作者：` |
| `##正则表达式##替换内容` | 替换为指定内容 | `##旧##新` |
| `##规则1\|规则2\|规则3` | 删除所有匹配内容 | `##广告\|提示\|版权` |

#### 错误示例

```json
{
  "content": ".content@html##规则1|规则2##"
}
```
❌ 末尾多写了 `##`，会把"规则1|规则2"替换为空白

#### 正确示例

```json
{
  "content": ".content@html##规则1|规则2"
}
```
✅ 末尾不写 `##`，分别删除规则1和规则2

---

### 2. nextContentUrl判断规则重大修正 🔧

#### 问题描述

SKILL.md 中关于 `nextContentUrl` 的判断规则有**重大错误**，原规则说"同一章节分页应该留空"，这是完全错误的。

#### 修正内容

**正确理解**：
- `nextContentUrl` **正是用于同一章节的分页**！
- Legado 会自动获取下一页内容并合并
- 只要有分页按钮（无论是"下一章"还是"下一页"），**都必须设置** `nextContentUrl`
- 只有单页正文（无分页按钮）才留空

#### 正确规则对照表

| 按钮文字 | 功能 | nextContentUrl |
|---------|------|----------------|
| "下一章"、"下章" | 跳转到下一章 | 设置 `text.下一章@href` |
| "下一页" | 同一章分页 | **设置** `text.下一页@href` |
| "下一"、"下页" | 模糊按钮 | **设置** `text.下一@href` |
| 无分页按钮 | 单页正文 | 留空 |

#### 正确示例

```json
{
  "ruleContent": {
    "content": "#booktxt@html##<p>.*本章未完.*</p>",
    "nextContentUrl": "text.下一页@href"
  }
}
```

---

### 3. JS返回值类型规范修正 🔧

#### 问题描述

使用 `JSON.stringify(list)` 返回字符串，导致 `ClassCastException: String cannot be cast to List` 错误。

#### 问题原因

- `getElements` 方法期望返回 `List<Any>` 类型
- JS中使用 `JSON.stringify(list)` 返回的是字符串
- 字符串无法强制转换为List

#### 正确做法

```javascript
// ❌ 错误：返回字符串
JSON.stringify(list);

// ✅ 正确：直接返回List对象
list;
```

---

### 4. 文件整理流程优化 🚀

#### 问题描述

SKILL.md 中虽然写了文件整理步骤，但没有足够强调这是**必须自动执行**的步骤，导致文件遗留在根目录。

#### 优化内容

- 步骤3保存文件到根目录只是**临时保存**
- 保存后**必须立即执行步骤4**整理文件
- 必须使用 RunCommand 工具执行 Python 代码来调用 file_organizer 模块

#### 正确流程

```
步骤3: Write 保存 JSON 到根目录
    ↓ 立即执行
步骤4: RunCommand 调用 file_organizer 整理文件
    ↓
结果: 文件移动到 temp/书源名称/ 文件夹
```

---

### 5. 工具调用优先级优化 🚀

#### 优化内容

1. **优先使用项目内置工具**：`debugger/` 目录下的调试模块
2. **不要过度依赖模拟调试**：`debugger/` 是模拟引擎，不能100%还原
3. **参考Legado源码**：`legado/` 目录下的Kotlin源码是权威
4. **缓存文件创建到 `temp/` 目录**：保持项目整洁

---

## 实战问题与解决方案

### 问题1：搜索结果无封面

**场景**：部分网站搜索结果页没有封面图片

**解决方案**：
```json
{
  "ruleSearch": {
    "coverUrl": ""
  }
}
```

---

### 问题2：信息合并（分类|作者）

**场景**：搜索结果中分类和作者信息合并在同一个标签中

**HTML示例**：
```html
<p class="author">科幻灵异 | 作者：钱真人</p>
```

**解决方案**：
```json
{
  "ruleSearch": {
    "author": ".author@text##.*作者：##",
    "kind": ".author@text##\\|.*##"
  }
}
```

---

### 问题3：正文分页（同一章多页）

**场景**：章节内容分多页显示，需要自动合并

**解决方案**：
```json
{
  "ruleContent": {
    "content": "#chaptercontent@html",
    "nextContentUrl": "text.下一页@href"
  }
}
```

---

### 问题4：目录分页（下拉选择器）

**场景**：目录页使用下拉选择器进行分页

**HTML示例**：
```html
<select onchange="location.href=this.value">
  <option value="/book/123/toc.html">第1页</option>
  <option value="/book/123/toc_2.html">第2页</option>
</select>
```

**解决方案**：
```json
{
  "ruleToc": {
    "chapterList": "#list dd a",
    "chapterName": "a@text",
    "chapterUrl": "a@href",
    "nextTocUrl": "select@option@value"
  }
}
```

---

### 问题5：GBK编码网站

**场景**：网站使用GBK编码，搜索中文乱码

**解决方案**：
```json
{
  "searchUrl": "/modules/article/search.php,{\"method\":\"POST\",\"body\":\"searchkey={{key}}&searchtype=all\",\"charset\":\"gbk\"}"
}
```

---

### 问题6：API返回JSON数据

**场景**：网站使用API返回JSON格式的章节列表

**解决方案**：
```json
{
  "ruleToc": {
    "chapterList": ".list[*]",
    "chapterName": ".title",
    "chapterUrl": "{{book.bookUrl}}{{$.pic}}"
  }
}
```

---

## 关键功能使用示例

### 示例1：标准小说站书源

```json
[
  {
    "bookSourceName": "示例书源",
    "bookSourceUrl": "https://www.example.com",
    "bookSourceType": 0,
    "searchUrl": "/search.php?q={{key}}",
    "ruleSearch": {
      "bookList": ".book-item",
      "name": ".title@text",
      "author": ".author@text##^作者：##",
      "bookUrl": "a@href",
      "coverUrl": "img@src"
    },
    "ruleBookInfo": {
      "name": "h1@text",
      "author": ".author@text",
      "coverUrl": ".cover img@src",
      "intro": ".intro@text"
    },
    "ruleToc": {
      "chapterList": "#list dd a",
      "chapterName": "a@text",
      "chapterUrl": "a@href"
    },
    "ruleContent": {
      "content": "#content@html"
    }
  }
]
```

---

### 示例2：POST请求书源（GBK编码）

```json
[
  {
    "bookSourceName": "GBK网站示例",
    "bookSourceUrl": "https://www.example.com",
    "bookSourceType": 0,
    "searchUrl": "/search.php,{\"method\":\"POST\",\"body\":\"keyword={{key}}\",\"charset\":\"gbk\"}",
    "ruleSearch": {
      "bookList": ".result-item",
      "name": ".title@text",
      "author": ".info@text##.*作者：##",
      "bookUrl": "a@href"
    }
  }
]
```

---

### 示例3：API分页目录书源

```json
[
  {
    "bookSourceName": "API分页示例",
    "bookSourceUrl": "https://www.example.com",
    "bookSourceType": 0,
    "ruleBookInfo": {
      "init": "<js>\nvar id=src.match(/data\\-id\\=\\\"(\\d+)\\\"/);\nif(id){java.put(\"id\",id[1]);}\n</js>",
      "tocUrl": "<js>\nvar id=java.get(\"id\");\n\"https://api.example.com/chapters?id=\"+id+\"&page=1\";\n</js>"
    },
    "ruleToc": {
      "chapterList": "$.data[*]",
      "chapterName": "$.name",
      "chapterUrl": "$.url",
      "nextTocUrl": "$.nextPage"
    }
  }
]
```

---

## 后续版本迭代建议

### 1. 功能增强建议

| 优先级 | 功能 | 说明 |
|--------|------|------|
| 高 | 自动编码检测 | 在获取HTML前自动检测网站编码 |
| 高 | 选择器智能推荐 | 根据HTML结构自动推荐最佳选择器 |
| 中 | 书源模板库扩展 | 增加更多类型网站的书源模板 |
| 中 | 调试日志优化 | 提供更详细的调试信息和错误定位 |
| 低 | 批量书源测试 | 支持批量测试多个书源 |

---

### 2. 规则优化建议

| 优先级 | 规则 | 说明 |
|--------|------|------|
| 高 | nextContentUrl判断流程图 | 添加可视化判断流程图 |
| 高 | 正则表达式速查表 | 整理常用正则表达式模式 |
| 中 | 选择器性能对比 | 分析不同选择器的性能差异 |
| 中 | 错误代码对照表 | 整理常见错误代码及解决方案 |

---

### 3. 文档完善建议

| 优先级 | 文档 | 说明 |
|--------|------|------|
| 高 | 新手入门指南 | 面向零基础用户的快速入门教程 |
| 高 | 常见问题FAQ | 整理用户常见问题及解答 |
| 中 | 视频教程脚本 | 制作视频教程的文字脚本 |
| 中 | 案例分析集 | 收集各类网站的书源开发案例 |

---

### 4. 工具开发建议

| 优先级 | 工具 | 说明 |
|--------|------|------|
| 高 | HTML结构分析器 | 自动分析HTML结构并推荐选择器 |
| 高 | 书源验证器 | 验证书源JSON格式是否正确 |
| 中 | 选择器测试器 | 在线测试选择器效果 |
| 中 | 书源转换器 | 支持其他阅读APP书源格式转换 |

---

## 附录：口诀速查表

### 正则表达式口诀

```
正则替换看末尾，
不写##就是删。
写了##替换掉，
多规则用|分隔开。
```

### nextContentUrl口诀

```
分页按钮必须配，
无论下章或下页。
nextContentUrl要设置，
Legado自动合并文。
只有单页才留空，
这是规则要记清。
```

### 搜索URL发现口诀

```
搜索URL别瞎猜，
先看HTML和JS。
表单action看仔细，
JS代码找API。
分析之后再测试，
效率提升好几倍！
```

### 文件整理口诀

```
书源创建完成后，
文件整理自动化。
temp文件夹下建，
书源名称子目录。
JSON、HTML、Python脚本，
统一整理归一处。
```

### API分页目录口诀

```
API分页目录处理：
init预处理存变量，
tocUrl指向API接口。
nextTocUrl返回URL列表，
Legado自动遍历获取。
{{book.bookUrl}}拼接URL，
$.pic提取章节路径。
```

### 工具调用口诀

```
工具调用要优先，
内置工具最可靠。
模拟调试仅供参考，
Legado源码是权威。
缓存文件存temp，
项目整洁好管理。
```

---

**报告结束**

*本报告基于2026-03-08的技能包更新内容编写，后续如有更新请及时修订。*
