package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.apache.commons.text.StringEscapeUtils
import java.net.URL
import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape")
object HtmlFormatter {
    private val nbspRegex = "(&nbsp;)+".toRegex()
    private val espRegex = "(&ensp;|&emsp;)".toRegex()
    private val noPrintRegex = "(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)".toRegex()
    private val wrapHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
    private val commentRegex = "<!--[^>]*-->".toRegex() //注释
    private val notImgHtmlRegex = "</?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val otherHtmlRegex = "</?[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val formatImagePattern = Pattern.compile(
        "<img[^>]*\\ssrc\\s*=\\s*['\"]([^'\"{>]*\\{(?:[^{}]|\\{[^}>]+\\})+\\})['\"][^>]*>|<img[^>]*\\sdata-(?:src|original|srcset)\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>|<img[^>]*\\ssrc\\s*=\\s*\"([^\">]+)\"[^>]*>|<img[^>]*\\s(?:data-[^=>]*|src)=\\s*['\"]([^'\">]*)['\"][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    /** 内部协议图片（段评气泡 dp:/bubble:// 等）：不参与 URL 拼接，整段原样保留 */
    private val internalImgPattern = Pattern.compile(
        "<img[^>]*\\ssrc\\s*=\\s*['\"](?:(?:dp|bubble):[^>]*?)['\"][^>]*>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )
    private val indent1Regex = "\\s*\\n+\\s*".toRegex()
    private val indent2Regex = "^[\\n\\s]+".toRegex()
    private val lastRegex = "[\\n\\s]+$".toRegex()

    fun format(html: String?, otherRegex: Regex = otherHtmlRegex): String {
        html ?: return ""
        return html.replace(nbspRegex, " ")
            .replace(espRegex, " ")
            .replace(noPrintRegex, "")
            .replace(wrapHtmlRegex, "\n")
            .replace(commentRegex, "")
            .replace(otherRegex, "")
            .replace(indent1Regex, "\n　　")
            .replace(indent2Regex, "　　")
            .replace(lastRegex, "")
    }

    fun formatKeepImg(html: String?, redirectUrl: URL? = null): String {
        html ?: return ""
        val keepImgHtml = format(html, notImgHtmlRegex)

        // 内部协议（段评气泡 dp:/bubble:// 等）不参与 URL 拼接，整段原样保留；
        // 否则 formatImagePattern 会把含引号/大括号的 src 截断，导致气泡点击脚本丢失
        val internalImgMap = HashMap<String, String>()
        val protectedSb = StringBuilder()
        val internalMatcher = internalImgPattern.matcher(keepImgHtml)
        var internalPos = 0
        while (internalMatcher.find()) {
            protectedSb.append(keepImgHtml, internalPos, internalMatcher.start())
            val placeholder = "\u0000internal_img_${internalImgMap.size}\u0000"
            internalImgMap[placeholder] = internalMatcher.group()
            protectedSb.append(placeholder)
            internalPos = internalMatcher.end()
        }
        protectedSb.append(keepImgHtml, internalPos, keepImgHtml.length)
        val protectedHtml = protectedSb.toString()

        //正则的“|”处于顶端而不处于（）中时，具有类似||的熔断效果，故以此机制简化原来的代码
        val matcher = formatImagePattern.matcher(protectedHtml)
        var appendPos = 0
        val sb = StringBuilder()
        while (matcher.find()) {
            var param = ""
            sb.append(
                protectedHtml.substring(appendPos, matcher.start()), "<img src=\"${
                    NetworkUtils.getAbsoluteURL(
                        redirectUrl,
                        matcher.group(1)?.let {
                            val urlMatcher = AnalyzeUrl.paramPattern.matcher(it)
                            if (urlMatcher.find()) {
                                param = ',' + it.substring(urlMatcher.end())
                                it.substring(0, urlMatcher.start())
                            } else it
                        } ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4)!!
                    ) + param
                }\">"
            )
            appendPos = matcher.end()
        }
        if (appendPos < protectedHtml.length) sb.append(
            protectedHtml.substring(
                appendPos,
                protectedHtml.length
            )
        )
        var result = sb.toString()
        internalImgMap.forEach { (placeholder, original) ->
            result = result.replace(placeholder, original)
        }
        return result
    }

    private val srcAttrRegex = Regex("""\bsrc\s*=\s*['"]""", RegexOption.IGNORE_CASE)

    /**
     * 解码 HTML 实体，但保护段评气泡（dp:/bubble:）等内部协议图片标签。
     *
     * 若直接对整个正文执行 StringEscapeUtils.unescapeHtml4，气泡点击脚本里的
     * URL 参数如 &paragraph_id= 会被误当作 HTML 实体 &paragraph;（¶）解码成
     * ¶graph_id=，导致段评弹窗请求到错误地址。这里先把内部气泡 img 标签整体
     * 替换为占位符，解码完再还原。
     */
    fun unescapeHtml4KeepImg(html: String?): String {
        html ?: return ""
        if (html.indexOf('&') < 0) return html
        val map = HashMap<String, String>()
        val protected = protectInternalImgTags(html, map)
        var result = StringEscapeUtils.unescapeHtml4(protected)
        map.forEach { (placeholder, original) ->
            result = result.replace(placeholder, original)
        }
        return result
    }

    /**
     * 将正文中的内部协议气泡图片标签（<img ... src="dp:..."> 或 bubble:）整体替换为占位符。
     * src 值内可能包含 >、双引号、反斜杠等（气泡点击 JS），因此不用 [^>]* 匹配，
     * 而是扫描到「未转义的结束引号之后紧跟 > 或 />」作为标签结束。
     */
    private fun protectInternalImgTags(html: String, map: HashMap<String, String>): String {
        val sb = StringBuilder()
        var pos = 0
        var searchFrom = 0
        while (true) {
            val imgStart = html.indexOf("<img", searchFrom, ignoreCase = true)
            if (imgStart < 0) break
            val end = internalImgTagEnd(html, imgStart)
            if (end > imgStart) {
                sb.append(html, pos, imgStart)
                val placeholder = "\u0000internal_img_${map.size}\u0000"
                map[placeholder] = html.substring(imgStart, end)
                sb.append(placeholder)
                pos = end
                searchFrom = end
            } else {
                val nextGt = html.indexOf('>', imgStart)
                if (nextGt < 0) break
                searchFrom = nextGt + 1
            }
        }
        sb.append(html, pos, html.length)
        return sb.toString()
    }

    /** 返回内部协议 img 标签的结束下标（不含），非内部气泡图返回 -1 */
    private fun internalImgTagEnd(html: String, tagStart: Int): Int {
        val srcMatch = srcAttrRegex.find(html, tagStart) ?: return -1
        val quoteChar = html[srcMatch.range.last]
        val valueStart = srcMatch.range.last + 1
        if (valueStart >= html.length) return -1
        val value = html.substring(valueStart)
        if (!value.startsWith("dp:", ignoreCase = true) &&
            !value.startsWith("bubble:", ignoreCase = true)
        ) {
            return -1
        }
        var j = valueStart
        while (j < html.length) {
            val c = html[j]
            if (c == '\\') {
                j += 2
                continue
            }
            if (c == quoteChar) {
                var k = j + 1
                while (k < html.length && (html[k] == ' ' || html[k] == '/')) k++
                if (k < html.length && html[k] == '>') return k + 1
            }
            j++
        }
        return -1
    }
}
