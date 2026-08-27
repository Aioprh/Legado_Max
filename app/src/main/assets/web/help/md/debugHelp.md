# 书源调试

* 调试搜索>>输入关键字，如：
```
系统
```
* 调试发现>>输入发现URL，如：
```
月票榜::https://www.qidian.com/rank/yuepiao?page={{page}}
```
* 调试详情页>>输入详情页URL，如：
```
https://m.qidian.com/book/1015609210
```
* 调试目录页>>输入目录页URL，如：
```
++https://www.zhaishuyuan.com/read/30394
```
* 调试正文页>>输入正文页URL，如：
```
--https://www.zhaishuyuan.com/chapter/30394/20940996
```
* 调试目录页/正文页也支持 Data URL，如：
```
++data:text/html;charset=utf-8;base64,PGh0bWw+...
--data:text/html;charset=utf-8;base64,PGh0bWw+...
```
Data URL 会直接解码为文本内容进行规则解析，无需网络请求。

支持两种格式：
- `data:text/html;charset=utf-8;base64,<base64编码内容>` — base64 编码（推荐，中文不乱码）
- `data:text/html;charset=utf-8,<原始文本>` — 非 base64 的原始文本

无需在 URL 后附加 `,{"type":"xxx"}` 参数，Data URL 会被自动识别并解码。

不同 Data URL 格式的处理方式：

| Data URL 格式 | 有无 type 参数 | 走哪条路径 | 结果 |
|---|---|---|---|
| `data:text/html;base64,xxx` | 无 type | `getDataUrlStrContent()` 短路 | ✅ 解码为文本 |
| `data:text/html;base64,xxx,{"type":"image"}` | 有 type | `getDataUrlStrContent()` 短路（先于 type 判断） | ✅ 解码为文本 |
| `data:text/html,<h1>hello</h1>` | 无 type | `getDataUrlStrContent()` 短路 | ✅ 解码为文本 |
| `data:image/png;base64,xxx,{"type":"image"}` | 有 type | `getDataUrlStrContent()` 短路（先于 type 判断） | ✅ 解码为文本 |

改动之前的情况：
- 无 type 的 Data URL → 走 `executeStrRequest` → OkHttp 不支持 `data:` 协议 → **报错**
- 有 type 的 Data URL → 走 `getByteArrayAwait()` → 里面有 `getByteArrayIfDataUri()` 短路 → **能工作，但返回的是 Hex 编码而非文本**

所以改动前的项目里，Data URL 必须带 `,{"type":"xxx"}` 才能通过 `getStrResponseAwait` 正常工作。改动后就不需要了。

因此，无论有没有 type，Data URL 都会被自动识别并解码为文本。
返回的是正确的 文本字符串，AnalyzeRule.setContent(body) 能正常解析。
不需要 OkHttp 网络请求，不需要 WebView。

* 调试正文下一页说明
```
从正文开始调试时，并且正文下一页也写了规则，这时如果下一章的元素和下一页的元素相同时，就会无限地解析网页。所以当网站有这种情况时，最好从目录开始调试，因为阅读会自动识别下一页的链接是否是目录里包含的章节链接，当目录里包含了，会自动停止解析。
```