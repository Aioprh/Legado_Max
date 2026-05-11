package io.legado.app.ui.book.read.config

data class HighlightRule(
    var id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var pattern: String = "",
    var sampleText: String = "",
    var group: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    var enabled: Boolean = true,
    var textColor: Int? = null,
    var underlineMode: Int = 0,
    var underlineColor: Int? = null,
) {

    fun styleSummary(): String {
        val parts = ArrayList<String>(3)
        textColor?.let {
            parts.add("字色 ${it.toHexColor()}")
        }
        if (underlineMode != 0) {
            parts.add(
                when (underlineMode) {
                    1 -> "实线下划线"
                    2 -> "虚线下划线"
                    3 -> "波浪下划线"
                    4 -> "标题强调条"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty()
            )
        }
        if (parts.isEmpty()) {
            parts.add("无样式")
        }
        return parts.joinToString(" / ")
    }

    fun displayPattern(): String {
        return pattern.ifBlank { ".*" }
    }

    fun normalizedSampleText(): String {
        return sampleText.ifBlank {
            "她轻声说：“今晚就出发。”\n最近在重读《百年孤独》（重刷版），节奏很稳。"
        }
    }

    fun copyWithNewId(): HighlightRule {
        return copy(id = "${System.currentTimeMillis()}_${name.hashCode()}")
    }

    companion object {
        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
