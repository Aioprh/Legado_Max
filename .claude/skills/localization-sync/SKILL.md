---
name: localization-sync
description: Synchronize user-visible string resources across four required languages in the Legado project (English, Simplified Chinese, Traditional Chinese Taiwan, Traditional Chinese Hong Kong). Use when adding or modifying UI text or discovering missing translations.
---

# Legado 多语言字符串资源同步

## 概述

本项目（Legado 阅读）要求用户可见的文本内容必须同步维护四种语言：
英语（基准）、简体中文（zh）、繁体中文台湾（zh-rTW）、繁体中文香港（zh-rHK）。

## 触发条件

以下任一情况均需执行本技能：

- 新增任何用户界面文本、提示信息、错误信息或按钮标签
- 修改现有的字符串资源内容
- 发现任何语言的字符串资源缺失或不一致

## 资源文件路径

详见 `references/resource-paths.md`。核心文件如下：

| 语言 | strings.xml | arrays.xml |
|------|------------|------------|
| 英语 (基准) | `app/src/main/res/values/strings.xml` | `app/src/main/res/values/arrays.xml` |
| 简体中文 | `app/src/main/res/values-zh/strings.xml` | `app/src/main/res/values-zh/arrays.xml` |
| 繁体中文 (台湾) | `app/src/main/res/values-zh-rTW/strings.xml` | `app/src/main/res/values-zh-rTW/arrays.xml` |
| 繁体中文 (香港) | `app/src/main/res/values-zh-rHK/strings.xml` | `app/src/main/res/values-zh-rHK/arrays.xml` |

**注意：** `non_translat.xml` 文件（`app/src/main/res/values/non_translat.xml`）中的字符串标记了 `translatable="false"`，不需要翻译，仅存在于基准 `values/` 目录中。

## 执行流程

### 第一步：识别变更

1. 确定变更来源（新增字符串、修改现有字符串、或缺失条目）
2. 明确变更涉及的具体 key 和对应的英文原文
3. 检查是否也涉及 `arrays.xml` 中的数组条目

### 第二步：以英文为基准

始终以 `values/strings.xml`（英文）为权威基准。对于新增字符串：
- 如果用户提供了英文原文，直接使用
- 如果用户仅提供中文，先推导出准确的英文对应文本
- 确保 key 命名符合项目现有规范（使用 `snake_case` 或项目已有的命名风格）

### 第三步：翻译为四种语言

按以下顺序同步翻译：

1. **英文** (`values/`) — 写入基准字符串
2. **简体中文** (`values-zh/`) — 翻译为简体中文
3. **繁体中文台湾** (`values-zh-rTW/`) — 翻译为台湾繁体中文
4. **繁体中文香港** (`values-zh-rHK/`) — 翻译为香港繁体中文

关键规则：
- 四种语言的资源 **key 必须完全一致**
- 对于修改场景，使用 `Edit` 工具精确定位并替换
- 对于新增场景，在对应文件的适当位置（按分组注释或字母顺序）插入新条目
- 每个文件保持与已有条目一致的缩进（4 空格）和格式风格

### 第四步：繁体中文区域差异处理

台湾繁体中文与香港繁体中文之间需要区分用词差异。常见对照表详见 `references/tw-hk-differences.md`。

核心差异类别：
- **计算机术语**：如「軟體」vs「軟件」、「記憶體」vs「記憶體」（香港也常用英文原词）
- **日常用词**：如「透過」vs「通過」、「資料」vs「數據」、「儲存」vs「存儲」
- **字符差异**：如「為」vs「爲」、「讀」vs「閲」、「線」vs「綫」

当无法确定正确的区域用词时，应参考：
1. `references/tw-hk-differences.md` 中的对照表
2. 已有翻译文件中类似字符串的用词惯例
3. 如仍不确定，在翻译后标注供审核

### 第五步：验证一致性

完成后执行以下检查：

1. **Key 完整性** — 四个文件中新增/修改的 key 必须完全一致
2. **XML 格式** — 确保所有 XML 标签正确闭合，特殊字符已转义（`&lt;`, `&gt;`, `&amp;`, `&apos;`, `&quot;`）
3. **无占位符残留** — 确保没有留空的 `translatable="false"` 误用
4. **区域用词一致** — 同一文件中相似概念的翻译用词保持统一

## arrays.xml 同步

对于 `arrays.xml` 中的 `<string-array>` 和 `<integer-array>` 条目：

1. 确认基准文件（`values/arrays.xml`）中的数组 name 和条目数量
2. 在所有四个语言的 `arrays.xml` 中保持 name 和条目数一致
3. 每个数组项的翻译需与单条 string 翻译遵循相同规则
4. 注意：日语 (`values-ja-rJP`) 没有 `arrays.xml`，此为已知情况，不属于本技能需修复的范围

## 注意事项

- `values-zh` 使用简体中文，字符集为简体汉字，不是 `values-zh-rCN`
- `values-zh-rTW` 和 `values-zh-rHK` 虽然都使用繁体中文，但存在地区性用词差异
- 台湾繁体使用「你」，香港繁体也使用「你」；与简体中文一致
- 不要翻译 `non_translat.xml` 中的字符串
- 新增字符串时遵循文件内已有的分组注释结构（如 `<!--App-->`、`<!--Other-->` 等）
- 如文件以 `<?xml version="1.0" encoding="utf-8"?>` 开头，保持该声明不变
- 如文件没有 XML 声明（如 `values-zh/strings.xml`），不要主动添加
