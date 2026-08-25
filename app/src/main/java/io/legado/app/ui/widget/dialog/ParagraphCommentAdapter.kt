package io.legado.app.ui.widget.dialog

import android.content.Context
import android.view.ViewGroup
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

            tvContent.text = item.content

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
                    tvContent.text = reply.content
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

    companion object {
        /** 时间戳（毫秒）转可读时间；兼容秒级时间戳 */
        fun formatTime(time: Long): String {
            if (time <= 0) return ""
            val t = if (time < 100_000_000_000L) time * 1000 else time
            return AppConst.dateFormat.format(Date(t))
        }
    }
}
