package io.legado.app.ui.book.source.ai

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import io.legado.app.api.controller.AiSourceController
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityAiSourceGenerateBinding
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * AI 生成书源页面
 *
 * 移植自 DandanLLab/legadoSkill（MIT）的 AI 写书源能力：
 * 1. 配置 OpenAI 兼容接口（地址/Key/模型，本地保存）
 * 2. 抓取目标网站 HTML（经 App 内置代理，自动检测编码）
 * 3. 调用 LLM 分析网页结构生成书源 JSON
 * 4. 校验规则 -> 一键导入书源编辑器
 */
class AiSourceGenerateActivity :
    VMBaseActivity<ActivityAiSourceGenerateBinding, AiSourceGenerateViewModel>() {

    /** 喂给 LLM 的 HTML 最大字符数，避免超上下文 */
    private companion object {
        const val PROMPT_HTML_LIMIT = 40_000
    }

    override val binding by viewBinding(ActivityAiSourceGenerateBinding::inflate)
    override val viewModel by viewModels<AiSourceGenerateViewModel>()

    private var htmlContent: AiSourceController.HtmlContent? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.etBaseUrl.setText(viewModel.baseUrl)
        binding.etApiKey.setText(viewModel.apiKey)
        binding.etModel.setText(viewModel.model)
        binding.btnFetchHtml.setOnClickListener { fetchHtml() }
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnValidate.setOnClickListener { validate() }
        binding.btnImport.setOnClickListener { importToEditor() }
        binding.btnClear.setOnClickListener { clearAll() }
    }

    private fun saveConfig() {
        viewModel.baseUrl = binding.etBaseUrl.text?.toString()?.trim() ?: ""
        viewModel.apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        viewModel.model = binding.etModel.text?.toString()?.trim() ?: ""
    }

    private fun fetchHtml() {
        val url = binding.etUrl.text?.toString()?.trim()
        if (url.isNullOrEmpty()) {
            toastOnUi("请先填写网站地址")
            return
        }
        val keyword = binding.etKeyword.text?.toString()?.trim()
        binding.btnFetchHtml.isEnabled = false
        viewModel.execute {
            viewModel.fetchHtml(url, keyword).getOrElse { throw it }
        }.onSuccess { content ->
            htmlContent = content
            binding.etHtmlPreview.setText(content.html)
            val apiInfo = content.apiEndpoints.joinToString("；") { "${it.type}: ${it.url}" }
            binding.tvHtmlHint.text = buildString {
                append("编码：${content.charset}，共 ${content.length} 字符（已截断）")
                if (apiInfo.isNotBlank()) append("；发现接口：$apiInfo")
                if (!content.sampleSearch.ok && content.sampleSearch.error.isNotBlank()) {
                    append("；接口探测：${content.sampleSearch.error}")
                }
            }
            binding.tvHtmlHint.visibility = View.VISIBLE
            toastOnUi("抓取成功（编码 ${content.charset}，共 ${content.length} 字符）")
        }.onError {
            toastOnUi("抓取失败: ${it.message}")
        }.onFinally {
            binding.btnFetchHtml.isEnabled = true
        }
    }

    private fun generate() {
        saveConfig()
        if (viewModel.baseUrl.isBlank() || viewModel.apiKey.isBlank()) {
            toastOnUi("请先填写 AI 接口地址和 API Key")
            return
        }
        val url = binding.etUrl.text?.toString()?.trim()
        if (url.isNullOrEmpty()) {
            toastOnUi("请先填写网站地址")
            return
        }
        val html = htmlContent?.html
        if (html.isNullOrEmpty()) {
            toastOnUi("请先点击「抓取HTML」获取网页内容")
            return
        }
        val keyword = binding.etKeyword.text?.toString()?.trim()
        val typeName = binding.spType.selectedItem?.toString() ?: "文本"
        val typeIndex = binding.spType.selectedItemPosition
        val content = htmlContent
        val userPrompt = buildString {
            appendLine("网站地址：$url")
            appendLine("搜索关键词：${keyword ?: "（未提供）"}")
            appendLine("书源类型：$typeName（$typeIndex）")
            content?.apiEndpoints?.takeIf { it.isNotEmpty() }?.let { endpoints ->
                appendLine()
                appendLine("【重要】本站为前端 JS 动态渲染（SPA）站点，静态 HTML 中不含书籍数据。自动发现以下 JSON API 接口，请优先基于这些接口编写 JSONPath 规则：")
                endpoints.forEach { appendLine("- ${it.type}: ${it.url}") }
            }
            content?.sampleSearch?.let { ss ->
                appendLine()
                if (ss.ok) {
                    appendLine("搜索接口示例响应（JSON）：")
                    appendLine("----------")
                    appendLine(ss.json)
                    appendLine("----------")
                } else {
                    appendLine("搜索接口探测失败：${ss.error}")
                }
            }
            content?.sampleCatalog?.let { sc ->
                appendLine()
                if (sc.ok) {
                    appendLine("目录接口示例响应（JSON，用于编写目录/正文规则）：")
                    appendLine("----------")
                    appendLine(sc.json)
                    appendLine("----------")
                } else {
                    appendLine("目录接口探测失败：${sc.error}")
                }
            }
            appendLine()
            appendLine("已抓取到的网页 HTML（预处理后，编码 ${content?.charset}，共 ${content?.length} 字符，已截断）：")
            appendLine("----------")
            val htmlForPrompt = if (html.length > PROMPT_HTML_LIMIT) {
                html.substring(0, PROMPT_HTML_LIMIT) + "\n...(已截断)"
            } else {
                html
            }
            appendLine(htmlForPrompt)
            appendLine("----------")
            appendLine("请分析以上内容，按系统要求输出完整书源 JSON 数组。若提供了 JSON API 接口与示例响应，请优先使用 JSONPath 规则。")
        }
        binding.btnGenerate.isEnabled = false
        viewModel.execute {
            viewModel.generate(
                baseUrl = viewModel.baseUrl,
                apiKey = viewModel.apiKey,
                model = viewModel.model,
                systemPrompt = AiSourceGenerateViewModel.SYSTEM_PROMPT,
                userPrompt = userPrompt
            )
        }.onSuccess { result ->
            binding.etResult.setText(result)
            toastOnUi("生成完成，可查看/修改 JSON，再验证规则或导入编辑器")
        }.onError {
            toastOnUi("AI 生成失败: ${it.message}")
        }.onFinally {
            binding.btnGenerate.isEnabled = true
        }
    }

    private fun validate() {
        val text = binding.etResult.text?.toString()
        if (text.isNullOrBlank()) {
            toastOnUi("请先生成书源 JSON")
            return
        }
        val checks = AiSourceValidate.validate(text)
        binding.tvChecks.text = checks.joinToString("\n") { c ->
            "${if (c.pass) "✓" else "✗"} ${c.name}${if (c.pass) "" else "：${c.msg}"}"
        }
        val pass = checks.count { it.pass }
        toastOnUi("验证完成：$pass/${checks.size} 项通过")
    }

    private fun importToEditor() {
        val text = binding.etResult.text?.toString()
        if (text.isNullOrBlank()) {
            toastOnUi("请先生成书源 JSON")
            return
        }
        val obj = AiSourceValidate.parseSource(text)
        if (obj == null) {
            toastOnUi("导入失败: 书源格式不正确")
            return
        }
        startActivity<BookSourceEditActivity> {
            putExtra("sourceJson", obj.toString())
        }
    }

    private fun clearAll() {
        htmlContent = null
        binding.etHtmlPreview.setText("")
        binding.tvHtmlHint.visibility = View.GONE
        binding.etResult.setText("")
        binding.tvChecks.text = ""
    }
}
