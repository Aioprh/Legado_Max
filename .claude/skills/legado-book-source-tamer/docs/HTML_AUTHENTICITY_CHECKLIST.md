# HTML真实性检查清单

> **核心原则**: 编辑书源的时候一定要用最真实的HTML！
>
> **重要程度**: ⭐⭐⭐⭐⭐（最高优先级）

---

## 📋 检查清单

### ✅ 在获取HTML时，确保满足以下条件

- [ ] **使用HTTP请求获取原始HTML**
  - 不是通过浏览器复制粘贴
  - 不是使用浏览器开发者工具的"Copy HTML"
  - 而是通过requests/curl等工具直接请求

- [ ] **保存完整的HTML源代码**
  - 包含`<!DOCTYPE html>`声明
  - 包含完整的`<html>`标签
  - 包含所有注释节点
  - 包含所有隐藏元素

- [ ] **检查HTML来源**
  - HTTP响应的body
  - 不是浏览器渲染后的DOM
  - 不是JavaScript执行后的结果

- [ ] **验证HTML编码**
  - 确保使用正确的编码（如UTF-8）
  - 检查`Content-Type`头部
  - 检查HTML中的`<meta charset>`标签

- [ ] **保存HTML到文件**
  - 保存到`assets/`目录
  - 使用`.html`扩展名
  - 使用UTF-8编码保存

---

### ❌ 避免以下错误

- [ ] **不要使用浏览器开发者工具的"Copy HTML"**
  - 开发者工具显示的是美化后的HTML
  - 可能包含JavaScript动态注入的内容
  - 可能隐藏了注释节点

- [ ] **不要仅查看"Elements"标签页**
  - 这是渲染后的DOM树
  - 不是原始HTML
  - 可能误导规则编写

- [ ] **不要复制粘贴HTML代码**
  - 复制过程中可能丢失信息
  - 可能改变编码
  - 可能丢失注释

- [ ] **不要假设HTML结构**
  - 不要猜测class名称
  - 不要假设元素位置
  - 不要假设HTML格式

---

## 🔍 如何验证HTML的真实性

### 方法1: 检查HTML特征

#### 1. 检查注释节点
```html
<!-- 真实HTML应该包含注释 -->
<div class="content">
  <!-- 广告位占位 -->
  <div class="ad-placeholder"></div>
</div>
```

#### 2. 检查隐藏元素
```html
<!-- 真实HTML应该包含display:none的元素 -->
<div class="hidden" style="display:none">隐藏内容</div>
```

#### 3. 检查压缩代码
```html
<!-- 真实HTML可能是压缩的，没有换行和缩进 -->
<div class="book-info"><h1 class="title">书名</h1><p class="author">作者</p></div>
```

#### 4. 检查空白字符
```html
<!-- 真实HTML可能包含多个空格 -->
<div class="book-info    " data-id="12345">
```

### 方法2: 对比浏览器和HTTP响应

```python
import requests

# 获取HTTP响应的原始HTML
response = requests.get('https://example.com')
real_html = response.text

# 保存原始HTML
with open('assets/real.html', 'w', encoding='utf-8') as f:
    f.write(real_html)

# 打印前1000字符检查
print(real_html[:1000])
```

**检查要点**:
- 是否包含`<!DOCTYPE html>`?
- 是否包含完整的`<head>`和`<body>`?
- 是否有注释节点`<!-- -->`?
- 是否有`display:none`的元素?
- 是否是压缩的格式?

### 方法3: 使用BeautifulSoup解析

```python
from bs4 import BeautifulSoup

soup = BeautifulSoup(real_html, 'lxml')

# 检查注释节点
comments = soup.find_all(string=lambda text: isinstance(text, Comment))
print(f"注释节点数量: {len(comments)}")

# 检查隐藏元素
hidden_elements = soup.find_all(style=lambda x: x and 'display:none' in x)
print(f"隐藏元素数量: {len(hidden_elements)}")

# 检查script标签
scripts = soup.find_all('script')
print(f"script标签数量: {len(scripts)}")
```

---

## 🎯 常见错误案例

### 案例1: 动态注入的内容

#### 开发者工具显示（美化后）
```html
<div class="book-info">
  <h1 class="title">书名</h1>
  <p class="author">作者</p>
</div>
```

#### 真实HTML（HTTP响应）
```html
<div class="book-info">
  <h1 class="title"></h1>
  <p class="author"></p>
  <script>
    // JavaScript动态注入内容
    document.querySelector('.title').textContent = '书名';
    document.querySelector('.author').textContent = '作者';
  </script>
</div>
```

#### ❌ 错误的规则编写
基于开发者工具的HTML编写的规则，无法提取到内容。

#### ✅ 正确的做法
1. 获取真实HTML
2. 发现内容是通过JavaScript动态注入的
3. 启用webView或查找API接口

---

### 案例2: 注释节点

#### 真实HTML
```html
<div class="chapter-list">
  <!-- 旧版本章节列表 -->
  <!--
  <ul class="old-list">
    <li><a href="/chapter/1.html">第1章</a></li>
  </ul>
  -->
  <!-- 新版本章节列表 -->
  <ul class="new-list">
    <li><a href="/chapter/1.html">第1章</a></li>
  </ul>
</div>
```

#### ❌ 错误的规则编写
```json
"chapterList": ".chapter-list ul li a"
```
可能同时匹配到注释中的旧列表和新列表。

#### ✅ 正确的做法
```json
"chapterList": ".chapter-list .new-list li a"
```
精确定位到新列表。

---

### 案例3: 动态class名称

#### 真实HTML
```html
<div class="book-info active-20250120" data-version="1.0">
  <h1 class="title main-title current">书名</h1>
</div>
```

#### ❌ 错误的规则编写
```json
"name": ".book-info .title@text"
```
`.title`选择器可能不够精确。

#### ✅ 正确的做法
```json
"name": ".book-info .main-title@text"
```
使用更具体和稳定的class名称。

---

### 案例4: 空白字符和格式

#### 真实HTML（压缩）
```html
<div class="book-info"><h1 class="title">书名</h1><p class="author">作者</p></div>
```

#### ❌ 错误的假设
假设HTML有换行和缩进，使用基于换行的正则表达式。

#### ✅ 正确的做法
```json
"name": ".title@text##\\s+"
```
处理可能的空白字符。

---

## 📊 真实HTML vs 开发者工具HTML 对比表

| 特性 | 真实HTML | 开发者工具HTML | 影响 |
|------|---------|--------------|------|
| **来源** | HTTP响应body | 浏览器渲染后 | ⚠️ 重大差异 |
| **格式** | 原始/可能压缩 | 美化格式化 | ⚠️ 可能误导 |
| **注释** | 包含完整注释 | 可能隐藏 | ⚠️ 可能丢失信息 |
| **隐藏元素** | 包含`display:none` | 可能不显示 | ⚠️ 可能遗漏选择器 |
| **动态内容** | 仅初始加载的内容 | 包含JS注入的内容 | ⚠️ 重大差异 |
| **class/ID** | 原始名称 | 可能被动态修改 | ⚠️ 选择器失效 |
| **空格** | 保留原始空格 | 自动规范化 | ⚠️ 影响正则表达式 |
| **script标签** | 包含所有script | 可能已执行 | ⚠️ 无法提取初始状态 |

---

## 🛠️ 工具使用指南

### 1. 使用 `smart_fetch_html` 获取真实HTML

```python
# 获取真实HTML
html = smart_fetch_html(
    url="https://example.com/book/123",
    method="GET",
    headers={"User-Agent": "Mozilla/5.0"}
)

# 保存到assets目录
with open('assets/book_info.html', 'w', encoding='utf-8') as f:
    f.write(html)
```

### 2. 使用 `analyze_complete_book_source` 分析真实HTML

```python
# 基于真实HTML分析
analysis = analyze_complete_book_source(
    search_html=search_html,
    book_info_html=book_info_html,
    toc_html=toc_html,
    content_html=content_html
)
```

### 3. 使用 `debug_book_source` 验证规则

```python
# 使用真实HTML验证规则
result = debug_book_source(
    book_source_json=book_source,
    test_type="all",
    real_html=book_info_html
)
```

---

## 🎯 核心原则总结

### 🚨 永远遵守的规则

1. **使用HTTP请求获取HTML，而不是浏览器**
2. **保存完整的HTML源代码**
3. **检查HTML是否包含注释、隐藏元素**
4. **基于真实的HTML结构编写规则**
5. **使用调试工具验证规则**

### ✅ 正确的流程

```
1. smart_fetch_html() → 获取真实HTTP响应HTML
2. 保存HTML到assets目录
3. 分析真实HTML结构
4. 基于真实HTML编写规则
5. debug_book_source() → 验证规则
```

### ❌ 错误的流程

```
1. 查看浏览器开发者工具
2. 复制粘贴HTML代码
3. 基于美化后的HTML编写规则
4. 规则无法正常工作
```

---

## 📖 相关文档

- **智能体自我认知.md** - 必须基于真实HTML章节
- **SELECT_DROPDOWN_PAGINATION.md** - select下拉菜单分页
- **TOC_PAGINATION_RULES.md** - 目录页分页规则
- **ESSENTIAL_KNOWLEDGE_SUMMARY.md** - 精华知识汇总

---

## 💡 记忆口诀

```
真实HTML要牢记，
HTTP请求是前提。
浏览器工具不可信，
复制粘贴不可取。
完整保存源代码，
注释隐藏都要在。
基于实际编规则，
调试验证保无误。
```

---

> **最重要的原则**: 编辑书源的时候一定要用最真实的HTML！
>
> **任何时候**: 不要仅依赖浏览器开发者工具，必须使用HTTP请求获取的原始HTML！
