package io.legado.app.help

import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
// 导出结果处理单例类
object ExportResultHandler {

    fun handleExportResult(
        activity: AppCompatActivity,
        result: HandleFileContract.Result,
        onCopy: (String) -> Unit
    ) {
        result.clipboardJson?.let { json ->
            onCopy(json)
            activity.toastOnUi("已复制到剪贴板")
            return
        }
        result.uri?.let { uri ->
            activity.alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(activity.layoutInflater).apply {
                    editView.hint = activity.getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    onCopy(uri.toString())
                }
            }
        }
    }
}
