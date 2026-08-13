# POST请求失败后自动尝试GET功能说明

## 功能概述

MCP服务器现在支持智能请求降级功能：当POST请求失败时，系统会自动尝试使用GET请求来获取内容。这大大提高了请求的成功率，特别是在处理不同网站时。

## 功能特点

### 1. 自动降级机制
- 当POST请求失败（超时、连接错误、SSL错误等）时，系统会自动将POST参数转换为GET参数
- 自动重试GET请求，无需手动干预
- 保留原始的编码设置（如GBK、UTF-8等）

### 2. 参数转换
- POST请求体中的数据会自动转换为URL参数
- 支持字典、字符串等多种数据格式
- 正确处理已编码的参数

### 3. 编码支持
- 支持GBK、UTF-8等多种编码
- 自动检测网站编码
- 确保中文参数正确传输

## 使用示例

### 示例1：基本使用

```python
from utils.smart_request import get_smart_request

requester = get_smart_request()

# 尝试POST请求
result = requester.fetch(
    url="https://example.com/search",
    method="POST",
    data={"keyword": "我的", "type": 1},
    url_charset="gbk"
)

# 如果POST失败，会自动尝试GET请求
# 结果中会包含 method_fallback 字段标记降级
if 'method_fallback' in result:
    print(f"请求方法已降级: {result['method_fallback']}")
```

### 示例2：MCP工具调用

```json
{
  "url": "https://m.bqg5.com/s.php",
  "method": "POST",
  "data": {
    "keyword": "斗破苍穹",
    "t": 1
  },
  "url_charset": "gbk"
}
```

如果POST请求失败，系统会自动转换为：
```
https://m.bqg5.com/s.php?keyword=斗破苍穹&t=1
```

## 工作流程

```
用户发起POST请求
    ↓
尝试POST请求（最多重试3次）
    ↓
POST请求成功？
    ├─ 是 → 返回结果
    └─ 否 → 自动转换为GET请求
              ↓
         尝试GET请求
              ↓
         GET请求成功？
              ├─ 是 → 返回结果（标记method_fallback）
              └─ 否 → 返回失败信息
```

## 返回结果说明

### 成功结果
```json
{
  "success": true,
  "status_code": 200,
  "method": "POST",  // 或 "GET"
  "method_fallback": "POST->GET",  // 如果发生了降级
  "encoding": "gbk",
  "html": "...",
  "size": 32586
}
```

### 失败结果
```json
{
  "success": false,
  "error": "请求超时（30秒）",
  "url": "https://example.com/search",
  "method": "POST"
}
```

## 技术细节

### 1. 参数合并逻辑
```python
# 合并所有参数来源
get_params = {}

if params:
    get_params.update(params)
if data and isinstance(data, dict):
    get_params.update(data)
if encoded_params:
    # 解析已编码的参数
    parsed = parse_qs(encoded_params)
    for key, values in parsed.items():
        if values:
            get_params[key] = values[0]
```

### 2. 编码处理
- 保留原始的 `url_charset` 和 `charset` 参数
- GET请求的URL参数使用相同的编码
- 确保中文参数正确传输

### 3. 调试信息
系统会输出详细的调试信息：
```
[🔄] POST请求失败，尝试使用GET请求...
[🔄] 将POST参数转换为GET参数
[🔄] 发送GET请求: https://example.com/search
[🔄] GET参数: {'keyword': '我的', 'type': 1}
[✅] GET请求成功！
```

## 适用场景

### 推荐使用场景
1. 不确定网站支持POST还是GET
2. 网站可能根据请求方法返回不同内容
3. 需要最大程度保证请求成功率
4. 处理GBK编码的中文网站

### 注意事项
1. 某些网站可能只支持POST或只支持GET
2. POST和GET请求可能返回不同的结果
3. 敏感数据（如密码）不应使用GET请求
4. GET请求有URL长度限制

## 测试

运行测试脚本验证功能：

```bash
python test_post_to_get_fallback.py
```

测试内容包括：
1. POST请求（带中文参数）
2. GET请求（带中文参数）
3. GBK编码处理

## 相关文件

- `src/utils/smart_request.py` - 核心请求逻辑
- `src/tools/smart_fetcher.py` - MCP工具封装
- `test_post_to_get_fallback.py` - 测试脚本

## 更新日志

### 2026-02-20
- 新增POST请求失败后自动尝试GET的功能
- 支持参数自动转换
- 保留编码设置
- 添加调试信息输出
