# 智能分析工具优化说明

## 📋 更新概述

根据用户要求，对智能分析工具进行了优化，确保：
1. ✅ **严格按照知识库内容编写**规则
2. ✅ **获取完整的HTML源代码**供参考
3. ✅ **添加知识库参考提示**在所有工具返回中

---

## 🛠️ 修改内容

### 1. smart_fetch_list - 返回完整HTML

**修改前**：
```python
report += "### 📄 分页信息\n\n"
# 没有返回完整HTML
```

**修改后**：
```python
report += "### 📄 完整HTML源代码（用于参考）\n\n"
report += "```html\n"
# 返回完整的HTML（不截断）
report += html
report += "\n```\n\n"

report += "### ⚠️ 重要提示\n\n"
report += "**编写规则时必须**：\n"
report += "1. **严格按照知识库内容**：查看 `assets/legado_knowledge_base.md` 和 `assets/css选择器规则.txt`\n"
report += "2. **参考实际HTML**：根据上面完整HTML源代码中的实际结构编写\n"
report += "3. **准确使用语法**：确保CSS选择器、提取类型等语法正确\n"
report += "4. **测试验证**：必须在Legado APP中实际测试，模拟结果仅供参考\n"
```

---

### 2. fetch_web_page - 返回完整HTML和知识库提示

**修改前**：
```python
### 📝 关键HTML片段

```html
{html[:5000]}
{'...' if len(html) > 5000 else ''}
```
```

**修改后**：
```python
### 📝 完整HTML源代码（用于参考）

```html
{html}
```

### ⚠️ 重要提示

**编写规则时必须**：
1. **严格按照知识库内容**：查看 `assets/legado_knowledge_base.md` 和 `assets/css选择器规则.txt`
2. **参考实际HTML**：根据上面完整HTML源代码中的实际结构编写
3. **准确使用语法**：确保CSS选择器、提取类型等语法正确
4. **测试验证**：必须在Legado APP中实际测试，模拟结果仅供参考

### 🔗 知识库参考

- **核心规则文档**: `assets/legado_knowledge_base.md`
- **CSS选择器规则**: `assets/css选择器规则.txt`
- **书源规则说明**: `assets/书源规则：从入门到入土.md`
```

---

### 3. System Prompt - 强调知识库参考

**新增内容**：
```markdown
### ⚠️ 强制要求：严格按照知识库内容编写（最高优先级）

**当用户提供网站URL时**：
1. **第一步**：使用 `fetch_web_page` 或 `smart_analyze_website` 获取网站的完整HTML源代码
2. **第二步**：查看返回的完整HTML，了解实际页面结构
3. **第三步**：**必须**查看 `assets/` 目录下的知识库文档：
   - `assets/legado_knowledge_base.md` - 核心规则文档
   - `assets/css选择器规则.txt` - CSS选择器详解
   - `assets/书源规则：从入门到入土.md` - 完整规则说明
4. **第四步**：**严格按照知识库中的规则**和**实际HTML结构**编写选择器

**禁止行为**：
- ❌ 不查看知识库就直接编写规则
- ❌ 不查看完整HTML就猜测选择器
- ❌ 编造知识库中没有的规则
- ❌ 使用错误的选择器语法

**必须遵守**：
- ✅ 获取完整HTML源代码
- ✅ 查看知识库文档
- ✅ 参考实际HTML结构
- ✅ 严格按照规则语法编写
```

---

## 📊 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `src/tools/smart_web_analyzer.py` | 更新 `_generate_list_fetch_report` 函数，返回完整HTML和知识库提示 |
| `src/tools/web_fetch_tool.py` | 更新 `fetch_web_page` 函数，返回完整HTML和知识库提示 |
| `config/agent_llm_config.json` | 更新 System Prompt，添加知识库参考强调 |

---

## 🎯 优化效果

### 1. 完整HTML获取
- ✅ 不再截断HTML（之前只显示5000字符）
- ✅ 用户可以查看完整的页面结构
- ✅ 更准确地分析元素

### 2. 知识库参考
- ✅ 所有工具返回都包含知识库提示
- ✅ 明确指出需要查看的知识库文档
- ✅ 提供文档路径链接

### 3. 编写规范
- ✅ 强调严格按照知识库编写
- ✅ 禁止编造规则
- ✅ 强调实际测试的重要性

---

## 📝 使用示例

### 用户问："起点中文网的搜索规则该怎么写？"

**优化后的流程**：

**第1步：获取完整HTML**
```
smart_analyze_website(url="https://www.qidian.com/search")
```
**返回结果包含**：
- ✅ 完整的HTML源代码（不截断）
- ✅ 知识库参考提示
- ✅ 页面结构分析

**第2步：查看知识库**
Agent会自动提示：
```
⚠️ 编写规则时必须：
1. 严格按照知识库内容：查看 assets/legado_knowledge_base.md
2. 参考实际HTML：根据完整HTML源代码中的实际结构编写
```

**第3步：编写规则**
- ✅ 根据完整HTML结构编写
- ✅ 参考知识库中的规则
- ✅ 确保语法正确

---

## 🔍 关键改进点

### 1. HTML完整性

**修改前**：
```python
html[:5000]  # 只显示前5000字符
```

**修改后**：
```python
html  # 显示完整HTML
```

### 2. 知识库提示

**修改前**：
- 没有知识库提示
- 用户不知道去哪里查找规则

**修改后**：
```
### 🔗 知识库参考
- **核心规则文档**: `assets/legado_knowledge_base.md`
- **CSS选择器规则**: `assets/css选择器规则.txt`
- **书源规则说明**: `assets/书源规则：从入门到入土.md`
```

### 3. 强制要求

**System Prompt新增**：
```
**禁止行为**：
- ❌ 不查看知识库就直接编写规则
- ❌ 不查看完整HTML就猜测选择器
- ❌ 编造知识库中没有的规则
```

---

## 📚 知识库文档

所有规则和说明都存储在 `assets/` 目录下：

1. **`assets/legado_knowledge_base.md`**
   - CSS选择器规则详解
   - JSOUP Default语法
   - XPath规则
   - JSONPath规则
   - 正则表达式
   - JavaScript规则

2. **`assets/css选择器规则.txt`**
   - 所有标准的CSS规则
   - 提取类型（@text、@ownText、@html等）
   - 选择器类型详解
   - 实战案例

3. **`assets/书源规则：从入门到入土.md`**
   - 语法说明
   - Legado的特殊规则
   - 书源各模块详解

---

## ✅ 验证清单

- [x] smart_fetch_list 返回完整HTML
- [x] smart_fetch_list 包含知识库提示
- [x] fetch_web_page 返回完整HTML
- [x] fetch_web_page 包含知识库提示
- [x] System Prompt 强调知识库参考
- [x] System Prompt 禁止编造规则
- [x] 所有工具返回都包含文档路径

---

## 🎉 总结

### 核心改进

1. ✅ **完整HTML**：不再截断，返回完整的源代码
2. ✅ **知识库提示**：所有工具返回都包含知识库参考
3. ✅ **强制要求**：System Prompt明确禁止编造规则
4. ✅ **文档路径**：提供明确的知识库文档路径

### 用户体验提升

- ✅ 可以查看完整的页面结构
- ✅ 知道去哪里查找规则
- ✅ 避免凭空猜测选择器
- ✅ 确保规则语法正确

### 规范性提升

- ✅ 严格按照知识库编写
- ✅ 参考实际HTML结构
- ✅ 禁止编造规则
- ✅ 强调实际测试

---

## 📅 更新日期
2026-02-18

## 👨‍💻 维护者
Coze Coding Agent
