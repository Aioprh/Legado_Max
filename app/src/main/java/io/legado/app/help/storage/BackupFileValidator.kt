package io.legado.app.help.storage

import android.util.Xml
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStore
import io.legado.app.utils.GSON
import io.legado.app.utils.isJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

enum class ValidationState {
    NOT_VALIDATED,
    VALIDATING,
    VALID,
    WARNING,
    ERROR
}

data class ValidationResult(
    val fileName: String,
    val state: ValidationState,
    val message: String = "",
    val details: String = "",
    val missingFields: List<String> = emptyList(),
    val exception: Throwable? = null
) {
    val canRestore: Boolean
        get() = state == ValidationState.VALID || state == ValidationState.WARNING
}

object BackupFileValidator {

    private const val LARGE_FILE_THRESHOLD = 1024 * 1024L

    fun isLargeFile(file: File): Boolean = file.length() > LARGE_FILE_THRESHOLD

    suspend fun validateFiles(
        path: String,
        fileNames: List<String>,
        onProgress: (String, ValidationResult) -> Unit
    ): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()
        val largeFiles = mutableListOf<String>()
        val smallFiles = mutableListOf<String>()

        fileNames.forEach { fileName ->
            val file = File(path, fileName)
            if (file.exists()) {
                if (isLargeFile(file)) {
                    largeFiles.add(fileName)
                } else {
                    smallFiles.add(fileName)
                }
            }
        }

        withContext(Dispatchers.IO) {
            coroutineScope {
                smallFiles.map { fileName ->
                    async {
                        val result = validateFile(path, fileName)
                        withContext(Dispatchers.Main) {
                            onProgress(fileName, result)
                        }
                        result
                    }
                }.awaitAll().let {
                    results.addAll(it)
                }
            }

            for (fileName in largeFiles) {
                val result = validateFile(path, fileName)
                withContext(Dispatchers.Main) {
                    onProgress(fileName, result)
                }
                results.add(result)
            }
        }

        return results
    }

    suspend fun validateFile(path: String, fileName: String): ValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path, fileName)
                if (!file.exists()) {
                    return@withContext ValidationResult(
                        fileName = fileName,
                        state = ValidationState.ERROR,
                        message = "文件不存在",
                        details = "备份文件中找不到 $fileName"
                    )
                }

                if (file.isDirectory) {
                    return@withContext ValidationResult(
                        fileName = fileName,
                        state = ValidationState.VALID,
                        message = "directory"
                    )
                }

                if (file.length() == 0L) {
                    return@withContext ValidationResult(
                        fileName = fileName,
                        state = ValidationState.ERROR,
                        message = "文件为空",
                        details = "$fileName 文件大小为 0 字节"
                    )
                }

                when {
                    fileName.endsWith(".json") -> validateJsonFile(file, fileName)
                    fileName.endsWith(".xml") -> validateXmlFile(file, fileName)
                    else -> ValidationResult(
                        fileName = fileName,
                        state = ValidationState.WARNING,
                        message = "未知文件格式",
                        details = "无法验证 $fileName 的格式"
                    )
                }
            } catch (e: Exception) {
                ValidationResult(
                    fileName = fileName,
                    state = ValidationState.ERROR,
                    message = "验证异常: ${e.message}",
                    details = e.stackTraceToString(),
                    exception = e
                )
            }
        }
    }

    private fun validateJsonFile(file: File, fileName: String): ValidationResult {
        return try {
            // Never use File.readText() here. Backup validation can run before restore and
            // a single large JSON file would otherwise be materialized as one huge String.
            when {
                fileName == HighlightRuleStore.backupFileName -> validateJsonObjectStream(file, fileName, "rules")
                fileName == "homepage.json" -> validateHomepageStream(file)
                fileName == "servers.json" -> validateServersStream(file)
                else -> validateJsonArrayStream(file, fileName, requiredFieldsFor(fileName))
            }
        } catch (e: Exception) {
            ValidationResult(
                fileName = fileName,
                state = ValidationState.ERROR,
                message = "JSON 解析失败",
                details = "解析 $fileName 时出错: ${e.message}",
                exception = e
            )
        }
    }

    private fun requiredFieldsFor(fileName: String): List<String> = when (fileName) {
        "bookshelf.json" -> listOf("name", "author")
        "bookmark.json" -> listOf("bookName", "chapterPos")
        "bookGroup.json" -> listOf("groupName")
        "bookSource.json" -> listOf("bookSourceUrl", "bookSourceName")
        "rssSources.json" -> listOf("sourceUrl", "sourceName")
        "rssStar.json" -> listOf("origin")
        "replaceRule.json" -> listOf("name")
        "readRecord.json" -> listOf("bookName")
        "readRecordDetail.json" -> listOf("bookName")
        "readRecordSession.json" -> listOf("bookName")
        "searchHistory.json" -> listOf("word")
        "txtTocRule.json" -> listOf("name")
        "httpTTS.json" -> listOf("name")
        "keyboardAssists.json" -> listOf("key")
        "dictRule.json" -> listOf("name")
        else -> emptyList()
    }

    private fun validateJsonArrayStream(
        file: File,
        fileName: String,
        requiredFields: List<String>
    ): ValidationResult {
        InputStreamReader(file.inputStream(), Charsets.UTF_8).use { input ->
            JsonReader(input).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    return ValidationResult(fileName, ValidationState.ERROR, "JSON 格式错误", "$fileName 不是有效的 JSON 数组格式")
                }
                reader.beginArray()
                if (!reader.hasNext()) {
                    reader.endArray()
                    return ValidationResult(fileName, ValidationState.WARNING, "数据为空", "JSON 数组为空，没有数据需要验证")
                }

                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                    return ValidationResult(fileName, ValidationState.WARNING, "数据格式不完整", "第一条数据不是有效的 JSON 对象")
                }

                val missing = requiredFields.toMutableSet()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name in missing) {
                        if (reader.peek() != JsonToken.NULL) missing.remove(name) else reader.skipValue()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()

                // Consume the remaining array without materializing any JSON element.
                while (reader.hasNext()) reader.skipValue()
                reader.endArray()

                return if (missing.isEmpty()) {
                    ValidationResult(fileName, ValidationState.VALID, "格式正确")
                } else {
                    ValidationResult(
                        fileName,
                        ValidationState.WARNING,
                        "缺少必需字段",
                        "缺少字段: ${missing.joinToString(", ")}",
                        missing.toList()
                    )
                }
            }
        }
    }

    private fun validateJsonObjectStream(file: File, fileName: String, requiredField: String): ValidationResult {
        InputStreamReader(file.inputStream(), Charsets.UTF_8).use { input ->
            JsonReader(input).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    return ValidationResult(fileName, ValidationState.ERROR, "JSON 格式错误", "$fileName 不是有效的 JSON 对象格式")
                }
                var found = false
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == requiredField) {
                        found = reader.peek() != JsonToken.NULL
                        reader.skipValue()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                return if (found) ValidationResult(fileName, ValidationState.VALID, "格式正确")
                else ValidationResult(fileName, ValidationState.WARNING, "缺少必需字段", "$fileName 缺少 $requiredField 字段")
            }
        }
    }

    private fun validateHomepageStream(file: File): ValidationResult {
        InputStreamReader(file.inputStream(), Charsets.UTF_8).use { input ->
            JsonReader(input).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    return ValidationResult("homepage.json", ValidationState.ERROR, "格式无效", "homepage.json 不是有效的 JSON 对象")
                }
                var hasModules = false
                var hasCustomSets = false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "modules" -> { hasModules = reader.peek() == JsonToken.BEGIN_ARRAY; reader.skipValue() }
                        "customSets" -> { hasCustomSets = reader.peek() == JsonToken.BEGIN_ARRAY; reader.skipValue() }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                return if (hasModules || hasCustomSets) {
                    ValidationResult("homepage.json", ValidationState.VALID, "格式正确")
                } else {
                    ValidationResult("homepage.json", ValidationState.WARNING, "数据为空", "首页数据中没有模块或书源集")
                }
            }
        }
    }

    private fun validateServersStream(file: File): ValidationResult {
        // servers.json may be AES-encrypted. Detect its outer JSON shape without reading
        // the whole encrypted payload into a String. Encrypted data is accepted as a warning.
        InputStreamReader(file.inputStream(), Charsets.UTF_8).use { input ->
            JsonReader(input).use { reader ->
                return when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) reader.skipValue()
                        reader.endArray()
                        ValidationResult("servers.json", ValidationState.VALID, "格式正确")
                    }
                    else -> ValidationResult("servers.json", ValidationState.WARNING, "加密配置", "servers.json 可能为加密内容，跳过完整结构验证")
                }
            }
        }
    }

    private fun validateXmlFile(file: File, fileName: String): ValidationResult {
        return try {
            val inputStream = file.inputStream()
            inputStream.use {
                val parser = Xml.newPullParser()
                parser.setInput(it, "utf-8")

                var event = parser.eventType
                var hasValidTags = false

                // SharedPreferences XML 格式的有效标签类型
                val validPrefTags = setOf("string", "int", "long", "float", "boolean", "set")

                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        // 对于 videoConfig.xml（SharedPreferences 格式），检查是否有有效的配置项标签
                        if (fileName == "videoConfig.xml" || fileName == "config.xml") {
                            if (parser.name in validPrefTags) {
                                hasValidTags = true
                                break
                            }
                        } else {
                            // 其他 XML 文件检查是否有 name 属性
                            val name = parser.getAttributeValue(null, "name")
                            if (!name.isNullOrBlank()) {
                                hasValidTags = true
                                break
                            }
                        }
                    }
                    event = parser.next()
                }

                if (!hasValidTags) {
                    return ValidationResult(
                        fileName = fileName,
                        state = ValidationState.WARNING,
                        message = "XML 格式不完整",
                        details = "$fileName 缺少有效的配置项"
                    )
                }

                ValidationResult(
                    fileName = fileName,
                    state = ValidationState.VALID,
                    message = "格式正确"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                fileName = fileName,
                state = ValidationState.ERROR,
                message = "XML 解析失败",
                details = "解析 $fileName 时出错: ${e.message}",
                exception = e
            )
        }
    }

}
