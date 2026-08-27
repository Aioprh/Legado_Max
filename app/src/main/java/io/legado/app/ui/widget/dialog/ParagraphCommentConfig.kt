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
    // 回复列表的列表路径（可选；缺省沿用 listPath，兼容回复接口与主列表结构不同的站点）
    var replyListPath: String = "",
    // 语音列表（可选，起点系需 type=audio 接口获取音频地址；缺省从 commentsUrl 推导 type=text -> type=audio）
    var audioUrl: String = "",
    // 评论字段路径
    var fields: FieldConfig = FieldConfig(),
    // 回复字段路径
    var replyFields: FieldConfig = FieldConfig(),
    // 是否允许切换排序模式（番茄段评接口不按时间/回复数排序，仅保留实时；起点段评保留全部排序）
    var sortEnabled: Boolean = true
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
    val isGod: Boolean = false,
    val replies: MutableList<ParagraphReplyItem> = mutableListOf(),
    var repliesLoaded: Boolean = false,
    var repliesLoading: Boolean = false,
    // 语音播放：audioUrl 实际播放地址（点击后从 audio 接口补全）
    var audioUrl: String = "",
    var audioLoading: Boolean = false,
    var audioPlaying: Boolean = false
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
