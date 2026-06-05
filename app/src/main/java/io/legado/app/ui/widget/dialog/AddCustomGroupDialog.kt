package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAddCustomGroupBinding
import io.legado.app.help.CustomHelpDocManager
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AddCustomGroupDialog : BaseDialogFragment(R.layout.dialog_add_custom_group) {

    private val binding by viewBinding(DialogAddCustomGroupBinding::bind)

    var onGroupCreated: (() -> Unit)? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.setBackgroundColor(primaryColor)

        binding.cancelBtn.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.createBtn.setOnClickListener {
            val groupName = binding.groupNameInput.text.toString().trim()

            if (groupName.isEmpty()) {
                Toast.makeText(requireContext(), R.string.invalid_group_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!CustomHelpDocManager.isValidFileName(groupName)) {
                Toast.makeText(requireContext(), R.string.invalid_group_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = CustomHelpDocManager.createGroup(requireContext(), groupName)

            if (success) {
                Toast.makeText(requireContext(), R.string.group_created_success, Toast.LENGTH_SHORT).show()
                onGroupCreated?.invoke()
                dismissAllowingStateLoss()
            } else {
                Toast.makeText(requireContext(), R.string.group_created_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
