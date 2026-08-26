package io.legado.app.ui.rss.source.edit

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.databinding.ItemSourceEditCheckBoxBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.isTrue

class RssSourceEditAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // P2: 当用户设置 maxLine >= 999（即不限行数）时，在列表中 clamp 为 30 行，
    // 避免 TextView 对全文做 StaticLayout 排版导致 measure 极重。
    // 用户需要查看/编辑完整内容时通过已有的"全屏编辑"入口。
    val editEntityMaxLine = if (AppConfig.sourceEditMaxLine >= 999) 30 else AppConfig.sourceEditMaxLine

    var editEntities: ArrayList<EditEntity> = ArrayList()
        set(value) {
            field = value
        }

    override fun getItemViewType(position: Int): Int {
        val item = editEntities[position]
        return item.viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            EditEntity.ViewType.checkBox -> {
                val binding = ItemSourceEditCheckBoxBinding
                    .inflate(LayoutInflater.from(parent.context), parent, false)
                CheckBoxViewHolder(binding)
            }

            else -> {
                val binding = ItemSourceEditBinding
                    .inflate(LayoutInflater.from(parent.context), parent, false)
                binding.editText.addLegadoPattern()
                binding.editText.addJsonPattern()
                binding.editText.addJsPattern()
                EditTextViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CheckBoxViewHolder -> holder.bind(editEntities[position])
            is EditTextViewHolder -> holder.bind(editEntities[position])
        }
    }

    override fun getItemCount(): Int {
        return editEntities.size
    }

    inner class EditTextViewHolder(val binding: ItemSourceEditBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // P1: 记录上次绑定的 key，用于判断是否需要跳过 setText
        private var lastBoundKey: String? = null

        fun bind(editEntity: EditEntity) = binding.run {
            editText.setTag(R.id.tag, editEntity.key)
            editText.maxLines = editEntityMaxLine
            if (editText.getTag(R.id.tag1) == null) {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        editText.isCursorVisible = false
                        editText.isCursorVisible = true
                        editText.isFocusable = true
                        editText.isFocusableInTouchMode = true
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
            }
            // P1: 如果 key 相同且文本内容一致，跳过 setText 及相关高亮操作
            val currentText = editText.text?.toString()
            if (editEntity.key == lastBoundKey && currentText == editEntity.value) {
                // 内容未变，只更新 TextWatcher 指向的 entity
                textInputLayout.hint = editEntity.hint
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
            lastBoundKey = editEntity.key
            // P0: setText 前关闭 CodeView 内部高亮级联，避免 bind 时触发全文正则匹配 + span 操作
            editText.skipNextHighlight = true
            editText.cancelHighlighterRender()
            editText.setText(editEntity.value)
            editText.skipNextHighlight = false
            // bind 后请求一次高亮，恢复初始语法着色（延迟执行，避免在 bind 同步流程中堆叠）
            editText.requestHighlight()
            textInputLayout.hint = editEntity.hint
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

    class CheckBoxViewHolder(val binding: ItemSourceEditCheckBoxBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(editEntity: EditEntity) = binding.run {
            checkBox.text = editEntity.hint
            checkBox.isChecked = editEntity.value.isTrue()
            checkBox.setOnUserCheckedChangeListener { isChecked ->
                editEntity.value = isChecked.toString()
            }
        }

    }


}