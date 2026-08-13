# MCP工具编码使用指南

## 问题说明

对于使用非UTF-8编码的网站（如GBK编码的中文网站），在发送搜索请求时，必须确保URL参数和POST数据的编码与网站一致，否则搜索功能无法正常工作。

## 解决方案

MCP工具 `fetch_html` 提供了 `url_charset` 参数，用于指定URL参数和POST数据的编码。

### 使用方法

#### 1. POST请求（表单提交）

```json
{
  "url": "https://m.bqg5.com/s.php",
  "method": "POST",
  "data": "{\"keyword\": \"我的\", \"t\": 1}",
  "url_charset": "gbk"
}
```

**参数说明：**
- `url`: 请求URL
- `method`: HTTP方法（POST）
- `data`: POST数据（JSON字符串格式）
- `url_charset`: URL参数编码（对于GBK网站使用 "gbk"）

#### 2. GET请求（URL参数）

```json
{
  "url": "https://example.com/search",
  "method": "GET",
  "params": "{\"keyword\": \"关键词\"}",
  "url_charset": "gbk"
}
```

**参数说明：**
- `params`: URL参数（JSON字符串格式）
- `url_charset`: URL参数编码

### 常见编码

| 编码 | 说明 | 适用网站 |
|------|------|----------|
| `gbk` | 中文GBK编码 | 大多数中文小说网站 |
| `utf-8` | UTF-8编码 | 现代网站（默认） |
| `big5` | 繁体中文编码 | 台湾/香港网站 |

### 实际示例

#### 示例1：bqg5.com搜索（GBK编码）

```json
{
  "url": "https://m.bqg5.com/s.php",
  "method": "POST",
  "data": "{\"keyword\": \"我的\", \"t\": 1}",
  "url_charset": "gbk"
}
```

**编码后的实际请求：**
```
POST /s.php HTTP/1.1
Host: m.bqg5.com
Content-Type: application/x-www-form-urlencoded

keyword=%CE%D2%B5%C4&t=1
```

其中 `%CE%D2%B5%C4` 是 "我的" 的GBK编码的URL编码。

#### 示例2：UTF-8网站搜索

```json
{
  "url": "https://example.com/search",
  "method": "POST",
  "data": "{\"keyword\": \"关键词\"}",
  "url_charset": "utf-8"
}
```

**编码后的实际请求：**
```
POST /search HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

keyword=%E5%85%B3%E9%94%AE%E8%AF%8D
```

### 如何确定网站编码

#### 方法1：查看HTTP响应头

使用浏览器开发者工具查看响应头中的 `Content-Type`：

```
Content-Type: text/html; charset=gbk
```

#### 方法2：查看HTML meta标签

```html
<meta charset="gbk">
```

或

```html
<meta http-equiv="Content-Type" content="text/html; charset=gbk">
```

#### 方法3：使用MCP工具自动检测

MCP工具会自动检测响应编码，并在结果中显示：

```
编码: gbk
```

### 注意事项

1. **必须指定正确的编码**：如果网站使用GBK编码，必须设置 `url_charset: "gbk"`
2. **响应编码 vs URL参数编码**：
   - `charset` 参数：指定响应内容的编码（用于解码HTML）
   - `url_charset` 参数：指定URL参数和POST数据的编码（用于编码请求）
3. **默认编码**：如果不指定 `url_charset`，默认使用UTF-8编码

### 故障排查

#### 问题：搜索结果为空或乱码

**可能原因：**
- 编码设置不正确
- 网站实际编码与设置不一致

**解决方法：**
1. 检查网站的实际编码
2. 设置正确的 `url_charset` 参数
3. 查看MCP工具返回的编码信息

#### 问题：请求成功但无搜索结果

**可能原因：**
- 关键词编码错误
- 网站需要特定的请求头或Cookie

**解决方法：**
1. 使用浏览器开发者工具查看实际请求
2. 对比MCP工具的请求参数
3. 添加必要的headers和cookies

### 完整示例

```json
{
  "url": "https://m.bqg5.com/s.php",
  "method": "POST",
  "data": "{\"keyword\": \"我的\", \"t\": 1}",
  "headers": "{\"Content-Type\": \"application/x-www-form-urlencoded\", \"User-Agent\": \"Mozilla/5.0\"}",
  "cookies": "{\"PHPSESSID\": \"xxx\"}",
  "url_charset": "gbk",
  "charset": "gbk"
}
```

### 总结

- ✅ 对于GBK编码的网站，必须设置 `url_charset: "gbk"`
- ✅ 对于UTF-8编码的网站，可以不设置或设置 `url_charset: "utf-8"`
- ✅ 使用浏览器开发者工具确定网站编码
- ✅ 查看MCP工具返回的编码信息验证设置是否正确
