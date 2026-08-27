package io.legado.app.data.entities.rule

import android.os.Parcelable
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSyntaxException
import io.legado.app.utils.INITIAL_GSON
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReviewRule(
    var reviewUrl: String? = null,          // 段评URL
    var avatarRule: String? = null,         // 段评发布者头像
    var contentRule: String? = null,        // 段评内容
    var postTimeRule: String? = null,       // 段评发布时间
    var reviewQuoteUrl: String? = null,     // 获取段评回复URL

    // ===================== 段评自适配（配置驱动） =====================
    // 通过书源 ruleReview 的 JSON 配置即可接入新的段评源，无需改 Kotlin 代码。
    // 占位符：{bookId} {chapterId} {pid} 用于 URL；[page] [pageSize] [reviewId] [rootId]
    //        保留给原生弹窗替换（仅 native 模式）。

    // 1) 书籍/章节 ID 提取正则（取第一个捕获组；缺省回退 XX=数字 与 末尾数字）
    var bookIdRule: String? = null,         // 书籍ID正则，如 (?:bookId|book_id)=(\d+)
    var chapterIdRule: String? = null,      // 章节ID正则，如 (?:item_id|chapterId)=(\d+)

    // 2) 段评摘要（气泡计数）接口
    var summaryUrl: String? = null,         // 摘要URL模板，支持 {bookId} {chapterId}
    var summaryListPath: String? = null,    // 摘要列表 JSONPath，如 $.data.distributions
    var summaryPidKey: String? = null,      // 每个条目里的段落序数字段（默认 para_index）
    var summaryCountKey: String? = null,    // 每个条目里的评论数字段（默认 count）
    var summaryOffset: Int? = null,         // 远程 pid 到注入段落号的偏移（默认 1，即远程 0基 -> 工程 1基）

    // 3) 打开模式：native=原生段评弹窗；web=内嵌浏览器打开网页
    var openMode: String? = null,           // native / web（缺省 native）
    var openUrl: String? = null,            // web 模式点击气泡打开 URL，支持 {bookId} {chapterId} {pid}

    // 4) 原生弹窗评论接口（openMode=native 时生效）
    var commentsUrl: String? = null,        // 评论列表URL模板，含 {bookId} {chapterId} {pid} [page] [pageSize]
    var listPath: String? = null,           // 评论列表 JSONPath
    var totalPath: String? = null,          // 总数 JSONPath
    var repliesUrl: String? = null,         // 回复列表URL模板，含 [reviewId] [rootId] [pageSize]
    var replyListPath: String? = null,      // 回复列表 JSONPath（缺省沿用 listPath）
    var audioUrl: String? = null,           // 语音列表URL模板
    var pageSize: Int? = null,              // 分页大小（默认 20）
    var sortEnabled: Boolean? = null,       // 是否允许切换排序（默认 true）

    // 5) 原生弹窗评论字段 JSONPath（缺省交给弹窗按常见字段名兜底解析）
    var nicknamePath: String? = null,
    var avatarPath: String? = null,
    var levelPath: String? = null,
    var ipPath: String? = null,
    var commentContentPath: String? = null,
    var agreePath: String? = null,
    var opposePath: String? = null,
    var timePath: String? = null,
    var floorPath: String? = null,
    var commentIdPath: String? = null,
    var rootIdPath: String? = null,
    var replyCountPath: String? = null,
    var replyToPath: String? = null,

    // 这些功能将在以上功能完成以后实现
    var voteUpUrl: String? = null,          // 点赞URL
    var voteDownUrl: String? = null,        // 点踩URL
    var postReviewUrl: String? = null,      // 发送回复URL
    var postQuoteUrl: String? = null,       // 发送回复段评URL
    var deleteUrl: String? = null,          // 删除段评URL
) : Parcelable {

    companion object {

        val jsonDeserializer = JsonDeserializer<ReviewRule?> { json, _, _ ->
            when {
                json.isJsonObject -> INITIAL_GSON.fromJson(json, ReviewRule::class.java)
                json.isJsonPrimitive -> runCatching {
                    INITIAL_GSON.fromJson(json.asString, ReviewRule::class.java)
                }.getOrElse {
                    if (it is JsonSyntaxException || it is ClassCastException) {
                        ReviewRule(contentRule = json.asString)
                    } else {
                        throw it
                    }
                }
                else -> null
            }
        }

    }

}