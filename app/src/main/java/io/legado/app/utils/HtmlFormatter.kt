package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
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
}
