# Legado书源调试工具使用文档

## 概述

本项目实现了完整的Legado书源规则调试功能，模拟了Legado的核心规则解析逻辑。

## 功能特性

### 1. 核心规则解析（`src/tools/legado_debugger.py`）

实现了以下规则的解析：
- ✅ **Default规则**: CSS选择器@提取类型
- ✅ **XPath规则**: XPath表达式
- ✅ **JsonPath规则**: JSONPath表达式
- ✅ **正则表达式**: ##分隔符的正则替换
- ✅ **text.文本@属性**: 根据文本查找元素
- ✅ **位置索引**: 支持 .0, .-1 等索引

### 2. 书源调试器（`src/tools/book_source_debugger.py`）

支持完整的书源测试：
- ✅ 搜索规则测试
- ✅ 书籍信息规则测试
- ✅ 目录规则测试
- ✅ 正文规则测试

### 3. 便捷工具（`src/tools/legado_debug_tools.py`）

提供了便捷的调试函数：
- `debug_book_source()` - 调试书源规则
- `test_legado_rule()` - 测试单个规则
- `format_debug_result()` - 格式化调试结果
- `quick_test()` - 快速测试规则

### 4. LangChain工具（`src/tools/legado_tools.py`）

为智能体提供的工具：
- `debug_legado_book_source` - 调试书源
- `test_legado_rule` - 测试规则
- `validate_legado_rules` - 验证书源JSON

## 使用方法

### 基础使用

```python
import sys
sys.path.insert(0, 'src')

from tools.legado_debug_tools import test_legado_rule, quick_test

# 准备HTML内容
html = """
<div class="book">
    <h1 class="title">斗破苍穹</h1>
    <p class="author">作者：天蚕土豆</p>
    <div class="nav">
        <a href="/chapter/1_2.html">下一页</a>
        <a href="/chapter/2.html">下一章</a>
    </div>
</div>
"""

# 测试CSS选择器
result = test_legado_rule(html, ".title@text")
print(result)  # {'success': True, 'singleResult': '斗破苍穹', ...}

# 快速测试
print(quick_test(html, "text.下一章@href"))
```

### 调试书源

```python
from tools.legado_debug_tools import debug_book_source, format_debug_result

# 书源JSON
book_source_json = '[{"bookSourceName": "测试书源", "bookSourceUrl": "https://example.com", ...}]'

# 完整测试
result = debug_book_source(book_source_json, "斗破苍穹", "full")
print(format_debug_result(result))

# 只测试搜索
result = debug_book_source(book_source_json, "斗破苍穹", "search")
print(format_debug_result(result))
```

### 在智能体中使用

```python
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI
from tools.legado_tools import debug_legado_book_source, test_legado_rule

# 创建智能体
agent = create_agent(
    model=ChatOpenAI(...),
    tools=[debug_legado_book_source, test_legado_rule],
    system_prompt="你是Legado书源驯兽师，帮助用户调试书源规则。"
)

# 使用智能体
response = agent.invoke({
    "messages": [
        ("user", "请帮我调试这个书源: [...]")
    ]
})
```

## 支持的规则格式

### Default规则

```
CSS选择器@提取类型
```

**提取类型**:
- `@text` - 提取文本
- `@html` - 提取HTML
- `@href` - 提取链接地址
- `@src` - 提取图片地址
- `@ownText` - 只提取当前元素文本
- `@textNodes` - 提取文本节点

**示例**:
```python
test_legado_rule(html, ".title@text")           # 提取文本
test_legado_rule(html, "a@href")                 # 提取链接
test_legado_rule(html, "img@src")                # 提取图片
```

### text.文本@属性

```
text.文本@属性
```

**示例**:
```python
test_legado_rule(html, "text.下一章@href")      # 查找"下一章"并提取href
test_legado_rule(html, "text.下一@href")        # 查找"下一"并提取href
```

### 位置索引

```
CSS选择器.索引@提取类型
```

**索引规则**:
- `.0` - 第一个元素
- `.1` - 第二个元素
- `.-1` - 倒数第一个元素
- `.-2` - 倒数第二个元素

**示例**:
```python
test_legado_rule(html, ".title.0@text")         # 第一个标题
test_legado_rule(html, ".title.-1@text")        # 最后一个标题
```

### 正则表达式

```
CSS选择器@提取类型##正则表达式##替换内容
```

**示例**:
```python
# 删除前缀
test_legado_rule(html, ".author@text##.*作者：##")

# 替换文本
test_legado_rule(html, ".content@text##广告##")

# 捕获组
test_legado_rule(html, ".author@text##作者：(.*?)##$1")
```

## 测试结果

已验证以下功能：
- ✅ CSS选择器解析
- ✅ text.文本@属性格式
- ✅ 位置索引处理
- ✅ 正则表达式替换
- ✅ 列表提取

## 注意事项

1. **仅在调试时使用**: 这些工具仅用于书源开发和调试，不应在生产环境中使用
2. **网络请求**: 调试书源时需要网络连接，某些网站可能有反爬机制
3. **依赖库**: 需要安装 `beautifulsoup4`, `lxml`, `jsonpath-ng`

## 安装依赖

```bash
pip install beautifulsoup4 lxml jsonpath-ng requests
```

## 文件结构

```
src/tools/
├── legado_debugger.py          # 核心规则解析器
├── book_source_debugger.py     # 书源调试器
├── legado_debug_tools.py       # 便捷工具函数
└── legado_tools.py             # LangChain工具

tests/
└── test_legado_tools.py        # 测试脚本
```

## 扩展开发

如需添加新的规则支持，可以修改 `legado_debugger.py` 中的相关方法。

## 许可证

本工具基于Legado项目（https://github.com/gedoor/legado）开发，遵循GPL-3.0许可证。
