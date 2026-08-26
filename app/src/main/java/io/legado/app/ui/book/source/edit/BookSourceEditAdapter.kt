package io.legado.app.ui.book.source.edit

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.safeTake

class BookSourceEditAdapter : RecyclerView.Adapter<BookSourceEditAdapter.MyViewHolder>() {

    companion object {
        /**
         * 长文本截断预览阈值。超过该字符数的字段在列表中只显示截断预览（只读），
         * 全文始终保存在 EditEntity.value，查看/编辑走全屏编辑器。
         * 保证列表内 EditText 的 StaticLayout 排版成本恒定有界，
         * 避免 Tab 切换时超大文本反复全文排版导致主线程冻结。
         */
        const val PREVIEW_MAX_CHARS = 1000

        // 预览模式下截断文本的显示行数上限
        const val PREVIEW_MAX_LINES = 10
    }

    // P2: 当用户设置 maxLine >= 999（即不限行数）时，在列表中 clamp 为 30 行，
    // 避免 TextView 对全文做 StaticLayout 排版导致 measure 极重。
    // 用户需要查看/编辑完整内容时通过已有的"全屏编辑"入口。
    val editEntityMaxLine = if (AppConfig.sourceEditMaxLine >= 999) 30 else AppConfig.sourceEditMaxLine

    var editEntities: ArrayList<EditEntity> = ArrayList()
        set(value) {
            field = value
        }

    /**
     * 预览模式字段被点击时请求打开全屏编辑的回调，由 Activity 设置。
     */
    var onRequestFullEdit: ((EditEntity) -> Unit)? = null

    private var attachedRecyclerView: RecyclerView? = null
    private val pendingHighlightPositions = mutableSetOf<Int>()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (attachedRecyclerView != null && attachedRecyclerView !== recyclerView) {
            attachedRecyclerView?.removeOnScrollListener(scrollListener)
        }
        attachedRecyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(scrollListener)
        attachedRecyclerView = null
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                highlightPendingVisibleItems(recyclerView)
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            highlightPendingVisibleItems(recyclerView)
        }
    }

    private fun highlightPendingVisibleItems(rv: RecyclerView) {
        if (pendingHighlightPositions.isEmpty()) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val toHighlight = pendingHighlightPositions.filter { it in first..last }
        for (pos in toHighlight) {
            val holder = rv.findViewHolderForAdapterPosition(pos) as? MyViewHolder ?: continue
            if (holder.binding.editText.getTag(R.id.tag3) == null) {
                holder.binding.editText.requestHighlight()
                holder.binding.editText.setTag(R.id.tag3, true)
            }
        }
        pendingHighlightPositions.removeAll(toHighlight.toSet())
    }

    fun highlightVisibleItems() {
        val rv = attachedRecyclerView ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        for (pos in first..last) {
            val holder = rv.findViewHolderForAdapterPosition(pos) as? MyViewHolder ?: continue
            if (holder.binding.editText.getTag(R.id.tag3) == null) {
                holder.binding.editText.requestHighlight(pos - first)
                holder.binding.editText.setTag(R.id.tag3, true)
            }
        }
        pendingHighlightPositions.clear()
        for (pos in 0 until itemCount) {
            if (pos !in first..last) {
                pendingHighlightPositions.add(pos)
            }
        }
    }

    fun cancelAllPendingHighlights() {
        pendingHighlightPositions.clear()
        val rv = attachedRecyclerView ?: return
        for (i in 0 until rv.childCount) {
            val holder = rv.getChildViewHolder(rv.getChildAt(i)) as? MyViewHolder ?: continue
            holder.binding.editText.cancelHighlighterRender()
            holder.binding.editText.setTag(R.id.tag3, null)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = ItemSourceEditBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        binding.editText.addLegadoPattern()
        binding.editText.addJsonPattern()
        binding.editText.addJsPattern()
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bind(editEntities[position])
    }

    override fun getItemCount(): Int {
        return editEntities.size
    }

    inner class MyViewHolder(val binding: ItemSourceEditBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // P1: 记录上次绑定的 key 与 value，用于判断是否需要跳过 setText
        private var lastBoundKey: String? = null
        private var lastBoundValue: String? = null
        // 当前是否处于截断预览模式（供 attach 监听器判断是否恢复可聚焦）
        private var isPreviewMode = false

        fun bind(editEntity: EditEntity) = binding.run {
            editText.setTag(R.id.tag, editEntity.key)
            editText.setTag(R.id.tag3, null)
            val fullValue = editEntity.value
            val needPreview = (fullValue?.length ?: 0) > PREVIEW_MAX_CHARS
            if (editText.getTag(R.id.tag1) == null) {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        editText.isCursorVisible = false
                        editText.isCursorVisible = true
                        // 预览模式保持不可聚焦，避免被键盘工具栏等流程误写入截断文本
                        if (!isPreviewMode) {
                            editText.isFocusable = true
                            editText.isFocusableInTouchMode = true
                        }
                    }

                    override fun onViewDetachedFromWindow(v: View) {

                    }
                }
                editText.addOnAttachStateChangeListener(listener)
                editText.setTag(R.id.tag1, listener)
            }
            editText.getTag(R.id.tag2)?.let {
                if (it is TextWatcher) {
                    editText.removeTextChangedListener(it)
                }
                editText.setTag(R.id.tag2, null)
            }
            // P1: key/value/预览态 均未变化时跳过 setText，避免无谓的全文重排版；
            // 预览态变化必须重绑
            val unchanged = editEntity.key == lastBoundKey
                    && fullValue == lastBoundValue
                    && needPreview == isPreviewMode
            lastBoundKey = editEntity.key
            lastBoundValue = fullValue
            isPreviewMode = needPreview
            textInputLayout.hint = editEntity.hint
            if (needPreview) {
                // 预览模式：EditText 只持有截断文本，排版成本有界；
                // 不注册业务 TextWatcher，防止截断文本回写覆盖 entity.value 全文
                editText.maxLines = PREVIEW_MAX_LINES
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
                textInputLayout.helperText = editText.context.getString(
                    R.string.source_edit_preview_truncated, fullValue!!.length
                )
                // EditText 会消费触摸事件，必须同时在 editText 和 itemView 上挂点击监听，
                // 否则点击文本区域无法触发全屏编辑
                val fullEditClickListener = View.OnClickListener { onRequestFullEdit?.invoke(editEntity) }
                editText.setOnClickListener(fullEditClickListener)
                itemView.setOnClickListener(fullEditClickListener)
                if (!unchanged) {
                    editText.skipNextHighlight = true
                    editText.cancelHighlighterRender()
                    editText.setText(fullValue.safeTake(PREVIEW_MAX_CHARS))
                    editText.skipNextHighlight = false
                }
                return
            }
            // 正常模式：恢复可编辑状态，走原有绑定流程
            editText.maxLines = editEntityMaxLine
            editText.isFocusable = true
            editText.isFocusableInTouchMode = true
            editText.setOnClickListener(null)
            editText.isClickable = false
            textInputLayout.helperText = null
            itemView.setOnClickListener(null)
            itemView.isClickable = false
            if (unchanged) {
                // 内容未变，只更新 TextWatcher 指向的 entity，跳过 setText
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                    }

                    override fun afterTextChanged(s: Editable?) {
                        editEntity.value = (s?.toString())
                    }
                }
                editText.addTextChangedListener(textWatcher)
                editText.setTag(R.id.tag2, textWatcher)
                return
            }
            // P0: setText 前关闭 CodeView 内部高亮级联，避免 bind 时触发全文正则匹配 + span 操作
            editText.skipNextHighlight = true
            editText.cancelHighlighterRender()
            editText.setText(fullValue)
            editText.skipNextHighlight = false
            // 高亮由 Activity 通过 highlightVisibleItems() 统一管理，bind 中不再触发
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {

                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                }

                override fun afterTextChanged(s: Editable?) {
                    editEntity.value = (s?.toString())
                }
            }
            editText.addTextChangedListener(textWatcher)
            editText.setTag(R.id.tag2, textWatcher)
            // P6: 移除逐个 clearFocus()，setEditEntities() 已有全局 clearFocus
        }
    }

}
