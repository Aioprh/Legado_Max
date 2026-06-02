package io.legado.app.help

data class HelpDoc(
    val fileName: String,
    val displayName: String
)

data class HelpDocGroup(
    val displayName: String,
    val docs: List<HelpDoc>
)

data class HelpDocSelectorItem(
    val displayName: String,
    val doc: HelpDoc? = null
) {
    val isHeader: Boolean
        get() = doc == null
}
