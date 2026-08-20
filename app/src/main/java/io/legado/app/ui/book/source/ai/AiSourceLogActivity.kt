package io.legado.app.ui.book.source.ai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.databinding.ActivityAiSourceLogBinding
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * AI 生成书源 - 运行日志页
 *
 * 展示 [AiSourceLog] 缓冲中记录的「抓取 HTML / AI 生成 / 规则校验 / 自动修复」等步骤与错误，
 * 支持一键复制全部日志与清空。
 */
class AiSourceLogActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityAiSourceLogBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.titleBar.setNavigationOnClickListener { finish() }
        refresh()
        binding.btnCopy.setOnClickListener {
            val text = binding.tvLog.text?.toString().orEmpty()
            if (text.isBlank()) {
                toastOnUi("暂无日志")
            } else {
                sendToClip(text)
            }
        }
        binding.btnClear.setOnClickListener {
            AiSourceLog.clear()
            refresh()
            toastOnUi("已清空日志")
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val dump = AiSourceLog.dump()
        binding.tvLog.text = if (dump.isBlank()) getString(R.string.ai_log_empty) else dump
    }
}