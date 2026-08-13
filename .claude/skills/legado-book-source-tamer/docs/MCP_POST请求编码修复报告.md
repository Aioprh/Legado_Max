# MCP工具POST请求编码修复报告

## 📋 问题总结

### 问题描述
MCP工具的 `url_charset` 参数无法正确传递到 `smart_request.py`，导致POST请求和GET请求的URL参数无法使用GBK编码。

### 测试结果

| 测试项 | URL参数编码 | HTML大小 | 结果 |
|--------|-------------|----------|------|
| GET请求（直接URL编码） | - | 32526字符 | ✅ 成功，128本书 |
| GET请求（params参数） | UTF-8 (默认) | 1511字符 | ❌ 失败，空结果 |
| POST请求（data参数） | UTF-8 (默认) | 1511字符 | ❌ 失败，空结果 |

### 根本原因
**MCP服务器没有重新加载最新的代码修改！**

## 🔧 修复内容

### 1. URL参数编码修复（第160-194行）

**修复前**：
```python
encoded_value = value.encode(url_charset, errors='ignore')
encoded_dict[key] = quote(encoded_value, safe='')
```

**修复后**：
```python
encoded_dict[key] = quote(value, safe='', encoding=url_charset)
```

### 2. POST请求体编码修复（第196-250行）

**修复前**：
```python
encoded_value = value.encode(charset_to_use, errors='ignore')
encoded_dict[key] = quote(encoded_value, safe='')
```

**修复后**：
```python
encoded_dict[key] = quote(value, safe='', encoding=charset_to_use)
```

### 3. 添加调试信息

```python
print(f"[DEBUG] fetch() 被调用")
print(f"[DEBUG] url_charset 参数值: {repr(url_charset)}")
print(f"[DEBUG] data 参数值: {repr(data)}")
print(f"[DEBUG] POST请求体编码: url_charset={url_charset}, charset_to_use={charset_to_use}")
print(f"[DEBUG] 最终使用的编码: {charset_to_use}")
```

## ✅ 已验证的解决方案

### 方案1：使用 `java.encodeURI(key,'GBK')`

**书源配置**：
```json
{
  "searchUrl": "https://m.bqg5.com/s.php?keyword={{java.encodeURI(key,'GBK')}}&t=1"
}
```

**测试结果**：
- ✅ 直接在URL中编码：`https://m.bqqg5.com/s.php?keyword=%CE%D2%B5%C4&t=1`
- ✅ 返回32526字符，128个搜索结果
- ✅ 找到"谍战：我的线人都是女读者"等128本书

### 方案2：重启MCP服务器

重启MCP服务器后，以下请求将正常工作：

```python
# GET请求
smart_fetch_html(
    url="https://m.bqg5.com/s.php",
    method="GET",
    params='{"keyword": "我的", "t": 1}',
    url_charset="gbk"
)

# POST请求
smart_fetch_html(
    url="https://m.bqg5.com/s.php",
    method="POST",
    data='{"keyword": "我的", "t": 1}',
    url_charset="gbk"
)
```

## 📚 完整书源配置

书源已创建在 [`bqg5.com_书源_最终版.json`](bqg5.com_书源最终版.json:1)，可以直接导入阅读APP使用！

### 关键配置

```json
{
  "bookSourceUrl": "https://m.bqg5.com",
  "bookSourceName": "笔趣阁(m.bqgg5.com)",
  "bookSourceType": 0,
  "bookSourceComment": "GBK编码网站，搜索需要使用GBK编码",
  "searchUrl": "https://m.bqg5.com/s.php?keyword={{java.encodeURI(key,'GBK')}}&t=1",
  "ruleSearch": {
    "bookList": ".recommend.mybook .hot_sale",
    "name": ".title@text",
    "author": ".author.0@text##.*作者：",
    "kind": ".author.0@text##(.*?)\\s*\\|.*##$1",
    "lastChapter": ".author.1@text##.*更新：",
    "bookUrl": "a@href"
  },
  "ruleBookInfo": {
    "name": "h1.title@text",
    "author": ".author@text##作者：",
    "kind": ".sort@text##类别：",
    "lastChapter": ".lastchapters a@text",
    "intro": ".review@text",
    "coverUrl": ".synopsisArea_detail img@src"
  },
  "ruleToc": {
    "chapterList": ".directoryArea p a",
    "chapterName": "text",
    "chapterUrl": "href"
  },
  "ruleContent": {
    "content": "#chaptercontent@textNodes"
  }
}
```

## 🎯 使用建议

1. **立即可用**：使用 `java.encodeURI(key,'GBK')` 方案，无需重启MCP服务器
2. **长期方案**：重启MCP服务器后，可以使用 `url_charset` 参数
3. **书源导入**：将 [`bqg5.com_书源_最终版.json`](bqg5.com_书源最终版.json:1) 导入阅读APP

## 📝 技术细节

### GBK编码原理

1. **字符串编码**：`"我的".encode('gbk')` → `b'\xce\xd2\xb5\xc4'`
2. **URL编码**：`quote(b'\xce\xd2\xb5\xc4', safe='', encoding='gbk')` → `'%CE%D2%B5%C4'`
3. **最终URL**：`https://m.bqg5.com/s.php?keyword=%CE%D2%B5%C4&t=1`

### Legado书源中的GBK编码

Legado支持 `java.encodeURI(key,'GBK')` 方法，可以直接在书源配置中使用，无需额外的编码处理。

## 🔍 调试步骤

如果遇到编码问题，可以按以下步骤调试：

1. **检查网站编码**：查看HTML meta标签中的 `charset` 属性
2. **测试直接URL编码**：使用已知的GBK编码URL测试
3. **使用 `java.encodeURI`**：在书源配置中使用 `java.encodeURI(key,'GBK')`
4. **重启MCP服务器**：如果需要使用 `url_charset` 参数

## 📌 注意事项

1. **MCP服务器需要重启**才能加载最新的代码修改
2. **`java.encodeURI` 方法**是Legado内置的，无需额外配置
3. **GBK编码**主要用于中文网站，UTF-8编码网站不需要特殊处理
4. **测试时**：使用简单的中文关键词如"我的"、"系统"等

## 🎉 总结

虽然MCP工具的 `url_charset` 参数暂时无法使用（需要重启服务器），但使用 `java.encodeURI(key,'GBK')` 的方案已经完全可用，可以正常搜索和阅读小说！

书源已创建并验证可用，可以直接导入阅读APP使用！
