package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAddCustomDocBinding
import io.legado.app.help.CustomHelpDocManager
import io.legado.app.help.HelpDocManager
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AddCustomDocDialog : BaseDialogFragment(R.layout.dialog_add_custom_doc) {

    private val binding by viewBinding(DialogAddCustomDocBinding::bind)

    var onDocAdded: (() -> Unit)? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.setBackgroundColor(primaryColor)

        // 设置分组下拉列表
        setupGroupSpinner()

        // 取消按钮
        binding.cancelBtn.setOnClickListener {
            dismissAllowingStateLoss()
        }

        // 添加按钮
        binding.addBtn.setOnClickListener {
            addDocument()
        }
    }

    private fun setupGroupSpinner() {
        val customGroups = HelpDocManager.getCustomGroups(requireContext())

        if (customGroups.isEmpty()) {
            Toast.makeText(requireContext(), "请先创建分组", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        val groupNames = customGroups.map { it.displayName }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            groupNames
        )
        binding.groupSpinner.adapter = adapter
    }

    private fun addDocument() {
        val docName = binding.docNameInput.text.toString().trim()
        val docContent = binding.docContentInput.text.toString()

        // 验证文档名
        if (docName.isEmpty()) {
            Toast.makeText(requireContext(), "请输入文档名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (!CustomHelpDocManager.isValidFileName(docName)) {
            Toast.makeText(requireContext(), R.string.invalid_group_name, Toast.LENGTH_SHORT).show()
            return
        }

        // 获取选中的分组
        val customGroups = HelpDocManager.getCustomGroups(requireContext())
        val selectedGroupIndex = binding.groupSpinner.selectedItemPosition
        val selectedGroup = customGroups.getOrNull(selectedGroupIndex)

        if (selectedGroup == null) {
            Toast.makeText(requireContext(), "请选择分组", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取文件格式
        val extension = if (binding.formatMd.isChecked) "md" else "txt"

        // 构建文件路径
        val filePath = "${selectedGroup.folderPath}/$docName.$extension"

        // 保存文档
        val success = CustomHelpDocManager.saveDoc(filePath, docContent)

        if (success) {
            Toast.makeText(requireContext(), R.string.doc_added_success, Toast.LENGTH_SHORT).show()
            onDocAdded?.invoke()
            dismissAllowingStateLoss()
        } else {
            Toast.makeText(requireContext(), R.string.doc_added_failed, Toast.LENGTH_SHORT).show()
        }
    }
}