package io.legado.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import com.google.gson.ToNumberPolicy
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.lang.reflect.Type
import kotlin.math.ceil

val INITIAL_GSON: Gson by lazy {
    GsonBuilder()
        .registerTypeAdapter(
            object : TypeToken<Map<String?, Any?>?>() {}.type,
            MapDeserializerDoubleAsIntFix()
        )
        .registerTypeAdapter(Int::class.java, IntJsonDeserializer())
        .registerTypeAdapter(String::class.java, StringJsonDeserializer())
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
}

val GSON: Gson by lazy {
    INITIAL_GSON.newBuilder()
        .registerTypeAdapter(BookSource::class.java, BookSource.jsonSerializer)
        .registerTypeAdapter(ExploreRule::class.java, ExploreRule.jsonDeserializer)
        .registerTypeAdapter(SearchRule::class.java, SearchRule.jsonDeserializer)
        .registerTypeAdapter(BookInfoRule::class.java, BookInfoRule.jsonDeserializer)
        .registerTypeAdapter(TocRule::class.java, TocRule.jsonDeserializer)
        .registerTypeAdapter(ContentRule::class.java, ContentRule.jsonDeserializer)
        .registerTypeAdapter(ReviewRule::class.java, ReviewRule.jsonDeserializer)
        .create()
}

val GSONStrict: Gson by lazy {
    GSON.newBuilder()
        .setStrictness(Strictness.STRICT)
        .create()
}

inline fun <reified T> genericType(): Type = object : TypeToken<T>() {}.type

inline fun <reified T> Gson.fromJsonObject(json: String?): Result<T> {
    return kotlin.runCatching {
        if (json == null) throw JsonSyntaxException("解析字符串为空")
        fromJson(json, genericType<T>()) as T
    }
}

inline fun <reified T> Gson.fromJsonArray(json: String?): Result<List<T>> {
    return kotlin.runCatching {
        if (json == null) throw JsonSyntaxException("解析字符串为空")
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(json, type) as List<T?>
        if (list.contains(null)) {
            throw JsonSyntaxException("列表不能存在null元素，可能是json格式错误，通常为列表存在多余的逗号所致")
        }
        @Suppress("UNCHECKED_CAST")
        list as List<T>
    }
}

inline fun <reified T> Gson.fromJsonObject(inputStream: InputStream?): Result<T> {
    return kotlin.runCatching {
        if (inputStream == null) throw JsonSyntaxException("解析流为空")
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            fromJson(reader, genericType<T>()) as T
        }
    }
}

inline fun <reified T> Gson.fromJsonArray(inputStream: InputStream?): Result<List<T>> {
    return kotlin.runCatching {
        if (inputStream == null) throw JsonSyntaxException("解析流为空")
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            fromJsonArray<T>(reader).getOrThrow()
        }
    }
}

/**
 * Reader 版本 JSON 数组解析。
 * 与 String 版本相比不会先把整个文件读入 String，避免大备份恢复时产生巨大的
 * StringWriter/String 临时对象。
 */
inline fun <reified T> Gson.fromJsonArray(reader: Reader?): Result<List<T>> {
    return kotlin.runCatching {
        if (reader == null) throw JsonSyntaxException("解析流为空")
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(reader, type) as List<T?>
        if (list.contains(null)) {
            throw JsonSyntaxException("列表不能存在null元素，可能是json格式错误，通常为列表存在多余的逗号所致")
        }
        @Suppress("UNCHECKED_CAST")
        list as List<T>
    }
}

inline fun <reified T> Gson.fromJsonObject(reader: Reader?): Result<T> {
    return kotlin.runCatching {
        if (reader == null) throw JsonSyntaxException("解析流为空")
        fromJson(reader, genericType<T>()) as T
    }
}

/**
 * 真正逐项消费 JSON 数组。调用方可以按批次写入数据库，不需要把整个数组保存在内存中。
 */
inline fun <reified T> Gson.forEachJsonArray(reader: Reader?, crossinline action: (T) -> Unit): Result<Unit> {
    return kotlin.runCatching {
        if (reader == null) throw JsonSyntaxException("解析流为空")
        val adapter = getAdapter(TypeToken.get(T::class.java))
        JsonReader(reader).use { jsonReader ->
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val item = adapter.read(jsonReader)
                    ?: throw JsonSyntaxException("列表不能存在null元素")
                action(item)
            }
            jsonReader.endArray()
        }
    }
}

/**
 * InputStream 版本逐项解析。不会先创建整份 JSON 字符串，也不会把整个数组装进 List。
 */
inline fun <reified T> Gson.forEachJsonArray(
    inputStream: InputStream?,
    crossinline action: (T) -> Unit
): Result<Unit> {
    return kotlin.runCatching {
        if (inputStream == null) throw JsonSyntaxException("解析流为空")
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            forEachJsonArray<T>(reader) { item -> action(item) }.getOrThrow()
        }
    }
}

/**
 * 按批次消费 JSON 数组，适合恢复大型备份。
 * batchAction 返回后才会继续解析下一批，保证内存中最多保留 batchSize 个对象。
 */
inline fun <reified T> Gson.forEachJsonArrayBatch(
    reader: Reader?,
    batchSize: Int = 300,
    crossinline batchAction: (List<T>) -> Unit
): Result<Unit> {
    return kotlin.runCatching {
        require(batchSize > 0) { "batchSize 必须大于 0" }
        if (reader == null) throw JsonSyntaxException("解析流为空")
        val adapter = getAdapter(TypeToken.get(T::class.java))
        val batch = ArrayList<T>(batchSize)
        JsonReader(reader).use { jsonReader ->
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val item = adapter.read(jsonReader)
                    ?: throw JsonSyntaxException("列表不能存在null元素")
                batch.add(item)
                if (batch.size >= batchSize) {
                    batchAction(batch)
                    batch.clear()
                }
            }
            jsonReader.endArray()
            if (batch.isNotEmpty()) batchAction(batch)
        }
    }
}

inline fun <reified T> Gson.forEachJsonArrayBatch(
    inputStream: InputStream?,
    batchSize: Int = 300,
    crossinline batchAction: (List<T>) -> Unit
): Result<Unit> {
    return kotlin.runCatching {
        if (inputStream == null) throw JsonSyntaxException("解析流为空")
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            forEachJsonArrayBatch<T>(reader, batchSize) { batch -> batchAction(batch) }.getOrThrow()
        }
    }
}

/**
 * 挂起版本的批量 JSON 数组读取。
 * batchAction 可以直接调用 suspend DAO/Repository 方法，避免先把整个 JSON 文件转换成 List。
 */
suspend inline fun <reified T> Gson.forEachJsonArrayBatchSuspend(
    reader: Reader?,
    batchSize: Int = 300,
    crossinline batchAction: suspend (List<T>) -> Unit
): Result<Unit> {
    return kotlin.runCatching {
        require(batchSize > 0) { "batchSize 必须大于 0" }
        if (reader == null) throw JsonSyntaxException("解析流为空")
        val adapter = getAdapter(TypeToken.get(T::class.java))
        val batch = ArrayList<T>(batchSize)
        JsonReader(reader).use { jsonReader ->
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val item = adapter.read(jsonReader)
                    ?: throw JsonSyntaxException("列表不能存在null元素")
                batch.add(item)
                if (batch.size >= batchSize) {
                    batchAction(batch)
                    batch.clear()
                }
            }
            jsonReader.endArray()
            if (batch.isNotEmpty()) batchAction(batch)
        }
    }
}

suspend inline fun <reified T> Gson.forEachJsonArrayBatchSuspend(
    inputStream: InputStream?,
    batchSize: Int = 300,
    crossinline batchAction: suspend (List<T>) -> Unit
): Result<Unit> {
    return kotlin.runCatching {
        if (inputStream == null) throw JsonSyntaxException("解析流为空")
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            forEachJsonArrayBatchSuspend<T>(reader, batchSize) { batch -> batchAction(batch) }.getOrThrow()
        }
    }
}

fun Gson.writeToOutputStream(out: OutputStream, any: Any) {
    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
    writer.setIndent("  ")
    if (any is List<*>) {
        writer.beginArray()
        any.forEach {
            it?.let { value ->
                toJson(value, value::class.java, writer)
            }
        }
        writer.endArray()
    } else {
        toJson(any, any::class.java, writer)
    }
    writer.close()
}

class StringJsonDeserializer : JsonDeserializer<String?> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext?): String? {
        return when {
            json.isJsonPrimitive -> json.asString
            json.isJsonNull -> null
            else -> json.toString()
        }
    }
}

class IntJsonDeserializer : JsonDeserializer<Int?> {
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Int? {
        return when {
            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toInt() else null
            }
            else -> null
        }
    }
}

class MapDeserializerDoubleAsIntFix : JsonDeserializer<Map<String, Any?>?> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        jsonElement: JsonElement,
        type: Type,
        jsonDeserializationContext: JsonDeserializationContext
    ): Map<String, Any?>? {
        @Suppress("unchecked_cast")
        return read(jsonElement) as? Map<String, Any?>
    }

    fun read(json: JsonElement): Any? {
        when {
            json.isJsonArray -> {
                val list: MutableList<Any?> = ArrayList()
                val arr = json.asJsonArray
                for (anArr in arr) list.add(read(anArr))
                return list
            }
            json.isJsonObject -> {
                val map: MutableMap<String, Any?> = LinkedTreeMap()
                for ((key, value) in json.asJsonObject.entrySet()) map[key] = read(value)
                return map
            }
            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                when {
                    prim.isBoolean -> return prim.asBoolean
                    prim.isString -> return prim.asString
                    prim.isNumber -> {
                        val num: Number = prim.asNumber
                        return if (ceil(num.toDouble()) == num.toLong().toDouble()) num.toLong() else num.toDouble()
                    }
                }
            }
        }
        return null
    }
}
