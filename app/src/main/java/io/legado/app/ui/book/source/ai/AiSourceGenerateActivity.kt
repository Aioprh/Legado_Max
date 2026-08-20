package io.legado.app.ui.book.source.ai

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import io.legado.app.api.controller.AiSourceController
import io.legado.app.R
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

    override val binding by viewBinding(ActivityAiSourceGenerateBinding::inflate)
    override val viewModel by viewModels<AiSourceGenerateViewModel>()

    private var htmlContent: AiSourceController.HtmlContent? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.etBaseUrl.setText(viewModel.baseUrl)
        binding.etApiKey.setText(viewModel.apiKey)
        binding.etModel.setText(viewModel.model)
        // 模型预设：选择预设时自动填充模型名
        binding.spModelPreset.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position <= 0) return // 0 = 自定义
                binding.etModel.setText(parent?.getItemAtPosition(position)?.toString())
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
        // 若已保存的模型名属于预设列表，则回显对应项
        val presets = resources.getStringArray(R.array.ai_model_presets)
        val idx = presets.indexOfFirst { it == viewModel.model }
        if (idx > 0) binding.spModelPreset.setSelection(idx)
        binding.btnFetchHtml.setOnClickListener { fetchHtml() }
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnValidate.setOnClickListener { validate() }
        binding.btnAutoFix.setOnClickListener { autoFix() }
        binding.btnImport.setOnClickListener { importToEditor() }
        binding.btnLog.setOnClickListener { startActivity<AiSourceLogActivity>() }
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
        val header = binding.etHeader.text?.toString()
        binding.btnFetchHtml.isEnabled = false
        binding.tvStatus.text = "正在抓取 HTML（${htmlContent?.charset ?: "未知编码"}）..."
        AiSourceLog.log("INFO", "抓取HTML", "开始 $url${keyword?.let { "，关键词 $it" }.orEmpty()}")
        viewModel.execute {
            viewModel.fetchHtml(url, keyword, header).getOrElse { throw it }
        }.onSuccess { content ->
            htmlContent = content
            binding.etHtmlPreview.setText(content.html)
            AiSourceLog.log("INFO", "抓取HTML", "成功：编码 ${content.charset}，${content.length} 字符；发现 ${content.apiEndpoints.size} 个接口、分类导航 ${content.exploreLinks.size} 个")
            content.sampleSearch.error?.takeIf { it.isNotBlank() }?.let {
                AiSourceLog.log("WARN", "接口探测", it)
            }
            content.sampleCatalog.error?.takeIf { it.isNotBlank() }?.let {
                AiSourceLog.log("WARN", "目录探测", it)
            }
            content.sampleExplore.error?.takeIf { it.isNotBlank() }?.let {
                AiSourceLog.log("WARN", "发现探测", it)
            }
            val apiInfo = content.apiEndpoints.joinToString("；") { "${it.method} ${it.type}: ${it.url}" }
            binding.tvHtmlHint.text = buildString {
                append("编码：${content.charset}，共 ${content.length} 字符（已截断）")
                if (content.embeddedJson.isNotEmpty()) append("；内嵌JSON：${content.embeddedJson.size} 块")
                if (apiInfo.isNotBlank()) append("；发现接口：$apiInfo")
                if (!content.sampleSearch.ok && content.sampleSearch.error.isNotBlank()) {
                    append("；接口探测：${content.sampleSearch.error}")
                }
            }
            binding.tvHtmlHint.visibility = View.VISIBLE
            binding.tvStatus.text = "抓取成功（编码 ${content.charset}，共 ${content.length} 字符）"
            toastOnUi("抓取成功（编码 ${content.charset}，共 ${content.length} 字符）")
        }.onError {
            AiSourceLog.log("ERROR", "抓取HTML", "${it.message}")
            binding.tvStatus.text = "抓取失败: ${it.message}"
            toastOnUi("抓取失败: ${it.message}")
        }.onFinally {
            binding.btnFetchHtml.isEnabled = true
        }
    }

    private fun generate() {
        saveConfig()
        if (viewModel.baseUrl.isBlank() || viewModel.apiKey.isBlank()) {
            AiSourceLog.log("WARN", "生成书源", "未填写 AI 接口地址或 API Key，已取消")
            toastOnUi("请先填写 AI 接口地址和 API Key")
            return
        }
        val url = binding.etUrl.text?.toString()?.trim()
        if (url.isNullOrEmpty()) {
            AiSourceLog.log("WARN", "生成书源", "未填写网站地址，已取消")
            toastOnUi("请先填写网站地址")
            return
        }
        val html = htmlContent?.html
        if (html.isNullOrEmpty()) {
            AiSourceLog.log("WARN", "生成书源", "未抓取 HTML，已取消")
            toastOnUi("请先点击「抓取HTML」获取网页内容")
            return
        }
        val keyword = binding.etKeyword.text?.toString()?.trim()
        val typeName = binding.spType.selectedItem?.toString() ?: "文本"
        val typeIndex = binding.spType.selectedItemPosition
        val content = htmlContent
        val userPrompt = buildUserPrompt(url, keyword, typeName, typeIndex, content, html)
        binding.btnGenerate.isEnabled = false
        binding.tvStatus.text = "正在调用 AI 生成书源（模型：${viewModel.model}）..."
        AiSourceLog.log("INFO", "生成书源", "调用 AI 模型 ${viewModel.model}（URL $url 关键词 ${keyword ?: "无"}）")
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
            AiSourceLog.log("SUCCESS", "生成书源", "生成完成，长度 ${result.length}")
            AiSourceLog.raw("-------- 生成书源 JSON --------\n$result")
            binding.tvStatus.text = "生成完成，可查看/修改 JSON，再验证规则或导入编辑器"
            toastOnUi("生成完成，可查看/修改 JSON，再验证规则或导入编辑器")
        }.onError {
            AiSourceLog.log("ERROR", "生成书源", "${it.message}")
            binding.tvStatus.text = "AI 生成失败: ${it.message}"
            toastOnUi("AI 生成失败: ${it.message}")
        }.onFinally {
            binding.btnGenerate.isEnabled = true
        }
    }

    private fun buildUserPrompt(
        url: String,
        keyword: String?,
        typeName: String,
        typeIndex: Int,
        content: AiSourceController.HtmlContent?,
        html: String
    ): String = buildString {
        appendLine("网站地址：$url")
        appendLine("搜索关键词：${keyword ?: "（未提供）"}")
        appendLine("书源类型：$typeName（$typeIndex）")
        content?.apiEndpoints?.takeIf { it.isNotEmpty() }?.let { endpoints ->
            appendLine()
            appendLine("【重要】本站为前端 JS 动态渲染（SPA）站点，静态 HTML 中不含书籍数据。自动发现以下 JSON API 接口，请优先基于这些接口编写 JSONPath 规则：")
            endpoints.forEach { appendLine("- ${it.method} ${it.type}: ${it.url}") }
            endpoints.firstOrNull { it.method == "POST" }?.let {
                appendLine("（注意：${it.type} 接口为 POST 请求，searchUrl 需写成 \"/api/xxx,{method:POST,body:...}\" 形式）")
            }
            appendLine("（占位符说明：URL 中的 {book_id} / {chapter_id} 表示需要从搜索/详情 JSON 中提取的真实 ID。本版 Legado【没有】bookId/chapterId 变量，禁止写成 {{bookId}}；正确做法：ruleSearch.bookUrl 用 JSONPath（如 {{$.book_id}}）或正则拼出含 ID 的完整详情 URL，再基于该 URL 解析详情/目录/正文）")
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
        content?.embeddedJson?.takeIf { it.isNotEmpty() }?.let { jsonList ->
            appendLine()
            appendLine("【重要】页面内嵌 JSON 数据（SSR 站点常见，已从 <script> 中提取，含书籍真实数据，请优先基于这些 JSON 编写 JSONPath 规则）：")
            jsonList.forEachIndexed { i, json ->
                appendLine("---------- 内嵌JSON #${i + 1} ----------")
                appendLine(json)
                appendLine("----------")
            }
        }
        content?.exploreLinks?.takeIf { it.isNotEmpty() }?.let { links ->
            appendLine()
            appendLine("【发现页（分类/榜单/推荐）】已从首页导航中探测到以下链接，请据其编写发现规则（exploreUrl 格式：分类名::URL 每行一个，ruleExplore 的 bookList/name/bookUrl 必填）：")
            links.forEach { (name, url) -> appendLine("- $name :: $url") }
        }
        content?.sampleExplore?.let { se ->
            appendLine()
            if (se.ok) {
                appendLine("首个分类/榜单页真实 HTML（用于编写 ruleExplore 选择器）：")
                appendLine("----------")
                appendLine(se.json)
                appendLine("----------")
            } else {
                appendLine("分类/榜单页探测失败：${se.error}（不影响搜索/详情/目录/正文生成）")
            }
        }
        appendLine()
        appendLine("已抓取到的网页 HTML（预处理后，编码 ${content?.charset}，共 ${content?.length} 字符，已截断）：")
        appendLine("----------")
        val limit = viewModel.promptHtmlLimit
        val htmlForPrompt = if (html.length > limit) {
            html.substring(0, limit) + "\n...(已截断)"
        } else {
            html
        }
        appendLine(htmlForPrompt)
        appendLine("----------")
        appendLine("请分析以上内容，按系统要求输出完整书源 JSON 数组。若提供了 JSON API 接口、内嵌 JSON 或示例响应，请优先使用 JSONPath 规则。")
    }

    private fun validate() {
        val text = binding.etResult.text?.toString()
        if (text.isNullOrBlank()) {
            AiSourceLog.log("WARN", "规则校验", "未生成书源 JSON，已取消")
            toastOnUi("请先生成书源 JSON")
            return
        }
        val checks = AiSourceValidate.validate(text)
        binding.tvChecks.text = checks.joinToString("\n") { c ->
            "${if (c.pass) "✓" else "✗"} ${c.name}${if (c.pass) "" else "：${c.msg}"}"
        }
        val pass = checks.count { it.pass }
        checks.filterNot { it.pass }.forEach {
            AiSourceLog.log("ERROR", "规则校验", "${it.name}：${it.msg}")
        }
        AiSourceLog.log("INFO", "规则校验", "通过 ${pass}/${checks.size} 项")
        toastOnUi("验证完成：$pass/${checks.size} 项通过")
    }

    private fun autoFix() {
        saveConfig()
        if (viewModel.baseUrl.isBlank() || viewModel.apiKey.isBlank()) {
            AiSourceLog.log("WARN", "自动修复", "未填写 AI 接口地址或 API Key，已取消")
            toastOnUi("请先填写 AI 接口地址和 API Key")
            return
        }
        val text = binding.etResult.text?.toString()
        if (text.isNullOrBlank()) {
            AiSourceLog.log("WARN", "自动修复", "未生成书源 JSON，已取消")
            toastOnUi("请先生成书源 JSON")
            return
        }
        val url = binding.etUrl.text?.toString()?.trim()
        if (url.isNullOrEmpty()) {
            AiSourceLog.log("WARN", "自动修复", "未填写网站地址，已取消")
            toastOnUi("请填写网站地址")
            return
        }
        val keyword = binding.etKeyword.text?.toString()?.trim()
        val typeName = binding.spType.selectedItem?.toString() ?: "文本"
        val typeIndex = binding.spType.selectedItemPosition
        val content = htmlContent
        val html = content?.html.orEmpty()
        val userPrompt = buildUserPrompt(url, keyword, typeName, typeIndex, content, html)
        binding.btnAutoFix.isEnabled = false
        binding.tvStatus.text = "正在用真实搜索验证并自动修复，最多 ${viewModel.maxFixRounds} 轮..."
        binding.tvChecks.text = binding.tvStatus.text
        AiSourceLog.log("INFO", "自动修复", "开始，最多 ${viewModel.maxFixRounds} 轮")
        viewModel.execute {
            viewModel.autoFix(
                baseUrl = viewModel.baseUrl,
                apiKey = viewModel.apiKey,
                model = viewModel.model,
                systemPrompt = AiSourceGenerateViewModel.SYSTEM_PROMPT,
                userPrompt = userPrompt,
                sourceJson = text,
                keyword = keyword ?: "",
                exploreLinks = content?.exploreLinks.orEmpty(),
                maxRounds = viewModel.maxFixRounds
            )
        }.onSuccess { result ->
            binding.etResult.setText(result.json)
            AiSourceLog.log(if (result.ok) "SUCCESS" else "ERROR", "自动修复",
                "${result.rounds} 轮${if (result.ok) "通过" else "未通过"}")
            AiSourceLog.raw("-------- 自动修复日志 --------\n${result.log}")
            binding.tvChecks.text = buildString {
                appendLine("自动修复日志：")
                append(result.log)
                append(if (result.ok) "\n✓ 修复成功，已用真实搜索验证通过" else "\n✗ 达到最大轮次仍未通过，请手动检查")
            }
            binding.tvStatus.text = if (result.ok) "修复成功（${result.rounds} 轮）" else "修复未通过（${result.rounds} 轮）"
            toastOnUi(if (result.ok) "修复成功（${result.rounds} 轮）" else "修复未通过（${result.rounds} 轮）")
        }.onError {
            AiSourceLog.log("ERROR", "自动修复", "${it.message}")
            binding.tvChecks.text = "自动修复失败：${it.message}"
            binding.tvStatus.text = "自动修复失败: ${it.message}"
            toastOnUi("自动修复失败: ${it.message}")
        }.onFinally {
            binding.btnAutoFix.isEnabled = true
        }
    }

    private fun importToEditor() {
        val text = binding.etResult.text?.toString()
        if (text.isNullOrBlank()) {
            AiSourceLog.log("WARN", "导入编辑", "未生成书源 JSON，已取消")
            toastOnUi("请先生成书源 JSON")
            return
        }
        val obj = AiSourceValidate.parseSource(text)
        if (obj == null) {
            AiSourceLog.log("ERROR", "导入编辑", "书源格式不正确")
            toastOnUi("导入失败: 书源格式不正确")
            return
        }
        AiSourceLog.log("INFO", "导入编辑", "已导入书源「${obj.get("bookSourceName")?.asString}」")
        startActivity<BookSourceEditActivity> {
            putExtra("sourceJson", obj.toString())
        }
    }

    private fun clearAll() {
        AiSourceLog.log("INFO", "清空", "清空表单")
        htmlContent = null
        binding.etHtmlPreview.setText("")
        binding.tvHtmlHint.visibility = View.GONE
        binding.etResult.setText("")
        binding.tvChecks.text = ""
    }
}
