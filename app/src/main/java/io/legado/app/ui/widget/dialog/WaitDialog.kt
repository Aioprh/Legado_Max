package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import io.legado.app.databinding.DialogWaitBinding
import io.legado.app.help.storage.BackupConfig


@Suppress("unused")
class WaitDialog(context: Context) : Dialog(context) {

    val binding = DialogWaitBinding.inflate(layoutInflater)

    private var restoreProgressCurrent = 0
    private var restoreProgressTotal = 0
    private var restoreProgressStarted = false

    init {
        setCanceledOnTouchOutside(false)
        setContentView(binding.root)
        binding.pb.isIndeterminate = true
    }

    fun setText(text: String): WaitDialog {
        binding.tvMsg.text = text
        updateRestoreProgress(text)
        return this
    }

    fun setText(res: Int): WaitDialog {
        binding.tvMsg.setText(res)
        binding.pb.isIndeterminate = true
        restoreProgressStarted = false
        return this
    }

    private fun updateRestoreProgress(text: String) {
        if (!text.startsWith("恢复：")) return

        val item = text.removePrefix("恢复：").trim()
        if (!restoreProgressStarted) {
            restoreProgressStarted = true
            val ignoreReadConfig = BackupConfig.ignoreReadConfig
            restoreProgressTotal = if (item == "解压备份文件") {
                if (ignoreReadConfig) 29 else 32
            } else {
                if (ignoreReadConfig) 28 else 31
            }
            restoreProgressCurrent = 0
        }

        restoreProgressCurrent = (restoreProgressCurrent + 1).coerceAtMost(restoreProgressTotal)
        binding.pb.isIndeterminate = false
        binding.pb.max = restoreProgressTotal
        binding.pb.progress = restoreProgressCurrent
        binding.tvProgress.text = "${restoreProgressCurrent * 100 / restoreProgressTotal}%  ·  $restoreProgressCurrent/$restoreProgressTotal"
    }

    override fun dismiss() {
        restoreProgressStarted = false
        restoreProgressCurrent = 0
        restoreProgressTotal = 0
        binding.pb.isIndeterminate = true
        super.dismiss()
    }

}