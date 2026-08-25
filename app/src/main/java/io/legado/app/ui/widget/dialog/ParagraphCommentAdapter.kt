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

    var replyListener: ReplyListener? = null

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
            bindAudio(binding.tvAudio, item.audio)

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
                if (item.replyCount <= 0) {
                    btn.gone()
                } else {
                    btn.text = context.getString(
                        R.string.paragraph_comment_expand_replies,
                        item.replyCount
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

    /** 绑定评论/回复图片：单行最多 3 张，超过则只显示前 3 张 */
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
        }
        for (i in list.size until 3) {
            views[i].gone()
        }
    }

    /** 绑定语音评论标记 */
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

        /** 将段评内容中的 `[fn=N]` 表情码替换为对应 emoji，未知码保留原文 */
        fun formatContent(text: String): String {
            if (text.isBlank()) return text
            return FN_EMOJI_REGEX.replace(text) { match ->
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
