# Legado 多语言资源文件路径

## Android 字符串资源 (strings.xml)

| 语言 | Android 限定符 | 绝对路径 | 条目数 (approx.) |
|------|---------------|----------|-----------------|
| 英语 (基准) | `values` | `C:\My Projects\Legado_Max\app\src\main\res\values\strings.xml` | ~1,982 |
| 简体中文 | `values-zh` | `C:\My Projects\Legado_Max\app\src\main\res\values-zh\strings.xml` | ~1,972 |
| 繁体中文 (台湾) | `values-zh-rTW` | `C:\My Projects\Legado_Max\app\src\main\res\values-zh-rTW\strings.xml` | ~1,612 |
| 繁体中文 (香港) | `values-zh-rHK` | `C:\My Projects\Legado_Max\app\src\main\res\values-zh-rHK\strings.xml` | ~1,498 |
| 西班牙语 | `values-es-rES` | `C:\My Projects\Legado_Max\app\src\main\res\values-es-rES\strings.xml` | ~1,207 |
| 日语 | `values-ja-rJP` | `C:\My Projects\Legado_Max\app\src\main\res\values-ja-rJP\strings.xml` | ~1,207 |
| 葡萄牙语 (巴西) | `values-pt-rBR` | `C:\My Projects\Legado_Max\app\src\main\res\values-pt-rBR\strings.xml` | ~1,207 |
| 越南语 | `values-vi` | `C:\My Projects\Legado_Max\app\src\main\res\values-vi\strings.xml` | ~1,206 |

## 数组资源 (arrays.xml)

| 语言 | 绝对路径 |
|------|----------|
| 英语 (基准) | `C:\My Projects\Legado_Max\app\src\main\res\values\arrays.xml` |
| 简体中文 | `C:\My Projects\Legado_Max\app\src\main\res\values-zh\arrays.xml` |
| 繁体中文 (台湾) | `C:\My Projects\Legado_Max\app\src\main\res\values-zh-rTW\arrays.xml` |
| 繁体中文 (香港) | `C:\My Projects\Legado_Max\app\src\main\res\values-zh-rHK\arrays.xml` |
| 西班牙语 | `C:\My Projects\Legado_Max\app\src\main\res\values-es-rES\arrays.xml` |
| 葡萄牙语 (巴西) | `C:\My Projects\Legado_Max\app\src\main\res\values-pt-rBR\arrays.xml` |
| 越南语 | `C:\My Projects\Legado_Max\app\src\main\res\values-vi\arrays.xml` |

注意：日语 (`values-ja-rJP`) 目录不存在 `arrays.xml` 文件。

## 不可翻译字符串

| 路径 | 说明 |
|------|------|
| `C:\My Projects\Legado_Max\app\src\main\res\values\non_translat.xml` | 标记 `translatable="false"` 的字符串，仅存在于基准目录 |

## Debug 构建覆盖

| 语言 | 绝对路径 | 用途 |
|------|----------|------|
| 英语 | `C:\My Projects\Legado_Max\app\src\debug\res\values\strings.xml` | 覆盖应用名为 "legado·D" |
| 简体中文 | `C:\My Projects\Legado_Max\app\src\debug\res\values-zh\strings.xml` | 覆盖中文应用名为 "阅读·D" |

Debug 构建覆盖通常不需要额外维护。

## 语言切换设置

语言选择偏好存储在 `language` 键中（定义于 `PreferKey.kt`）：

```kotlin
override fun attachBaseContext(newBase: Context) {
    val locale = when (newBase.getPrefString(PreferKey.language)) {
        "zh" -> Locale.SIMPLIFIED_CHINESE   // 简体中文
        "tw" -> Locale.TRADITIONAL_CHINESE  // 繁体中文
        "en" -> Locale.ENGLISH              // 英语
        else -> getSystemLocale()           // 跟随系统
    }
}
```
