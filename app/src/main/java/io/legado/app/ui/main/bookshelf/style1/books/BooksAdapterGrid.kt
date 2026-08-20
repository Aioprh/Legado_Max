package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.databinding.ItemBookshelfGrid2Binding
import io.legado.app.databinding.ItemBookshelfGridBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import splitties.views.onLongClick

class BooksAdapterGrid(context: Context, private val callBack: CallBack) :
    BaseBooksAdapter<ViewBinding>(context) {
    private val showBookname = AppConfig.showBookname
    override fun getViewBinding(parent: ViewGroup): ViewBinding {
        return when (showBookname) {
            2 -> ItemBookshelfGrid2Binding.inflate(inflater, parent, false)
            else -> ItemBookshelfGridBinding.inflate(inflater, parent, false)
        }
    }

    /**
     * 方案E：取消封面图片加载
     */
    override fun cancelCoverLoad(binding: ViewBinding) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.ivCover.cancelLoad()
            is ItemBookshelfGrid2Binding -> binding.ivCover.cancelLoad()
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ViewBinding,
        item: BookShelfDisplay,
        payloads: MutableList<Any>
    ) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.run {
                if (payloads.isEmpty()) {
                    if (showBookname == 0) {
                        tvName.visible()
                        tvName.text = item.name
                    } else {
                        tvName.gone()
                    }
                    ivCover.load(item, false)
                    upRefresh(binding, item)
                } else {
                    for (i in payloads.indices) {
                        val bundle = payloads[i] as Bundle
                        bundle.keySet().forEach {
                            when (it) {
                                "name" -> tvName.text = item.name
                                "cover" -> ivCover.load(item, false)
                                "refresh" -> upRefresh(binding, item)
                            }
                        }
                    }
                }
            }
            is ItemBookshelfGrid2Binding -> binding.run {
                if (payloads.isEmpty()) {
                    tvName.text = item.name
                    ivCover.load(item, false)
                    upRefresh(binding, item)
                } else {
                    for (i in payloads.indices) {
                        val bundle = payloads[i] as Bundle
                        bundle.keySet().forEach {
                            when (it) {
                                "name" -> tvName.text = item.name
                                "cover" -> ivCover.load(item, false)
                                "refresh" -> upRefresh(binding, item)
                            }
                        }
                    }
                }
            }
        }

    }

    private fun upRefresh(binding: ViewBinding, item: BookShelfDisplay) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.run {
                if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                    bvUnread.invisible()
                    rlLoading.visible()
                } else {
                    rlLoading.inVisible()
                    if (AppConfig.showUnread) {
                        bvUnread.setBadgeCount(item.getUnreadChapterNum())
                        bvUnread.setHighlight(item.lastCheckCount > 0)
                    } else {
                        bvUnread.invisible()
                    }
                }
            }
            is ItemBookshelfGrid2Binding -> binding.run {
                if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                    bvUnread.invisible()
                    rlLoading.visible()
                } else {
                    rlLoading.inVisible()
                    if (AppConfig.showUnread) {
                        bvUnread.setBadgeCount(item.getUnreadChapterNum())
                        bvUnread.setHighlight(item.lastCheckCount > 0)
                    } else {
                        bvUnread.invisible()
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ViewBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it.toMinimalBook())
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it.toMinimalBook())
                }
            }
        }
    }
}
