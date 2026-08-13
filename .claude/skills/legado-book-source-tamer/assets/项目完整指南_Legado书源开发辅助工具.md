# Legado书源开发辅助工具 - 完整项目指南

> **版本**: 2.0  
> **更新时间**: 2025-01-18  
> **项目核心**: 为Legado（阅读）源码项目提供书源开发辅助功能  
> **知识来源**: 167个知识文件、24.93MB、完整的Legado源码文档

---

## 📋 目录

1. [项目概述](#项目概述)
2. [Legado项目介绍](#legado项目介绍)
3. [智能体功能](#智能体功能)
4. [知识库结构](#知识库结构)
5. [核心技术](#核心技术)
6. [使用指南](#使用指南)
7. [防止内容截断机制](#防止内容截断机制)
8. [最佳实践](#最佳实践)

---

## 项目概述

### 项目定位

本项目是一个基于LangChain和LangGraph的智能体，专门用于辅助Legado（阅读）Android应用的书源开发。

### 核心目标

1. **自动化书源开发**：通过分析网站HTML结构，自动生成符合Legado规范的书源JSON
2. **知识库支持**：提供完整的CSS选择器规则、POST请求配置、真实书源模板等知识支持
3. **智能分析**：自动分析网站结构，识别关键元素（书名、作者、封面、目录、正文等）
4. **规则验证**：严格验证生成的规则是否符合Legado官方规范
5. **教学模式**：提供知识查询和文档展示功能，帮助用户学习书源开发

### 项目价值

- **提高效率**：从手动编写规则到自动生成，节省80%+的开发时间
- **降低门槛**：让不懂CSS选择器的用户也能快速创建书源
- **保证质量**：基于真实HTML结构和知识库规范，确保书源的准确性
- **持续学习**：通过分析134个真实书源，不断优化生成规则

---

## Legado项目介绍

### 项目基本信息

- **项目名称**: Legado（阅读）
- **GitHub**: https://github.com/gedoor/legado
- **平台**: Android
- **开发语言**: Kotlin
- **项目类型**: 开源阅读APP

### 项目结构

Legado是一个功能强大的Android阅读应用，主要功能包括：

#### 1. 书源管理
- 支持自定义书源
- 书源导入/导出
- 书源分组管理
- 书源调试功能

#### 2. 阅读功能
- 支持TXT、EPUB等多种格式
- 自定义阅读样式
- 听书功能
- 阅读进度同步

#### 3. 书源特性

**书源JSON结构**：
```json
{
  "bookSourceUrl": "书源地址",
  "bookSourceName": "书源名称",
  "bookSourceGroup": "书源分组",
  "bookSourceType": 0,
  "searchUrl": "搜索URL",
  "ruleSearch": {
    "bookList": "列表选择器",
    "name": "书名规则",
    "author": "作者规则",
    "coverUrl": "封面规则",
    "bookUrl": "书籍URL规则"
  },
  "ruleBookInfo": {
    "name": "书名规则",
    "author": "作者规则",
    "intro": "简介规则",
    "tocUrl": "目录URL"
  },
  "ruleToc": {
    "chapterList": "章节列表",
    "chapterName": "章节名",
    "chapterUrl": "章节URL"
  },
  "ruleContent": {
    "content": "正文内容",
    "nextContentUrl": "下一页URL"
  }
}
```

#### 4. 支持的规则语法

- **JSOUP Default**: Legado原生语法
- **JSOUP CSS**: 标准CSS选择器
- **XPath**: XML路径语言
- **JSONPath**: JSON数据提取
- **JavaScript**: 动态脚本支持

---

## 智能体功能

### 三种工作模式

#### 📖 模式1：知识对话模式（辅助模式）

##### 🔍 子功能1：查询模式

**触发条件**：
- 用户询问知识、规则、语法等问题
- "什么是CSS选择器？"
- "POST请求怎么配置？"
- "@text和@html有什么区别？"

**工作流程**：
1. 调用 `search_knowledge` 工具查询知识库
2. 基于查询结果回答用户问题
3. 提供示例帮助理解

**查询示例**：
```python
# 查询CSS选择器规则
get_css_selector_rules()

# 查询书源JSON结构
search_knowledge("书源JSON结构 BookSource 字段")

# 查询POST请求配置
search_knowledge("POST请求配置 method body String()")
```

##### 📚 子功能2：教学模式

**触发条件**：
- 用户要求查看源代码、阅读文档、查看文件内容
- "给我看一下CSS选择器的源代码"
- "阅读一下legado_knowledge_base.md"

**工作流程**：
1. 调用 `search_knowledge` 或直接读取文件
2. 展示原始内容
3. 标注重点部分（可选）

**输出格式**：
```
文档名称：xxx.md
文件路径：assets/xxx.md
原始内容：
（展示文档原始内容）

重点提示（可选）
（如果有需要，可以标注重点部分）
```

#### 🚀 模式2：完整生成模式（主模式）

**触发条件**：
- 用户要求创建书源
- "创建一个书源"
- "帮我写一个书源"

**工作流程**：

##### 第一阶段：收集信息

**步骤1：查询知识库**
```python
get_css_selector_rules()
search_knowledge("书源JSON结构")
search_knowledge("POST请求配置")
get_real_book_source_examples()
get_book_source_templates()
```

**步骤2：获取真实HTML**
```python
smart_fetch_html(url="http://example.com/search")
```

**步骤3：分析HTML结构**
- 列表结构
- 元素位置
- 特殊属性（懒加载、自定义属性）
- 嵌套关系

**步骤4：记录信息**
- 知识库查询结果
- HTML分析结果
- 推断的CSS选择器

##### 第二阶段：严格审查

**步骤1：编写规则**
- 基于真实HTML结构
- 参考真实书源模板
- 符合Legado规范

**步骤2：验证规则**
- 选择器语法
- 提取类型
- 正则表达式
- JSON结构

**步骤3：特殊处理**
- 无封面图片
- 懒加载图片
- 信息合并
- 多个同名标签

##### 第三阶段：创建书源

**步骤1：准备完整JSON**
- 包含所有必需字段
- 处理特殊情况

**步骤2：调用工具**
```python
edit_book_source(complete_source="完整JSON")
```

**步骤3：输出JSON**
- 输出完整JSON数组
- 用户可直接导入

#### 🎓 模式3：调试优化模式

**触发条件**：
- 用户需要调试现有书源
- "这个书源为什么不能用？"
- "帮我检查一下规则"

**工作流程**：
1. 获取真实HTML
2. 对比规则与HTML
3. 找出问题
4. 提供修复建议

---

## 知识库结构

### 知识库统计

- **总文件数**: 167个
- **总大小**: 24.93 MB
- **分类数**: 8个主要分类

### 分类详情

#### 1. CSS选择器规则
**文件数量**: 1个  
**文件大小**: 80KB  
**核心内容**:
- 基础语法
- 选择器类型（标签、类、ID、属性、伪类）
- 提取类型（@text, @html, @ownText, @textNode, @href, @src）
- 正则表达式集成
- Default语法
- 高级功能

**关键字段**：
```css
/* 基础格式 */
选择器@提取类型

/* 示例 */
.book-title@text          /* 提取文本 */
.book-cover@src           /* 提取图片地址 */
.chapter-link@href        /* 提取链接地址 */
.content@text##\\s+##     /* 使用正则替换 */
img.lazy@data-original||img@src  /* 备选选择器 */
```

#### 2. 书源规则
**文件数量**: 3个  
**文件大小**: 39KB  
**核心内容**:
- 书源JSON结构
- 搜索规则
- 详情规则
- 目录规则
- 正文规则
- POST请求配置
- 高级功能

**关键字段**：
```json
{
  "bookSourceUrl": "书源地址",
  "bookSourceName": "书源名称",
  "searchUrl": "搜索URL",
  "ruleSearch": {
    "bookList": "列表选择器",
    "name": "书名规则",
    "author": "作者规则",
    "coverUrl": "封面规则",
    "bookUrl": "书籍URL规则"
  },
  "ruleBookInfo": {...},
  "ruleToc": {...},
  "ruleContent": {...}
}
```

#### 3. 书源模板
**文件数量**: 2个  
**文件大小**: 8KB  
**核心内容**:
- 真实书源模板库
- 标准书源格式
- 常见模式

**模板示例**：
```json
{
  "bookSourceName": "笔趣阁",
  "bookSourceUrl": "https://www.biquge.com",
  "searchUrl": "/search.php?q={{key}}",
  "ruleSearch": {
    "bookList": "class.result-list@class.result-item",
    "name": "class.result-game-item-title-link@text",
    "author": "@css:.result-game-item-info-tag:nth-child(1)@text",
    "bookUrl": "class.result-game-item-title-link@href"
  }
}
```

#### 4. 真实书源分析
**文件数量**: 1个  
**文件大小**: 9KB  
**核心内容**:
- 134个真实书源分析结果
- 常用CSS选择器统计
- 提取类型统计
- 特殊功能统计
- 常见模式

**统计数据**：
- 最常用选择器：img(40次)、h1(30次)、div(13次)、content(12次)
- 最常用提取类型：@href(81次)、@text(72次)、@src(60次)、@html(33次)
- 特殊功能：正则表达式(42次)、XPath(24次)、JavaScript(8次)、JSONPath(6次)

#### 5. JavaScript扩展
**文件数量**: 5个  
**核心内容**:
- JS扩展类
- 加密解密方法
- 登录检查JS
- 高级JavaScript功能

#### 6. 其他知识
**文件数量**: 148个  
**核心内容**:
- Legado源码文档
- API参考
- 测试用例
- 配置文件
- 说明文档

### 核心知识文件

#### 必读文件（按优先级）

1. **css选择器规则.txt** (80KB)
   - CSS选择器语法手册
   - 所有提取类型详解
   - 正则表达式格式
   - 最佳实践

2. **书源规则：从入门到入土.md** (39KB)
   - 最详细的书源开发教程
   - 所有语法说明
   - 实际案例
   - 常见陷阱

3. **真实书源模板库.txt** (8KB)
   - 可直接使用的书源模板
   - 标准格式参考
   - 常见模式

4. **真实书源高级功能分析.md** (9KB)
   - 134个真实书源的分析报告
   - 统计数据
   - 高级功能说明

5. **书源输出模板_严格模式.md** (19KB)
   - 标准化书源输出格式
   - 字段完整性检查
   - 验证规则

6. **Legado书源开发_长记忆系统.md** (23KB)
   - 整合所有知识
   - 长记忆系统
   - 最佳实践

7. **阅读源码.txt** (未统计，约40万行)
   - Legado完整源码
   - 所有API文档
   - 内部实现细节

---

## 核心技术

### 1. CSS选择器规则

#### 基础格式
```
选择器@提取类型##正则表达式##替换内容
```

#### 选择器类型

| 类型 | 示例 | 说明 |
|------|------|------|
| 标签选择器 | `div`, `p`, `a`, `img` | 选择指定标签 |
| 类选择器 | `.class-name` | 选择指定class |
| ID选择器 | `#id-name` | 选择指定id |
| 属性选择器 | `[attr=value]` | 根据属性选择 |
| 伪类选择器 | `:first-child`, `:last-child` | 根据位置选择 |

#### 提取类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `@text` | 提取纯文本内容 | `h1@text` |
| `@html` | 提取完整HTML结构 | `div.content@html` |
| `@href` | 提取链接地址 | `a@href` |
| `@src` | 提取图片地址 | `img@src` |
| `@ownText` | 提取自身文本 | `div@ownText` |
| `@textNode` | 提取文本节点 | `div@textNode(2)` |

#### 正则表达式

**格式**：
```
选择器@提取类型##正则表达式##替换内容
```

**示例**：
```css
/* 清理前缀 */
.author@text##^作者：##

/* 清理后缀 */
.update@text##更新时间：$##

/* 提取中间内容 */
.info@text##.*作者：(.*?)##$1

/* 替换内容 */
.content@text##\n##<br>
```

#### 备选选择器
```css
/* 优先取data-original，失败则取src */
img.lazy@data-original||img@src
```

### 2. JSOUP Default语法

#### 语法说明
```
@为分隔符，用来分隔获取规则
每段规则可分为3段：
- 第一段是类型（class, id, tag, text, children等）
- 第二段是名称
- 第三段是位置
```

#### 示例
```
class.odd.0@tag.a.0@text
tag.dd.0@tag.h1@text##全文阅读
```

#### 排除语法
```
class.chapter@text!0:-1  // 排除第1个和最后1个
```

#### 数组写法
```
tag.li[0:10]      // 前10个
tag.li[-1:0]      // 倒序
tag.li[!0:-1]     // 排除第1个和最后1个
```

### 3. POST请求配置

#### 简单POST格式
```
https://www.example.com/search,{"charset": "gbk", "method": "POST", "body": "keyword={{key}}&page={{page}}", "headers": {"User-Agent":"Mozilla/5.0..."}, "webView": true}
```

#### 复杂POST格式（使用JavaScript）
```
@js:
var ua = "Mozilla/5.0...";
var headers = {"User-Agent": ua};
var body = "keyword=" + String(key) + "&page=" + String(page);
var option = {"charset": "gbk", "method": "POST", "body": String(body), "headers": headers, "webView": true};
"https://www.example.com/search," + JSON.stringify(option)
```

#### 关键要点（必须遵守）
1. `body`必须保证是JavaScript的`String`类型
2. 变量是计算得到的尽量都用`String()`强转一下类型
3. `charset`为utf-8时可省略
4. 无特殊情况不需要请求头和webView
5. 参数`webView`非空时采用webView加载
6. **严格按照知识库中的格式编写，禁止编造**

### 4. 常见HTML结构及规则

#### 示例1：标准列表结构（有封面）

**HTML**：
```html
<div class="book-list">
  <div class="item">
    <img src="cover.jpg" class="cover"/>
    <a href="/book/1" class="title">书名</a>
    <p class="author">作者：张三</p>
  </div>
</div>
```

**规则**：
```json
{
  "ruleSearch": {
    "bookList": ".book-list .item",
    "name": ".title@text",
    "author": ".author@text##^作者：##",
    "bookUrl": "a@href",
    "coverUrl": ".cover@src"
  }
}
```

#### 示例2：搜索页结构（无封面，信息合并）

**HTML**：
```html
<div class="hot_sale">
  <a href="/biquge_317279/">
    <p class="title">末日成神：我的我的我的都是我的异能</p>
    <p class="author">科幻灵异 | 作者：钱真人</p>
    <p class="author">连载 | 更新：第69章 魔师</p>
  </a>
</div>
```

**规则**：
```json
{
  "ruleSearch": {
    "bookList": ".hot_sale a",
    "name": ".title@text",
    "author": ".author:first-child@text##.*作者：##",
    "kind": ".author:first-child@text##^[^|]*##",
    "lastChapter": ".author:last-child@text##.*更新：##",
    "bookUrl": "href",
    "coverUrl": ""
  }
}
```

#### 示例3：懒加载图片

**HTML**：
```html
<img class="lazy" data-original="http://example.com/cover.jpg" src="placeholder.jpg"/>
```

**规则**：
```json
{
  "coverUrl": "img.lazy@data-original||img@src"
}
```

### 5. 高级功能

#### 正则表达式
```css
/* 清理广告 */
.content@html##<script[\\s\\S]*?</script>|请收藏.*|歌书网.*com##

/* 提取特定内容 */
.info@text##.*作者：(.*?)##$1
```

#### XPath
```xpath
//div[@class='title']/text()
//a[contains(@href, '/book/')]/@href
```

#### JavaScript
```javascript
@js:
var text = document.querySelector('.content').textContent;
text.replace(/广告/g, '');
```

#### JSONPath
```jsonpath
$.data.books[*].name
$.list[?(@.price < 100)].title
```

---

## 使用指南

### 快速开始

#### 1. 查询知识

**用户输入**：
```
什么是CSS选择器中的@text和@html的区别？
```

**智能体响应**：
```
@text和@html的区别：

@text：
- 提取纯文本内容
- 自动去除HTML标签
- 保留文本格式
- 示例：<div>你好</div> → "你好"

@html：
- 提取完整HTML结构
- 保留所有HTML标签
- 保留嵌套关系
- 示例：<div>你好</div> → "<div>你好</div>"

使用场景：
- 提取书名、作者等纯文本：使用@text
- 提取正文内容（保留格式）：使用@html
```

#### 2. 查看文档

**用户输入**：
```
给我看一下CSS选择器规则的原文
```

**智能体响应**：
```
文档名称：css选择器规则.txt
文件路径：assets/css选择器规则.txt
原始内容：
CSS选择器规则

基本格式
CSS选择器@提取类型

提取类型
- @text：提取所有文本（包含子元素）
...

重点提示：
- 提取类型必须以@开头
- 正则表达式使用##分隔
- 备选选择器使用||
```

#### 3. 创建书源

**用户输入**：
```
帮我为这个网站创建书源：http://m.gashuw.com
```

**智能体工作流程**：

**第一阶段：收集信息**
```
1. 查询知识库
   - 调用get_css_selector_rules()
   - 调用search_knowledge("书源JSON结构")
   - 调用search_knowledge("POST请求配置")
   - 调用get_real_book_source_examples()
   - 调用get_book_source_templates()

2. 获取真实HTML
   - 调用smart_fetch_html(url="http://m.gashuw.com/s.php", method="POST", body="keyword={{key}}&t=1")

3. 分析HTML结构
   - 列表容器：.hot_sale a
   - 书名：.title
   - 作者：.author:first-child
   - 最新章节：.author:last-child

4. 记录信息
   - 无封面图片
   - 信息合并在.author标签中
   - 需要正则拆分
```

**第二阶段：严格审查**
```
1. 编写规则
   - 基于真实HTML结构
   - 参考笔趣阁模板
   - 使用正则拆分信息

2. 验证规则
   - 选择器格式正确
   - 提取类型正确
   - 正则表达式正确

3. 特殊处理
   - coverUrl设为空字符串
   - 使用:first-child和:last-child区分多个.author标签
```

**第三阶段：创建书源**
```
1. 准备完整JSON
   - 包含所有必需字段
   - 处理特殊情况

2. 调用工具
   - edit_book_source(complete_source="完整JSON")

3. 输出JSON
   - 输出完整JSON数组
```

**最终输出**：
```json
[
  {
    "bookSourceName": "歌书网",
    "bookSourceUrl": "http://m.gashuw.com",
    "bookSourceType": 0,
    "searchUrl": "/s.php,{\"method\":\"POST\",\"body\":\"keyword={{key}}&t=1\",\"headers\":{\"Content-Length\":\"0\"}}",
    "ruleSearch": {
      "bookList": ".hot_sale a",
      "name": ".title@text",
      "author": ".author:first-child@text##.*作者：##",
      "kind": ".author:first-child@text##^[^|]*##",
      "lastChapter": ".author:last-child@text##.*更新：##",
      "bookUrl": "href",
      "coverUrl": ""
    },
    "ruleBookInfo": {
      "name": "#info h1@text",
      "author": "#info p:nth-child(1) a@text",
      "intro": "#intro@text",
      "tocUrl": "#more@href"
    },
    "ruleToc": {
      "chapterList": "#catalog p",
      "chapterName": "a@text",
      "chapterUrl": "a@href"
    },
    "ruleContent": {
      "content": "#content@html##<div id=\"content_tip\">[\\s\\S]*?</div>|本章节未完，点击下一页继续阅读|歌书网.*com##"
    }
  }
]
```

### 高级用法

#### 1. 调试书源

**用户输入**：
```
这个书源的搜索功能不工作，帮我检查一下
```

**智能体响应**：
```
让我检查一下这个书源的问题：

1. 获取真实HTML
   - 调用smart_fetch_html获取搜索页HTML

2. 分析问题
   - 检查HTML结构与规则是否匹配
   - 检查选择器是否正确
   - 检查POST请求配置是否正确

3. 找出问题
   - 问题：author字段使用了错误的提取类型
   - 当前：.author@html
   - 应该：.author@text

4. 提供修复建议
   - 将.html改为.text
   - 添加正则表达式清理前缀
```

#### 2. 优化规则

**用户输入**：
```
这个书源的正文提取不完整，帮我优化一下
```

**智能体响应**：
```
让我分析一下正文提取的问题：

1. 获取真实HTML
   - 调用smart_fetch_html获取正文页HTML

2. 分析正文结构
   - 发现正文中有广告脚本
   - 发现有提示文本"本章节未完，点击下一页继续阅读"

3. 优化规则
   - 添加正则表达式清理广告
   - 添加正则表达式清理提示文本

4. 验证规则
   - 确保提取完整的正文内容
   - 确保不包含广告和提示文本
```

---

## 防止内容截断机制

### 问题分析

在处理大量知识文件和长文档时，经常会遇到以下问题：

1. **读取大文件被截断**：单次read_file最多只能读取200行
2. **输出内容被截断**：AI输出有长度限制
3. **知识库查询不完整**：长文档无法完全检索
4. **用户看到的内容不完整**：影响使用体验

### 解决方案

#### 1. 文件分页读取机制

**实现方式**：
```python
def read_large_file(file_path, chunk_size=200):
    """分页读取大文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        total_lines = len(lines)
        
        chunks = []
        for start in range(0, total_lines, chunk_size):
            end = min(start + chunk_size, total_lines)
            chunk = lines[start:end]
            chunks.append({
                'chunk_id': start // chunk_size + 1,
                'total_chunks': (total_lines + chunk_size - 1) // chunk_size,
                'lines': chunk,
                'start_line': start + 1,
                'end_line': end
            })
        return chunks
```

**使用方式**：
```python
# 分页读取css选择器规则.txt
chunks = read_large_file('assets/css选择器规则.txt')

for chunk in chunks:
    print(f"第{chunk['chunk_id']}/{chunk['total_chunks']}页")
    print("".join(chunk['lines']))
```

#### 2. 知识库索引系统

**实现方式**：
```python
# scripts/build_knowledge_index.py
import os
import json
from pathlib import Path

def build_knowledge_index(assets_dir='assets'):
    """构建知识库索引"""
    index = {
        'version': '1.0',
        'total_files': 0,
        'total_size': 0,
        'categories': {},
        'files': []
    }
    
    for root, dirs, files in os.walk(assets_dir):
        for file in files:
            if file.endswith(('.txt', '.md', '.json')):
                file_path = os.path.join(root, file)
                file_size = os.path.getsize(file_path)
                
                # 确定分类
                category = classify_file(file)
                
                # 提取关键词
                keywords = extract_keywords(file_path)
                
                # 添加到索引
                index['files'].append({
                    'path': file_path,
                    'name': file,
                    'size': file_size,
                    'category': category,
                    'keywords': keywords
                })
                
                # 更新统计
                index['total_files'] += 1
                index['total_size'] += file_size
                
                # 更新分类统计
                if category not in index['categories']:
                    index['categories'][category] = {
                        'count': 0,
                        'size': 0
                    }
                index['categories'][category]['count'] += 1
                index['categories'][category]['size'] += file_size
    
    # 保存索引
    with open('assets/knowledge_index.json', 'w', encoding='utf-8') as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
    
    return index
```

**索引结构**：
```json
{
  "version": "1.0",
  "total_files": 167,
  "total_size": 26168822,
  "categories": {
    "CSS选择器": {
      "count": 1,
      "size": 81920
    },
    "书源规则": {
      "count": 3,
      "size": 39936
    },
    "书源模板": {
      "count": 2,
      "size": 8192
    }
  },
  "files": [
    {
      "path": "assets/css选择器规则.txt",
      "name": "css选择器规则.txt",
      "size": 81920,
      "category": "CSS选择器",
      "keywords": ["css", "选择器", "提取类型", "正则", "语法"]
    }
  ]
}
```

#### 3. 智能搜索工具

**实现方式**：
```python
# src/tools/knowledge_index_tool.py
from langchain.tools import tool
from coze_coding_utils.runtime_ctx.context import new_context

@tool
def search_knowledge_index(query: str, category: str = "", runtime=None) -> str:
    """搜索知识库索引
    
    Args:
        query: 搜索关键词
        category: 可选，限定分类
    
    Returns:
        搜索结果（包含文件路径和摘要）
    """
    ctx = runtime.context if runtime else new_context(method="search_knowledge_index")
    
    # 加载索引
    with open('assets/knowledge_index.json', 'r', encoding='utf-8') as f:
        index = json.load(f)
    
    # 搜索文件
    results = []
    query_lower = query.lower()
    
    for file in index['files']:
        # 按分类筛选
        if category and file['category'] != category:
            continue
        
        # 按关键词匹配
        matched = False
        for keyword in file['keywords']:
            if query_lower in keyword.lower():
                matched = True
                break
        
        # 按文件名匹配
        if query_lower in file['name'].lower():
            matched = True
        
        if matched:
            results.append(file)
    
    # 格式化结果
    output = f"找到 {len(results)} 个相关文件：\n\n"
    for i, file in enumerate(results[:10], 1):  # 最多返回10个结果
        output += f"{i}. {file['name']}\n"
        output += f"   路径: {file['path']}\n"
        output += f"   分类: {file['category']}\n"
        output += f"   大小: {file['size']} bytes\n"
        output += f"   关键词: {', '.join(file['keywords'])}\n\n"
    
    return output

@tool
def get_css_selector_rules(runtime=None) -> str:
    """获取完整的CSS选择器规则（自动分页读取）"""
    ctx = runtime.context if runtime else new_context(method="get_css_selector_rules")
    
    # 分页读取文件
    chunks = read_large_file('assets/css选择器规则.txt')
    
    output = f"CSS选择器规则（共{len(chunks)}页）：\n\n"
    
    for chunk in chunks:
        output += f"=== 第{chunk['chunk_id']}/{chunk['total_chunks']}页 ===\n"
        output += "".join(chunk['lines'])
        output += "\n\n"
    
    return output

@tool
def get_real_book_source_examples(runtime=None) -> str:
    """获取真实书源示例"""
    ctx = runtime.context if runtime else new_context(method="get_real_book_source_examples")
    
    # 读取文件（分页）
    with open('assets/真实书源模板库.txt', 'r', encoding='utf-8') as f:
        content = f.read()
    
    return content
```

#### 4. 内容分段输出

**实现方式**：
```python
def output_with_pagination(content, chunk_size=4000):
    """分段输出内容"""
    total_length = len(content)
    chunks = []
    
    for start in range(0, total_length, chunk_size):
        end = min(start + chunk_size, total_length)
        chunk = content[start:end]
        chunks.append({
            'chunk_id': start // chunk_size + 1,
            'total_chunks': (total_length + chunk_size - 1) // chunk_size,
            'content': chunk,
            'is_last': end == total_length
        })
    
    return chunks
```

**使用方式**：
```python
# 输出长文档
chunks = output_with_pagination(long_content)

for chunk in chunks:
    print(f"=== 第{chunk['chunk_id']}/{chunk['total_chunks']}部分 ===")
    print(chunk['content'])
    
    if not chunk['is_last']:
        print("\n[内容未完，继续...]\n")
```

#### 5. 智能体集成

**系统提示词更新**：
```markdown
## 防止内容截断机制

### 文件读取规则
- 对于大文件（>200行），使用分页读取
- 每次读取最多200行
- 明确标注页码信息

### 知识库查询规则
- 优先使用知识库索引系统
- 使用search_knowledge_index工具搜索
- 使用get_css_selector_rules获取完整规则

### 输出规则
- 对于长内容，使用分段输出
- 明确标注分页信息
- 提供完整内容的获取方式

### 示例
```
文档名称：css选择器规则.txt
文件路径：assets/css选择器规则.txt
总页数：5页

=== 第1/5页 ===
CSS选择器规则

基本格式
CSS选择器@提取类型

...

[内容未完，回复"继续"查看下一页]
```

### 用户交互
```
用户：查看CSS选择器规则
智能体：展示第1页内容
用户：继续
智能体：展示第2页内容
...
用户：继续
智能体：展示第5页内容（最后一页）
```
```

---

## 最佳实践

### 1. 创建书源的最佳实践

#### ✅ 推荐做法

1. **必须查询知识库**
   ```python
   get_css_selector_rules()
   search_knowledge("书源JSON结构")
   get_real_book_source_examples()
   ```

2. **必须获取真实HTML**
   ```python
   smart_fetch_html(url="http://example.com/search")
   ```

3. **必须基于真实HTML分析**
   - 检查HTML结构
   - 识别关键元素
   - 处理特殊情况

4. **必须参考真实模板**
   - 查看相似书源
   - 参考常见模式
   - 使用标准格式

5. **必须严格验证**
   - 选择器语法
   - 提取类型
   - 正则表达式
   - JSON结构

#### ❌ 禁止做法

1. **不查询知识库**
   - ❌ 直接编写规则
   - ❌ 凭记忆编写
   - ❌ 编造规则

2. **不获取真实HTML**
   - ❌ 假设HTML结构
   - ❌ 使用示例HTML
   - ❌ 编造HTML

3. **不处理特殊情况**
   - ❌ 忽略无封面图片
   - ❌ 忽略懒加载
   - ❌ 忽略信息合并

4. **不参考模板**
   - ❌ 随意编写格式
   - ❌ 不遵循标准
   - ❌ 不符合规范

### 2. 知识查询的最佳实践

#### ✅ 推荐做法

1. **使用专用工具**
   ```python
   get_css_selector_rules()  # 获取CSS选择器规则
   get_real_book_source_examples()  # 获取真实书源示例
   get_book_source_templates()  # 获取书源模板
   ```

2. **使用索引搜索**
   ```python
   search_knowledge_index("POST请求配置")
   list_all_knowledge_files()
   ```

3. **分页查看大文件**
   ```
   用户：查看CSS选择器规则
   智能体：展示第1页
   用户：继续
   智能体：展示第2页
   ```

#### ❌ 禁止做法

1. **不使用专用工具**
   - ❌ 直接读取文件
   - ❌ 不使用索引

2. **不分页查看大文件**
   - ❌ 一次性读取所有内容
   - ❌ 内容被截断

### 3. 调试书源的最佳实践

#### ✅ 推荐做法

1. **获取真实HTML**
   ```python
   smart_fetch_html(url="http://example.com/search")
   ```

2. **对比规则与HTML**
   - 检查选择器是否匹配
   - 检查提取类型是否正确
   - 检查正则表达式是否正确

3. **逐步验证**
   - 先验证搜索规则
   - 再验证详情规则
   - 最后验证目录和正文规则

#### ❌ 禁止做法

1. **不获取真实HTML**
   - ❌ 假设HTML结构
   - ❌ 使用示例HTML

2. **一次性验证所有规则**
   - ❌ 难以定位问题
   - ❌ 难以修复

### 4. 防止内容截断的最佳实践

#### ✅ 推荐做法

1. **使用知识库索引**
   ```python
   search_knowledge_index("CSS选择器")
   ```

2. **使用专用工具**
   ```python
   get_css_selector_rules()  # 自动分页读取
   ```

3. **分段输出**
   - 明确标注页码
   - 提供"继续"选项
   - 完整输出所有内容

#### ❌ 禁止做法

1. **不使用索引**
   - ❌ 直接读取文件
   - ❌ 内容被截断

2. **不分页输出**
   - ❌ 一次性输出所有内容
   - ❌ 内容被截断

---

## 总结

### 项目价值

1. **提高效率**：自动化书源开发，节省80%+时间
2. **降低门槛**：让不懂CSS选择器的用户也能创建书源
3. **保证质量**：基于真实HTML和知识库规范，确保准确性
4. **持续学习**：通过分析真实书源，不断优化生成规则
5. **知识管理**：完整的知识库系统，防止内容截断

### 技术亮点

1. **知识库索引系统**：167个文件，快速搜索
2. **防止内容截断**：分页读取，分段输出
3. **三阶段工作流程**：收集信息 → 严格审查 → 创建书源
4. **真实HTML分析**：必须获取真实网页内容
5. **知识库验证**：基于134个真实书源分析结果
6. **模板参考**：使用真实书源模板确保格式正确

### 未来优化方向

1. **增加更多真实书源分析**
   - 扩展到1000+真实书源
   - 提取更多模式
   - 优化生成规则

2. **优化知识库索引**
   - 添加更多关键词
   - 优化搜索算法
   - 支持语义搜索

3. **增加更多工具**
   - 书源验证工具
   - 书源优化工具
   - 书源调试工具

4. **优化防止内容截断机制**
   - 自动分页
   - 智能分段
   - 更好的用户体验

5. **增加更多文档**
   - API文档
   - 教程
   - 示例

---

## 附录

### A. 常用工具列表

| 工具名 | 功能 | 使用场景 |
|--------|------|----------|
| `search_knowledge` | 搜索知识库 | 查询特定关键词相关内容 |
| `search_knowledge_index` | 搜索知识库索引 | 快速搜索相关知识文件 |
| `get_css_selector_rules` | 获取CSS选择器规则 | 获取完整的CSS选择器语法说明 |
| `get_real_book_source_examples` | 获取真实书源示例 | 获取真实书源参考 |
| `get_book_source_templates` | 获取书源模板 | 获取书源模板 |
| `list_all_knowledge_files` | 列出所有知识库文件 | 查看知识库整体结构 |
| `smart_fetch_html` | 获取网页HTML | 获取真实网页内容 |
| `edit_book_source` | 编辑书源 | 创建或修改书源 |

### B. 常见问题解答

#### Q1: 如何查询CSS选择器规则？
**A**: 使用 `get_css_selector_rules()` 工具，会自动分页读取完整内容。

#### Q2: 如何查看所有知识库文件？
**A**: 使用 `list_all_knowledge_files()` 工具，会列出所有文件及分类。

#### Q3: 如何防止大文件内容被截断？
**A**: 
1. 使用知识库索引系统搜索
2. 使用专用工具获取内容（自动分页）
3. 分段输出，明确标注页码

#### Q4: 如何创建书源？
**A**: 按照"完整生成模式"的三阶段工作流程：
1. 第一阶段：查询知识库，获取真实HTML，分析结构
2. 第二阶段：基于知识库和真实HTML编写规则，严格验证
3. 第三阶段：调用 edit_book_source 创建书源

#### Q5: 为什么必须获取真实HTML？
**A**: 
1. 网站结构可能变化
2. 示例HTML可能不准确
3. 只有真实HTML才能保证规则正确
4. 避免编造规则

### C. 参考资源

- **Legado GitHub**: https://github.com/gedoor/legado
- **Legado Wiki**: https://github.com/gedoor/legado/wiki
- **JSOUP 文档**: https://jsoup.org/
- **CSS 选择器**: https://www.w3schools.com/cssref/css_selectors.asp
- **XPath 教程**: https://www.w3schools.com/xml/xpath_intro.asp
- **JSONPath 教程**: https://goessner.net/articles/JsonPath/

---

**文档结束**

如有任何问题，请联系智能体进行咨询。
