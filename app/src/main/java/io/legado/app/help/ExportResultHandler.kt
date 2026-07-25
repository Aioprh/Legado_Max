package io.legado.app.help

import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.StringUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object ExportResultHandler {

    @OptIn(DelicateCoroutinesApi::class)
    fun handleExportResult(
        activity: AppCompatActivity,
        result: HandleFileContract.Result,
        onCopy: (String) -> Unit,
        type: String? = null
    ) {
        result.clipboardJson?.let { json ->
            onCopy(json)
            activity.toastOnUi("已复制到剪贴板")
            return
        }
        result.uri?.let { uri ->
            val url = uri.toString()
            activity.alert(R.string.export_success) {
                if (url.isAbsUrl()) {
                    // 从数据库读取默认规则的summary
                    GlobalScope.launch(Dispatchers.IO) {
                        val defaultRule = appDb.directLinkUploadRuleDao.getDefault()
                        val summary = defaultRule?.summary ?: DirectLinkUpload.getSummary()
                        activity.runOnUiThread {
                            setMessage(summary)
                        }
                    }
                    if (type != null) {
                        val time = System.currentTimeMillis()
                        shibboleth {
                            val shibbolethUrl = StringUtils.toShibboleth(url, type, time)
                            activity.alert(R.string.shibboleth) {
                                val alertBinding = DialogEditTextBinding.inflate(activity.layoutInflater).apply {
                                    editView.setText(shibbolethUrl)
                                }
                                customView { alertBinding.root }
                                okButton {
                                    onCopy(shibbolethUrl)
                                }
                            }
                        }
                    }
                }
                val alertBinding = DialogEditTextBinding.inflate(activity.layoutInflater).apply {
                    editView.hint = activity.getString(R.string.path)
                    editView.setText(url)
                }
                customView { alertBinding.root }
                okButton {
                    onCopy(url)
                }
            }
        }
    }
}