# 智能分析功能使用指南

## 🎯 新增智能分析工具

### 1. 综合分析工具

#### `analyze_complete_book_source` - 完整书源分析
**功能**: 一站式分析完整书源，整合所有页面分析结果

**参数**:
- `search_url`: 搜索页URL
- `book_info_url`: 书籍详情页URL（可选）
- `toc_url`: 目录页URL（可选）
- `content_url`: 正文页URL（可选）

**使用场景**:
- 当用户需要创建完整书源时
- 当用户提供了多个页面URL时
- 需要全面分析网站结构时

**示例**:
```
analyze_complete_book_source(
    search_url="https://example.com/search",
    book_info_url="https://example.com/book/123",
    toc_url="https://example.com/book/123/",
    content_url="https://example.com/chapter/456"
)
```

---

### 2. 页面分析工具

#### `analyze_book_info_page` - 书籍详情页分析
**功能**: 智能分析书籍详情页，识别书名、作者、封面、简介等所有关键元素

**参数**:
- `url`: 书籍详情页URL
- `book_url`: 书籍URL模板（可选）

**识别内容**:
- ✅ 书名（Top 3候选）
- ✅ 作者（Top 3候选）
- ✅ 封面图片（Top 3候选）
- ✅ 简介（Top 3候选）
- ✅ 目录链接（Top 3候选）
- ✅ 最新章节（Top 3候选）
- ✅ 状态（连载/完结）

**输出内容**:
- 完整HTML源代码
- 所有元素的识别结果（含置信度）
- 推荐的CSS选择器
- 推荐的正则表达式
- 生成的ruleBookInfo规则
- 缺失字段提示

**使用场景**:
```
analyze_book_info_page(url="https://example.com/book/123")
```

---

#### `analyze_toc_page` - 目录页分析
**功能**: 智能分析目录页，识别章节列表结构

**参数**:
- `url`: 目录页URL

**识别内容**:
- ✅ 章节列表容器（Top 3候选）
- ✅ 章节项选择器（Top 2候选）
- ✅ 分卷信息（Top 3候选）
- ✅ 分页信息（Top 2候选）
- ✅ 章节名称选择器
- ✅ 章节链接选择器
- ✅ 更新时间

**输出内容**:
- 完整HTML源代码
- 章节列表结构分析
- 推荐的CSS选择器
- 生成的ruleToc规则
- 分页处理建议

**使用场景**:
```
analyze_toc_page(url="https://example.com/book/123/")
```

---

#### `analyze_content_page` - 正文页分析
**功能**: 智能分析正文页，识别正文内容结构

**参数**:
- `url`: 正文页URL

**识别内容**:
- ✅ 正文内容容器（Top 3候选）
- ✅ 段落结构（Top 2候选）
- ✅ 正文中的图片
- ✅ 导航链接（上一章、下一章、目录）
- ✅ 章节标题（Top 3候选）

**输出内容**:
- 完整HTML源代码
- 正文结构分析
- 推荐的CSS选择器
- 推荐的正则表达式（处理广告、多余换行等）
- 生成的ruleContent规则
- 图片处理建议

**使用场景**:
```
analyze_content_page(url="https://example.com/chapter/456")
```

---

### 3. 结构分析工具

#### `analyze_book_structure` - 书籍结构分析
**功能**: 智能分析网站URL模式，推测各种页面的URL模板

**参数**:
- `website_url`: 网站基础URL
- `book_id`: 书籍ID（可选）

**输出内容**:
- 搜索页URL模式推测
- 书籍详情页URL模式推测
- 目录页URL模式推测
- 正文页URL模式推测
- 推荐的URL模板

**使用场景**:
```
analyze_book_structure(
    website_url="https://example.com",
    book_id="123"
)
```

---

## 🔄 工作流程更新

### 完整书源分析流程

**第一阶段：智能分析**
1. 调用 `analyze_book_structure` 分析网站URL模式
2. 调用 `search_knowledge` 查询知识库，了解规则规范
3. 获取用户提供的关键页面URL（搜索页、详情页、目录页、正文页）

**第二阶段：页面分析**
1. 调用 `analyze_book_info_page` 分析书籍详情页
2. 调用 `analyze_toc_page` 分析目录页
3. 调用 `analyze_content_page` 分析正文页

**第三阶段：规则生成**
1. 根据所有分析结果，生成完整的书源规则
2. 验证规则语法
3. 调用 `edit_book_source` 创建完整书源

---

## 💡 使用建议

### 1. 获取真实HTML

所有智能分析工具都会：
- ✅ 获取页面的真实HTML
- ✅ 使用正确的编码
- ✅ 保存完整的HTML结构
- ✅ 提供HTML源代码供查看

### 2. 智能识别

分析工具会自动：
- ✅ 识别页面中的关键元素
- ✅ 为每个元素提供多个候选选择器
- ✅ 计算置信度，推荐最佳选择
- ✅ 识别缺失的字段

### 3. 规则推荐

基于真实HTML分析：
- ✅ 推荐CSS选择器
- ✅ 推荐正则表达式
- ✅ 生成完整的规则JSON
- ✅ 提供使用建议

### 4. 缺失处理

当无法识别某些字段时：
- ⚠️ 明确标注缺失字段
- 💡 提供手动检查建议
- 📝 建议查看HTML源代码

---

## 🎯 最佳实践

### 完整分析流程

**步骤1：分析网站结构**
```
analyze_book_structure(
    website_url="https://example.com",
    book_id="123"
)
```

**步骤2：分析各个页面**
```
analyze_book_info_page(url="https://example.com/book/123")
analyze_toc_page(url="https://example.com/book/123/")
analyze_content_page(url="https://example.com/chapter/456")
```

**步骤3：整合结果**
根据所有分析结果，生成完整的书源规则。

---

## 🔧 工具优先级

在完整生成模式中，工具调用优先级：

1. **第一优先级**：
   - `analyze_complete_book_source` - 综合分析
   - `analyze_book_structure` - 结构分析

2. **第二优先级**：
   - `analyze_book_info_page` - 详情页分析
   - `analyze_toc_page` - 目录页分析
   - `analyze_content_page` - 正文页分析

3. **第三优先级**：
   - `search_knowledge` - 知识库查询
   - `smart_analyze_website` - 网站分析
   - `edit_book_source` - 书源创建

---

## 📊 输出格式

### 标准输出结构

每个分析工具都会输出：

```markdown
## 📊 分析报告

### 📍 页面信息
- URL
- 状态
- 编码
- 大小

### 🔍 元素识别结果
- 元素1（Top 3）
  - 候选1
  - 候选2
  - 候选3
- 元素2（Top 3）
  - ...

### ⚠️ 缺失字段
- 缺失字段1
- 缺失字段2
- ...

### 🎯 推荐规则
```js
{
  "field1": "selector1@type",
  "field2": "selector2@type##regex##replace"
}
```

### 📄 完整HTML
```html
<!-- 完整的HTML源代码 -->
```

### 💡 使用建议
1. 验证选择器
2. 调整正则
3. 处理缺失字段
4. 测试完整流程
```

---

## 🚀 快速开始

### 示例1：分析单个页面

```
用户：帮我分析这个详情页 https://example.com/book/123

Agent：
1. 调用 analyze_book_info_page(url="https://example.com/book/123")
2. 分析结果返回：
   - 书名识别：3个候选，置信度0.95
   - 作者识别：3个候选，置信度0.88
   - 封面识别：3个候选，置信度0.92
   - 简介识别：3个候选，置信度0.85
   - 推荐规则：{"name": "h1@text", "author": ".author@text", ...}
```

### 示例2：完整书源分析

```
用户：创建这个网站的书源 https://example.com

Agent：
1. 调用 search_knowledge 查询知识库
2. 调用 analyze_book_structure 分析网站结构
3. 获取关键页面URL
4. 调用 analyze_book_info_page, analyze_toc_page, analyze_content_page
5. 整合所有分析结果
6. 生成完整书源规则
7. 调用 edit_book_source 创建书源
```

---

## 📚 知识库参考

所有智能分析工具都会参考：

- `assets/legado_knowledge_base.md` - 核心数据结构
- `assets/css选择器规则.txt` - CSS选择器语法
- `assets/书源规则：从入门到入土.md` - 书源规则说明

---

## ✅ 检查清单

使用智能分析工具前，请确认：

- [ ] URL可以正常访问
- [ ] 网站没有复杂的反爬机制
- [ ] 页面结构相对标准
- [ ] 愿意查看HTML源代码以验证结果

---

**智能分析工具旨在帮助用户快速分析网站结构，生成准确的书源规则！** 🎉
