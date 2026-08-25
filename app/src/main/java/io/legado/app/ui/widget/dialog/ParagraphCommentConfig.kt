package io.legado.app.ui.widget.dialog

/**
 * 段评弹窗配置：由书源 JS（pclick 点击脚本）通过
 * `java.showParagraphComments(JSON.stringify(config))` 传入。
 *
 * 字段均为可选，缺失时弹窗会用常见候选路径兜底解析，
 * 兼容起点系（UserName/UserHeadIcon/ShowTag/IpLocation/ReviewCount…）等不同站点字段命名。
 *
 * URL 模板占位符：
 * - {{page}} / {{pageSize}}   段评列表分页（commentsUrl）
 * - {{reviewId}} / {{rootId}} 某条评论的 id / 根评论 id（repliesUrl）
 */
data class ParagraphCommentConfig(
    var title: String = "",
    // 段评列表
    var listPath: String = "",
    var totalPath: String = "",
    var commentsUrl: String = "",
    var pageParam: String = "page",
    var pageSizeParam: String = "page_size",
    var pageSize: Int = 20,
    // 回复列表（可选，无回复接口则只展示条数不展开）
    var repliesUrl: String = "",
    // 评论字段路径
    var fields: FieldConfig = FieldConfig(),
    // 回复字段路径
    var replyFields: FieldConfig = FieldConfig()
) {
    data class FieldConfig(
        var nickname: String = "",
        var avatar: String = "",
        var level: String = "",
        var ip: String = "",
        var content: String = "",
        var agree: String = "",
        var oppose: String = "",
        var time: String = "",
        var floor: String = "",
        var id: String = "",
        var rootId: String = "",
        var replyCount: String = "",
        var replyTo: String = ""
    )
}

/** 段评条目（含回复列表，用于展开/收起） */
data class ParagraphCommentItem(
    val id: String = "",
    val rootId: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: String = "",
    val ip: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val audio: String = "",
    val agree: Long = 0,
    val oppose: Long = 0,
    val time: Long = 0,
    val floor: Int = 0,
    val replyCount: Int = 0,
    val replies: MutableList<ParagraphReplyItem> = mutableListOf(),
    var repliesLoaded: Boolean = false,
    var repliesLoading: Boolean = false
)

/** 段评回复条目 */
data class ParagraphReplyItem(
    val nickname: String = "",
    val avatar: String = "",
    val replyTo: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val audio: String = "",
    val agree: Long = 0,
    val time: Long = 0
)
