package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.databinding.ActivityAiReaderBinding
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * AI 阅读助手结果页
 *
 * 复用 AI 书源生成的接口配置，对阅读页传来的请求（选中文解释/章节总结）调用 AI，
 * 展示结果，支持一键复制与重新生成。
 */
class AiReaderActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityAiReaderBinding::inflate)
    private val viewModel by viewModels<AiReaderViewModel>()

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTEXT = "context"
        const val EXTRA_TASK = "task"
        const val EXTRA_MODE = "mode"

        const val MODE_EXPLAIN = "explain"
        const val MODE_SUMMARY = "summary"
    }

    private var title: String = ""
    private var context: String = ""
    private var task: String = ""
    private var mode: String = MODE_SUMMARY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.titleBar.setNavigationOnClickListener { finish() }

        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        context = intent.getStringExtra(EXTRA_CONTEXT).orEmpty()
        task = intent.getStringExtra(EXTRA_TASK).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SUMMARY

        binding.titleBar.setTitle(
            if (mode == MODE_EXPLAIN) getString(R.string.ai_reader_explain_title)
            else getString(R.string.ai_reader_title)
        )

        binding.btnCopy.setOnClickListener {
            val text = binding.tvResult.text?.toString().orEmpty()
            if (text.isBlank() || text == getString(R.string.ai_reader_empty)) {
                toastOnUi(getString(R.string.ai_reader_empty))
            } else {
                sendToClip(text)
                toastOnUi(R.string.ai_reader_copied)
            }
        }
        binding.btnRegenerate.setOnClickListener { runAi() }
    }

    override fun onResume() {
        super.onResume()
        // 首次进入自动生成
        if (binding.tvResult.text.isNullOrBlank()) runAi()
    }

    private fun runAi() {
        binding.btnRegenerate.isEnabled = false
        binding.tvLoading.visibility = View.VISIBLE
        binding.tvResult.text = ""
        viewModel.execute {
            viewModel.aiChat(title, context, task, mode)
        }.onSuccess { result ->
            binding.tvLoading.visibility = View.GONE
            binding.tvResult.text = result.ifBlank { getString(R.string.ai_reader_empty) }
            binding.btnRegenerate.isEnabled = true
        }.onError {
            binding.tvLoading.visibility = View.GONE
            binding.tvResult.text = getString(R.string.ai_reader_error, it.message.orEmpty())
            binding.btnRegenerate.isEnabled = true
        }
    }
}