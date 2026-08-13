# 编码检测优先级更新说明

## 📝 更新概述

为了优化智能体的工作流程，确保在获取 HTML 之前先检测网站编码，并且编码只需要检测一次。

## 🔄 更新内容

### 1. 系统提示词更新

**文件：** `config/system_prompt.md`

#### 新增步骤 2：检测网站编码

**原工作流程：**
```
1. 查询知识库
2. 获取真实 HTML
3. 分析 HTML 结构
```

**新工作流程：**
```
1. 查询知识库
2. 检测网站编码（detect_charset）⭐ 新增
3. 查询真实书源分析结果
4. 查询真实书源模板
5. 获取真实 HTML（使用步骤2检测到的编码）⭐ 修改
6. 分析 HTML 结构
```

#### 更新的位置

1. **第一阶段：收集信息**
   - 新增步骤 2：检测网站编码
   - 更新步骤 3：获取真实 HTML（添加编码参数说明）

2. **知识库查询优先级**
   - 添加 `detect_charset()` 到必查工具列表（高优先级）

3. **完整生成模式**
   - 更新步骤顺序，将编码检测插入到第二步

4. **绝对禁止**
   - 新增：不调用 `detect_charset` 检测网站编码就获取 HTML

5. **新增章节：网站编码检测和处理**
   - 详细的编码检测规则
   - 配置示例
   - 常见编码类型
   - 必要性说明
   - 绝对禁止的行为

## 🎯 核心原则

### 编码检测的三原则

1. **编码只需要检测一次**
   - 在流程开始时检测
   - 后续所有操作都使用这个编码
   - 避免重复检测

2. **检测结果必须记录**
   - 记录检测到的编码类型（UTF-8、GBK等）
   - 在后续所有工具调用中使用

3. **编码信息必须传递**
   - 在 `smart_fetch_html` 调用时传递编码参数
   - 在书源配置中正确设置 charset

## 📋 使用示例

### 正确的工作流程

```javascript
// 步骤 1：查询知识库
search_knowledge("CSS选择器规则")

// 步骤 2：检测网站编码 ⭐
detect_charset(url="https://www.69shuba.com")
// 结果：{"charset": "gbk", "confidence": 0.95}

// 步骤 3：获取真实 HTML（使用检测到的编码）⭐
smart_fetch_html(
    url="https://www.69shuba.com/modules/article/search.php",
    method="POST",
    body="searchkey=斗破苍穹&searchtype=all",
    charset="gbk"  // ⭐ 使用步骤 2 检测到的编码
)

// 步骤 4：分析 HTML 结构
// ...

// 步骤 5：创建书源（使用检测到的编码）
{
  "searchUrl": "/modules/article/search.php,{\"method\":\"POST\",\"body\":\"searchkey={{key}}&searchtype=all\",\"charset\":\"gbk\"}"
  // ⭐ charset="gbk" 来自步骤 2
}
```

### 错误的工作流程

```javascript
// ❌ 错误：未检测编码就直接获取 HTML
smart_fetch_html(url="https://www.69shuba.com/modules/article/search.php")
// 可能导致：HTML 内容乱码

// ❌ 错误：多次检测编码（浪费资源）
detect_charset(url="https://www.69shuba.com")
detect_charset(url="https://www.69shuba.com")  // 重复！

// ❌ 错误：检测到编码但未使用
detect_charset(url="https://www.69shuba.com")  // 结果：gbk
smart_fetch_html(url="https://www.69shuba.com")  // 未传递 charset！
```

## 🔧 工具调用示例

### detect_charset 调用

```javascript
// 通过 URL 检测编码
detect_charset(url="https://www.example.com")

// 返回结果
{
  "url": "https://www.example.com",
  "charset": "gbk",
  "confidence": 0.95,
  "source": "headers",
  "details": {
    "headers": "gbk",
    "meta": "gbk",
    "content": {"charset": "gbk", "confidence": 0.99},
    "characters": {"charset": "gbk", "confidence": 0.85}
  },
  "recommendation": {
    "charset_config": "\"gbk\"",
    "usage_example": "/search,{\"charset\":\"gbk\"}"
  }
}
```

### smart_fetch_html 调用（使用检测到的编码）

```javascript
// GET 请求（使用检测到的编码）
smart_fetch_html(
    url="https://www.example.com/search",
    charset="gbk"  // 来自 detect_charset 的结果
)

// POST 请求（使用检测到的编码）
smart_fetch_html(
    url="https://www.example.com/search.php",
    method="POST",
    body="keyword={{key}}&page=1",
    headers={"Content-Type": "application/x-www-form-urlencoded"},
    charset="gbk"  // 来自 detect_charset 的结果
)
```

## ✅ 验证清单

在创建书源时，检查以下项目：

- [ ] 在获取 HTML 之前调用了 `detect_charset`
- [ ] 记录了检测到的编码类型
- [ ] 在 `smart_fetch_html` 调用中使用了检测到的编码
- [ ] 在书源配置中正确设置了 charset 参数
- [ ] 没有重复调用 `detect_charset`
- [ ] 编码信息在整个流程中保持一致

## 📊 对比总结

| 项目 | 更新前 | 更新后 |
|------|--------|--------|
| 编码检测 | 未强制要求 | 必须在获取 HTML 前执行 |
| 检测次数 | 可能多次 | 只检测一次 |
| 编码使用 | 可能不一致 | 全程使用统一编码 |
| 乱码问题 | 常见 | 避免 |
| 效率 | 低（重复检测） | 高（一次检测） |

## 🎯 优势

1. **避免乱码**：正确使用编码，确保中文内容正常显示
2. **提高效率**：只需检测一次，节省时间和资源
3. **确保一致性**：整个流程使用统一编码，避免混淆
4. **提升体验**：用户能够正常阅读内容

## 📚 相关文档

- `assets/Legado书源编码处理指南.md` - 编码处理完整指南
- `assets/编码检测工具使用指南.md` - 编码检测工具使用说明
- `config/system_prompt.md` - 系统提示词（已更新）

## 🔗 工具优先级

**高优先级工具（第一阶段必须调用）**：
1. `search_knowledge()` - 查询知识库
2. `detect_charset()` - ⭐ 检测网站编码（新增）
3. `get_css_selector_rules()` - 获取 CSS 选择器规则
4. `get_real_book_source_examples()` - 获取真实书源示例
5. `get_book_source_templates()` - 获取书源模板
6. `smart_fetch_html()` - 获取真实 HTML（使用检测到的编码）

## 🚨 重要提醒

**绝对禁止**：
1. ❌ 不调用 `detect_charset` 检测网站编码就获取 HTML
2. ❌ 在流程中多次调用 `detect_charset`
3. ❌ 检测到编码后不在后续请求中使用
4. ❌ 忽略检测结果，使用错误的编码配置

**必须遵守**：
1. ✅ 在获取 HTML 之前先调用 `detect_charset`
2. ✅ 记录检测结果（UTF-8 或 GBK）
3. ✅ 在后续所有工具调用中使用检测到的编码
4. ✅ 在书源配置中正确设置 charset 参数
