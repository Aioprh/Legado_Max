package io.legado.app.ui.book.source.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.config.LocalConfig
import io.legado.app.utils.putString
import io.legado.app.utils.remove
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AI 生成书源功能的「历史记录」持久化（磁盘级）。
 *
 * 用于防止用户不小心退出（Activity 重建 / 进程被杀）导致已抓取的 HTML、
 * 已生成的书源 JSON 等内容丢失。每次关键操作（抓取/生成/修复成功）后写入一条快照，
 * 进入界面时可恢复最近一次，也可查看历史列表选择恢复某一条。
 * 数据存于 SharedPreferences（JSON 数组），应用重启后仍保留。
 *
 * 生成/修复结果非空时，同时在后台运行真实的首/中/末章节验证。
 * 这样即使生成流程已经返回成功，也不会把“只能读第一章”的书源误认为真正可用。
 */
object AiSourceHistory {

    private const val KEY = "ai_source_history"
    /** 最多保留的历史条数 */
    private const val MAX = 20

    private val gson = Gson()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val runtimeValidationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Record(
        val time: Long = System.currentTimeMillis(),
        val url: String = "",
        val keyword: String = "",
        val html: String = "",
        val result: String = ""
    ) {
        fun timeText(): String = timeFormat.format(Date(time))
    }

    /** 读取全部历史（新的在前） */
    @Synchronized
    fun all(): List<Record> {
        val raw = LocalConfig.getString(KEY, "") ?: return emptyList()
        return runCatching {
            val arr = JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                Record(
                    time = obj.get("time")?.takeIf { it.isJsonPrimitive }?.asLong ?: System.currentTimeMillis(),
                    url = strOf(obj, "url"),
                    keyword = strOf(obj, "keyword"),
                    html = strOf(obj, "html"),
                    result = strOf(obj, "result")
                )
            }.sortedByDescending { it.time }
        }.getOrDefault(emptyList())
    }

    private fun strOf(obj: JsonObject, key: String): String =
        obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: ""

    /** 追加一条记录（时间新的在前，超出上限丢弃最旧） */
    @Synchronized
    fun add(record: Record) {
        val list = all().toMutableList()
        // 完全相同的记录去重
        list.removeAll { it.url == record.url && it.result == record.result && it.html == record.html }
        list.add(0, record)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(list)
        scheduleRuntimeValidation(record)
    }

    /** 最近一条，无则 null */
    fun latest(): Record? = all().firstOrNull()

    /** 清空历史 */
    @Synchronized
    fun clear() {
        LocalConfig.remove(KEY)
    }

    private fun save(list: List<Record>) {
        val arr = JsonArray()
        list.forEach { r ->
            val obj = JsonObject()
            obj.addProperty("time", r.time)
            obj.addProperty("url", r.url)
            obj.addProperty("keyword", r.keyword)
            obj.addProperty("html", r.html)
            obj.addProperty("result", r.result)
            arr.add(obj)
        }
        LocalConfig.putString(KEY, gson.toJson(arr))
    }

    /**
     * 生成/修复结果落盘后再跑一次真实运行时验证。
     * 不阻塞 UI，也不改变历史记录本身；验证结果统一进入 AI 日志。
     */
    private fun scheduleRuntimeValidation(record: Record) {
        if (record.keyword.isBlank() || record.result.isBlank()) return
        runtimeValidationScope.launch {
            val result = runCatching {
                AiSourceRuntimeMultiChapter.verify(record.result, record.keyword)
            }.getOrElse { e ->
                AiSourceLog.log(
                    "ERROR",
                    "多章节验证",
                    "运行时验证异常：${e.message ?: e.javaClass.simpleName}"
                )
                return@launch
            }
            if (result.passed) {
                AiSourceLog.log("SUCCESS", "多章节验证", result.summary)
            } else {
                AiSourceLog.log(
                    "ERROR",
                    "多章节验证",
                    buildString {
                        appendLine(result.summary)
                        if (result.repairPrompt.isNotBlank()) {
                            appendLine("自动修复提示：")
                            appendLine(result.repairPrompt)
                        }
                    }.trim()
                )
            }
        }
    }
}
