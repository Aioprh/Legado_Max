package io.legado.app.ui.widget.dialog

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppConst
import io.legado.app.databinding.ItemParagraphCommentBinding
import io.legado.app.databinding.ItemParagraphReplyBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import java.util.Date

/**
 * 段评列表适配器：展示头像 / 昵称 / 等级 / 地区 / 时间 / 内容 / 赞踩 / 楼层，
 * 并支持展开/收起某条评论的回复列表。
 */
class ParagraphCommentAdapter(context: Context) :
    RecyclerAdapter<ParagraphCommentItem, ItemParagraphCommentBinding>(context) {

    interface ReplyListener {
        fun onToggleReplies(item: ParagraphCommentItem)
    }

    interface AudioListener {
        /** 点击语音条：请求 audio 接口补全音频地址并播放/停止 */
        fun onToggleAudio(item: ParagraphCommentItem)
    }

    interface ImageListener {
        /** 点击评论/回复图片：打开大图查看 */
        fun onImageClick(url: String)
    }

    var replyListener: ReplyListener? = null
    var audioListener: AudioListener? = null
    var imageListener: ImageListener? = null

    override fun getViewBinding(parent: ViewGroup): ItemParagraphCommentBinding {
        return ItemParagraphCommentBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemParagraphCommentBinding,
        item: ParagraphCommentItem,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            ImageLoader.load(context, item.avatar)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .circleCrop()
                .into(ivAvatar)

            tvNickname.text = item.nickname.ifBlank {
                context.getString(R.string.paragraph_comment_anonymous)
            }

            // 神评论角标（起点 EssenceType==2 / IsEssence==true）
            if (item.isGod) {
                tvGod.visible()
            } else {
                tvGod.gone()
            }

            if (item.level.isBlank()) {
                tvLevel.gone()
            } else {
                tvLevel.text = item.level
                tvLevel.visible()
            }
            if (item.ip.isBlank()) {
                tvIp.gone()
            } else {
                tvIp.text = item.ip
                tvIp.visible()
            }
            tvTime.text = formatTime(item.time)

            if (item.floor > 0) {
                tvFloor.text = context.getString(R.string.paragraph_comment_floor, item.floor)
                tvFloor.visible()
            } else {
                tvFloor.gone()
            }

            // 内容为空但有图/语音时显示占位提示
            val text = formatContent(item.content)
            tvContent.text = text.ifBlank {
                if (item.images.isNotEmpty() || item.audio.isNotBlank()) {
                    context.getString(R.string.paragraph_comment_image)
                } else {
                    ""
                }
            }
            bindImages(binding.llImages, binding.ivImg1, binding.ivImg2, binding.ivImg3, item.images)
            bindAudio(binding.tvAudio, item)

            if (item.agree > 0) {
                tvAgree.text = context.getString(R.string.paragraph_comment_like, item.agree)
                tvAgree.visible()
            } else {
                tvAgree.gone()
            }
            if (item.oppose > 0) {
                tvDislike.text = context.getString(R.string.paragraph_comment_dislike, item.oppose)
                tvDislike.visible()
            } else {
                tvDislike.gone()
            }

            setupReplyButton(binding, item)
            bindReplies(binding, item)
        }
    }

    /** 回复按钮状态：未加载->展开 N 条回复；加载中->加载中…；已加载->收起/无回复隐藏 */
    private fun setupReplyButton(binding: ItemParagraphCommentBinding, item: ParagraphCommentItem) {
        val btn = binding.tvReplyBtn
        when {
            item.repliesLoading -> {
                btn.text = context.getString(R.string.paragraph_comment_loading)
                btn.visible()
                btn.isEnabled = false
            }

            item.repliesLoaded -> {
                if (item.replies.isEmpty()) {
                    btn.gone()
                } else {
                    btn.text = context.getString(R.string.paragraph_comment_collapse_replies)
                    btn.visible()
                    btn.isEnabled = true
                }
            }

            else -> {
                // 无评论 ID（如番茄主楼无 Id 字段）时只能展开评论自带的内嵌回复，
                // 按钮条数以实际内嵌条数为准，避免"展开 N 条"却只显示预览里的几条
                val showCount = if (item.id.isBlank()) item.replies.size else item.replyCount
                if (showCount <= 0) {
                    btn.gone()
                } else {
                    btn.text = context.getString(
                        R.string.paragraph_comment_expand_replies,
                        showCount
                    )
                    btn.visible()
                    btn.isEnabled = true
                }
            }
        }
    }

    /** 展开/收起回复列表 */
    private fun bindReplies(binding: ItemParagraphCommentBinding, item: ParagraphCommentItem) {
        val container = binding.replyContainer
        container.removeAllViews()
        if (item.repliesLoaded) {
            container.visible()
            item.replies.forEach { reply ->
                val replyBinding = ItemParagraphReplyBinding.inflate(inflater, container, false)
                replyBinding.apply {
                    ImageLoader.load(context, reply.avatar)
                        .placeholder(R.drawable.image_cover_default)
                        .error(R.drawable.image_cover_default)
                        .circleCrop()
                        .into(ivAvatar)
                    tvNickname.text = reply.nickname.ifBlank {
                        context.getString(R.string.paragraph_comment_anonymous)
                    }
                    if (reply.replyTo.isBlank()) {
                        tvReplyTo.gone()
                    } else {
                        tvReplyTo.text = context.getString(
                            R.string.paragraph_comment_reply_to,
                            reply.replyTo
                        )
                        tvReplyTo.visible()
                    }
                    val text = formatContent(reply.content)
                    tvContent.text = text.ifBlank {
                        if (reply.images.isNotEmpty() || reply.audio.isNotBlank()) {
                            context.getString(R.string.paragraph_comment_image)
                        } else {
                            ""
                        }
                    }
                    bindImages(replyBinding.llImages, replyBinding.ivImg1, replyBinding.ivImg2, replyBinding.ivImg3, reply.images)
                    bindAudio(replyBinding.tvAudio, reply.audio)
                    tvTime.text = formatTime(reply.time)
                    if (reply.agree > 0) {
                        tvAgree.text = context.getString(
                            R.string.paragraph_comment_like,
                            reply.agree
                        )
                        tvAgree.visible()
                    } else {
                        tvAgree.gone()
                    }
                }
                container.addView(replyBinding.root)
            }
        } else {
            container.gone()
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemParagraphCommentBinding) {
        binding.tvReplyBtn.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                replyListener?.onToggleReplies(item)
            }
        }
    }

    /** 绑定评论/回复图片：单行最多 3 张，超过则只显示前 3 张；点击图片打开大图 */
    private fun bindImages(
        container: View,
        img1: ImageView,
        img2: ImageView,
        img3: ImageView,
        images: List<String>
    ) {
        val list = images.take(3)
        if (list.isEmpty()) {
            container.gone()
            return
        }
        container.visible()
        val views = listOf(img1, img2, img3)
        list.forEachIndexed { index, url ->
            ImageLoader.load(context, url)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .into(views[index])
            views[index].setOnClickListener {
                if (url.isNotBlank()) imageListener?.onImageClick(url)
            }
        }
        for (i in list.size until 3) {
            views[i].gone()
        }
    }

    /** 绑定语音评论条：可点击，根据加载/播放状态切换文案 */
    private fun bindAudio(tvAudio: TextView, item: ParagraphCommentItem) {
        if (item.audio.isBlank()) {
            tvAudio.gone()
            return
        }
        tvAudio.visible()
        tvAudio.text = when {
            item.audioLoading -> context.getString(R.string.paragraph_comment_loading)
            item.audioPlaying -> context.getString(R.string.paragraph_comment_playing)
            else -> context.getString(R.string.paragraph_comment_voice)
        }
        tvAudio.setOnClickListener { audioListener?.onToggleAudio(item) }
    }

    /** 绑定回复语音标记（无播放能力，仅提示） */
    private fun bindAudio(tvAudio: TextView, audio: String) {
        if (audio.isBlank()) {
            tvAudio.gone()
        } else {
            tvAudio.text = context.getString(R.string.paragraph_comment_voice)
            tvAudio.visible()
        }
    }

    companion object {
        /** 起点段评表情码映射（与镜像站前端 qd.html 的 commentEmojiMap 一致） */
        private val COMMENT_EMOJI_MAP: Map<Int, String> = mapOf(
            1 to "👏", 2 to "🌹", 3 to "🤝", 4 to "😁", 5 to "😄", 6 to "🥺", 7 to "🙂", 8 to "😏",
            9 to "😙", 10 to "👆🏻🐽", 11 to "🙄", 12 to "😭", 13 to "😵", 14 to "😥", 15 to "🖕🏻", 16 to "🥵",
            17 to "😓", 18 to "🤫", 19 to "😂", 20 to "😢", 21 to "😍", 22 to "🤕🔨", 23 to "😑", 24 to "😫",
            25 to "🤗", 26 to "🤪", 27 to "🙏", 28 to "😣", 29 to "💪", 30 to "💀", 31 to "😳", 32 to "😎",
            33 to "🤭", 34 to "😄👏", 35 to "👍🏻", 36 to "🤓", 37 to "😡", 38 to "🙁", 39 to "😄❓", 40 to "😞",
            41 to "😧", 42 to "💋", 43 to "☺️", 44 to "🤬", 45 to "😴", 46 to "🤠🚬", 47 to "😱", 48 to "🐷",
            49 to "😪", 50 to "🤐", 51 to "🥴", 52 to "🌙", 53 to "❤️", 54 to "🔪", 55 to "🎁", 56 to "💔",
            57 to "👊🏻", 58 to "😒", 59 to "✌🏻️", 60 to "😮", 61 to "🤨", 62 to "😴", 63 to "👏🏻", 64 to "🐲"
        )

        /** 段评内容里的表情码标记，如 `[fn=12]` / `*[fn＝12]` */
        private val FN_EMOJI_REGEX = Regex("""\*?\[fn[=＝](\d+)\]""")

        /** 中文名表情占位符，如 `[笑哭]`（番茄等站点评论里的常见表情） */
        private val FQ_EMOJI_REGEX = Regex("""\[[^\[\]]+\]""")

        /** 番茄/中文段评占位符 → Unicode 表情。番茄评论的表情是 `[名称]` 占位符，
         *  站点前端由后端 emojiEndpoint 映射为图片，原生端用 Unicode emoji 兜底展示。 */
        private val FQ_EMOJI_MAP: Map<String, String> = mapOf(
            // 笑 / 开心
            "[笑哭]" to "😂", "[大笑]" to "😄", "[笑]" to "😄", "[哈哈]" to "😃", "[嘻嘻]" to "😁", "[憨笑]" to "😊",
            "[微笑]" to "🙂", "[呲牙]" to "😁", "[开心]" to "😄", "[高兴]" to "😀", "[得意]" to "😏",
            "[坏笑]" to "😏", "[奸笑]" to "😏", "[偷笑]" to "😏", "[阴险]" to "😏", "[滑稽]" to "🤪",
            "[调皮]" to "😜", "[吐舌]" to "😛", "[哇]" to "😮", "[期待]" to "🥰",
            // 喜欢 / 害羞
            "[色]" to "😍", "[花痴]" to "😍", "[可爱]" to "🥰", "[害羞]" to "😳", "[脸红]" to "😳",
            "[眨眼]" to "😉", "[飞吻]" to "😘", "[亲亲]" to "😗", "[送心]" to "😘",
            // 哭 / 难过
            "[大哭]" to "😭", "[流泪]" to "😢", "[哭]" to "😭", "[哭泣]" to "😢", "[委屈]" to "🥺", "[可怜]" to "🥺",
            "[难过]" to "😔", "[失望]" to "😞", "[伤心]" to "😢", "[不开心]" to "😞",
            // 生气
            "[生气]" to "😡", "[发怒]" to "😡", "[愤怒]" to "😡", "[抓狂]" to "🤬", "[怄火]" to "😤",
            "[哼]" to "😤", "[不服]" to "😤",
            // 惊讶 / 恐惧
            "[惊讶]" to "😲", "[惊呆]" to "😲", "[震惊]" to "😱", "[惊恐]" to "😱", "[吓]" to "😱",
            "[恐惧]" to "😨", "[吃惊]" to "😮",
            // 无语 / 鄙视 / 思考
            "[无语]" to "😒", "[白眼]" to "🙄", "[翻白眼]" to "🙄", "[鄙视]" to "😒", "[嫌弃]" to "🙄",
            "[傲慢]" to "😏", "[抠鼻]" to "🤨", "[疑惑]" to "🤔", "[疑问]" to "🤔", "[思考]" to "🤔", "[嘘]" to "🤫",
            // 尴尬 / 汗
            "[尴尬]" to "😅", "[汗]" to "😓", "[流汗]" to "😓", "[擦汗]" to "😅", "[冷汗]" to "😰", "[天啊]" to "😱",
            // 困 / 累 / 晕
            "[困]" to "😴", "[睡觉]" to "😴", "[哈欠]" to "🥱", "[发呆]" to "😶", "[晕]" to "😵",
            // 手势 / 动作
            "[强]" to "👍", "[赞]" to "👍", "[good]" to "👍", "[弱]" to "👎", "[加油]" to "💪", "[奋斗]" to "💪",
            "[抱拳]" to "🙏", "[握手]" to "🤝", "[胜利]" to "✌️", "[耶]" to "✌️", "[拳头]" to "👊", "[鼓掌]" to "👏",
            "[拜拜]" to "👋", "[打call]" to "🙌", "[OK]" to "👌", "[666]" to "6️⃣", "[比心]" to "🫶",
            "[收到]" to "👍", "[好的]" to "👍", "[狗头]" to "🐶", "[吃瓜]" to "🍉",
            // 物品 / 符号
            "[爱心]" to "❤️", "[心]" to "❤️", "[心碎]" to "💔", "[玫瑰]" to "🌹", "[鲜花]" to "🌸",
            "[礼物]" to "🎁", "[蛋糕]" to "🎂", "[啤酒]" to "🍺", "[咖啡]" to "☕", "[西瓜]" to "🍉",
            "[月亮]" to "🌙", "[太阳]" to "☀️", "[星星]" to "⭐", "[便便]" to "💩", "[骷髅]" to "💀",
            "[炸弹]" to "💣", "[闪电]" to "⚡", "[烟花]" to "🎆", "[爆竹]" to "🧨", "[干杯]" to "🍻"
        )

        /** 将段评内容中的表情占位符替换为真正的 emoji：
         *  先替换 `[中文名]`（番茄等），再替换起点 `[fn=N]` 表情码，未知占位符保留原文 */
        fun formatContent(text: String): String {
            if (text.isBlank()) return text
            val fqReplaced = FQ_EMOJI_REGEX.replace(text) { match ->
                FQ_EMOJI_MAP[match.value] ?: match.value
            }
            return FN_EMOJI_REGEX.replace(fqReplaced) { match ->
                val code = match.groupValues[1].toIntOrNull()
                code?.let { COMMENT_EMOJI_MAP[it] } ?: match.value
            }
        }

        /** 时间戳（毫秒）转可读时间；兼容秒级时间戳 */
        fun formatTime(time: Long): String {
            if (time <= 0) return ""
            val t = if (time < 100_000_000_000L) time * 1000 else time
            return AppConst.dateFormat.format(Date(t))
        }
    }
}
