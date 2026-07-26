package io.legado.app.ui.widget.dialog

import android.os.Bundle
import io.legado.app.R
import io.legado.app.help.IntentData
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern

/**
 * 代码查看/编辑对话框
 *
 * 用于导入书源、订阅源、替换规则、TTS、字典规则、主题等时查看和编辑 JSON 内容。
 *
 * - 只读模式（disableEdit = true）：仅查看，隐藏保存/重置按钮，禁用编辑
 * - 编辑模式（disableEdit = false）：可编辑，通过 [Callback] 回调保存
 *
 * 继承 [BaseContentEditDialog]，拥有搜索、全屏编辑器、复制全部等功能。
 */
class CodeDialog() : BaseContentEditDialog() {

    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
        }
    }

    override fun getTitle(): CharSequence {
        return if (isReadOnly) "code view" else getString(R.string.edit_content)
    }

    override val isReadOnly: Boolean
        get() = arguments?.getBoolean("disableEdit") == true

    override fun getSourceType(): String = "codeDialog"

    override fun setupContentView() {
        binding.contentView.addLegadoPattern()
        binding.contentView.addJsonPattern()
        binding.contentView.addJsPattern()
    }

    override fun onContentReady() {
        arguments?.getString("code")?.let {
            val code: String? = IntentData.get(it)
            if (code != null) {
                binding.contentView.setText(code)
                originalContent = null
            }
        }
    }

    override fun onSave(content: String): Boolean {
        val requestId = arguments?.getString("requestId")
        (parentFragment as? Callback)?.onCodeSave(content, requestId)
            ?: (activity as? Callback)?.onCodeSave(content, requestId)
        return true
    }

    override fun onReset() {
        // 恢复为原始内容
        arguments?.getString("code")?.let {
            val code: String? = IntentData.get(it)
            if (code != null) {
                binding.contentView.setText(code)
                originalContent = null
            }
        }
    }

    interface Callback {
        fun onCodeSave(code: String, requestId: String?)
    }
}
